import { requestClient } from '#/api/request';

export namespace InboxApi {
  export interface Boundary {
    id: string;
    receivedAt: string;
  }

  export interface ResourceReference {
    actionId?: string;
    ownerService: string;
    resourceId: string;
    resourceKey: string;
    resourceType: string;
  }

  export interface Item {
    archivedAt?: string;
    expired?: boolean;
    id: string;
    itemType: string;
    readAt?: string;
    receivedAt: string;
    resourceReference?: ResourceReference;
    sourceAvailable?: boolean;
    summary: string;
    taskRelated: boolean;
    title: string;
    withdrawn: boolean;
  }

  export interface BellPreview {
    boundary?: Boundary;
    recentItems: Item[];
    unreadCount: number;
  }

  export interface Page {
    hasMore: boolean;
    items: Item[];
    page: number;
    size: number;
  }

  export interface RegisteredNavigation {
    applicationKey: string;
    available: boolean;
    resourceId: string;
    resourceKey: string;
  }
}

/** SSE 只发失效提示；铃铛展示始终以该权威查询结果为准。 */
export function getInboxBell(limit = 10) {
  return requestClient.get<InboxApi.BellPreview>('/v2/inbox/bell', {
    params: { limit },
  });
}

export function markInboxItemsRead(ids: string[]) {
  return requestClient.post('/v2/inbox/read', { ids });
}

export function markAllInboxItemsRead(boundary: InboxApi.Boundary) {
  return requestClient.post('/v2/inbox/read-all', boundary);
}

export function getInboxPage(params: Record<string, unknown>) {
  return requestClient.get<InboxApi.Page>('/v2/inbox', { params });
}

export function archiveInboxItem(id: string) {
  return requestClient.post(`/v2/inbox/${id}/archive`);
}

export function restoreInboxItem(id: string) {
  return requestClient.post(`/v2/inbox/${id}/restore`);
}

export function hideInboxItem(id: string) {
  return requestClient.delete(`/v2/inbox/${id}`);
}

export function getInboxNavigation(id: string) {
  return requestClient.get<InboxApi.RegisteredNavigation>(
    `/v2/inbox/${id}/navigation`,
  );
}

export function executeInboxAction(
  id: string,
  data: { idempotencyKey: string; payload: Record<string, unknown> },
) {
  return requestClient.post(`/v2/inbox/${id}/actions/execute`, data);
}
