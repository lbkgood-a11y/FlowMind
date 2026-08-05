import { describe, expect, it } from 'vitest';

import {
  buildInboxQuery,
  INBOX_RESPONSIVE_BREAKPOINT,
  inboxViewState,
  presentInboxItem,
} from './inbox-presentation';

const base = {
  id: '1',
  itemType: 'NOTIFICATION',
  receivedAt: '2026-08-04T00:00:00Z',
  summary: '摘要',
  taskRelated: false,
  title: '标题',
  withdrawn: false,
};

describe('inbox presentation policies', () => {
  it('never forwards tenant or user selectors from the browser', () => {
    expect(
      buildInboxQuery({
        page: 1,
        size: 20,
        tenantId: 'tenant-b',
        userId: 'other',
      }),
    ).toEqual({
      itemType: undefined,
      page: 1,
      readState: undefined,
      size: 20,
      sourceOwner: undefined,
    });
  });

  it.each([
    [{ ...base, withdrawn: true }, 'WITHDRAWN', '原内容已撤回'],
    [{ ...base, expired: true }, 'EXPIRED', '消息已过期'],
    [
      { ...base, sourceAvailable: false },
      'SOURCE_UNAVAILABLE',
      '来源服务暂不可用',
    ],
  ] as const)(
    'disables interaction for evidence or unavailable states',
    (item, status, summary) => {
      const view = presentInboxItem(item);
      expect(view).toMatchObject({ canInteract: false, status });
      expect(view.summary).toContain(summary);
    },
  );

  it('provides readable state labels and a mobile breakpoint', () => {
    expect(presentInboxItem(base).ariaLabel).toBe('未读消息：标题');
    expect(INBOX_RESPONSIVE_BREAKPOINT).toBe(768);
  });

  it('distinguishes loading, empty, error and ready states', () => {
    expect(inboxViewState(true, '', 0)).toBe('LOADING');
    expect(inboxViewState(false, '', 0)).toBe('EMPTY');
    expect(inboxViewState(false, 'failed', 0)).toBe('ERROR');
    expect(inboxViewState(false, '', 1)).toBe('READY');
  });
});
