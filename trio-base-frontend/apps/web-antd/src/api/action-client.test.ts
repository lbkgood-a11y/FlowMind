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
  dispatchActionCandidate,
  getActionDetail,
  getActionEvents,
  queryActions,
  submitAction,
  subscribeActionEvents,
  validateActionCandidate,
  validateActionCandidates,
} from './action-client';

describe('action-client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits a Global Action through the owner-hosted endpoint', async () => {
    requestClientMock.post.mockResolvedValue({ actionId: 'ACT001', status: 'SUCCEEDED' });
    const result = await submitAction({
      actionType: 'process.task.approve',
      idempotencyKey: 'idem-1',
    });

    expect(requestClientMock.post).toHaveBeenCalledWith('/workflow-actions/dispatch', {
      actionType: 'process.task.approve',
      idempotencyKey: 'idem-1',
    });
    expect(result.actionId).toBe('ACT001');
  });

  it('keeps central submit fallback for unknown action owners', async () => {
    await submitAction({
      actionType: 'custom.action.run',
      idempotencyKey: 'idem-2',
    });

    expect(requestClientMock.post).toHaveBeenCalledWith('/actions', {
      actionType: 'custom.action.run',
      idempotencyKey: 'idem-2',
    });
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

  it('routes action detail and event lookups with explicit owner hints', async () => {
    await getActionDetail('ACT002', { ownerService: 'service-workflow-engine' });
    await getActionEvents('ACT003', { actionType: 'lowcode.form.submit' });

    expect(requestClientMock.get).toHaveBeenNthCalledWith(1, '/workflow-actions/ACT002');
    expect(requestClientMock.get).toHaveBeenNthCalledWith(
      2,
      '/lowcode-runtime/actions/ACT003/events',
    );
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
      '/workflow-actions/candidates/validate',
      { actionType: 'process.task.approve' },
    );
    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      2,
      '/workflow-actions/candidates/batch-validate',
      { candidates: [{ actionType: 'process.task.approve' }] },
    );
    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      3,
      '/workflow-actions/candidates/dispatch',
      { actionType: 'process.task.approve' },
    );
    expect(batch).toEqual([
      { actionType: 'process.task.approve', enabled: true, visible: true },
    ]);
  });

  it('dispatches a lowcode leave-form candidate through the lowcode owner endpoint', async () => {
    requestClientMock.post.mockResolvedValueOnce({
      actionId: 'ACT-LEAVE-1',
      ownerExecutionRef: 'leave-001',
      ownerService: 'service-lowcode',
      refreshScopes: ['document', 'actions', 'timeline'],
      status: 'SUCCEEDED',
    });

    const result = await dispatchActionCandidate({
      actionType: 'lowcode.form.submit',
      payload: {
        actionCode: 'submitAndLaunch',
        appKey: 'leave',
        data: { reason: 'family' },
      },
      target: { ownerService: 'service-lowcode', type: 'LOWCODE_FORM' },
    });

    expect(requestClientMock.post).toHaveBeenCalledWith(
      '/lowcode-runtime/actions/candidates/dispatch',
      {
        actionType: 'lowcode.form.submit',
        payload: {
          actionCode: 'submitAndLaunch',
          appKey: 'leave',
          data: { reason: 'family' },
        },
        target: { ownerService: 'service-lowcode', type: 'LOWCODE_FORM' },
      },
    );
    expect(result).toMatchObject({
      actionId: 'ACT-LEAVE-1',
      ownerExecutionRef: 'leave-001',
      status: 'SUCCEEDED',
    });
  });

  it('groups batch candidate validation by owner endpoint', async () => {
    requestClientMock.post
      .mockResolvedValueOnce({
        results: [{ actionType: 'lowcode.form.submit', enabled: true }],
      })
      .mockResolvedValueOnce({
        results: [{ actionType: 'integration.orchestration.start', enabled: true }],
      });

    const batch = await validateActionCandidates([
      { actionType: 'lowcode.form.submit' },
      { actionType: 'integration.orchestration.start' },
    ]);

    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      1,
      '/lowcode-runtime/actions/candidates/batch-validate',
      { candidates: [{ actionType: 'lowcode.form.submit' }] },
    );
    expect(requestClientMock.post).toHaveBeenNthCalledWith(
      2,
      '/openapi/management/actions/candidates/batch-validate',
      { candidates: [{ actionType: 'integration.orchestration.start' }] },
    );
    expect(batch).toEqual([
      { actionType: 'lowcode.form.submit', enabled: true },
      { actionType: 'integration.orchestration.start', enabled: true },
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

  it('routes SSE subscriptions with explicit owner hints', async () => {
    requestClientMock.requestSSE.mockImplementation(async (_url, _data, options) => {
      options.onEnd?.();
    });

    await subscribeActionEvents('ACT002', {
      ownerService: 'service-workflow-engine',
    });

    expect(requestClientMock.requestSSE).toHaveBeenCalledWith(
      '/workflow-actions/ACT002/stream',
      undefined,
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('creates namespaced idempotency keys', () => {
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'uuid-1') });
    expect(createActionIdempotencyKey('process.task.approve', 'TASK001')).toBe(
      'process.task.approve:TASK001:uuid-1',
    );
  });
});
