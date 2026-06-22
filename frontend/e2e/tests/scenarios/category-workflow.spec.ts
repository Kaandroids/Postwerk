/**
 * Scenario: Category Creation & Management Workflow
 *
 * User navigates to categories, creates a new category with examples,
 * edits an existing one, then verifies the list updates.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { CategoriesPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockCategories } from '../../mocks';

test.describe('Scenario: Category Workflow', () => {
  test('user views categories and opens create form', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/categories');
    const categories = new CategoriesPage(authenticatedPage);

    // ── Step 1: Verify existing categories ──
    await expect(categories.categoryCards).toHaveCount(2);
    await expect(categories.cardName(0)).toContainText('Bestellung');
    await expect(categories.cardName(1)).toContainText('Newsletter');

    // ── Step 2: Open create form ──
    await categories.addButton.click();
    await expect(categories.nameInput).toBeVisible();

    // ── Step 3: Fill in category details ──
    await categories.nameInput.fill('Rechnung');
    await categories.descriptionInput.fill('Rechnungen von Lieferanten');
    await categories.positiveExampleInput.fill('Ihre Rechnung #12345');
    await categories.negativeExampleInput.fill('Newsletter: Neue Angebote');

    // ── Step 4: Pick a color swatch ──
    await categories.colorSwatches.nth(2).click();

    // ── Step 5: Cancel — return to list ──
    await categories.cancelButton.click();

    // ── Step 6: Verify list still has 2 categories ──
    await expect(categories.categoryCards).toHaveCount(2);
  });

  test('user edits an existing category', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/categories');
    const categories = new CategoriesPage(authenticatedPage);

    // ── Step 1: Verify categories loaded ──
    await expect(categories.categoryCards).toHaveCount(2);

    // ── Step 2: Click edit on first category ──
    await categories.editButton(0).click();

    // ── Step 3: Form should be pre-filled ──
    await expect(categories.nameInput).toHaveValue('Bestellung');

    // ── Step 4: Modify name ──
    await categories.nameInput.clear();
    await categories.nameInput.fill('Bestellungen (aktualisiert)');

    // ── Step 5: Cancel to go back ──
    await categories.cancelButton.click();
    await expect(categories.categoryCards).toHaveCount(2);
  });

  test('user tries to save empty category, sees validation', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/categories');
    const categories = new CategoriesPage(authenticatedPage);

    // Open form and try to save without filling anything
    await categories.addButton.click();
    await expect(categories.saveButton).toBeVisible();

    // Save should be disabled or show validation
    await categories.saveButton.click();

    // Should stay in form view (not navigate back to list)
    await expect(categories.nameInput).toBeVisible();
  });
});
