import { describe, expect, it } from 'vitest';

import {
  isValidEmail,
  isValidPhone,
  mapWithConcurrency,
  pageAfterDelete,
} from './user-list-utils';

describe('user list helpers', () => {
  it('validates contact fields', () => {
    expect(isValidEmail('user@example.com')).toBe(true);
    expect(isValidEmail('invalid@')).toBe(false);
    expect(isValidPhone('13800138000')).toBe(true);
    expect(isValidPhone('123')).toBe(false);
  });

  it('moves back after deleting the last row on a non-first page', () => {
    expect(pageAfterDelete(3, 1)).toBe(2);
    expect(pageAfterDelete(3, 2)).toBe(3);
    expect(pageAfterDelete(1, 1)).toBe(1);
  });

  it('limits concurrent work and preserves result order', async () => {
    let active = 0;
    let peak = 0;
    const results = await mapWithConcurrency([1, 2, 3, 4], 2, async (value) => {
      active += 1;
      peak = Math.max(peak, active);
      await Promise.resolve();
      active -= 1;
      return value * 2;
    });
    expect(peak).toBe(2);
    expect(results).toEqual([
      { status: 'fulfilled', value: 2 },
      { status: 'fulfilled', value: 4 },
      { status: 'fulfilled', value: 6 },
      { status: 'fulfilled', value: 8 },
    ]);
  });
});
