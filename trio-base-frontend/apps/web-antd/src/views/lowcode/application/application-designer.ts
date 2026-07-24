import type { LowcodeApi } from '#/api/lowcode';

export interface ListColumnDesign {
  fieldKey: string;
  label: string;
  format?: 'date' | 'datetime' | 'money' | 'status' | 'text';
  sortable?: boolean;
  visible?: boolean;
  width?: number;
}

export interface ListFilterDesign {
  fieldKey: string;
  label: string;
  operator?: 'contains' | 'eq' | 'gte' | 'lte';
}

export interface ListPageDesign {
  columns: ListColumnDesign[];
  defaultSort?: { direction: 'ASC' | 'DESC'; fieldKey: string };
  filters: ListFilterDesign[];
  pageSize: number;
  rowActions: string[];
}

export function defaultListDesign(fields: LowcodeApi.FormFieldSchema[]): ListPageDesign {
  return {
    columns: fields.slice(0, 8).map((field) => ({
      fieldKey: field.fieldKey,
      format: formatFor(field.fieldType),
      label: field.label,
      sortable: true,
      visible: true,
      width: 140,
    })),
    filters: fields.slice(0, 3).map((field) => ({
      fieldKey: field.fieldKey,
      label: field.label,
      operator: 'contains',
    })),
    pageSize: 20,
    rowActions: ['OPEN_DETAIL'],
  };
}

export function defaultPages(fields: LowcodeApi.FormFieldSchema[]) {
  const fieldRefs = fields.map(({ fieldKey }) => ({ fieldKey }));
  return [
    { metadataJson: JSON.stringify(defaultListDesign(fields)), pageType: 'LIST', sortOrder: 10 },
    {
      metadataJson: JSON.stringify({ sections: [{ fields: fieldRefs, title: '基本信息' }] }),
      pageType: 'CREATE',
      sortOrder: 20,
    },
    {
      metadataJson: JSON.stringify({ sections: [{ fields: fieldRefs, title: '基本信息' }] }),
      pageType: 'DETAIL',
      sortOrder: 30,
    },
  ];
}

export function defaultActions(formDefinitionId: string): LowcodeApi.SaveApplication['actions'] {
  const permissionCode = '/api/v1/lowcode-runtime/apps/*/actions/*:POST';
  return [
    { actionCode: 'CREATE', actionType: 'CREATE', formDefinitionId, label: '新建', permissionCode, sortOrder: 10, status: 'ENABLED' },
    { actionCode: 'SAVE', actionType: 'SAVE', formDefinitionId, label: '保存', permissionCode, sortOrder: 20, status: 'ENABLED' },
    { actionCode: 'OPEN_DETAIL', actionType: 'OPEN_DETAIL', formDefinitionId, label: '查看', permissionCode, sortOrder: 30, status: 'ENABLED' },
  ];
}

export function validateRelationDepth(rootFormId: string, relations: LowcodeApi.FormRelation[]) {
  const graph = new Map<string, string[]>();
  for (const relation of relations) {
    if (relation.parentFormDefinitionId === relation.childFormDefinitionId) return '表单不能关联自身';
    const children = graph.get(relation.parentFormDefinitionId) ?? [];
    children.push(relation.childFormDefinitionId);
    graph.set(relation.parentFormDefinitionId, children);
  }
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const walk = (id: string, depth: number): string | undefined => {
    if (depth > 3) return '主子孙关系最多支持三级';
    if (visiting.has(id)) return '表单关系不能形成环';
    visiting.add(id);
    for (const child of graph.get(id) ?? []) {
      const error = walk(child, depth + 1);
      if (error) return error;
    }
    visiting.delete(id);
    visited.add(id);
  };
  const error = walk(rootFormId, 1);
  if (error) return error;
  const nodes = new Set(relations.flatMap((item) => [item.parentFormDefinitionId, item.childFormDefinitionId]));
  return [...nodes].every((id) => visited.has(id)) ? undefined : '所有关联表单必须连接到主表';
}

function formatFor(type?: string): ListColumnDesign['format'] {
  if (type === 'date') return 'date';
  if (type === 'money' || type === 'number') return 'money';
  return 'text';
}
