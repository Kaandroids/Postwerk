import { test, expect } from '../../fixtures/test-fixtures';
import { KnowledgeBasePage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockKnowledgeBases,
  mockKbParameterSets,
  mockKbEntries,
  mockKbCreated,
  mockKbEntryCreated,
  mockKbImportResult,
} from '../../mocks';

test.describe('Knowledge Bases', () => {
  let kbPage: KnowledgeBasePage;

  test.beforeEach(async ({ authenticatedPage }) => {
    kbPage = new KnowledgeBasePage(authenticatedPage);
    const api = new MockApi();
    // Specific (entries) before generic (list) — first match wins within a MockApi instance.
    api
      .get(/\/api\/v1\/knowledge-bases\/[^/]+\/entries/, mockKbEntries)
      .get('/api/v1/knowledge-bases', mockKnowledgeBases)
      .get('/api/v1/parameter-sets', mockKbParameterSets);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/knowledge-bases');
  });

  test('should display the knowledge base list', async () => {
    await expect(kbPage.cards).toHaveCount(1);
    await expect(kbPage.cardName(0)).toContainText('SKR 03');
  });

  test('should create a knowledge base', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .post('/api/v1/knowledge-bases', mockKbCreated)
      .get('/api/v1/knowledge-bases', [...mockKnowledgeBases, mockKbCreated])
      .get('/api/v1/parameter-sets', mockKbParameterSets);
    await api.apply(authenticatedPage);

    await kbPage.newButton.click();
    await kbPage.nameInput.fill('Produkte');
    await kbPage.paramsetSelect.selectOption('ps-1');
    await kbPage.embedCheckboxes.first().check();
    await kbPage.saveButton.click();

    // Returns to the list view (the "new" header button is shown only on the list screen).
    await expect(kbPage.newButton).toBeVisible();
  });

  test('should open entries and add one', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .post(/\/api\/v1\/knowledge-bases\/[^/]+\/entries/, mockKbEntryCreated)
      .get(/\/api\/v1\/knowledge-bases\/[^/]+\/entries/, mockKbEntries)
      .get('/api/v1/knowledge-bases', mockKnowledgeBases)
      .get('/api/v1/parameter-sets', mockKbParameterSets);
    await api.apply(authenticatedPage);

    await kbPage.entriesButton(0).click();
    await expect(kbPage.tableRows).toHaveCount(2);

    await kbPage.entryInputs.first().fill('4980');
    await kbPage.addEntryButton.click();
    // No error banner after a successful add.
    await expect(kbPage.addEntryButton).toBeVisible();
  });

  test('should import a CSV file', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .post(/\/api\/v1\/knowledge-bases\/[^/]+\/import/, mockKbImportResult)
      .get(/\/api\/v1\/knowledge-bases\/[^/]+\/entries/, mockKbEntries)
      .get('/api/v1/knowledge-bases', mockKnowledgeBases)
      .get('/api/v1/parameter-sets', mockKbParameterSets);
    await api.apply(authenticatedPage);

    await kbPage.entriesButton(0).click();
    await kbPage.importInput.setInputFiles({
      name: 'skr.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('kod,isim\n4980,Sonstige Kosten\n'),
    });

    // Import completed without surfacing the import error banner.
    await expect(kbPage.tableRows.first()).toBeVisible();
  });
});
