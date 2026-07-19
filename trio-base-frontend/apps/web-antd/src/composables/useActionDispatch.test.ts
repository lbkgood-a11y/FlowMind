// @vitest-environment happy-dom

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createPinia, setActivePinia } from 'pinia';

import { useUserStore } from '@vben/stores';

const submitActionMock = vi.hoisted(() => vi.fn());
const messageMock = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
}));

vi.mock('#/api/action-client', () => ({
  submitAction: submitActionMock,
}));

vi.mock('@vben/preferences', () => ({
  preferences: { app: { locale: 'zh-CN' } },
}));

vi.mock('ant-design-vue', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    message: messageMock,
    Modal: {
      confirm: vi.fn((options) => options.onOk?.()),
    },
  };
});

import {
  isActionDispatchError,
  useActionDispatch,
} from './useActionDispatch';

describe('useActionDispatch', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useUserStore().setUserInfo({
      avatar: '',
      realName: 'Alice',
      roles: [],
      userId: 'U001',
      username: 'alice',
    });
    submitActionMock.mockReset();
    messageMock.error.mockReset();
    messageMock.success.mockReset();
  });

  it('fills GUI source, actor, locale, and refreshes after success', async () => {
    submitActionMock.mockResolvedValue({ actionId: 'ACT001', status: 'SUCCEEDED' });
    const refresh = vi.fn();
    const { dispatchAction, loading } = useActionDispatch();

    const result = await dispatchAction(
      {
        actionType: 'process.task.approve',
        target: { tenantId: 'tenant-a', type: 'PROCESS_TASK' },
      },
      { refresh, successMessage: '操作成功' },
    );

    expect(result.actionId).toBe('ACT001');
    expect(loading.value).toBe(false);
    expect(submitActionMock).toHaveBeenCalledWith(
      expect.objectContaining({
        actor: expect.objectContaining({
          displayName: 'Alice',
          id: 'U001',
          tenantId: 'tenant-a',
          type: 'USER',
        }),
        context: expect.objectContaining({
          locale: 'zh-CN',
          tenantId: 'tenant-a',
        }),
        source: 'GUI',
      }),
    );
    expect(messageMock.success).toHaveBeenCalledWith('操作成功');
    expect(refresh).toHaveBeenCalled();
  });

  it('throws structured action errors for terminal failures', async () => {
    submitActionMock.mockResolvedValue({
      errors: [{ code: 'ACTION_DENIED', message: '无权限' }],
      status: 'REJECTED',
    });
    const { dispatchAction } = useActionDispatch();

    let caught: unknown;
    try {
      await dispatchAction({ actionType: 'process.task.approve' });
    } catch (error) {
      caught = error;
    }
    expect(isActionDispatchError(caught)).toBe(true);
    expect(messageMock.error).toHaveBeenCalledWith('无权限');
  });
});
