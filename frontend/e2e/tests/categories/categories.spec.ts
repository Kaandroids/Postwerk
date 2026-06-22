import { test, expect } from '../../fixtures/test-fixtures';
import { CategoriesPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockCategories } from '../../mocks';

test.describe('Categories', () => {
  let categoriesPage: CategoriesPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    categoriesPage = new CategoriesPage(authenticatedPage);
    const api = new MockApi();
    api.get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/categories');
  });

  test('should display category list', async () => {
    await expect(categoriesPage.categoryCards).toHaveCount(2);
    await expect(categoriesPage.cardName(0)).toContainText('Bestellung');
    await expect(categoriesPage.cardName(1)).toContainText('Newsletter');
  });

  test('should open create form', async () => {
    await categoriesPage.addButton.click();
    await expect(categoriesPage.nameInput).toBeVisible();
    await expect(categoriesPage.descriptionInput).toBeVisible();
  });

  test('should validate name length (min 3)', async ({ authenticatedPage }) => {
    await categoriesPage.addButton.click();
    await categoriesPage.nameInput.fill('AB');
    await categoriesPage.descriptionInput.fill('A'.repeat(30));
    await categoriesPage.saveButton.click();
    // Should remain on form (validation error)
    await expect(categoriesPage.nameInput).toBeVisible();
  });

  test('should create category with valid data', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .post('/api/v1/categories', { id: 3, name: 'Rechnung', color: '#6366f1' })
      .get('/api/v1/categories', [...mockCategories, { id: 3, name: 'Rechnung', color: '#6366f1' }]);
    await api.apply(authenticatedPage);

    await categoriesPage.addButton.click();
    await categoriesPage.nameInput.fill('Rechnung');
    await categoriesPage.descriptionInput.fill('Rechnungen und Zahlungsaufforderungen von Lieferanten');
    await categoriesPage.positiveExampleInput.fill('Ihre Rechnung #123 ist fällig');
    await categoriesPage.negativeExampleInput.fill('Willkommen bei unserem Newsletter');
    await categoriesPage.saveButton.click();
  });

  test('should edit existing category', async () => {
    await categoriesPage.editButton(0).click();
    await expect(categoriesPage.nameInput).toHaveValue('Bestellung');
  });

  test('should show color swatches', async () => {
    await categoriesPage.addButton.click();
    await expect(categoriesPage.colorSwatches.first()).toBeVisible();
  });
});
