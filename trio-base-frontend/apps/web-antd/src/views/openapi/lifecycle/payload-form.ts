export type PayloadFormFieldKind =
  | 'array'
  | 'boolean'
  | 'datetime'
  | 'json'
  | 'number'
  | 'object'
  | 'reference'
  | 'select'
  | 'tags'
  | 'text'
  | 'textarea';

export interface PayloadFormOption {
  label: string;
  value: string;
}

export interface PayloadFormReference {
  assetType: string;
  codePath?: string;
  labelPath?: string;
  state?: string;
  valuePath?: string;
}

export interface PayloadFormField {
  addLabel?: string;
  clearKeysOnChange?: string[];
  defaultValue?: unknown;
  fields?: PayloadFormField[];
  help?: string;
  itemFields?: PayloadFormField[];
  key: string;
  kind: PayloadFormFieldKind;
  label: string;
  max?: number;
  min?: number;
  multiple?: boolean;
  omitEmpty?: boolean;
  options?: PayloadFormOption[];
  placeholder?: string;
  precision?: number;
  preserveEmptyArray?: boolean;
  preserveEmptyObject?: boolean;
  reference?: PayloadFormReference;
  referenceDependsOn?: string;
  referenceMap?: Record<string, PayloadFormReference>;
  required?: boolean;
  rows?: number;
  wide?: boolean;
}

export interface PayloadFormGroup {
  description?: string;
  fields: PayloadFormField[];
  title: string;
}

export interface PayloadFormDefinition {
  groups: PayloadFormGroup[];
  title?: string;
}

export type PayloadFormState = Record<string, unknown>;

export class PayloadFormError extends Error {
  constructor(
    public readonly fieldLabel: string,
    message: string,
  ) {
    super(message);
    this.name = 'PayloadFormError';
  }
}

export function buildPayload(
  form: PayloadFormDefinition,
  state: PayloadFormState,
): Record<string, unknown> {
  return buildFieldsPayload(getPayloadFormFields(form), state);
}

export function createArrayItemState(field: PayloadFormField): PayloadFormState {
  return createFieldsState(field.itemFields ?? []);
}

export function createPayloadFormState(
  form: PayloadFormDefinition,
): PayloadFormState {
  return createFieldsState(getPayloadFormFields(form));
}

export function getPayloadFormFields(
  form: PayloadFormDefinition,
): PayloadFormField[] {
  return form.groups.flatMap((group) => group.fields);
}

export function parsePayloadJson(text: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(text) as unknown;
    if (!isRecord(parsed)) {
      throw new PayloadFormError('JSON', 'JSON 顶层必须是对象');
    }
    return parsed;
  } catch (error) {
    if (error instanceof PayloadFormError) {
      throw error;
    }
    throw new PayloadFormError('JSON', '请输入合法 JSON');
  }
}

export function payloadToFormState(
  form: PayloadFormDefinition,
  payload: Record<string, unknown>,
): PayloadFormState {
  return payloadToFieldsState(getPayloadFormFields(form), payload);
}

export function payloadToJson(payload: Record<string, unknown>): string {
  return JSON.stringify(payload, null, 2);
}

export function resetPayloadFormState(
  target: PayloadFormState,
  form: PayloadFormDefinition,
  payload?: Record<string, unknown>,
) {
  const next = payload ? payloadToFormState(form, payload) : createPayloadFormState(form);
  for (const key of Object.keys(target)) {
    delete target[key];
  }
  Object.assign(target, next);
}

function buildFieldValue(field: PayloadFormField, rawValue: unknown): unknown {
  if (field.kind === 'json') {
    return parseJsonField(field, rawValue);
  }
  if (field.kind === 'number') {
    if (rawValue === undefined || rawValue === null || rawValue === '') {
      return undefined;
    }
    return Number(rawValue);
  }
  if (field.kind === 'boolean') {
    return Boolean(rawValue);
  }
  if (field.kind === 'tags') {
    return Array.isArray(rawValue)
      ? rawValue.map((item) => String(item).trim()).filter(Boolean)
      : [];
  }
  if (field.kind === 'reference') {
    if (field.multiple) {
      return Array.isArray(rawValue)
        ? rawValue.map((item) => String(item).trim()).filter(Boolean)
        : [];
    }
    return rawValue;
  }
  if (field.kind === 'object') {
    return buildFieldsPayload(field.fields ?? [], toState(rawValue));
  }
  if (field.kind === 'array') {
    if (!Array.isArray(rawValue)) {
      return [];
    }
    return rawValue.map((row) => buildFieldsPayload(field.itemFields ?? [], toState(row)));
  }
  return rawValue;
}

