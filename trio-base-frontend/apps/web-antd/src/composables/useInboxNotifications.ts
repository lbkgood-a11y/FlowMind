import type { InboxStreamState } from './inbox-sse-client';

import type { InboxApi } from '#/api/inbox';

import { onBeforeUnmount, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { getInboxBell } from '#/api/inbox';

import { InboxSseClient } from './inbox-sse-client';

export function useInboxNotifications() {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  const accessStore = useAccessStore();
  const preview = ref<InboxApi.BellPreview>({
    recentItems: [],
    unreadCount: 0,
  });
  const state = ref<InboxStreamState>('stopped');

  const reconcile = async () => {
    preview.value = await getInboxBell(10);
  };
  const client = new InboxSseClient({
    getAccessToken: () => accessStore.accessToken,
    onStateChange: (next) => (state.value = next),
    reconcile,
    streamUrl: `${apiURL.replace(/\/$/, '')}/v2/inbox/events`,
  });

  onBeforeUnmount(() => client.stop());

  return {
    preview,
    reconcile,
    start: () => client.start(),
    state,
    stop: () => client.stop(),
  };
}
