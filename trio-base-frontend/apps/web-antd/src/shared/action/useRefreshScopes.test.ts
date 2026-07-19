import { describe, expect, it, vi } from 'vitest';

import { normalizeRefreshScopes, refreshByScopes } from './useRefreshScopes';

describe('useRefreshScopes', () => {
  it('deduplicates refresh scopes from Global Action results', () => {
    expect(
      normalizeRefreshScopes({
        actionId: 'act-1',
        refreshScopes: ['document', 'actions', 'document'],
        status: 'SUCCEEDED',
      }),
    ).toEqual(['document', 'actions']);
  });

  it('runs handlers for returned scopes only', async () => {
    const document = vi.fn();
    const list = vi.fn();
    const timeline = vi.fn();

    const scopes = await refreshByScopes(
      {
        actionId: 'act-1',
        refreshScopes: ['document', 'timeline'],
        status: 'SUCCEEDED',
      },
      { document, list, timeline },
    );

    expect(scopes).toEqual(['document', 'timeline']);
    expect(document).toHaveBeenCalledOnce();
    expect(timeline).toHaveBeenCalledOnce();
    expect(list).not.toHaveBeenCalled();
  });
});
