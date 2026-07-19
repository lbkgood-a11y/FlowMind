// @vitest-environment happy-dom

import type { ActionApi } from './action-client';

import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestClientMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  requestSSE: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: requestClientMock,
}));

import {
  createActionIdempotencyKey,
  getActionDetail,
  getActionEvents,
  queryActions,
  dispatchActionCandidate,
  submitAction,
  subscribeActionEvents,
  validateActionCandidate,
  validateActionCandidates,
} from './action-client';

describe('action-client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits a Global Action through the facade endpoint', async () => {
    requestClientMock.post.mockResolvedValue({ actionId: 'ACT001', status: 'SUCCEEDED' });
    const result = await submitAction({
      actionType: 'process.task.approve',
      idempotencyKey: 'idem-1',
    });

    expect(requestClientMock.post).toHaveBeenCalledWith('/actions', {
      actionType: 'process.task.approve',
      idempotencyKey: 'idem-1',
    });
    expect(result.actionId).toBe('ACT001');
  });

  it('queries action detail, events, and list filters', async () => {
    await getActionDetail('ACT001');
    await getActionEvents('ACT001');
    await queryActions({ actionType: 'process.task.approve', page: 1 });

    expect(requestClientMock.get).toHaveBeenNthCalledWith(1, '/actions/ACT001');
    expect(requestClientMock.get).toHaveBeenNthCalledWith(2, '/actions/ACT001/events');
    expect(requestClientMock.get).toHaveBeenNthCalledWith(3, '/actions', {
      params: { actionType: 'process.task.approve', page: 1 },
    });
  });

  it('validates and dispatches Action Candidates through candidate endpoints', async () => {
    await validateActionCandidate({ actionType: 'process.task.approve' });
    requestClientMock.post.mockResolvedValueOnce({
      results: [{ actionType: 'process.task.approve', enabled: true, visible: true }],
    });
    const batch = await validateActionCandidates([
      { actionType: 'process.task.approve' },
    ]);
    await dispatchActionCandidate({ actionType: 'process.task.approve' });

    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      1,
      '/actions/candidates/validate',
      { actionType: 'process.task.approve' },
    );
    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      2,
      '/actions/candidates/batch-validate',
      { candidates: [{ actionType: 'process.task.approve' }] },
    );
    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      3,
      '/actions/candidates/dispatch',
      { actionType: 'process.task.approve' },
    );
    expect(batch).toEqual([
      { actionType: 'process.task.approve', enabled: true, visible: true },
    ]);
  });

  it('parses SSE chunks into ordered action events', async () => {
    requestClientMock.requestSSE.mockImplementation(async (_url, _data, options) => {
      options.onMessage('id: EVT001\nevent: CREATED\ndata: {"eventId":"EVT001",');
      options.onMessage('"actionId":"ACT001","eventType":"CREATED"}\n\n');
      options.onEnd();
    });
    const events: ActionApi.ActionEvent[] = [];
    const onEnd = vi.fn();

    await subscribeActionEvents('ACT001', {
      onEnd,
      onEvent: (event) => events.push(event),
    });

    expect(requestClientMock.requestSSE).toHaveBeenCalledWith(
      '/actions/ACT001/stream',
      undefined,
      expect.objectContaining({ method: 'GET' }),
    );
    expect(events).toEqual([
      { actionId: 'ACT001', eventId: 'EVT001', eventType: 'CREATED' },
    ]);
    expect(onEnd).toHaveBeenCalled();
  });

  it('creates namespaced idempotency keys', () => {
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'uuid-1') });
    expect(createActionIdempotencyKey('process.task.approve', 'TASK001')).toBe(
      'process.task.approve:TASK001:uuid-1',
    );
  });
});
