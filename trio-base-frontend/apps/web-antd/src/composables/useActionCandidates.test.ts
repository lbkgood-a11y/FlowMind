// @vitest-environment happy-dom

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createPinia, setActivePinia } from 'pinia';

import { useUserStore } from '@vben/stores';

const actionClientMocks = vi.hoisted(() => ({
  createActionIdempotencyKey: vi.fn(() => 'cand-1'),
  dispatchActionCandidate: vi.fn(),
  validateActionCandidate: vi.fn(),
  validateActionCandidates: vi.fn(),
}));

vi.mock('#/api/action-client', () => actionClientMocks);

vi.mock('@vben/preferences', () => ({
  preferences: { app: { locale: 'zh-CN' } },
}));

import { useActionCandidates } from './useActionCandidates';

describe('useActionCandidates', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useUserStore().setUserInfo({
      avatar: '',
      realName: 'Alice',
      roles: [],
      userId: 'U001',
      username: 'alice',
    });
    vi.clearAllMocks();
  });

  it('creates LUI candidates without mutating business data directly', async () => {
    const { createCandidate, validateCandidate } = useActionCandidates();
    actionClientMocks.validateActionCandidate.mockResolvedValue({ valid: true });

    const candidate = createCandidate(
      {
        actionType: 'lowcode.form.submit',
        payload: { data: { amount: 100 } },
        target: { tenantId: 'tenant-a', type: 'LOWCODE_FORM' },
      },
      { correlationId: 'lui-turn-1', reason: '用户自然语言提交' },
    );
    await validateCandidate(candidate);

    expect(candidate).toMatchObject({
      actor: { displayName: 'Alice', id: 'U001', tenantId: 'tenant-a' },
      candidateId: 'cand-1',
      context: {
        attributes: { naturalLanguageCorrelationId: 'lui-turn-1' },
        correlationId: 'lui-turn-1',
        locale: 'zh-CN',
        tenantId: 'tenant-a',
      },
      proposedBy: 'LUI',
      source: 'LUI',
    });
    expect(actionClientMocks.validateActionCandidate).toHaveBeenCalledWith(candidate);
    expect(actionClientMocks.dispatchActionCandidate).not.toHaveBeenCalled();
  });

  it('dispatches only through the action candidate endpoint', async () => {
    const { dispatchCandidate } = useActionCandidates();
    actionClientMocks.dispatchActionCandidate.mockResolvedValue({
      actionId: 'ACT001',
      status: 'SUCCEEDED',
    });

    await dispatchCandidate({ actionType: 'process.task.approve' });

    expect(actionClientMocks.dispatchActionCandidate).toHaveBeenCalledWith({
      actionType: 'process.task.approve',
    });
  });

  it('batch-validates candidates through the Action Client', async () => {
    const { validateCandidates } = useActionCandidates();
    const candidates = [
      { actionType: 'process.task.approve' },
      { actionType: 'process.task.reject' },
    ];
    actionClientMocks.validateActionCandidates.mockResolvedValue([
      { actionType: 'process.task.approve', enabled: true },
    ]);

    const result = await validateCandidates(candidates);

    expect(result).toEqual([{ actionType: 'process.task.approve', enabled: true }]);
    expect(actionClientMocks.validateActionCandidates).toHaveBeenCalledWith(candidates);
  });
});
