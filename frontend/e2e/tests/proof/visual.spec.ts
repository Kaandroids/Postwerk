/**
 * Visual regression baselines for every dashboard surface (deterministic mock data,
 * animations disabled). First run generates baselines (--update-snapshots); subsequent
 * runs fail on unexpected pixel diffs → catches accidental UI regressions.
 * Run: npx playwright test tests/proof/visual.spec.ts
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { applyProofMocks, DASHBOARD_SURFACES } from '../../fixtures/proof-mocks';

test.describe('Visual regression — dashboard surfaces', () => {
  for (const s of DASHBOARD_SURFACES) {
    test(`visual ${s.name}`, async ({ authenticatedPage: page }) => {
      const api = new MockApi();
      applyProofMocks(api);
      await api.apply(page);

      await page.goto(s.path);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(600);

      await expect(page).toHaveScreenshot(`${s.name}.png`, {
        fullPage: true,
        animations: 'disabled',
        maxDiffPixelRatio: 0.02,
      });
    });
  }
});
