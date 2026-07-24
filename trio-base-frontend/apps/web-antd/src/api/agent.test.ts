import { describe, expect, it } from 'vitest';

import { parseAgentEvent } from './agent';

describe('agent SSE contract', () => {
  it('parses a versioned event envelope', () => {
    const event = parseAgentEvent(`id: run-1:2
event: message.delta
data: {"eventId":"run-1:2","eventType":"message.delta","runId":"run-1","threadId":"thread-1","sequence":2,"timestamp":"2026-07-24T00:00:00Z","traceId":"trace-1","dataSchemaVersion":"1.0","data":{"text":"你好"}}
`);

    expect(event).toMatchObject({
      eventId: 'run-1:2',
      eventType: 'message.delta',
      sequence: 2,
    });
  });

  it('ignores malformed or unsafe envelopes', () => {
    expect(parseAgentEvent('data: not-json\n')).toBeUndefined();
    expect(parseAgentEvent('data: {"eventType":1}\n')).toBeUndefined();
  });
});
