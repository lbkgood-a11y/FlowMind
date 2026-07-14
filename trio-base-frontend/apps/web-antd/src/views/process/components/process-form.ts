import type { ErrorObject } from 'ajv';

import Ajv from 'ajv';

export type FormWidget =
  | 'boolean'
  | 'date'
  | 'integer'
  | 'money'
  | 'number'
  | 'select'
  | 'string'
  | 'textarea';

export interface ProcessFieldSchema {
  default?: unknown;
  enum?: Array<number | string>;
  enumNames?: string[];
  format?: string;
  maximum?: number;
  maxLength?: number;
  minimum?: number;
  minLength?: number;
  title?: string;
  type: 'boolean' | 'integer' | 'number' | 'string';
}

export interface ProcessFormSchema {
  additionalProperties?: boolean;
  properties: Record<string, ProcessFieldSchema>;
  required?: string[];
  title?: string;
  type: 'object';
}

export interface ProcessUiFieldSchema {
  'ui:placeholder'?: string;
  'ui:widget'?: FormWidget | 'enum';
}

export type ProcessUiSchema = Record<string, ProcessUiFieldSchema>;

export interface ProcessFormField {
  key: string;
  label: string;
  placeholder?: string;
  required: boolean;
  schema: ProcessFieldSchema;
  widget: FormWidget;
}

export interface ClientFormError {
  code: string;
  field: string;
  message: string;
}

export const FORM_WIDGET_REGISTRY: Readonly<Record<FormWidget, FormWidget>> =
  Object.freeze({
    boolean: 'boolean',
    date: 'date',
    integer: 'integer',
    money: 'money',
    number: 'number',
    select: 'select',
    string: 'string',
    textarea: 'textarea',
  });

const ajv = new Ajv({ allErrors: true, strict: false });

export function parseFormSchema(schemaJson?: string): ProcessFormSchema | undefined {
  if (!schemaJson) return undefined;
  const schema = JSON.parse(schemaJson) as ProcessFormSchema;
  return schema;
}

export function parseUiSchema(uiSchemaJson?: string): ProcessUiSchema {
  if (!uiSchemaJson) return {};
  return JSON.parse(uiSchemaJson) as ProcessUiSchema;
}

export function getProcessFormFields(
  schemaJson?: string,
  uiSchemaJson?: string,
): ProcessFormField[] {
  const schema = parseFormSchema(schemaJson);
  if (!schema?.properties) return [];
  const uiSchema = parseUiSchema(uiSchemaJson);
  const required = new Set(schema.required ?? []);

  return Object.entries(schema.properties).map(([key, fieldSchema]) => {
    const fieldUi = uiSchema[key] ?? {};
    return {
      key,
      label: fieldSchema.title || key,
      placeholder: fieldUi['ui:placeholder'],
      required: required.has(key),
      schema: fieldSchema,
      widget: resolveWidget(fieldSchema, fieldUi),
    };
  });
}

export function resolveWidget(
  schema: ProcessFieldSchema,
  uiSchema: ProcessUiFieldSchema = {},
): FormWidget {
  const requested = uiSchema['ui:widget'];
  if (requested) {
    const normalized = requested === 'enum' ? 'select' : requested;
    if (!(normalized in FORM_WIDGET_REGISTRY)) {
      throw new Error(`UNREGISTERED_FORM_WIDGET:${requested}`);
    }
    return normalized;
  }
  if (schema.enum) return 'select';
  if (schema.format === 'date') return 'date';
  if (schema.type === 'boolean') return 'boolean';
  if (schema.type === 'integer') return 'integer';
  if (schema.type === 'number') return 'number';
  return 'string';
}

export function validateFormDefinition(
  schemaJson?: string,
  uiSchemaJson?: string,
): string[] {
  if (!schemaJson) return uiSchemaJson ? ['缺少表单 Schema'] : [];
  try {
    const schema = parseFormSchema(schemaJson);
    if (!schema || schema.type !== 'object' || !schema.properties) {
      return ['表单 Schema 根节点必须是 object'];
    }
    const uiSchema = parseUiSchema(uiSchemaJson);
    const errors: string[] = [];
    for (const [key, fieldSchema] of Object.entries(schema.properties)) {
      if (!['boolean', 'integer', 'number', 'string'].includes(fieldSchema.type)) {
        errors.push(`字段 ${key} 使用了不支持的类型`);
        continue;
      }
      try {
        resolveWidget(fieldSchema, uiSchema[key]);
      } catch {
        errors.push(`字段 ${key} 使用了未注册组件`);
      }
    }
    return errors;
  } catch {
    return ['表单 Schema 或 UI Schema 不是合法 JSON'];
  }
}

export function validateProcessFormData(
  schemaJson: string | undefined,
  data: Record<string, unknown>,
): ClientFormError[] {
  if (!schemaJson) return [];
  try {
    const schema = parseFormSchema(schemaJson);
    if (!schema) return [];
    const validate = ajv.compile(schema);
    if (validate(data)) return [];
    return (validate.errors ?? []).map(toClientError);
  } catch {
    return [{ code: 'INVALID_SCHEMA', field: '', message: '表单定义无效' }];
  }
}

function toClientError(error: ErrorObject): ClientFormError {
  const params = error.params as Record<string, unknown>;
  const suffix = String(
    params.missingProperty ?? params.additionalProperty ?? '',
  );
  const base = error.instancePath.replace(/^\//, '').replaceAll('/', '.');
  const field = suffix ? [base, suffix].filter(Boolean).join('.') : base;
  return {
    code: clientErrorCode(error.keyword),
    field,
    message: error.message || '字段值无效',
  };
}

function clientErrorCode(keyword: string) {
  if (keyword === 'required') return 'REQUIRED';
  if (keyword === 'type') return 'TYPE_MISMATCH';
  if (keyword === 'additionalProperties') return 'UNKNOWN_FIELD';
  if (
    ['exclusiveMaximum', 'exclusiveMinimum', 'maximum', 'maxLength', 'minimum', 'minLength'].includes(
      keyword,
    )
  ) {
    return 'OUT_OF_RANGE';
  }
  return 'INVALID_VALUE';
}
