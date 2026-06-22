import { defineConfig } from '@playwright/test';
import * as dotenv from 'dotenv';
import * as path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../../.env') });

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [
    ['html', { open: 'never', outputFolder: '../playwright-report-integration' }],
    ['list'],
  ],
  use: {
    baseURL: process.env['INTEGRATION_API_URL'] || 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
    },
  },
});
