import { describe, expect, it } from 'vitest';

import { formatApiErrorMessage } from '#/api/error-messages';

import {
  buildLowcodeFormSchemas,
  createDefaultLowcodeFields,
  createLowcodeEditorField,
  getLowcodeLifecycleActions,
  LOWCODE_WIDGET_OPTIONS,
  validateLowcodeEditorFields,
} from './form-schema-editor';

describe('lowcode form schema editor', () => {
  it('keeps the MVP widget registry explicit', () => {
    expect(LOWCODE_WIDGET_OPTIONS.map((item) => item.value)).toEqual([
      'string',
      'textarea',
      'number',
      'money',
      'integer',
      'boolean',
      'select',
      'date',
    ]);
  });

  it('builds JSON Schema, UI Schema, and field metadata from visual fields', () => {
    const fields = createDefaultLowcodeFields();
    fields.push(
      createLowcodeEditorField(2, {
        fieldKey: 'expenseType',
        label: '费用类型',
        optionsText: 'TRAVEL=差旅\nMEAL=餐饮',
        required: true,
        widget: 'select',
      }),
    );

    const payload = buildLowcodeFormSchemas('费用报销', fields);
    const schema = JSON.parse(payload.schemaJson);
    const uiSchema = JSON.parse(payload.uiSchemaJson);

    expect(schema.required).toEqual(['amount', 'reason', 'expenseType']);
    expect(schema.properties.amount.type).toBe('number');
    expect(schema.properties.expenseType.enum).toEqual(['TRAVEL', 'MEAL']);
    expect(schema.properties.expenseType.enumNames).toEqual(['差旅', '餐饮']);
    expect(uiSchema.amount['ui:widget']).toBe('money');
    expect(uiSchema.reason['ui:widget']).toBe('textarea');
    expect(payload.fields.find((item) => item.fieldKey === 'expenseType')?.fieldType).toBe(
      'select',
    );
  });

  it('validates duplicate keys and select options before submit', () => {
    expect(
      validateLowcodeEditorFields([
        createLowcodeEditorField(0, { fieldKey: 'amount', label: '金额' }),
        createLowcodeEditorField(1, { fieldKey: 'amount', label: '金额副本' }),
      ])[0],
    ).toContain('重复');

    expect(
      validateLowcodeEditorFields([
        createLowcodeEditorField(0, {
          fieldKey: 'type',
          label: '类型',
          optionsText: '',
          widget: 'select',
        }),
      ])[0],
    ).toContain('至少配置一个选项');
  });

  it('exposes publish, offline, history, and version derivation visibility', () => {
    const access = {
      canOffline: true,
      canPublish: true,
      canUpdate: true,
      canVersion: true,
      canVersions: true,
    };

    expect(getLowcodeLifecycleActions('DRAFT', access)).toMatchObject({
      edit: true,
      newVersion: false,
      offline: false,
      publish: true,
    });
    expect(getLowcodeLifecycleActions('PUBLISHED', access)).toMatchObject({
      edit: false,
      history: true,
      newVersion: true,
      offline: true,
      publish: false,
    });
    expect(getLowcodeLifecycleActions('OFFLINE', access)).toMatchObject({
      newVersion: true,
      offline: false,
      publish: false,
    });
  });

  it('translates server validation codes into business language', () => {
    expect(formatApiErrorMessage('UNREGISTERED_FORM_WIDGET')).toBe(
      '存在未注册的表单控件，请选择受支持控件',
    );
    expect(
      formatApiErrorMessage('FORM_DATA_VALIDATION_FAILED', {
        fieldErrors: [{ code: 'REQUIRED', field: 'amount' }],
      }),
    ).toBe('amount: 必填字段未填写');
  });
});
