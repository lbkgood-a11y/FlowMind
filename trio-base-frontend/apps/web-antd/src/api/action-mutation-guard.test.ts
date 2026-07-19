// @vitest-environment node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { MIGRATED_OPERATION_PAGES } from '../shared/page/migrated-page-registry';

const srcRoot = fileURLToPath(new URL('..', import.meta.url));
const scanRoots = ['api', 'composables', 'shared'].map((name) => `${srcRoot}/${name}`);
const migratedPageFiles = MIGRATED_OPERATION_PAGES.map(
  (page) => `${srcRoot}/${page.file}`,
);

const forbiddenPatterns = [
  /runRuntimeApplicationAction/,
  /retryRuntimeApplicationWorkflow/,
  /submitFormInstance/,
  /bindFormInstanceProcess/,
  /startProcessInstance/,
  /approveTask/,
  /rejectTask/,
  /transferTask/,
  /addSignTask/,
  /retryClosureEffect/,
  /markClosureEffectHandled/,
  /\/lowcode-runtime\/apps\/[^`'"]+\/actions\//,
  /\/lowcode-runtime\/apps\/[^`'"]+\/retry-workflow/,
  /\/process-instances\/start/,
  /\/tasks\/[^`'"]+\/(approve|reject|transfer|add-sign)/,
  /\/process-closures\/[^`'"]+\/(retry|manual-handled)/,
  /\/openapi\/runtime\/[^`'"]+\/orchestrations/,
];

describe('action mutation guard', () => {
  it('keeps migrated business mutations behind the Action Client', () => {
    const offenders = [...scanRoots.flatMap(sourceFiles), ...migratedPageFiles]
      .flatMap((file) => {
        const content = stripPermissionConstants(readFileSync(file, 'utf8'));
        return forbiddenPatterns
          .filter((pattern) => pattern.test(content))
          .map((pattern) => `${relative(file)} -> ${pattern}`);
      });

    expect(offenders).toEqual([]);
  });
});

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = `${dir}/${name}`;
    const stat = statSync(path);
    if (stat.isDirectory()) {
      if (['node_modules', 'dist', '.turbo'].includes(name)) {
        return [];
      }
      return sourceFiles(path);
    }
    if (!/\.(ts|vue)$/.test(name) || name.endsWith('.d.ts') || name.includes('.test.')) {
      return [];
    }
    return [path];
  });
}

function relative(file: string) {
  return file.replace(`${srcRoot}/`, '');
}

function stripPermissionConstants(content: string) {
  return content.replace(
    /const\s+\w*PERMISSIONS\s*=\s*\{[\s\S]*?\}\s+as const;/g,
    '',
  );
}
