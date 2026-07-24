import { describe, expect, it } from 'vitest';

import { resolveAgentComponent } from './agent-component-registry';

describe('agent component registry', () => {
  it('accepts registered schema-valid candidate components', () => {
    expect(
      resolveAgentComponent('AgentActionCandidate', {
        candidate: {
          actionType: 'lowcode.form.submit',
          payload: { appKey: 'LEAVE_APP' },
        },
      }),
    ).toMatchObject({ key: 'AgentActionCandidate' });
  });

  it('rejects unknown components and dynamic handlers', () => {
    expect(() => resolveAgentComponent('RawHtml', {})).toThrow(
      'AGENT_COMPONENT_NOT_REGISTERED',
    );
    expect(() =>
      resolveAgentComponent('AgentError', {
        code: 'FAILED',
        message: 'failed',
        onClick: 'alert(1)',
      }),
    ).toThrow('AGENT_COMPONENT_PROPS_INVALID');
  });
});
