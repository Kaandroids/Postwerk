import { test, expect } from '@playwright/test';
import { WizardPage } from '../../pages/wizard.page';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import {
  buildWizardChatSSE,
  buildWizardBuildSSE,
  mockWizardClaimResponse,
} from '../../mocks/wizard.mocks';

test.describe('Wizard (Get Started)', () => {
  let wizardPage: WizardPage;

  test.beforeEach(async ({ page }) => {
    await dismissCookieConsent(page);

    // Mock the wizard SSE chat endpoint
    await page.route('**/api/v1/wizard/chat', async (route) => {
      const body = route.request().postDataJSON();
      const isFirstMessage = !body.sessionId;

      const sseData = isFirstMessage
        ? buildWizardChatSSE('Hello! How would you like to automate your emails?')
        : buildWizardBuildSSE();

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseData,
      });
    });

    wizardPage = new WizardPage(page);
    await wizardPage.goto();
  });

  test('should show the wizard page with intro and chat', async () => {
    await expect(wizardPage.flowPage).toBeVisible();
    await expect(wizardPage.chatIntro).toBeVisible();
    await expect(wizardPage.composerInput).toBeVisible();
    await expect(wizardPage.composerSend).toBeVisible();
  });

  test('should collapse intro after sending first message', async () => {
    await wizardPage.sendMessage('Automate my support emails');

    // Intro should collapse
    await expect(wizardPage.chatIntro).toHaveAttribute('data-hide', '1');
  });

  test('should display AI response after sending message', async () => {
    await wizardPage.sendMessage('Automate my support emails');

    // Wait for the AI reply to appear
    await expect(
      wizardPage.chatMessages.locator('[data-from="ai"]').first()
    ).toBeVisible({ timeout: 5000 });
  });

  test('should show CTA and summary when ready', async ({ page }) => {
    // Send first message to get chatting reply
    await wizardPage.sendMessage('Automate my support emails');
    await page.waitForTimeout(500);

    // Send second message to trigger building
    await wizardPage.sendMessage('Sounds good, build it');
    await page.waitForTimeout(3000);

    // The canvas should appear
    await expect(wizardPage.canvas).toBeVisible({ timeout: 10000 });

    // CTA corner and summary should appear in ready phase
    await expect(wizardPage.ctaCorner).toHaveAttribute('data-on', '1', { timeout: 10000 });
    await expect(wizardPage.summaryBadge).toHaveAttribute('data-on', '1');
    await expect(wizardPage.ctaRegister).toBeVisible();
  });

  test('should show how-card with explain steps when ready', async ({ page }) => {
    // Send first message
    await wizardPage.sendMessage('Automate my support emails');
    await page.waitForTimeout(500);

    // Trigger build
    await wizardPage.sendMessage('Sounds good, build it');
    await page.waitForTimeout(3000);

    // How-card should appear with data-on="1"
    await expect(wizardPage.howCard).toHaveAttribute('data-on', '1', { timeout: 10000 });
    await expect(wizardPage.ctaContinue).toBeVisible();
  });

  test('should navigate to register when CTA clicked', async ({ page }) => {
    // Mock to start in ready state
    await page.route('**/api/v1/wizard/chat', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: buildWizardBuildSSE(),
      });
    });

    await wizardPage.sendMessage('Build my automation');
    await page.waitForTimeout(3000);

    // Check if CTA corner is visible before clicking
    const ctaOn = await wizardPage.ctaCorner.getAttribute('data-on');
    if (ctaOn === '1') {
      await wizardPage.ctaRegister.click();
      await expect(page).toHaveURL(/\/auth\/register\?from=wizard/);
    }
  });
});
