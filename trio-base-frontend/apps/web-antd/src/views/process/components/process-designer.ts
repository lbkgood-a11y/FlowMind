import { validateFormDefinition } from './process-form';

export type ParticipantType = 'DEPT' | 'ROLE' | 'USER';

export interface ParticipantAssignment {
  deptCode?: string;
  dimensionCode?: string;
  roleCode?: string;
  type: ParticipantType;
  userId?: string;
}

interface FlowNode {
  assignment?: ParticipantAssignment;
  id?: string;
  name?: string;
  next?: Array<{ condition?: string; target?: string }>;
  strategy?: string;
  type?: string;
}

const SUPPORTED_NODE_TYPES = new Set([
  'APPROVAL',
  'COUNTERSIGN',
  'END',
  'START',
]);

export function buildParticipantAssignment(
  type: ParticipantType,
  value: string,
  dimensionCode?: string,
): ParticipantAssignment {
  const normalized = value.trim();
  if (type === 'ROLE') return { roleCode: normalized || undefined, type };
  if (type === 'DEPT') {
    return {
      deptCode: normalized || undefined,
      dimensionCode: dimensionCode?.trim() || undefined,
      type,
    };
  }
  return { type, userId: normalized || undefined };
}

export function participantAssignmentValue(assignment?: ParticipantAssignment) {
  if (!assignment) return '';
  if (assignment.type === 'ROLE') return assignment.roleCode ?? '';
  if (assignment.type === 'DEPT') return assignment.deptCode ?? '';
  return assignment.userId ?? '';
}

export function validateProcessDefinition(processJson: string): string[] {
  let root: any;
  try {
    root = JSON.parse(processJson);
  } catch {
    return ['流程定义不是合法 JSON'];
  }

  const nodes = root?.flow?.nodes as FlowNode[] | undefined;
  if (!Array.isArray(nodes) || nodes.length === 0) {
    return ['流程至少需要一个节点'];
  }

  const errors: string[] = [];
  const ids = new Set<string>();
  const nodeById = new Map<string, FlowNode>();
  for (const node of nodes) {
    if (!node.id?.trim()) {
      errors.push('存在缺少 ID 的节点');
      continue;
    }
    if (ids.has(node.id)) errors.push(`节点 ID 重复：${node.id}`);
    ids.add(node.id);
    nodeById.set(node.id, node);
    if (!node.type || !SUPPORTED_NODE_TYPES.has(node.type)) {
      errors.push(`节点 ${node.id} 使用了未支持类型 ${node.type || '-'}`);
    }
    if (node.type === 'APPROVAL' || node.type === 'COUNTERSIGN') {
      if (!isValidAssignment(node.assignment)) {
        errors.push(`节点 ${node.id} 缺少有效参与者`);
      }
    }
    if (
      node.type === 'COUNTERSIGN' &&
      node.strategy !== 'ALL' &&
      node.strategy !== 'ANY'
    ) {
      errors.push(`会签节点 ${node.id} 必须配置 ALL 或 ANY`);
    }
  }

  const starts = nodes.filter((node) => node.type === 'START');
  const ends = nodes.filter((node) => node.type === 'END');
  if (starts.length !== 1) errors.push('流程必须且只能包含一个 START');
  if (ends.length === 0) errors.push('流程至少需要一个 END');

  for (const node of nodes) {
    const next = node.next ?? [];
    if (node.type !== 'END' && next.length === 0) {
      errors.push(`节点 ${node.id || '-'} 没有后续连线`);
    }
    if (next.length > 0) {
      const defaultCount = next.filter(
        (edge) => edge.condition?.trim() === 'true',
      ).length;
      if (defaultCount !== 1) {
        errors.push(`节点 ${node.id || '-'} 必须配置一个 true 默认分支`);
      }
    }
    for (const edge of next) {
      if (!edge.target || !nodeById.has(edge.target)) {
        errors.push(`节点 ${node.id || '-'} 存在无效目标 ${edge.target || '-'}`);
      }
      if (!isSafeCondition(edge.condition)) {
        errors.push(`节点 ${node.id || '-'} 存在无效条件表达式`);
      }
    }
  }

  if (starts[0]?.id) {
    const reachable = new Set<string>();
    const queue = [starts[0].id];
    while (queue.length > 0) {
      const id = queue.shift()!;
      if (reachable.has(id)) continue;
      reachable.add(id);
      for (const edge of nodeById.get(id)?.next ?? []) {
        if (edge.target) queue.push(edge.target);
      }
    }
    for (const id of ids) {
      if (!reachable.has(id)) errors.push(`节点 ${id} 从 START 不可达`);
    }
  }

  const schemaJson = root?.form?.schema
    ? JSON.stringify(root.form.schema)
    : undefined;
  const uiSchemaJson = root?.form?.uiSchema
    ? JSON.stringify(root.form.uiSchema)
    : undefined;
  errors.push(...validateFormDefinition(schemaJson, uiSchemaJson));
  return [...new Set(errors)];
}

function isValidAssignment(assignment?: ParticipantAssignment) {
  if (!assignment) return false;
  if (assignment.type === 'ROLE') return Boolean(assignment.roleCode?.trim());
  if (assignment.type === 'DEPT') return Boolean(assignment.deptCode?.trim());
  if (assignment.type === 'USER') return Boolean(assignment.userId?.trim());
  return false;
}

function isSafeCondition(condition?: string) {
  if (!condition?.trim() || condition.length > 1000) return false;
  return !/[;{}]|\b(class|new|runtime|system)\b/i.test(condition);
}
