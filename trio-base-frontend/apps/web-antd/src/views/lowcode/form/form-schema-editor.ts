import type { LowcodeApi } from '#/api/lowcode';

export type LowcodeEditorWidget =
  | 'boolean'
  | 'date'
  | 'integer'
  | 'money'
  | 'number'
  | 'select'
  | 'string'
  | 'textarea';

export type LowcodeEditorFieldType =
  | 'boolean'
  | 'integer'
  | 'number'
  | 'string';

export interface LowcodeEditorField {
  fieldKey: string;
  label: string;
  optionsText: string;
  placeholder: string;
  required: boolean;
  sortOrder: number;
  widget: LowcodeEditorWidget;
}

export interface LowcodeFormSchemaBuild {
  fields: LowcodeApi.FormFieldSchema[];
  schemaJson: string;
  uiSchemaJson: string;
}

export interface LowcodeLifecycleAccess {
  canOffline?: boolean;
  canPublish?: boolean;
  canUpdate?: boolean;
  canVersion?: boolean;
  canVersions?: boolean;
}

export interface LowcodeLifecycleActions {
  edit: boolean;
  history: boolean;
  newVersion: boolean;
  offline: boolean;
  publish: boolean;
}

interface JsonSchemaField {
  enum?: Array<number | string>;
  enumNames?: string[];
  format?: string;
  minimum?: number;
  title: string;
  type: LowcodeEditorFieldType;
}

interface JsonSchema {
  additionalProperties: false;
  properties: Record<string, JsonSchemaField>;
  required?: string[];
  title: string;
  type: 'object';
}

interface UiFieldSchema {
  'ui:placeholder'?: string;
  'ui:widget': LowcodeEditorWidget | 'enum';
}

interface SelectOption {
  label: string;
  value: string;
}

const FIELD_KEY_PATTERN = /^[A-Za-z][\w]*$/;

export const LOWCODE_WIDGET_OPTIONS: Array<{
  label: string;
  value: LowcodeEditorWidget;
}> = [
  { label: '单行文本', value: 'string' },
  { label: '多行文本', value: 'textarea' },
  { label: '数字', value: 'number' },
  { label: '金额', value: 'money' },
  { label: '整数', value: 'integer' },
  { label: '开关', value: 'boolean' },
  { label: '下拉选项', value: 'select' },
  { label: '日期', value: 'date' },
];

export function getLowcodeLifecycleActions(
  status: string | undefined,
  access: LowcodeLifecycleAccess,
): LowcodeLifecycleActions {
  return {
    edit: status === 'DRAFT' && Boolean(access.canUpdate),
    history: Boolean(access.canVersions),
    newVersion: status !== 'DRAFT' && Boolean(access.canVersion),
    offline: status === 'PUBLISHED' && Boolean(access.canOffline),
    publish: status === 'DRAFT' && Boolean(access.canPublish),
  };
}

export function createLowcodeEditorField(
  index: number,
  overrides: Partial<LowcodeEditorField> = {},
): LowcodeEditorField {
  return {
    fieldKey: `field_${index + 1}`,
    label: `字段 ${index + 1}`,
    optionsText: '',
    placeholder: '',
    required: false,
    sortOrder: index + 1,
    widget: 'string',
    ...overrides,
  };
}

export function createDefaultLowcodeFields(): LowcodeEditorField[] {
  return [
    createLowcodeEditorField(0, {
      fieldKey: 'amount',
      label: '金额',
      placeholder: '请输入金额',
      required: true,
      widget: 'money',
    }),
    createLowcodeEditorField(1, {
      fieldKey: 'reason',
      label: '事由',
      placeholder: '请输入事由',
      required: true,
      widget: 'textarea',
    }),
  ];
}

export function normalizeLowcodeFieldWidget(field: LowcodeEditorField) {
  if (!LOWCODE_WIDGET_OPTIONS.some((item) => item.value === field.widget)) {
    field.widget = 'string';
  }
  if (field.widget === 'select' && !field.optionsText.trim()) {
    field.optionsText = 'OPTION_A=选项 A\nOPTION_B=选项 B';
  }
  if (field.widget !== 'select' && field.optionsText.trim()) {
    field.optionsText = '';
  }
}

