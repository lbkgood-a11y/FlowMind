import Vue from '@vitejs/plugin-vue';
import VueJsx from '@vitejs/plugin-vue-jsx';
import { configDefaults, defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [Vue(), VueJsx()],
  resolve: {
    alias: {
      '#': fileURLToPath(new URL('./apps/web-antd/src', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    environmentOptions: {
      happyDOM: {
        settings: {
          // happy-dom v20+ disables JS evaluation by default (security fix).
          // Treat disabled script loading as success to preserve test behavior.
          handleDisabledFileLoadingAsSuccess: true,
        },
      },
    },
    exclude: [
      ...configDefaults.exclude,
      '**/e2e/**',
      '**/e2e-process/**',
      '**/dist/**',
      '**/.{idea,git,cache,output,temp}/**',
      '**/node_modules/**',
      '**/{stylelint,eslint}.config.*',
      '**/{oxfmt,oxlint}.config.*',
    ],
  },
});
