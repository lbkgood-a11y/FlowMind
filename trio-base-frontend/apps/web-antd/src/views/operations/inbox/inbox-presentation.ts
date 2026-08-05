import type { InboxApi } from '#/api/inbox';

export const INBOX_RESPONSIVE_BREAKPOINT = 768;

export function inboxViewState(
  loading: boolean,
  error: string,
  itemCount: number,
) {
  return error
    ? 'ERROR'
    : loading
      ? 'LOADING'
      : itemCount === 0
        ? 'EMPTY'
        : 'READY';
}

export function buildInboxQuery(input: {
  itemType?: string;
  page: number;
  readState?: string;
  size: number;
  sourceOwner?: string;
  tenantId?: string;
  userId?: string;
}) {
  // 身份范围只能来自服务端认证上下文，前端即使被注入同名字段也不会转发。
  const { itemType, page, readState, size, sourceOwner } = input;
  return { itemType, page, readState, size, sourceOwner };
}

export function presentInboxItem(item: InboxApi.Item) {
  const expired = Boolean(item.expired);
  const unavailable = item.sourceAvailable === false;
  return {
    ariaLabel: `${item.readAt ? '已读' : '未读'}消息：${item.withdrawn ? '已撤回消息' : item.title}`,
    canInteract: !item.withdrawn && !expired && !unavailable,
    status: item.withdrawn
      ? 'WITHDRAWN'
      : expired
        ? 'EXPIRED'
        : unavailable
          ? 'SOURCE_UNAVAILABLE'
          : 'ACTIVE',
    summary: item.withdrawn
      ? '原内容已撤回，历史证据仍按策略保留。'
      : expired
        ? '消息已过期，快捷操作不可用。'
        : unavailable
          ? '来源服务暂不可用，请稍后重试。'
          : item.summary,
    title: item.withdrawn ? '已撤回消息' : item.title,
  };
}
