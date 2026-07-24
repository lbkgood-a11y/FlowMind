import type { ActionApi } from '#/api/action-client';
import type { RefreshScopeHandlers } from '#/shared/action/useRefreshScopes';

import { watch } from 'vue';

import { refreshByScopes } from '#/shared/action/useRefreshScopes';
import { useAgentStore } from '#/store';

function useAgentRefreshScopes(handlers: RefreshScopeHandlers) {
  const agentStore = useAgentStore();
  watch(
    () => agentStore.refreshVersion,
    async (version, previous) => {
      if (version === previous) return;
      await refreshByScopes(
        {
          refreshScopes: agentStore.refreshScopes,
        } as ActionApi.GlobalActionResult,
        handlers,
      );
    },
  );
}

export { useAgentRefreshScopes };
