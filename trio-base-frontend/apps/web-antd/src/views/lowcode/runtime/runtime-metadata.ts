import type { LowcodeApi } from '#/api/lowcode';

import {
  getProcessFormFields,
  type ProcessFormField,
} from '../../process/components/process-form';

export interface RuntimeFieldDescriptor {
  fieldKey: string;
  format?: string;
  label: string;
  visible: boolean;
  width?: number;
}

export interface RuntimeSectionDescriptor {
  fields: RuntimeFieldDescriptor[];
  title: string;
}

export type RuntimeTableRecord = LowcodeApi.FormInstance &
  Record<string, unknown> & {
    __data: Record<string, unknown>;
  };

const CREATE_ACTION_TYPES = new Set([
  'CREATE',
  'SAVE',
  'SUBMIT',
  'SUBMIT_AND_LAUNCH_WORKFLOW',
]);
const RETRY_ACTION_TYPES = new Set(['RETRY_WORKFLOW', 'SUBMIT_AND_LAUNCH_WORKFLOW']);

export function getRuntimePage(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
  pageType: string,
) {
  return descriptor?.pages
    ?.slice()
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .find((page) => page.pageType?.toUpperCase() === pageType.toUpperCase());
}

export function getRuntimeListColumns(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
): RuntimeFieldDescriptor[] {
  const listPage = getRuntimePage(descriptor, 'LIST');
  const metadata = parseMetadata<{ columns?: unknown[] }>(listPage?.metadataJson);
  const fields = getSchemaFields(descriptor);
  const fieldsByKey = new Map(fields.map((field) => [field.key, field]));
  const columns = Array.isArray(metadata?.columns)
    ? metadata.columns
        .map((item) => toFieldDescriptor(item, fieldsByKey))
        .filter((item): item is RuntimeFieldDescriptor => Boolean(item))
        .filter((item) => item.visible)
    : [];
  return columns.length > 0 ? columns : fallbackFields(fields);
}

export function getRuntimeDetailSections(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
  pageType: 'CREATE' | 'DETAIL' = 'DETAIL',
): RuntimeSectionDescriptor[] {
  const page = getRuntimePage(descriptor, pageType);
  const metadata = parseMetadata<{ sections?: unknown[] }>(page?.metadataJson);
  const fields = getSchemaFields(descriptor);
  const fieldsByKey = new Map(fields.map((field) => [field.key, field]));
  const sections = Array.isArray(metadata?.sections)
    ? metadata.sections
        .map((item, index) => toSectionDescriptor(item, fieldsByKey, index))
        .filter((item): item is RuntimeSectionDescriptor => Boolean(item))
    : [];
  return sections.length > 0
    ? sections
    : [{ fields: fallbackFields(fields), title: pageType === 'CREATE' ? '填写信息' : '基本信息' }];
}

export function getRuntimeActions(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
) {
  return (descriptor?.actions ?? [])
    .filter((action) => (action.status || 'ENABLED').toUpperCase() === 'ENABLED')
    .slice()
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
}

export function getPrimaryCreateAction(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
) {
  const actions = getRuntimeActions(descriptor).filter((action) =>
    CREATE_ACTION_TYPES.has(action.actionType.toUpperCase()),
  );
  return (
    actions.find((action) => action.actionType === 'SUBMIT_AND_LAUNCH_WORKFLOW') ||
    actions[0]
  );
}

export function getRetryWorkflowAction(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
) {
  return getRuntimeActions(descriptor).find((action) =>
    RETRY_ACTION_TYPES.has(action.actionType.toUpperCase()),
  );
}

export function toRuntimeTableRecord(
  instance: LowcodeApi.FormInstance,
): RuntimeTableRecord {
  const data = parseInstanceData(instance.dataJson);
  return { ...instance, ...data, __data: data };
}

export function parseInstanceData(dataJson?: string) {
  if (!dataJson) return {};
  try {
    const parsed = JSON.parse(dataJson) as unknown;
    return typeof parsed === 'object' && parsed !== null
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

export function formatRuntimeValue(
  value: unknown,
  descriptor?: Pick<RuntimeFieldDescriptor, 'format'>,
) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const format = descriptor?.format?.toLowerCase();
  if (format === 'money') {
    return `¥ ${Number(value || 0).toFixed(2)}`;
  }
  if (format === 'boolean') {
    return value ? '是' : '否';
  }
  return String(value);
}

export function workflowStatusTag(status?: string, processInstanceId?: string) {
  if (status === 'PENDING_WORKFLOW') {
    return { color: 'orange', label: '待启动流程' };
  }
  if (status === 'COMPLETED') {
    return { color: 'blue', label: '已完成' };
  }
  if (processInstanceId || status === 'RUNNING') {
    return { color: 'green', label: '审批中' };
  }
  return { color: 'default', label: status || '已保存' };
}

export function createRuntimeDraftKey(
  appKey: string,
  version: number | undefined,
  actionCode: string,
) {
  const randomPart =
    typeof crypto === 'object' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `runtime:${appKey}:${version ?? 'latest'}:${actionCode}:${randomPart}`;
}

export function createRuntimeRetryKey(
  appKey: string,
  version: number | undefined,
  instanceId: string,
  actionCode: string,
) {
  return `runtime-retry:${appKey}:${version ?? 'latest'}:${instanceId}:${actionCode}`;
}

function toSectionDescriptor(
  value: unknown,
  fieldsByKey: Map<string, ProcessFormField>,
  index: number,
): RuntimeSectionDescriptor | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const section = value as Record<string, unknown>;
  const fields = Array.isArray(section.fields)
    ? section.fields
        .map((item) => toFieldDescriptor(item, fieldsByKey))
        .filter((item): item is RuntimeFieldDescriptor => Boolean(item))
        .filter((item) => item.visible)
    : [];
  if (fields.length === 0) return undefined;
  return {
    fields,
    title: String(section.title || section.label || `分组 ${index + 1}`),
  };
}

function toFieldDescriptor(
  value: unknown,
  fieldsByKey: Map<string, ProcessFormField>,
): RuntimeFieldDescriptor | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const node = value as Record<string, unknown>;
  const fieldKey = String(node.fieldKey || '').trim();
  if (!fieldKey) return undefined;
  const schemaField = fieldsByKey.get(fieldKey);
  return {
    fieldKey,
    format: String(node.format || schemaField?.widget || ''),
    label: String(node.label || node.title || schemaField?.label || fieldKey),
    visible: node.visible === undefined ? true : Boolean(node.visible),
    width: typeof node.width === 'number' ? node.width : undefined,
  };
}

function fallbackFields(fields: ProcessFormField[]): RuntimeFieldDescriptor[] {
  return fields.map((field) => ({
    fieldKey: field.key,
    format: field.widget,
    label: field.label,
    visible: true,
  }));
}

function getSchemaFields(
  descriptor: LowcodeApi.RuntimeApplicationDescriptor | undefined,
) {
  try {
    return getProcessFormFields(descriptor?.schemaJson, descriptor?.uiSchemaJson);
  } catch {
    return [];
  }
}

function parseMetadata<T>(json?: string): T | undefined {
  if (!json) return undefined;
  try {
    const parsed = JSON.parse(json) as T;
    return typeof parsed === 'object' && parsed !== null ? parsed : undefined;
  } catch {
    return undefined;
  }
}
