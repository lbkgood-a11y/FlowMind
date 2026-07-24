import { requestClient } from '#/api/request';

export namespace AgentApi {
  export type RunStatus =
    | 'CANCELLED'
    | 'COMPLETED'
    | 'CREATED'
    | 'FAILED'
    | 'RUNNING'
    | 'WAITING_CONFIRMATION'
    | 'WAITING_INPUT';

  export interface PageContext {
    appKey?: string;
    objectId?: string;
    objectType?: string;
    route?: string;
  }

  export interface RunCreate {
    graphId?: string;
    graphVersion?: string;
    message: string;
    model?: string;
    pageContext?: PageContext;
    threadId?: string;
  }

  export interface RunResume {
    kind: 'action_result' | 'cancel' | 'input';
    values?: Record<string, unknown>;
  }

  export interface Run {
    actionRefs?: string[];
    correlationId: string;
    createdAt: string;
    error?: {
      code: string;
      message: string;
      retryable?: boolean;
    };
    graphId: string;
    graphVersion: string;
    lastSequence: number;
    pendingInterrupt?: Record<string, unknown>;
    runId: string;
    status: RunStatus;
    threadId: string;
    traceId: string;
    updatedAt: string;
  }

  export interface Event {
    data: Record<string, unknown>;
    dataSchemaVersion: string;
    eventId: string;
    eventType: string;
    runId: string;
    sequence: number;
    threadId: string;
    timestamp: string;
    traceId: string;
  }

  export interface StreamOptions {
    cursor?: number;
    onEvent: (event: Event) => void;
    signal?: AbortSignal;
  }
}

async function createAgentRun(data: AgentApi.RunCreate) {
  const key =
    globalThis.crypto?.randomUUID?.() ??
    `agent-run-${Date.now().toString(36)}`;
  return requestClient.post<AgentApi.Run>('/agent/runs', data, {
    headers: { 'Idempotency-Key': key },
  });
}

async function getAgentRun(runId: string) {
  return requestClient.get<AgentApi.Run>(`/agent/runs/${runId}`);
}

async function resumeAgentRun(runId: string, data: AgentApi.RunResume) {
  return requestClient.post<AgentApi.Run>(`/agent/runs/${runId}/resume`, data);
}

async function cancelAgentRun(runId: string) {
  return requestClient.post<AgentApi.Run>(`/agent/runs/${runId}/cancel`);
}

async function subscribeAgentRunEvents(
  runId: string,
  options: AgentApi.StreamOptions,
) {
  let buffer = '';
  let cursor = options.cursor ?? 0;
  let terminal = false;
  let retry = 0;

  while (!terminal && !options.signal?.aborted) {
    try {
      await requestClient.requestSSE(
        `/agent/runs/${runId}/events?cursor=${cursor}`,
        undefined,
        {
          method: 'GET',
          onMessage(message: string) {
            buffer += message;
            const chunks = buffer.split(/\r?\n\r?\n/);
            buffer = chunks.pop() ?? '';
            chunks.forEach((chunk) => {
              const event = parseAgentEvent(chunk);
              if (!event || event.sequence <= cursor) return;
              cursor = event.sequence;
              options.onEvent(event);
              terminal = [
                'run.cancelled',
                'run.completed',
                'run.failed',
              ].includes(event.eventType);
            });
          },
          signal: options.signal,
        },
      );
      retry = 0;
      if (!terminal && !options.signal?.aborted) {
        await wait(300);
      }
    } catch (error) {
      if (options.signal?.aborted) return;
      retry += 1;
      if (retry > 5) throw error;
      await wait(Math.min(3000, retry * 500));
    }
  }
}

function parseAgentEvent(chunk: string): AgentApi.Event | undefined {
  const dataLines = chunk
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart());
  if (dataLines.length === 0) return undefined;
  try {
    const value = JSON.parse(dataLines.join('\n')) as AgentApi.Event;
    if (
      !value ||
      typeof value.eventType !== 'string' ||
      typeof value.sequence !== 'number' ||
      typeof value.runId !== 'string'
    ) {
      return undefined;
    }
    return value;
  } catch {
    return undefined;
  }
}

function wait(milliseconds: number) {
  return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds));
}

export {
  cancelAgentRun,
  createAgentRun,
  getAgentRun,
  parseAgentEvent,
  resumeAgentRun,
  subscribeAgentRunEvents,
};
