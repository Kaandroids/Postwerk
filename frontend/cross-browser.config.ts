import { defineConfig, devices } from '@playwright/test';

/**
 * Dedicated cross-browser smoke config — runs ONLY the proof smoke spec across
 * Chromium, Firefox and WebKit. Kept separate from playwright.config.ts so the main
 * E2E suite (and CI) stays single-browser; run explicitly:
 *   npx playwright test --config=cross-browser.config.ts
 */
export default defineConfig({
  testDir: './e2e/tests/proof',
  testMatch: 'smoke.spec.ts',
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'npx ng serve --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
