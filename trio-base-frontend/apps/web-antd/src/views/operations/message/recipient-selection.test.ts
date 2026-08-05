import { describe, expect, it } from 'vitest';

import { authorizedRecipientIds } from './recipient-selection';

describe('message recipient selection', () => {
  it('accepts only users returned by the authorized selector query', () => {
    expect(authorizedRecipientIds(['u1', 'u2'], new Set(['u1', 'u2']))).toEqual(
      ['u1', 'u2'],
    );
    expect(() =>
      authorizedRecipientIds(['u1', 'foreign-user'], new Set(['u1'])),
    ).toThrow('MESSAGE_RECIPIENT_NOT_AUTHORIZED');
  });

  it('does not treat comma-separated text as recipient identities', () => {
    expect(() =>
      authorizedRecipientIds(['u1,u2'], new Set(['u1', 'u2'])),
    ).toThrow();
  });
});
