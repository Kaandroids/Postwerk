import { test, expect } from '../../fixtures/test-fixtures';
import { PlansPage } from '../../pages';
import { mockUsageResponse } from '../../mocks/plan.mocks';
import { MockApi } from '../../fixtures/mock-api.fixture';

test.describe('Plans Page', () => {
  let plans: PlansPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    // Usage mock is already set up in authenticatedPage fixture
    await authenticatedPage.goto('/dashboard/plans');
    plans = new PlansPage(authenticatedPage);
  });

  // ── Banner ──────────────────────────────────────────────────
  test('should display the current plan banner with plan name from API', async () => {
    await expect(plans.banner).toBeVisible();
    // Plan name from mockUsageResponse is 'PRO', component lowercases to find matching plan def
    await expect(plans.bannerName).toContainText('Pro');
  });

  test('should show payment method chip in banner', async () => {
    await expect(plans.bannerPaymentChip).toBeVisible();
    await expect(plans.bannerPaymentChip).toContainText('Visa');
    await expect(plans.bannerPaymentChip).toContainText('4242');
  });

  test('should show upgrade button in banner', async () => {
    await expect(plans.bannerUpgrade).toBeVisible();
  });

  // ── Usage bars ──────────────────────────────────────────────
  test('should display 3 usage bars', async () => {
    await expect(plans.usageBars).toHaveCount(3);
  });

  test('should show usage data from API', async () => {
    // Email accounts: 4/5 = 80% → warn
    const mailboxBar = plans.usageBars.nth(0);
    await expect(mailboxBar).toHaveAttribute('data-warn', '1');
    await expect(mailboxBar).toContainText('4');
    await expect(mailboxBar).toContainText('5');

    // Automations: 8/25 = 32% → no warn
    const automationBar = plans.usageBars.nth(1);
    await expect(automationBar).toHaveAttribute('data-warn', '0');

    // AI tokens: 31200/100000 = 31% → no warn
    const aiBar = plans.usageBars.nth(2);
    await expect(aiBar).toHaveAttribute('data-warn', '0');
  });

  // ── Cycle switch ────────────────────────────────────────────
  test('should toggle cycle between monthly and yearly', async () => {
    await expect(plans.cycleSwitch).toHaveAttribute('data-cycle', 'monthly');
    await plans.yearlyBtn.click();
    await expect(plans.cycleSwitch).toHaveAttribute('data-cycle', 'yearly');
    await plans.monthlyBtn.click();
    await expect(plans.cycleSwitch).toHaveAttribute('data-cycle', 'monthly');
  });

  test('should display correct monthly prices for plan cards', async () => {
    // Starter = Kostenlos, Pro = 19 €
    await expect(plans.planCardPrice(0)).toContainText('Kostenlos');
    await expect(plans.planCardPrice(1)).toContainText('19');
    await expect(plans.planCardPrice(2)).toContainText('49');
    await expect(plans.planCardPrice(3)).toContainText('Auf Anfrage');
  });

  // ── Plan cards ──────────────────────────────────────────────
  test('should display 4 plan cards', async () => {
    await expect(plans.planCards).toHaveCount(4);
  });

  test('should mark current plan card with data-current', async () => {
    const proCard = plans.planCardByIndex(1);
    await expect(proCard).toHaveAttribute('data-current', '1');
    // Other cards should not be current
    await expect(plans.planCardByIndex(0)).toHaveAttribute('data-current', '0');
    await expect(plans.planCardByIndex(2)).toHaveAttribute('data-current', '0');
    await expect(plans.planCardByIndex(3)).toHaveAttribute('data-current', '0');
  });

  test('should show active badge on current plan card', async () => {
    const proCard = plans.planCardByIndex(1);
    const badge = proCard.locator('[data-testid="plan-card-badge"]');
    await expect(badge).toBeVisible();
  });

  test('should show recommended badge on business card', async () => {
    const businessCard = plans.planCardByIndex(2);
    await expect(businessCard).toHaveAttribute('data-recommended', '1');
    const badge = businessCard.locator('[data-testid="plan-card-badge"]');
    await expect(badge).toBeVisible();
  });

  test('should set tone attribute on plan cards', async () => {
    await expect(plans.planCardByIndex(0)).toHaveAttribute('data-tone', 'sage');
    await expect(plans.planCardByIndex(1)).toHaveAttribute('data-tone', 'ochre');
    await expect(plans.planCardByIndex(2)).toHaveAttribute('data-tone', 'terracotta');
    await expect(plans.planCardByIndex(3)).toHaveAttribute('data-tone', 'indigo');
  });

  // ── CTA buttons ─────────────────────────────────────────────
  test('should disable CTA button for current plan', async () => {
    const proCta = plans.planCardCtas.nth(1);
    await expect(proCta).toBeDisabled();
  });

  test('should show correct CTA labels per plan rank', async () => {
    // Starter (rank 0) < Pro (rank 1, current) → Downgrade
    await expect(plans.planCardCtas.nth(0)).toContainText('Downgraden');
    // Pro is current → Aktueller Plan
    await expect(plans.planCardCtas.nth(1)).toContainText('Aktueller Plan');
    // Business (rank 2) > Pro → Upgrade
    await expect(plans.planCardCtas.nth(2)).toContainText('Upgraden');
    // Enterprise → Kontakt aufnehmen
    await expect(plans.planCardCtas.nth(3)).toContainText('Kontakt aufnehmen');
  });

  test('should show enterprise price as on-request', async () => {
    const enterprisePrice = plans.planCardPrice(3);
    await expect(enterprisePrice).toContainText('Auf Anfrage');
  });

  test('should show free for starter plan', async () => {
    const starterPrice = plans.planCardPrice(0);
    await expect(starterPrice).toContainText('Kostenlos');
  });

  // ── Add-ons ─────────────────────────────────────────────────
  test('should display 2 addon cards with prices', async () => {
    await expect(plans.addonCards).toHaveCount(2);
    // First addon: +1 Postfach, 4 €
    const firstAddon = plans.addonCards.nth(0);
    await expect(firstAddon).toContainText('Postfach');
    await expect(firstAddon).toContainText('4');
  });

  // ── Invoice table ───────────────────────────────────────────
  test('should display 6 invoice rows', async () => {
    await expect(plans.invoiceRows).toHaveCount(6);
  });

  test('should show paid status and download button on invoices', async () => {
    await expect(plans.invoiceStatus(0)).toContainText('Bezahlt');
    await expect(plans.invoiceDownloadBtn(0)).toBeVisible();
  });

  test('should display invoice amounts', async () => {
    await expect(plans.invoiceAmount(0)).toContainText('19');
  });
});
