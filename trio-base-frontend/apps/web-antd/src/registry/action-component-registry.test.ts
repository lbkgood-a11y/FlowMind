import { describe, expect, it } from 'vitest';

import { resolveActionComponent } from './action-component-registry';

describe('action component registry', () => {
  it('resolves registered confirmation and result components', () => {
    expect(
      resolveActionComponent('ActionCandidateConfirmation', {
        actionType: 'process.task.reject',
        candidateId: 'cand-1',
        title: '确认驳回',
      }),
    ).toMatchObject({ key: 'ActionCandidateConfirmation' });

    expect(
      resolveActionComponent('ActionResultSummary', {
        actionId: 'ACT001',
        status: 'SUCCEEDED',
      }),
    ).toMatchObject({ key: 'ActionResultSummary' });
  });

  it('rejects unregistered components and unsafe props', () => {
    expect(() => resolveActionComponent('RawHtml', {})).toThrow(
      'ACTION_COMPONENT_NOT_REGISTERED',
    );
    expect(() =>
      resolveActionComponent('ActionCandidateConfirmation', {
        actionType: 'process.task.reject',
        candidateId: 'cand-1',
        innerHTML: '<script>alert(1)</script>',
        title: '确认驳回',
      }),
    ).toThrow('ACTION_COMPONENT_PROPS_INVALID');
  });
});
