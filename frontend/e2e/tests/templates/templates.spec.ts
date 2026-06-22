import { test, expect } from '../../fixtures/test-fixtures';
import { TemplatesPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockTemplates } from '../../mocks';

test.describe('Templates', () => {
  let templatesPage: TemplatesPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    templatesPage = new TemplatesPage(authenticatedPage);
    const api = new MockApi();
    api
      .get('/api/v1/templates', mockTemplates)
      .get('/api/v1/parameter-sets', []);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/templates');
  });

  test('should display template list', async () => {
    await expect(templatesPage.templateCards).toHaveCount(1);
    await expect(templatesPage.cardName(0)).toContainText('Bestellbestätigung');
  });

  test('should show parameter chips', async () => {
    await expect(templatesPage.templateCards.first().locator('.tpl-param-chip')).toHaveCount(2);
  });

  test('should open create form', async () => {
    await templatesPage.addButton.click();
    await expect(templatesPage.nameInput).toBeVisible();
    await expect(templatesPage.subjectInput).toBeVisible();
  });

  test('should edit existing template', async () => {
    await templatesPage.editButton(0).click();
    await expect(templatesPage.nameInput).toHaveValue('Bestellbestätigung');
    await expect(templatesPage.subjectInput).toHaveValue('Ihre Bestellung {{orderNumber}} wurde bestätigt');
  });

  test('should switch between visual and HTML mode', async () => {
    await templatesPage.addButton.click();
    await expect(templatesPage.htmlModeTab).toBeVisible();
    await templatesPage.htmlModeTab.click();
    await expect(templatesPage.bodyEditor).toBeVisible();
  });
});
