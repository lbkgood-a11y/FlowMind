import { shallowMount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import DynamicProcessForm from './DynamicProcessForm.vue';
import {
  getProcessFormFields,
  validateProcessFormData,
} from './process-form';

const schemaJson = JSON.stringify({
  additionalProperties: false,
  properties: {
    active: { title: '启用', type: 'boolean' },
    amount: { minimum: 0.01, title: '金额', type: 'number' },
    count: { title: '数量', type: 'integer' },
    date: { format: 'date', title: '日期', type: 'string' },
    description: { title: '说明', type: 'string' },
    name: { title: '名称', type: 'string' },
    reason: { title: '事由', type: 'string' },
    type: { enum: ['TRAVEL', 'MEAL'], title: '类型', type: 'string' },
  },
  required: ['amount', 'reason'],
  type: 'object',
});

const uiSchemaJson = JSON.stringify({
  amount: { 'ui:widget': 'money' },
  description: { 'ui:widget': 'textarea' },
  name: { 'ui:widget': 'string' },
  type: { 'ui:widget': 'select' },
});

describe('process form runtime', () => {
  it('maps all MVP field widgets through the fixed registry', () => {
    const fields = getProcessFormFields(schemaJson, uiSchemaJson);
    expect(fields.map((field) => field.widget)).toEqual([
      'boolean',
      'money',
      'integer',
      'date',
      'textarea',
      'string',
      'string',
      'select',
    ]);
  });

  it('returns required, type, range, and unknown field errors', () => {
    const errors = validateProcessFormData(schemaJson, {
      amount: 'invalid',
      count: 0,
      unexpected: true,
    });
    expect(errors.map((error) => error.code)).toEqual(
      expect.arrayContaining(['REQUIRED', 'TYPE_MISMATCH', 'UNKNOWN_FIELD']),
    );
  });

  it('exposes component validation for the parent start form', () => {
    const wrapper = shallowMount(DynamicProcessForm, {
      props: {
        modelValue: {},
        schemaJson,
        uiSchemaJson,
      },
    });
    const errors = (wrapper.vm as any).validate();
    expect(errors.some((error: any) => error.field === 'amount')).toBe(true);
    expect(errors.some((error: any) => error.field === 'reason')).toBe(true);
  });
});