export function validateLowcodeEditorFields(
  fields: LowcodeEditorField[],
): string[] {
  if (fields.length === 0) {
    return ['至少添加一个业务字段'];
  }
  const errors: string[] = [];
  const seen = new Set<string>();
  fields.forEach((field, index) => {
    const label = `第 ${index + 1} 行`;
    const fieldKey = field.fieldKey.trim();
    if (!fieldKey) {
      errors.push(`${label} 缺少字段标识`);
    } else if (!FIELD_KEY_PATTERN.test(fieldKey)) {
      errors.push(`${label} 字段标识只能使用字母、数字、下划线，且必须以字母开头`);
    } else if (seen.has(fieldKey)) {
      errors.push(`${label} 字段标识重复`);
    } else {
      seen.add(fieldKey);
    }
    if (!field.label.trim()) {
      errors.push(`${label} 缺少字段名称`);
    }
    if (!LOWCODE_WIDGET_OPTIONS.some((item) => item.value === field.widget)) {
      errors.push(`${label} 使用了未注册控件`);
    }
    if (field.widget === 'select') {
      try {
        parseSelectOptions(field.optionsText);
      } catch (error) {
        errors.push(error instanceof Error ? `${label} ${error.message}` : `${label} 选项无效`);
      }
    }
  });
  return errors;
}

export function buildLowcodeFormSchemas(
  title: string,
  fields: LowcodeEditorField[],
): LowcodeFormSchemaBuild {
  const validationErrors = validateLowcodeEditorFields(fields);
  if (validationErrors.length > 0) {
    throw new Error(validationErrors[0]);
  }

  const required: string[] = [];
  const properties: Record<string, JsonSchemaField> = {};
  const uiSchema: Record<string, UiFieldSchema> = {};
  const fieldMetadata: LowcodeApi.FormFieldSchema[] = [];

  fields.forEach((field, index) => {
    const key = field.fieldKey.trim();
    const schemaField = buildJsonSchemaField(field);
    properties[key] = schemaField;
    if (field.required) {
      required.push(key);
    }
    uiSchema[key] = {
      'ui:widget': field.widget,
      ...(field.placeholder.trim()
        ? { 'ui:placeholder': field.placeholder.trim() }
        : {}),
    };
    fieldMetadata.push({
      fieldKey: key,
      fieldType: toMetadataFieldType(field),
      label: field.label.trim(),
      optionsJson:
        field.widget === 'select'
          ? JSON.stringify(parseSelectOptions(field.optionsText))
          : undefined,
      placeholder: field.placeholder.trim() || undefined,
      required: field.required,
      sortOrder: index + 1,
    });
  });

  const schema: JsonSchema = {
    additionalProperties: false,
    properties,
    ...(required.length > 0 ? { required } : {}),
    title: title.trim() || '未命名表单',
    type: 'object',
  };

  return {
    fields: fieldMetadata,
    schemaJson: JSON.stringify(schema, null, 2),
    uiSchemaJson: JSON.stringify(uiSchema, null, 2),
  };
}

export function createFieldsFromDefinition(
  definition?: Pick<
    LowcodeApi.FormDefinition,
    'fields' | 'schemaJson' | 'uiSchemaJson'
  >,
): LowcodeEditorField[] {
  if (!definition) {
    return createDefaultLowcodeFields();
  }
  const schema = parseJsonObject<JsonSchema>(definition.schemaJson);
  const uiSchema = parseJsonObject<Record<string, Partial<UiFieldSchema>>>(
    definition.uiSchemaJson,
  );
  const required = new Set(schema?.required ?? []);
  const fieldMetadata = definition.fields ?? [];

  if (fieldMetadata.length > 0) {
    return fieldMetadata
      .slice()
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
      .map((field, index) => {
        const key = field.fieldKey.trim();
        const schemaField = schema?.properties?.[key];
        const widget = inferWidget(schemaField, uiSchema?.[key], field.fieldType);
        return createLowcodeEditorField(index, {
          fieldKey: key,
          label: field.label || schemaField?.title || key,
          optionsText:
            optionsJsonToText(field.optionsJson) ||
            enumToOptionsText(schemaField?.enum, schemaField?.enumNames),
          placeholder:
            field.placeholder || uiSchema?.[key]?.['ui:placeholder'] || '',
          required: Boolean(field.required) || required.has(key),
          sortOrder: field.sortOrder ?? index + 1,
          widget,
        });
      });
  }

  if (!schema?.properties) {
    return createDefaultLowcodeFields();
  }

  return Object.entries(schema.properties).map(([key, field], index) =>
    createLowcodeEditorField(index, {
      fieldKey: key,
      label: field.title || key,
      optionsText: enumToOptionsText(field.enum, field.enumNames),
      placeholder: uiSchema?.[key]?.['ui:placeholder'] || '',
      required: required.has(key),
      widget: inferWidget(field, uiSchema?.[key]),
    }),
  );
}

