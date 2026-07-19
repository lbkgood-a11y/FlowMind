import type { ActionApi } from '#/api/action-client';

export type RefreshScope =
  | 'actions'
  | 'attachments'
  | 'document'
  | 'list'
  | 'relatedTables'
  | 'timeline'
  | 'workflow'
  | string;

export type RefreshScopeHandlers = Partial<Record<RefreshScope, () => Promise<void> | void>>;

function normalizeRefreshScopes(result?: ActionApi.GlobalActionResult) {
  const scopes = result?.refreshScopes ?? [];
  return [...new Set(scopes.filter(Boolean))];
}

async function refreshByScopes(
  result: ActionApi.GlobalActionResult | undefined,
  handlers: RefreshScopeHandlers,
) {
  const scopes = normalizeRefreshScopes(result);
  await Promise.all(scopes.map((scope) => handlers[scope]?.()));
  return scopes;
}

export { normalizeRefreshScopes, refreshByScopes };
