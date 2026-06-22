/**
 * Cross-browser smoke: the critical surfaces render on Chromium, Firefox AND WebKit.
 * Run via the dedicated multi-browser config:
 *   npx playwright test --config=cross-browser.config.ts
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { applyProofMocks } from '../../fixtures/proof-mocks';

test.describe('Cross-browser smoke', () => {
  test('core dashboard surfaces render', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    applyProofMocks(api);
    await api.apply(page);

    // Dashboard shell + sidebar
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle').catch(() => {});
    await expect(page.getByText('Übersicht').first()).toBeVisible();

    // Automations list renders mock data
    await page.goto('/dashboard/automations');
    await page.waitForLoadState('networkidle').catch(() => {});
    await expect(page.getByText('Bestellungen verarbeiten').first()).toBeVisible();

    // Email inbox renders
    await page.goto('/dashboard/emails');
    await page.waitForLoadState('networkidle').catch(() => {});
    await expect(page.getByText('Posteingang').first()).toBeVisible();
  });
});