function buildFieldsPayload(
  fields: PayloadFormField[],
  state: PayloadFormState,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  for (const field of fields) {
    const value = buildFieldValue(field, state[field.key]);
    if (shouldOmit(field, value)) {
      continue;
    }
    payload[field.key] = value;
  }
  return payload;
}

function cloneDefault(value: unknown): unknown {
  if (Array.isArray(value) || isRecord(value)) {
    return JSON.parse(JSON.stringify(value)) as unknown;
  }
  return value;
}

function createFieldState(field: PayloadFormField): unknown {
  if (field.defaultValue !== undefined) {
    if (field.kind === 'json') {
      return formatJsonValue(field.defaultValue);
    }
    if (field.kind === 'object') {
      return payloadToFieldsState(field.fields ?? [], toState(field.defaultValue));
    }
    if (field.kind === 'array' && Array.isArray(field.defaultValue)) {
      return field.defaultValue.map((item) =>
        payloadToFieldsState(field.itemFields ?? [], toState(item)),
      );
    }
    return cloneDefault(field.defaultValue);
  }
  if (
    field.kind === 'array' ||
    field.kind === 'tags' ||
    (field.kind === 'reference' && field.multiple)
  ) {
    return [];
  }
  if (field.kind === 'boolean') {
    return false;
  }
  if (field.kind === 'json') {
    return field.required ? '{}' : '';
  }
  if (field.kind === 'number') {
    return undefined;
  }
  if (field.kind === 'object') {
    return createFieldsState(field.fields ?? []);
  }
  return '';
}

function createFieldsState(fields: PayloadFormField[]): PayloadFormState {
  return Object.fromEntries(fields.map((field) => [field.key, createFieldState(field)]));
}

function formatJsonValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value ?? {}, null, 2);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseJsonField(field: PayloadFormField, rawValue: unknown): unknown {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return undefined;
  }
  if (typeof rawValue !== 'string') {
    return rawValue;
  }
  try {
    return JSON.parse(rawValue) as unknown;
  } catch {
    throw new PayloadFormError(field.label, `${field.label} 不是合法 JSON`);
  }
}

function payloadToFieldState(field: PayloadFormField, payload: Record<string, unknown>) {
  if (!Object.hasOwn(payload, field.key)) {
    return createFieldState(field);
  }
  const value = payload[field.key];
  if (field.kind === 'json') {
    return formatJsonValue(value);
  }
  if (field.kind === 'object') {
    return payloadToFieldsState(field.fields ?? [], toState(value));
  }
  if (field.kind === 'array') {
    return Array.isArray(value)
      ? value.map((item) => payloadToFieldsState(field.itemFields ?? [], toState(item)))
      : [];
  }
  if (field.kind === 'tags') {
    return Array.isArray(value)
      ? value.map((item) => String(item))
      : String(value ?? '')
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean);
  }
  return cloneDefault(value);
}

function payloadToFieldsState(
  fields: PayloadFormField[],
  payload: Record<string, unknown>,
): PayloadFormState {
  return Object.fromEntries(fields.map((field) => [field.key, payloadToFieldState(field, payload)]));
}

function shouldOmit(field: PayloadFormField, value: unknown): boolean {
  if (field.required) {
    return value === undefined;
  }
  if (field.omitEmpty === false) {
    return value === undefined || value === null;
  }
  if (value === undefined || value === null) {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim() === '';
  }
  if (Array.isArray(value)) {
    return value.length === 0 && !field.preserveEmptyArray;
  }
  if (isRecord(value)) {
    return Object.keys(value).length === 0 && !field.preserveEmptyObject;
  }
  return false;
}

function toState(value: unknown): PayloadFormState {
  return isRecord(value) ? value : {};
}
