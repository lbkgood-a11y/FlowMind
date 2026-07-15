import type { ProcessApi } from '#/api/process';

import { validateFormDefinition } from './process-form';

export type ParticipantType = 'DEPT' | 'ROLE' | 'USER';
export type BusinessRefSourceType =
  | 'API_INPUT'
  | 'FIXED'
  | 'FORM_FIELD'
  | 'PAGE_CONTEXT'
  | 'PROCESS_CONTEXT';
export type ConditionOperator = 'EQ' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'NE' | 'TRUE';

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

export interface BusinessClosureDesignerConfig {
  agentActionCode?: string;
  allowedStatuses: string[];
  approvedEventCode?: string;
  approvedStatus: string;
  approveActionCode?: string;
  businessRefContextKey?: string;
  businessRefFieldKey?: string;
  businessRefFixedValue?: string;
  businessRefSourceType: BusinessRefSourceType;
  conditionFieldKey?: string;
  conditionOperator?: ConditionOperator;
  conditionValue?: string;
  createActionCode?: string;
  launchMode: 'CREATE_AND_LAUNCH' | 'EXISTING_DOCUMENT';
  name: string;
  notifyActionCode?: string;
  participantDimensionCode?: string;
  participantType?: ParticipantType;
  participantValue?: string;
  processKey: string;
  rejectedEventCode?: string;
  rejectedStatus: string;
  retryClosureActionCode?: string;
  selectedFormKey?: string;
  startStatus: string;
  submitActionCode?: string;
  updateStatusActionCode: string;
  viewActionCode?: string;
}

export interface CatalogFormField {
  key: string;
  label: string;
  required: boolean;
  source: string;
  type: string;
}

export interface BusinessClosureCheck {
  key: string;
  label: string;
  location: string;
  message: string;
  ok: boolean;
}

export interface RestoredBusinessClosureDefinition {
  config: Partial<BusinessClosureDesignerConfig>;
  typeCode?: string;
}

export function existingProcessDesignerMode(
  status: string | undefined,
  canUpdate: boolean,
) {
  const existing = Boolean(status);
  const readOnly = existing && status !== 'DRAFT';
  return {
    canSave: !existing || (status === 'DRAFT' && canUpdate),
    readOnly,
  };
}

export function restoreBusinessClosureDefinition(
  processJson: string,
): RestoredBusinessClosureDefinition {
  try {
    const root = JSON.parse(processJson);
    const launch = root?.launchPolicy ?? {};
    const permissions = root?.permissionPolicy ?? {};
    const businessRef = root?.businessObject?.businessRef ?? launch?.businessRef ?? {};
    const approved = root?.closurePolicy?.outcomes?.APPROVED ?? [];
    const rejected = root?.closurePolicy?.outcomes?.REJECTED ?? [];
    const approvedStatusEffect = approved.find((item: any) => item?.params?.status);
    const rejectedStatusEffect = rejected.find((item: any) => item?.params?.status);
    const approvedEvent = approved.find((item: any) => item?.eventCode);
    const rejectedEvent = rejected.find((item: any) => item?.eventCode);
    const notify = approved.find(
      (item: any) => item?.actionCode && !item?.params?.status,
    );
    const agent = approved.find((item: any) => item?.agentActionCode);
    const approvalNode = root?.flow?.nodes?.find(
      (item: any) => item?.type === 'APPROVAL' || item?.type === 'COUNTERSIGN',
    );
    const assignment = approvalNode?.assignment as ParticipantAssignment | undefined;
    const condition = root?.flow?.nodes
      ?.flatMap((item: any) => item?.next ?? [])
      .map((item: any) => item?.condition)
      .find((item: unknown) => typeof item === 'string' && item !== 'true');
    const parsedCondition = parseCondition(String(condition ?? ''));
    return {
      config: {
        agentActionCode: agent?.agentActionCode,
        allowedStatuses: Array.isArray(launch.allowedStatuses)
          ? launch.allowedStatuses
          : undefined,
        approvedEventCode: approvedEvent?.eventCode,
        approvedStatus: approvedStatusEffect?.params?.status,
        approveActionCode: permissions.approveActionCode,
        businessRefContextKey: businessRef.contextKey,
        businessRefFieldKey: businessRef.fieldKey,
        businessRefFixedValue: businessRef.fixedValue,
        businessRefSourceType: businessRef.sourceType,
        conditionFieldKey: parsedCondition?.fieldKey,
        conditionOperator: parsedCondition?.operator,
        conditionValue: parsedCondition?.value,
        createActionCode: launch.createActionCode,
        launchMode: launch.modes?.[0],
        name: root?.name,
        notifyActionCode: notify?.actionCode,
        participantDimensionCode: assignment?.dimensionCode,
        participantType: assignment?.type,
        participantValue: participantAssignmentValue(assignment),
        processKey: root?.processKey,
        rejectedEventCode: rejectedEvent?.eventCode,
        rejectedStatus: rejectedStatusEffect?.params?.status,
        retryClosureActionCode: permissions.retryClosureActionCode,
        startStatus: launch.startEffects?.[0]?.params?.status,
        submitActionCode: launch.submitActionCode ?? permissions.submitActionCode,
        updateStatusActionCode:
          approvedStatusEffect?.actionCode ?? launch.startEffects?.[0]?.actionCode,
        viewActionCode: permissions.viewActionCode,
      },
      typeCode: root?.businessObject?.typeCode ?? launch?.businessObjectType,
    };
  } catch {
    return { config: {} };
  }
}

