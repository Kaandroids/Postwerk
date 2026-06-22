import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { SecretsPage } from '../../pages';
import { mockSecrets, mockEmptySecrets, mockSecretCreated, mockSecretUpdated } from '../../mocks';

test.describe('Secrets Page', () => {
  let secretsPage: SecretsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    secretsPage = new SecretsPage(authenticatedPage);
    const api = new MockApi();
    api.get('/api/v1/secrets', mockSecrets);
    await api.apply(authenticatedPage);
    await secretsPage.navigate();
  });

  test('displays secret cards', async () => {
    await expect(secretsPage.cards).toHaveCount(2);
    await expect(secretsPage.card(0)).toContainText('SLACK_TOKEN');
    await expect(secretsPage.card(1)).toContainText('GITHUB_PAT');
  });

  test('displays version badge', async () => {
    await expect(secretsPage.versionBadges.first()).toContainText('v2');
  });

  test('shows empty state when no secrets', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/secrets', mockEmptySecrets);
    await api.apply(authenticatedPage);
    await secretsPage.navigate();

    await expect(secretsPage.cards).toHaveCount(0);
    await expect(authenticatedPage.locator('app-empty-state')).toBeVisible();
  });

  test('opens create form', async () => {
    await secretsPage.createBtn.click();
    await expect(secretsPage.form).toBeVisible();
    await expect(secretsPage.nameInput).toBeVisible();
    await expect(secretsPage.valueInput).toBeVisible();
  });

  test('creates a new secret', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get('/api/v1/secrets', mockSecrets)
      .post('/api/v1/secrets', mockSecretCreated);
    await api.apply(authenticatedPage);

    await secretsPage.createBtn.click();
    await secretsPage.nameInput.fill('NEW_SECRET');
    await secretsPage.valueInput.fill('super-secret-value');

    // After save, list reloads with updated data
    const apiAfter = new MockApi();
    apiAfter.get('/api/v1/secrets', [...mockSecrets, mockSecretCreated]);
    await apiAfter.apply(authenticatedPage);

    await secretsPage.saveBtn.click();
    await expect(secretsPage.cards).toHaveCount(3);
  });

  test('opens edit form with pre-filled data', async () => {
    await secretsPage.editBtns.first().click();
    await expect(secretsPage.form).toBeVisible();
    await expect(secretsPage.nameInput).toHaveValue('SLACK_TOKEN');
  });

  test('cancels form and returns to list', async () => {
    await secretsPage.createBtn.click();
    await expect(secretsPage.form).toBeVisible();
    await secretsPage.cancelBtn.click();
    await expect(secretsPage.form).not.toBeVisible();
    await expect(secretsPage.cards).toHaveCount(2);
  });

  test('deletes a secret', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.delete(/\/api\/v1\/secrets\/sec-1/, {});
    await api.apply(authenticatedPage);

    // After delete, list reloads with one less
    const apiAfter = new MockApi();
    apiAfter.get('/api/v1/secrets', [mockSecrets[1]]);
    await apiAfter.apply(authenticatedPage);

    await secretsPage.deleteBtns.first().click();
    await expect(secretsPage.cards).toHaveCount(1);
  });
});
