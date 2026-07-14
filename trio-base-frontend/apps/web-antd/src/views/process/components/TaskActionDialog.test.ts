import { flushPromises, shallowMount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import { Modal } from 'ant-design-vue';

const apiMocks = vi.hoisted(() => ({
  addSignTask: vi.fn(),
  approveTask: vi.fn(),
  getRejectTargets: vi.fn().mockResolvedValue([]),
  rejectTask: vi.fn(),
  transferTask: vi.fn(),
}));

vi.mock('#/api/process', () => apiMocks);
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
  it('submits an approval with a generated operation id', async () => {
    apiMocks.approveTask.mockResolvedValue(task);
    const wrapper = shallowMount(TaskActionDialog, {
      props: { action: 'APPROVE', open: true, task },
    });

    wrapper.findComponent(Modal).vm.$emit('ok');
    await flushPromises();

    expect(apiMocks.approveTask).toHaveBeenCalledWith(
      'TASK001',
      expect.objectContaining({ operationId: expect.any(String) }),
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
