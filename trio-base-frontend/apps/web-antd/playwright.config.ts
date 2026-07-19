import { defineConfig } from '@playwright/test';

export default defineConfig({
  forbidOnly: !!process.env.CI,
  outputDir: 'node_modules/.e2e/test-results',
  reporter: [['list']],
  testDir: './src/__tests__/e2e',
  timeout: 10_000,
  workers: 1,
});