function parseCondition(condition: string) {
  const match = condition.match(/^\s*([\w.]+)\s*(==|!=|>=|<=|>|<)\s*(.+?)\s*$/);
  if (!match) return undefined;
  const operator = {
    '!=': 'NE',
    '<': 'LT',
    '<=': 'LTE',
    '==': 'EQ',
    '>': 'GT',
    '>=': 'GTE',
  }[match[2]!] as ConditionOperator;
  return {
    fieldKey: match[1],
    operator,
    value: match[3]?.replace(/^['"]|['"]$/g, ''),
  };
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

export function buildBusinessClosureProcessDefinition(
  catalog: ProcessApi.BusinessObjectCatalog,
  config: BusinessClosureDesignerConfig,
  flowJson: string,
): string {
  const flow = normalizedFlow(flowJson, config);
  const businessRef = buildBusinessRef(config);
  const formSchema = buildFormSchema(catalog, config);
  const startEffects = statusEffect(
    'launch.updateStatus',
    config.updateStatusActionCode,
    config.startStatus,
    'HARD',
  );
  const approvedEffects = [
    statusEffect(
      'approved.updateStatus',
      config.updateStatusActionCode,
      config.approvedStatus,
      'HARD',
    ),
    eventEffect('approved.event', config.approvedEventCode),
    actionEffect('approved.notify', config.notifyActionCode, 'ASYNC', {
      channel: 'INTERNAL',
      recipient: 'INITIATOR',
    }),
    agentEffect(catalog, config),
  ].filter(Boolean);
  const rejectedEffects = [
    statusEffect(
      'rejected.updateStatus',
      config.updateStatusActionCode,
      config.rejectedStatus,
      'HARD',
    ),
    eventEffect('rejected.event', config.rejectedEventCode),
    actionEffect('rejected.notify', config.notifyActionCode, 'ASYNC', {
      channel: 'INTERNAL',
      recipient: 'INITIATOR',
    }),
  ].filter(Boolean);

  const definition: Record<string, unknown> = {
    businessObject: {
      businessRef,
      typeCode: catalog.object.typeCode,
    },
    category: 'business',
    closurePolicy: {
      businessObjectType: catalog.object.typeCode,
      businessRef,
      outcomes: {
        APPROVED: approvedEffects,
        REJECTED: rejectedEffects,
      },
    },
    flow,
    form: formSchema ? { schema: formSchema, uiSchema: {} } : undefined,
    launchPolicy: {
      allowedStatuses: config.allowedStatuses,
      businessObjectType: catalog.object.typeCode,
      businessRef,
      modes: [config.launchMode],
      startEffects: [startEffects],
      submitActionCode: config.submitActionCode,
      ...(config.launchMode === 'CREATE_AND_LAUNCH' && config.createActionCode
        ? { createActionCode: config.createActionCode }
        : {}),
    },
    name: config.name,
    permissionPolicy: {
      agentFollowUpActionCode: permissionExists(catalog, 'agentFollowUp')
        ? 'agentFollowUp'
        : undefined,
      approveActionCode: config.approveActionCode,
      retryClosureActionCode: config.retryClosureActionCode,
      submitActionCode: config.submitActionCode,
      viewActionCode: config.viewActionCode,
    },
    processKey: config.processKey,
    version: '1.0.0',
  };

  if (config.agentActionCode) {
    const agentAction = catalog.agentActions.find(
      (item) => item.agentActionCode === config.agentActionCode,
    );
    definition.agentFollowUpPolicy = {
      actions: [
        {
          agentActionCode: config.agentActionCode,
          effectKey: `approved.agent.${config.agentActionCode}`,
          mode: 'ASYNC',
          params: defaultAgentParams(agentAction?.paramSchemaJson, config),
        },
      ],
    };
  }

  return JSON.stringify(pruneUndefined(definition), null, 2);
}

export function businessClosureCompletionChecks(
  catalog: ProcessApi.BusinessObjectCatalog | undefined,
  config: BusinessClosureDesignerConfig,
): BusinessClosureCheck[] {
  const issues = businessClosureValidationIssues(catalog, config);
  const hasIssue = (key: string) => issues.some((item) => item.key === key);
  return [
    {
      key: 'businessObject',
      label: '业务对象',
      location: '业务对象',
      message: hasIssue('businessObject') ? '请选择已发布业务对象' : '已选择业务对象',
      ok: Boolean(catalog?.object?.typeCode),
    },
    {
      key: 'formBinding',
      label: '表单绑定',
      location: '业务对象 / 表单',
      message: hasIssue('formBinding') ? '请选择发起表单和业务编号来源' : '表单与业务编号来源已配置',
      ok: Boolean(config.selectedFormKey)
        && businessRefConfigured(config)
        && !hasIssue('formBinding'),
    },
    {
      key: 'launchPolicy',
      label: '发起策略',
      location: '发起',
      message: hasIssue('launchPolicy') ? '请选择允许发起状态和发起后状态' : '发起策略已配置',
      ok: config.allowedStatuses.length > 0 && Boolean(config.startStatus),
    },
    {
      key: 'permissionPolicy',
      label: '权限矩阵',
      location: '权限',
      message: hasIssue('permissionPolicy') ? '提交、查看、办理、重试权限需要完整选择' : '权限矩阵已配置',
      ok: Boolean(
        config.submitActionCode
          && config.viewActionCode
          && config.approveActionCode
          && config.retryClosureActionCode,
      ),
    },
    {
      key: 'closurePolicy',
      label: '闭环动作',
      location: '闭环',
      message: hasIssue('closurePolicy') ? '通过/驳回闭环状态或动作未完整' : '闭环动作已配置',
      ok: Boolean(config.updateStatusActionCode && config.approvedStatus && config.rejectedStatus),
    },
    {
      key: 'agentEffect',
      label: 'Agent 参数',
      location: '闭环 / Agent follow-up',
      message: hasIssue('agentEffect') ? 'Agent 必填参数需要绑定字段或业务编号' : 'Agent 参数已配置',
      ok: !hasIssue('agentEffect'),
    },
    {
      key: 'conditionAndParticipant',
      label: '流程图配置',
      location: '流程图',
      message: hasIssue('conditionAndParticipant') ? '条件分支或参与人选择未完整' : '流程图配置已补齐',
      ok: !hasIssue('conditionAndParticipant'),
    },
  ];
}

export function businessClosureValidationIssues(
  catalog: ProcessApi.BusinessObjectCatalog | undefined,
  config: BusinessClosureDesignerConfig,
): BusinessClosureCheck[] {
  const issues: BusinessClosureCheck[] = [];
  const push = (key: string, label: string, location: string, message: string) => {
    issues.push({ key, label, location, message, ok: false });
  };

  if (!catalog?.object?.typeCode) {
    push('businessObject', '业务对象', '业务对象', '请先选择业务对象');
  }
  if (!config.selectedFormKey) {
    push('formBinding', '表单绑定', '业务对象 / 表单', '请选择用于发起或查看的业务表单');
  }
  if (!businessRefConfigured(config)) {
    push('formBinding', '业务编号来源', '业务对象 / 业务编号来源', '请选择业务编号来源所需的字段、上下文键或固定值');
  }
  if (
    config.businessRefSourceType === 'FORM_FIELD'
    && config.businessRefFieldKey
    && !catalogFormFields(catalog).some((field) => field.key === config.businessRefFieldKey)
  ) {
    push('formBinding', '业务编号字段', '业务对象 / 业务编号来源', '选择的业务编号字段不在当前表单字段中');
  }
  if (config.allowedStatuses.length === 0 || !config.startStatus) {
    push('launchPolicy', '发起策略', '发起', '请选择允许发起状态和发起后状态');
  }
  if (config.launchMode === 'CREATE_AND_LAUNCH' && !config.createActionCode) {
    push('launchPolicy', '创建动作', '发起 / 创建动作', '新建并发起需要选择创建单据动作');
  }
  if (
    !config.submitActionCode
    || !config.viewActionCode
    || !config.approveActionCode
    || !config.retryClosureActionCode
  ) {
    push('permissionPolicy', '权限矩阵', '权限', '提交、查看、办理、重试闭环权限需要完整选择');
  }
  if (!config.updateStatusActionCode || !config.approvedStatus || !config.rejectedStatus) {
    push('closurePolicy', '闭环动作', '闭环', '请配置通过/驳回后的状态回写动作和目标状态');
  }
  if (config.agentActionCode && missingAgentParamKeys(catalog, config).length > 0) {
    push('agentEffect', 'Agent 参数', '闭环 / Agent follow-up', 'Agent 必填参数需要绑定字段或业务编号');
  }
  if (
    config.conditionOperator
    && config.conditionOperator !== 'TRUE'
    && (!config.conditionFieldKey || !config.conditionValue?.trim())
  ) {
    push('conditionAndParticipant', '条件分支', '流程图 / 条件', '条件分支需要选择字段、操作符和值');
  }
  if (config.participantType && !config.participantValue?.trim()) {
    push('conditionAndParticipant', '参与人', '流程图 / 参与人', '办理人选择需要指定角色、部门或用户');
  }
  return issues;
}

function normalizedFlow(flowJson: string, config: BusinessClosureDesignerConfig) {
  let nodes: FlowNode[] = defaultFlowNodes();
  const defaultAssignment = defaultParticipantAssignment(config);
  const builtCondition = buildSafeCondition(
    config.conditionFieldKey,
    config.conditionOperator,
    config.conditionValue,
  );
  try {
    const parsed = JSON.parse(flowJson);
    if (Array.isArray(parsed?.flow?.nodes) && parsed.flow.nodes.length > 0) {
      nodes = parsed.flow.nodes;
    }
  } catch {
    nodes = defaultFlowNodes();
  }
  return {
    nodes: nodes.map((node) => ({
      ...node,
      assignment:
        (node.type === 'APPROVAL' || node.type === 'COUNTERSIGN')
          ? node.assignment ?? defaultAssignment
          : node.assignment,
      next: node.next?.map((edge) => ({
        ...edge,
        condition:
          edge.condition === '条件' && builtCondition
            ? builtCondition
            : edge.condition === '通过' || edge.condition === '条件'
            ? 'true'
            : edge.condition || 'true',
      })),
    })),
  };
}

function defaultFlowNodes(): FlowNode[] {
  return [
    {
      id: 'start',
      name: '开始',
      next: [{ condition: 'true', target: 'approve1' }],
      type: 'START',
    },
    {
      assignment: { roleCode: 'DEPT_HEAD', type: 'ROLE' },
      id: 'approve1',
      name: '部门审批',
      next: [{ condition: 'true', target: 'end' }],
      type: 'APPROVAL',
    },
    { id: 'end', name: '结束', type: 'END' },
  ];
}

export function catalogFormFields(
  catalog: ProcessApi.BusinessObjectCatalog | undefined,
): CatalogFormField[] {
  if (!catalog) return [];
  const fields = new Map<string, CatalogFormField>();
  const addSchema = (schemaJson: string | undefined, source: string) => {
    const schema = parseJsonObject(schemaJson);
    const properties = schema?.properties;
    if (!properties || typeof properties !== 'object') return;
    const required = new Set(
      Array.isArray(schema.required)
        ? schema.required.filter((item: unknown): item is string => typeof item === 'string')
        : [],
    );
    Object.entries(properties as Record<string, any>).forEach(([key, value]) => {
      if (!key.trim() || fields.has(key)) return;
      fields.set(key, {
        key,
        label: value?.title || key,
        required: required.has(key),
        source,
        type: value?.type || 'string',
      });
    });
  };

  catalog.actions.forEach((item) => addSchema(item.paramSchemaJson, item.displayName));
  catalog.events.forEach((item) => addSchema(item.payloadSchemaJson, item.displayName));
  catalog.agentActions.forEach((item) => addSchema(item.paramSchemaJson, item.displayName));
  return [...fields.values()];
}

export function buildSafeCondition(
  fieldKey?: string,
  operator: ConditionOperator = 'TRUE',
  value?: string,
) {
  if (!operator || operator === 'TRUE') return 'true';
  if (!fieldKey?.trim() || !value?.trim()) return undefined;
  const literal = conditionLiteral(value);
  const op = {
    EQ: '==',
    GT: '>',
    GTE: '>=',
    LT: '<',
    LTE: '<=',
    NE: '!=',
    TRUE: '==',
  }[operator];
  return `${fieldKey.trim()} ${op} ${literal}`;
}

export function participantDisplayName(
  type: ParticipantType | undefined,
  value: string | undefined,
  options: Array<{ label: string; type: ParticipantType; value: string }>,
) {
  if (!type || !value) return '-';
  return options.find((item) => item.type === type && item.value === value)?.label ?? value;
}

export function businessActionDisplayName(
  catalog: ProcessApi.BusinessObjectCatalog | undefined,
  code: string | undefined,
) {
  if (!catalog || !code) return code || '-';
  return catalog.actions.find((item) => item.actionCode === code)?.displayName
    ?? catalog.permissions.find((item) => item.actionCode === code)?.displayName
    ?? catalog.events.find((item) => item.eventCode === code)?.displayName
    ?? catalog.agentActions.find((item) => item.agentActionCode === code)?.displayName
    ?? code;
}

function buildBusinessRef(config: BusinessClosureDesignerConfig) {
  if (config.businessRefSourceType === 'FORM_FIELD') {
    return {
      fieldKey: config.businessRefFieldKey,
      sourceType: 'FORM_FIELD',
    };
  }
  if (config.businessRefSourceType === 'PAGE_CONTEXT') {
    return {
      contextKey: config.businessRefContextKey || 'businessId',
      sourceType: 'PAGE_CONTEXT',
    };
  }
  if (config.businessRefSourceType === 'PROCESS_CONTEXT') {
    return {
      contextKey: config.businessRefContextKey || 'businessId',
      sourceType: 'PROCESS_CONTEXT',
    };
  }
  if (config.businessRefSourceType === 'FIXED') {
    return {
      fixedValue: config.businessRefFixedValue,
      sourceType: 'FIXED',
    };
  }
  return {
    sourceType: 'API_INPUT',
  };
}

function businessRefConfigured(config: BusinessClosureDesignerConfig) {
  if (config.businessRefSourceType === 'FORM_FIELD') {
    return Boolean(config.businessRefFieldKey);
  }
  if (
    config.businessRefSourceType === 'PAGE_CONTEXT'
    || config.businessRefSourceType === 'PROCESS_CONTEXT'
  ) {
    return Boolean((config.businessRefContextKey || 'businessId').trim());
  }
  if (config.businessRefSourceType === 'FIXED') {
    return Boolean(config.businessRefFixedValue?.trim());
  }
  return true;
}

function buildFormSchema(
  catalog: ProcessApi.BusinessObjectCatalog,
  config: BusinessClosureDesignerConfig,
) {
  if (!config.selectedFormKey) return undefined;
  const fields = catalogFormFields(catalog);
  const createSchema = parseJsonObject(
    catalog.actions.find((item) => item.actionCode === config.createActionCode)
      ?.paramSchemaJson
      ?? catalog.actions.find((item) => item.actionType === 'CREATE_DOCUMENT')?.paramSchemaJson,
  );
  const properties: Record<string, unknown> = {
    ...(createSchema?.properties && typeof createSchema.properties === 'object'
      ? createSchema.properties
      : {}),
  };
  if (config.businessRefSourceType === 'FORM_FIELD' && config.businessRefFieldKey) {
    const field = fields.find((item) => item.key === config.businessRefFieldKey);
    properties[config.businessRefFieldKey] = properties[config.businessRefFieldKey] ?? {
      title: field?.label ?? config.businessRefFieldKey,
      type: field?.type === 'number' || field?.type === 'integer' ? field.type : 'string',
    };
  }
  const required = new Set<string>(
    Array.isArray(createSchema?.required)
      ? createSchema.required.filter((item: unknown): item is string => typeof item === 'string')
      : [],
  );
  if (config.businessRefSourceType === 'FORM_FIELD' && config.businessRefFieldKey) {
    required.add(config.businessRefFieldKey);
  }
  if (Object.keys(properties).length === 0) return undefined;
  return {
    additionalProperties: false,
    properties,
    required: [...required],
    title: catalog.forms.find((item) => item.formKey === config.selectedFormKey)?.displayName
      ?? catalog.object.displayName,
    type: 'object',
  };
}

function agentEffect(
  catalog: ProcessApi.BusinessObjectCatalog,
  config: BusinessClosureDesignerConfig,
) {
  if (!config.agentActionCode) return undefined;
  const agentAction = catalog.agentActions.find(
    (item) => item.agentActionCode === config.agentActionCode,
  );
  return {
    agentActionCode: config.agentActionCode,
    effectKey: `approved.agent.${config.agentActionCode}`,
    mode: 'ASYNC',
    params: defaultAgentParams(agentAction?.paramSchemaJson, config),
  };
}

function defaultParticipantAssignment(config: BusinessClosureDesignerConfig) {
  if (!config.participantType || !config.participantValue?.trim()) return undefined;
  return buildParticipantAssignment(
    config.participantType,
    config.participantValue,
    config.participantDimensionCode,
  );
}

function statusEffect(effectKey: string, actionCode: string, status: string, mode: string) {
  return {
    actionCode,
    effectKey,
    mode,
    params: { status },
  };
}

function actionEffect(
  effectKey: string,
  actionCode: string | undefined,
  mode: string,
  params: Record<string, unknown>,
) {
  if (!actionCode) return undefined;
  return {
    actionCode,
    effectKey,
    mode,
    params,
  };
}

function eventEffect(effectKey: string, eventCode?: string) {
  if (!eventCode) return undefined;
  return {
    effectKey,
    eventCode,
    mode: 'ASYNC',
  };
}

function permissionExists(catalog: ProcessApi.BusinessObjectCatalog, actionCode: string) {
  return catalog.permissions.some((permission) => permission.actionCode === actionCode);
}

function defaultAgentParams(
  paramSchemaJson: string | undefined,
  config: BusinessClosureDesignerConfig,
) {
  if (!paramSchemaJson) return {};
  try {
    const schema = JSON.parse(paramSchemaJson);
    const required = Array.isArray(schema?.required) ? schema.required : [];
    const businessRef = buildBusinessRef(config);
    return Object.fromEntries(
      required.map((key: string) => [
        key,
        key === 'businessId'
          ? { refSource: businessRef, sourceType: 'BUSINESS_REF' }
          : { fieldKey: key, sourceType: 'FORM_FIELD' },
      ]),
    );
  } catch {
    return {};
  }
}

function missingAgentParamKeys(
  catalog: ProcessApi.BusinessObjectCatalog | undefined,
  config: BusinessClosureDesignerConfig,
) {
  if (!catalog || !config.agentActionCode) return [];
  const action = catalog.agentActions.find(
    (item) => item.agentActionCode === config.agentActionCode,
  );
  const params = defaultAgentParams(action?.paramSchemaJson, config);
  return requiredKeys(action?.paramSchemaJson).filter((key) => !params[key]);
}

function requiredKeys(schemaJson?: string) {
  const schema = parseJsonObject(schemaJson);
  return Array.isArray(schema?.required)
    ? schema.required.filter((item: unknown): item is string => typeof item === 'string')
    : [];
}

function parseJsonObject(json?: string): Record<string, any> | undefined {
  if (!json) return undefined;
  try {
    const parsed = JSON.parse(json);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : undefined;
  } catch {
    return undefined;
  }
}

function conditionLiteral(value: string) {
  const trimmed = value.trim();
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return trimmed;
  if (trimmed === 'true' || trimmed === 'false') return trimmed;
  return JSON.stringify(trimmed);
}

function pruneUndefined(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(pruneUndefined);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, entry]) => entry !== undefined)
        .map(([key, entry]) => [key, pruneUndefined(entry)]),
    );
  }
  return value;
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
