import { test, expect } from '../../fixtures/test-fixtures';
import { AdminMarketplacePage } from '../../pages';

test.describe('Admin Marketplace Moderation', () => {
  let mkt: AdminMarketplacePage;

  test.beforeEach(async ({ adminPage }) => {
    mkt = new AdminMarketplacePage(adminPage);
    await adminPage.goto('/dashboard/admin/moderation');
  });

  test('should render the KPI strip and listings table', async () => {
    await expect(mkt.kpis).toBeVisible();
    await expect(mkt.listingsTable).toBeVisible();
    await expect(mkt.listingRows).toHaveCount(4);
    await expect(mkt.listingsTable).toContainText('Invoice auto-filer');
    await expect(mkt.listingsTable).toContainText('Spammy lead blaster');
  });

  test('should mark a taken-down listing with the taken-down status', async () => {
    // Default app locale is German, so the status label renders as "Entfernt".
    const row = mkt.listingRows.filter({ hasText: 'Spammy lead blaster' });
    await expect(row).toHaveAttribute('data-takendown', '1');
    await expect(row).toContainText('Entfernt');
  });

  test('should switch to the reviews segment and show review rows', async () => {
    await mkt.segReviews.click();
    await expect(mkt.reviewsTable).toBeVisible();
    await expect(mkt.reviewRows).toHaveCount(3);
    await expect(mkt.reviewsTable).toContainText('total scam');
    // The hidden review carries the Hidden badge ("Ausgeblendet" in German).
    await expect(mkt.reviewRows.filter({ hasText: 'Docs are thin' })).toContainText('Ausgeblendet');
  });

  test('should open the listing detail modal with reviews tab', async () => {
    await mkt.listingRows.first().click();
    await expect(mkt.listingModal).toBeVisible();
    await expect(mkt.listingModal).toContainText('Invoice auto-filer');
    await expect(mkt.listingModal).toContainText('files incoming invoices');
    // Reviews tab ("Bewertungen") includes the staff-only hidden review.
    await mkt.listingModal.getByRole('tab', { name: /Bewertungen/ }).click();
    await expect(mkt.listingModal).toContainText('Hidden Critic');
  });

  test('should open the review detail modal from a review row', async () => {
    await mkt.segReviews.click();
    await mkt.reviewRows.first().click();
    await expect(mkt.reviewModal).toBeVisible();
    await expect(mkt.reviewModal).toContainText('Saved me hours');
  });

  test('should close the listing modal via the close button', async () => {
    await mkt.listingRows.first().click();
    await expect(mkt.listingModal).toBeVisible();
    await mkt.modalClose.click();
    await expect(mkt.listingModal).toBeHidden();
  });
});
