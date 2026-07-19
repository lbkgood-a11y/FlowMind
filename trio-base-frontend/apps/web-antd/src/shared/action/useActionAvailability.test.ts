// @vitest-environment happy-dom

import { beforeEach, describe, expect, it, vi } from 'vitest';

const actionClientMocks = vi.hoisted(() => ({
  validateActionCandidates: vi.fn(),
}));

vi.mock('#/api/action-client', () => actionClientMocks);

import { useActionAvailability } from './useActionAvailability';

describe('useActionAvailability', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('indexes repeated action types by candidate id', async () => {
    actionClientMocks.validateActionCandidates.mockResolvedValue([
      {
        actionType: 'process.closure.effect.retry',
        candidateId: 'process.closure.effect.retry:E001',
        enabled: false,
        visible: true,
      },
      {
        actionType: 'process.closure.effect.retry',
        candidateId: 'process.closure.effect.retry:E002',
        enabled: true,
        visible: true,
      },
    ]);
    const { getAvailability, loadAvailability } = useActionAvailability();

    await loadAvailability([
      {
        actionType: 'process.closure.effect.retry',
        candidateId: 'process.closure.effect.retry:E001',
      },
      {
        actionType: 'process.closure.effect.retry',
        candidateId: 'process.closure.effect.retry:E002',
      },
    ]);

    expect(
      getAvailability('process.closure.effect.retry:E001')?.enabled,
    ).toBe(false);
    expect(
      getAvailability('process.closure.effect.retry:E002')?.enabled,
    ).toBe(true);
    expect(getAvailability('process.closure.effect.retry')).toBeUndefined();
  });

  it('clears cached availability when no candidates are present', async () => {
    actionClientMocks.validateActionCandidates.mockResolvedValueOnce([
      {
        actionType: 'process.task.approve',
        enabled: true,
        visible: true,
      },
    ]);
    const { availabilityMap, loadAvailability } = useActionAvailability();

    await loadAvailability([{ actionType: 'process.task.approve' }]);
    await loadAvailability([]);

    expect(availabilityMap.value.size).toBe(0);
    expect(actionClientMocks.validateActionCandidates).toHaveBeenCalledTimes(1);
  });
});