function buildJsonSchemaField(field: LowcodeEditorField): JsonSchemaField {
  const label = field.label.trim();
  if (field.widget === 'boolean') {
    return { title: label, type: 'boolean' };
  }
  if (field.widget === 'integer') {
    return { title: label, type: 'integer' };
  }
  if (field.widget === 'money') {
    return { minimum: 0, title: label, type: 'number' };
  }
  if (field.widget === 'number') {
    return { title: label, type: 'number' };
  }
  if (field.widget === 'date') {
    return { format: 'date', title: label, type: 'string' };
  }
  if (field.widget === 'select') {
    const options = parseSelectOptions(field.optionsText);
    return {
      enum: options.map((item) => item.value),
      enumNames: options.map((item) => item.label),
      title: label,
      type: 'string',
    };
  }
  return { title: label, type: 'string' };
}

function inferWidget(
  schema?: JsonSchemaField,
  uiSchema?: Partial<UiFieldSchema>,
  fieldType?: string,
): LowcodeEditorWidget {
  const requested = uiSchema?.['ui:widget'];
  if (requested === 'select' || requested === 'string' || requested === 'textarea') {
    return requested;
  }
  if (
    requested === 'boolean' ||
    requested === 'date' ||
    requested === 'integer' ||
    requested === 'money' ||
    requested === 'number'
  ) {
    return requested;
  }
  if (requested === 'enum') {
    return 'select';
  }
  if (fieldType === 'text') return 'textarea';
  if (
    fieldType === 'boolean' ||
    fieldType === 'date' ||
    fieldType === 'integer' ||
    fieldType === 'money' ||
    fieldType === 'number' ||
    fieldType === 'select' ||
    fieldType === 'string' ||
    fieldType === 'textarea'
  ) {
    return fieldType;
  }
  if (schema?.enum?.length) return 'select';
  if (schema?.format === 'date') return 'date';
  if (schema?.type === 'boolean') return 'boolean';
  if (schema?.type === 'integer') return 'integer';
  if (schema?.type === 'number') return 'number';
  return 'string';
}

function parseJsonObject<T>(json?: string): T | undefined {
  if (!json) return undefined;
  try {
    const parsed = JSON.parse(json) as T;
    return typeof parsed === 'object' && parsed !== null ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function parseSelectOptions(optionsText: string): SelectOption[] {
  const rows = optionsText
    .split(/\r?\n/)
    .map((row) => row.trim())
    .filter(Boolean);
  if (rows.length === 0) {
    throw new Error('下拉字段至少配置一个选项');
  }
  const seen = new Set<string>();
  return rows.map((row) => {
    const separator = row.includes('=') ? '=' : row.includes('|') ? '|' : '';
    const [rawValue, ...rawLabelParts] = separator ? row.split(separator) : [row];
    const value = String(rawValue ?? '').trim();
    const label = rawLabelParts.join(separator).trim() || value;
    if (!value) {
      throw new Error('下拉选项缺少值');
    }
    if (seen.has(value)) {
      throw new Error(`下拉选项 ${value} 重复`);
    }
    seen.add(value);
    return { label, value };
  });
}

function optionsJsonToText(optionsJson?: string) {
  if (!optionsJson) return '';
  const parsed = parseJsonObject<unknown>(optionsJson);
  if (!Array.isArray(parsed)) return '';
  return parsed
    .map((item) => {
      if (typeof item === 'string' || typeof item === 'number') {
        return String(item);
      }
      if (typeof item === 'object' && item !== null) {
        const option = item as Record<string, unknown>;
        const value = String(option.value ?? option.code ?? '');
        const label = String(option.label ?? option.name ?? value);
        return value ? `${value}=${label}` : '';
      }
      return '';
    })
    .filter(Boolean)
    .join('\n');
}

function enumToOptionsText(values?: Array<number | string>, labels?: string[]) {
  if (!values?.length) return '';
  return values
    .map((value, index) => {
      const label = labels?.[index] ?? String(value);
      return `${value}=${label}`;
    })
    .join('\n');
}

function toMetadataFieldType(field: LowcodeEditorField) {
  if (field.widget === 'date' || field.widget === 'money' || field.widget === 'select') {
    return field.widget;
  }
  if (field.widget === 'textarea') {
    return 'textarea';
  }
  return buildJsonSchemaField(field).type;
}
