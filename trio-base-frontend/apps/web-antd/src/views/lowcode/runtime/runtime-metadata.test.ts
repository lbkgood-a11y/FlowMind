import type { LowcodeApi } from '#/api/lowcode';

import { describe, expect, it } from 'vitest';

import { formatApiErrorMessage } from '#/api/error-messages';

import {
  getAuthorizedRuntimeFormSchemas,
  getPrimaryCreateAction,
  getRetryWorkflowAction,
  getRuntimeActions,
  getRuntimeDetailSections,
  getRuntimeListColumns,
  toRuntimeTableRecord,
  workflowStatusTag,
} from './runtime-metadata';

const descriptor: LowcodeApi.RuntimeApplicationDescriptor = {
  actions: [
    {
      actionCode: 'hidden',
      actionType: 'SUBMIT',
      id: 'ACT0',
      label: '隐藏动作',
      status: 'DISABLED',
    },
    {
      actionCode: 'submitAndLaunch',
      actionType: 'SUBMIT_AND_LAUNCH_WORKFLOW',
      id: 'ACT1',
      label: '提交并启动流程',
      sortOrder: 2,
      status: 'ENABLED',
    },
    {
      actionCode: 'save',
      actionType: 'SUBMIT',
      id: 'ACT2',
      label: '保存',
      sortOrder: 1,
      status: 'ENABLED',
    },
  ],
  appKey: 'expense_report',
  formKey: 'expense',
  name: '费用报销',
  pages: [
    {
      id: 'LIST',
      metadataJson: JSON.stringify({
        columns: [
          { fieldKey: 'amount', format: 'money', label: '金额', width: 120 },
          { fieldKey: 'reason', label: '事由' },
        ],
      }),
      pageType: 'LIST',
    },
    {
      id: 'DETAIL',
      metadataJson: JSON.stringify({
        sections: [{ fields: [{ fieldKey: 'amount' }, { fieldKey: 'reason' }], title: '单据信息' }],
      }),
      pageType: 'DETAIL',
    },
  ],
  primaryFormDefinitionId: 'FORM001',
  schemaJson: JSON.stringify({
    properties: {
      amount: { title: '金额', type: 'number' },
      reason: { title: '事由', type: 'string' },
    },
    required: ['amount'],
    type: 'object',
  }),
  tenantId: 'tenant-a',
  uiSchemaJson: JSON.stringify({
    amount: { 'ui:widget': 'money' },
    reason: { 'ui:widget': 'textarea' },
  }),
  version: 1,
  versionId: 'APPV001',
};

describe('lowcode runtime metadata', () => {
  it('renders list columns and detail sections from descriptor metadata', () => {
    expect(getRuntimeListColumns(descriptor)).toMatchObject([
      { fieldKey: 'amount', format: 'money', label: '金额', width: 120 },
      { fieldKey: 'reason', label: '事由' },
    ]);
    expect(getRuntimeDetailSections(descriptor)[0]).toMatchObject({
      fields: [{ fieldKey: 'amount', format: 'money' }, { fieldKey: 'reason' }],
      title: '单据信息',
    });
  });

  it('selects only enabled runtime actions and prefers workflow launch', () => {
    expect(getPrimaryCreateAction(descriptor)?.actionCode).toBe('submitAndLaunch');
    expect(getRetryWorkflowAction(descriptor)?.actionCode).toBe('submitAndLaunch');
  });

  it('consumes authorization descriptors for actions and fields', () => {
    const restrictedDescriptor: LowcodeApi.RuntimeApplicationDescriptor = {
      ...descriptor,
      actions: [
        ...descriptor.actions,
        {
          actionCode: 'deniedCreate',
          actionType: 'CREATE',
          allowed: false,
          id: 'ACT3',
          label: '被拒绝的新建',
          sortOrder: 0,
          status: 'ENABLED',
        },
      ],
      fieldRules: [
        {
          fieldKey: 'amount',
          maskStrategy: 'MONEY',
          readMode: 'MASKED',
          writeMode: 'READONLY',
        },
        {
          fieldKey: 'reason',
          readMode: 'HIDDEN',
          writeMode: 'DENIED',
        },
      ],
    };

    expect(getRuntimeActions(restrictedDescriptor).map((action) => action.actionCode)).not.toContain(
      'deniedCreate',
    );
    expect(getRuntimeListColumns(restrictedDescriptor)).toMatchObject([
      {
        editable: false,
        fieldKey: 'amount',
        maskStrategy: 'MONEY',
        readMode: 'MASKED',
      },
    ]);
    expect(getRuntimeDetailSections(restrictedDescriptor)[0]?.fields).toMatchObject([
      { editable: false, fieldKey: 'amount' },
    ]);

    const formSchemas = getAuthorizedRuntimeFormSchemas(restrictedDescriptor);
    const schema = JSON.parse(formSchemas.schemaJson || '{}') as {
      properties: Record<string, unknown>;
      required?: string[];
    };
    const uiSchema = JSON.parse(formSchemas.uiSchemaJson || '{}') as Record<
      string,
      Record<string, unknown>
    >;
    expect(Object.keys(schema.properties)).toEqual(['amount']);
    expect(schema.required).toEqual([]);
    expect(uiSchema.amount).toMatchObject({
      'ui:disabled': true,
      'ui:readonly': true,
      'ui:widget': 'money',
    });
  });

  it('parses instance data and exposes pending workflow state', () => {
    const record = toRuntimeTableRecord({
      dataJson: '{"amount":12,"reason":"差旅"}',
      formDefinitionId: 'FORM001',
      formKey: 'expense',
      id: 'INS001',
      status: 'SUBMITTED',
      submittedAt: '2026-07-18T20:00:00',
      submittedBy: 'alice',
      workflowStatus: 'PENDING_WORKFLOW',
    });

    expect(record.amount).toBe(12);
    expect(record.__data.reason).toBe('差旅');
    expect(workflowStatusTag(record.workflowStatus, record.processInstanceId)).toEqual({
      color: 'orange',
      label: '待启动流程',
    });
    expect(workflowStatusTag('COMPLETED', 'PROC001')).toEqual({
      color: 'blue',
      label: '已完成',
    });
  });

  it('keeps runtime validation errors in business language', () => {
    expect(
      formatApiErrorMessage('FORM_DATA_VALIDATION_FAILED', {
        fieldErrors: [{ code: 'TYPE_MISMATCH', field: 'amount' }],
      }),
    ).toBe('amount: 字段类型不正确');
  });
});
