// @vitest-environment happy-dom

import { flushPromises, shallowMount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import { Modal } from 'ant-design-vue';

const apiMocks = vi.hoisted(() => ({
  getRejectTargets: vi.fn().mockResolvedValue([]),
}));

const actionApiMocks = vi.hoisted(() => ({
  ACTION_TARGET_TYPES: { processTask: 'PROCESS_TASK' },
  ACTION_TYPES: {
    processTaskAddSign: 'process.task.addSign',
    processTaskApprove: 'process.task.approve',
    processTaskReject: 'process.task.reject',
    processTaskTransfer: 'process.task.transfer',
  },
  createActionIdempotencyKey: vi.fn(() => 'idem-task'),
  requireActionData: vi.fn((result: any, key: string) => result.data[key]),
}));

const dispatchMocks = vi.hoisted(() => ({
  dispatchAction: vi.fn(),
}));

vi.mock('#/api', () => actionApiMocks);
vi.mock('#/api/process', () => apiMocks);
vi.mock('#/composables/useActionDispatch', () => ({
  useActionDispatch: () => ({ dispatchAction: dispatchMocks.dispatchAction }),
}));
vi.mock('#/components/business', () => ({
  UserSelect: { name: 'UserSelect', template: '<div class="user-select" />' },
}));

import TaskActionDialog from './TaskActionDialog.vue';

const task = {
  assigneeType: 'ROLE',
  createdAt: '2026-07-13T10:00:00',
  id: 'TASK001',
  nodeId: 'approve',
  nodeName: '部门审批',
  nodeType: 'APPROVAL',
  processInstanceId: 'INSTANCE001',
  processKey: 'expense_report',
  processName: '费用报销',
  status: 'PENDING',
  title: '审批费用报销',
};

describe('TaskActionDialog', () => {
  it('dispatches approval as a Global Action with idempotency', async () => {
    dispatchMocks.dispatchAction.mockResolvedValue({ data: { task }, status: 'SUCCEEDED' });
    const wrapper = shallowMount(TaskActionDialog, {
      props: { action: 'APPROVE', open: true, task },
    });

    wrapper.findComponent(Modal).vm.$emit('ok');
    await flushPromises();

    expect(dispatchMocks.dispatchAction).toHaveBeenCalledWith(
      expect.objectContaining({
        actionType: 'process.task.approve',
        executionMode: 'SIGNAL',
        idempotencyKey: 'idem-task',
        payload: expect.objectContaining({
          operationId: 'idem-task',
          taskId: 'TASK001',
        }),
        target: expect.objectContaining({
          id: 'TASK001',
          type: 'PROCESS_TASK',
        }),
      }),
      expect.objectContaining({ failureMessage: '任务操作失败' }),
    );
    expect(wrapper.emitted('success')?.[0]?.[0]).toBe('APPROVE');
  });

  it('loads reject targets when the reject dialog opens', async () => {
    apiMocks.getRejectTargets.mockResolvedValue(['dept_approve']);
    shallowMount(TaskActionDialog, {
      props: { action: 'REJECT', open: true, task },
    });
    await flushPromises();
    expect(apiMocks.getRejectTargets).toHaveBeenCalledWith('INSTANCE001');
  });
});
