import type { AgentApi } from '#/api/agent';

import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useAgentStore } from './agent';

function run(): AgentApi.Run {
  return {
    correlationId: 'correlation-1',
    createdAt: '2026-07-24T00:00:00Z',
    graphId: 'triobase-assistant',
    graphVersion: '1.0.0',
    lastSequence: 0,
    runId: 'run-1',
    status: 'RUNNING',
    threadId: 'thread-1',
    traceId: 'trace-1',
    updatedAt: '2026-07-24T00:00:00Z',
  };
}

function event(
  sequence: number,
  eventType: string,
  data: Record<string, unknown>,
): AgentApi.Event {
  return {
    data,
    dataSchemaVersion: '1.0',
    eventId: `run-1:${sequence}`,
    eventType,
    runId: 'run-1',
    sequence,
    threadId: 'thread-1',
    timestamp: '2026-07-24T00:00:00Z',
    traceId: 'trace-1',
  };
}

describe('agent store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('applies ordered events and ignores replay duplicates', () => {
    const store = useAgentStore();
    store.begin(run(), '帮我请假');
    store.applyEvent(event(1, 'message.delta', { text: '正在处理' }));
    store.applyEvent(event(1, 'message.delta', { text: '重复消息' }));
    expect(store.messages.map((item) => item.text)).toEqual([
      '帮我请假',
      '正在处理',
    ]);
  });

  it('ignores unknown future event types safely', () => {
    const store = useAgentStore();
    store.begin(run(), '测试');
    expect(() =>
      store.applyEvent(event(1, 'future.event', { html: '<script />' })),
    ).not.toThrow();
    expect(store.lastSequence).toBe(1);
  });
});
