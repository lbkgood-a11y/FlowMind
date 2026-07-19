// @vitest-environment node

import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  MIGRATED_OPERATION_PAGES,
  OPERATION_PAGE_EXCEPTIONS,
} from './migrated-page-registry';

const srcRoot = fileURLToPath(new URL('../../', import.meta.url));
const sharedImportPattern = /from\s+['"]#\/shared(?:\/page)?['"]/;
const documentedExceptionPattern = /delegates|pending|excluded|shell|inherit/i;

describe('migrated page standards', () => {
  it('keeps the migrated page inventory unique and backed by files', () => {
    const files = MIGRATED_OPERATION_PAGES.map((page) => page.file);

    expect(new Set(files).size).toBe(files.length);
    for (const page of MIGRATED_OPERATION_PAGES) {
      expect(existsSync(pageFile(page.file)), `${page.file} should exist`).toBe(true);
    }
  });

  it('requires governed pages to import declared shared operation primitives', () => {
    for (const page of MIGRATED_OPERATION_PAGES) {
      const content = readFileSync(pageFile(page.file), 'utf8');

      if (page.primitives.length === 0) {
        expect(
          page.notes,
          `${page.file} should document why it has no direct shared primitive import`,
        ).toMatch(documentedExceptionPattern);
        continue;
      }

      expect(content, `${page.file} should import shared TrioBase primitives`).toMatch(
        sharedImportPattern,
      );
      const missing = page.primitives.filter((primitive) => !content.includes(primitive));
      expect(missing, `${page.file} missing registered primitives`).toEqual([]);
    }
  });

  it('documents excluded non-operation surfaces', () => {
    for (const exception of OPERATION_PAGE_EXCEPTIONS) {
      expect(exception.file).toBeTruthy();
      expect(exception.reason).toBeTruthy();
    }
  });
});

function pageFile(file: string) {
  return `${srcRoot}/${file}`;
}
