import { describe, expect, it } from 'vitest';

import {
  buildParticipantAssignment,
  buildBusinessClosureProcessDefinition,
  businessClosureCompletionChecks,
  businessClosureValidationIssues,
  buildSafeCondition,
  catalogFormFields,
  validateProcessDefinition,
} from './process-designer';

describe('process designer validation', () => {
  it('builds ROLE, USER, and DEPT participant payloads', () => {
    expect(buildParticipantAssignment('ROLE', 'DEPT_HEAD')).toEqual({
      roleCode: 'DEPT_HEAD',
      type: 'ROLE',
    });
    expect(buildParticipantAssignment('USER', 'USER001')).toEqual({
      type: 'USER',
      userId: 'USER001',
    });
    expect(buildParticipantAssignment('DEPT', 'FINANCE', 'ADMIN')).toEqual({
      deptCode: 'FINANCE',
      dimensionCode: 'ADMIN',
      type: 'DEPT',
    });
  });

  it('blocks unsupported nodes and missing participants before publish', () => {
    const errors = validateProcessDefinition(
      JSON.stringify({
        flow: {
          nodes: [
            {
              id: 'start',
              next: [{ condition: 'true', target: 'approve' }],
              type: 'START',
            },
            {
              id: 'approve',
              next: [{ condition: 'true', target: 'service' }],
              type: 'APPROVAL',
            },
            {
              id: 'service',
              next: [{ condition: 'true', target: 'end' }],
              type: 'SERVICE_TASK',
            },
            { id: 'end', type: 'END' },
          ],
        },
      }),
    );
    expect(errors.some((error) => error.includes('缺少有效参与者'))).toBe(true);
    expect(errors.some((error) => error.includes('未支持类型'))).toBe(true);
  });

  it('builds business-object-first closure definition without executor internals', () => {
    const json = buildBusinessClosureProcessDefinition(
      catalog(),
      {
        agentActionCode: 'paymentSummary',
        allowedStatuses: ['DRAFT', 'REJECTED'],
        approvedEventCode: 'ExpenseReportApproved',
        approvedStatus: 'APPROVED',
        approveActionCode: 'approve',
        businessRefFieldKey: 'businessId',
        businessRefSourceType: 'FORM_FIELD',
        conditionFieldKey: 'amount',
        conditionOperator: 'GT',
        conditionValue: '5000',
        launchMode: 'EXISTING_DOCUMENT',
        name: '费用报销',
        notifyActionCode: 'notifyApplicant',
        participantType: 'ROLE',
        participantValue: 'DEPT_HEAD',
        processKey: 'expense_report',
        rejectedEventCode: 'ExpenseReportRejected',
        rejectedStatus: 'REJECTED',
        retryClosureActionCode: 'retryClosure',
        selectedFormKey: 'expense_report_start',
        startStatus: 'IN_APPROVAL',
        submitActionCode: 'submit',
        updateStatusActionCode: 'updateStatus',
        viewActionCode: 'view',
      },
      JSON.stringify({
        flow: {
          nodes: [
            {
              id: 'start',
              next: [{ condition: '通过', target: 'approve' }],
              type: 'START',
            },
            {
              id: 'approve',
              next: [{ condition: '条件', target: 'end' }],
              type: 'APPROVAL',
            },
            { id: 'end', type: 'END' },
          ],
        },
      }),
    );
    const definition = JSON.parse(json);

    expect(definition.businessObject.typeCode).toBe('expense_report');
    expect(definition.businessObject.businessRef).toEqual({
      fieldKey: 'businessId',
      sourceType: 'FORM_FIELD',
    });
    expect(definition.form.schema.properties.businessId.title).toBe('报销单编号');
    expect(definition.flow.nodes[1].assignment.roleCode).toBe('DEPT_HEAD');
    expect(definition.flow.nodes[1].next[0].condition).toBe('amount > 5000');
    expect(definition.launchPolicy.allowedStatuses).toEqual(['DRAFT', 'REJECTED']);
    expect(definition.closurePolicy.outcomes.APPROVED[0].params.status).toBe('APPROVED');
    expect(definition.closurePolicy.outcomes.APPROVED[3].agentActionCode).toBe('paymentSummary');
    expect(definition.agentFollowUpPolicy.actions[0].params.businessId.sourceType).toBe('BUSINESS_REF');
    expect(json).not.toContain('executorKey');
  });

  it('builds selector-backed fields, conditions, and targeted completion checks', () => {
    const fields = catalogFormFields(catalog());
    expect(fields.map((field) => field.key)).toContain('businessId');
    expect(buildSafeCondition('amount', 'GTE', '3000')).toBe('amount >= 3000');

    const completeConfig = {
      allowedStatuses: ['DRAFT'],
      approvedStatus: 'APPROVED',
      approveActionCode: 'approve',
      businessRefFieldKey: 'businessId',
      businessRefSourceType: 'FORM_FIELD' as const,
      conditionFieldKey: 'amount',
      conditionOperator: 'GT' as const,
      conditionValue: '5000',
      launchMode: 'EXISTING_DOCUMENT' as const,
      name: '费用报销',
      participantType: 'ROLE' as const,
      participantValue: 'DEPT_HEAD',
      processKey: 'expense_report',
      rejectedStatus: 'REJECTED',
      retryClosureActionCode: 'retryClosure',
      selectedFormKey: 'expense_report_start',
      startStatus: 'IN_APPROVAL',
      submitActionCode: 'submit',
      updateStatusActionCode: 'updateStatus',
      viewActionCode: 'view',
    };

    expect(businessClosureValidationIssues(catalog(), completeConfig)).toEqual([]);
    expect(businessClosureCompletionChecks(catalog(), completeConfig).every((item) => item.ok)).toBe(true);

    const incomplete = businessClosureValidationIssues(catalog(), {
      ...completeConfig,
      businessRefFieldKey: undefined,
    });
    expect(incomplete[0]?.location).toContain('业务编号来源');
  });
});

