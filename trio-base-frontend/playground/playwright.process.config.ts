import type { PlaywrightTestConfig } from '@playwright/test';

import { devices } from '@playwright/test';

const config: PlaywrightTestConfig = {
  expect: {
    timeout: 10_000,
  },
  forbidOnly: !!process.env.CI,
  outputDir: 'node_modules/.e2e/process-results',
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  reporter: [['list']],
  retries: process.env.CI ? 1 : 0,
  testDir: './__tests__/e2e-process',
  timeout: 60_000,
  use: {
    baseURL: 'http://127.0.0.1:5556',
    headless: true,
    trace: 'retain-on-failure',
  },
  webServer: {
    command:
      'pnpm --dir ../apps/web-antd exec vite --mode development --host 127.0.0.1 --port 5556',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    url: 'http://127.0.0.1:5556',
  },
  workers: 1,
};

export default config;
