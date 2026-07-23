import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

function source(name: string) {
  return readFileSync(new URL(`./${name}.vue`, import.meta.url), 'utf8');
}

describe('page action layout contract', () => {
  it('renders reset before submit in query actions', () => {
    const content = source('PageQueryActions');
    expect(content.indexOf('name="reset"')).toBeLessThan(content.indexOf('name="submit"'));
  });

  it('renders cancel before primary in form footer', () => {
    const content = source('FormFooterActions');
    expect(content.indexOf('name="cancel"')).toBeLessThan(content.indexOf('name="primary"'));
  });

  it('keeps toolbar start and end regions explicit', () => {
    const content = source('PageToolbar');
    expect(content).toContain('tb-page-toolbar__start');
    expect(content).toContain('tb-page-toolbar__end');
    expect(content.indexOf('name="start"')).toBeLessThan(content.indexOf('name="end"'));
  });
});