function catalog() {
  return {
    actions: [
      { actionCode: 'updateStatus', actionType: 'UPDATE_STATUS', displayName: '更新状态' },
      {
        actionCode: 'createDocument',
        actionType: 'CREATE_DOCUMENT',
        displayName: '创建报销单',
        paramSchemaJson:
          '{"type":"object","required":["businessId","amount","reason"],"properties":{"businessId":{"type":"string","title":"报销单编号"},"amount":{"type":"number","title":"报销金额"},"reason":{"type":"string","title":"报销事由"}}}',
      },
      { actionCode: 'notifyApplicant', actionType: 'NOTIFICATION', displayName: '通知申请人' },
    ],
    agentActions: [
      {
        agentActionCode: 'paymentSummary',
        displayName: '付款准备摘要',
        paramSchemaJson: '{"required":["amount","businessId"]}',
      },
    ],
    events: [
      { displayName: '审批通过', eventCode: 'ExpenseReportApproved', eventType: 'ExpenseReportApproved' },
      { displayName: '审批驳回', eventCode: 'ExpenseReportRejected', eventType: 'ExpenseReportRejected' },
    ],
    forms: [
      {
        displayName: '报销申请表',
        formKey: 'expense_report_start',
        formRole: 'START',
        required: true,
      },
    ],
    object: {
      displayName: '报销单',
      id: 'BIZ_EXPENSE_REPORT',
      serviceCode: 'expense-service',
      status: 'PUBLISHED',
      tenantId: 'GLOBAL',
      typeCode: 'expense_report',
      version: 1,
    },
    permissions: [
      { actionCode: 'submit', displayName: '提交报销', permissionCode: '/submit' },
      { actionCode: 'view', displayName: '查看报销', permissionCode: '/view' },
      { actionCode: 'approve', displayName: '审批报销', permissionCode: '/approve' },
      { actionCode: 'retryClosure', displayName: '重试闭环', permissionCode: '/retry' },
      { actionCode: 'agentFollowUp', displayName: 'Agent 跟进', permissionCode: '/agent' },
    ],
    statuses: [
      { displayName: '草稿', statusCode: 'DRAFT' },
      { displayName: '审批中', statusCode: 'IN_APPROVAL' },
      { displayName: '已通过', statusCode: 'APPROVED' },
      { displayName: '已驳回', statusCode: 'REJECTED' },
    ],
    templates: [],
  };
}
