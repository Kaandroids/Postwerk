import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { CategoriesPageComponent } from './categories-page.component';
import { CategoryService } from '../../../../core/services/category.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/**
 * Exemplar CRUD-page spec: a CrudPageBase subclass created via TestBed (template auto-renders),
 * with its resource service + the base's collaborators stubbed. Methods / computeds are exercised
 * directly. New CRUD-page specs should follow this shape.
 */
describe('CategoriesPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let exportImport: { downloadJson: ReturnType<typeof vi.fn>; readJsonFile: ReturnType<typeof vi.fn> };
  let cmp: CategoriesPageComponent;

  beforeEach(() => {
    svc = {
      list: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 'c1' })),
      update: vi.fn(() => of({ id: 'c1' })),
      delete: vi.fn(() => of(undefined)),
      toggleLock: vi.fn(() => of({ id: 'c1', locked: true })),
      export: vi.fn(() => of([])),
      import: vi.fn(() => of({ failed: 0 })),
    };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    exportImport = { downloadJson: vi.fn(), readJsonFile: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CategoriesPageComponent],
      providers: [
        { provide: CategoryService, useValue: svc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: exportImport },
      ],
    });
    cmp = TestBed.createComponent(CategoriesPageComponent).componentInstance;
  });

  it('loads categories on construction', () => {
    expect(svc['list']).toHaveBeenCalled();
  });

  it('descScore / posBoost / negBoost / accuracy scoring', () => {
    cmp.description.set('x'.repeat(15));
    expect(cmp.descScore()).toBe(30);
    cmp.description.set('x'.repeat(30));
    expect(cmp.descScore()).toBe(60);
    cmp.positiveExample.set('good');
    expect(cmp.posBoost()).toBe(22);
    cmp.negativeExample.set('bad');
    expect(cmp.negBoost()).toBe(15);
    expect(cmp.accuracy()).toBe(97); // 60+22+15 capped at 97
  });

  it('accuracyTone escalates with the score', () => {
    cmp.description.set('');
    expect(cmp.accuracyTone()).toBe('warn');
    cmp.description.set('x'.repeat(30)); // 60 → mid
    expect(cmp.accuracyTone()).toBe('mid');
    cmp.positiveExample.set('a'); cmp.negativeExample.set('b'); // 97 → ok
    expect(cmp.accuracyTone()).toBe('ok');
  });

  it('canSave requires a name and a 30+ char description', () => {
    expect(cmp.canSave()).toBe(false);
    cmp.name.set('Invoices'); cmp.description.set('x'.repeat(30));
    expect(cmp.canSave()).toBe(true);
  });

  it('editCategory loads the row into the form', () => {
    cmp.editCategory({ id: 'c1', name: 'Bills', color: '#fff', description: 'desc', positiveExample: 'p', negativeExample: 'n' } as never);
    expect(cmp.editId()).toBe('c1');
    expect(cmp.name()).toBe('Bills');
    expect(cmp.view()).toBe('form');
  });

  it('submit validates required fields before calling the service', () => {
    cmp.name.set('ab'); cmp.description.set('short');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('name')).toBe(true);
    expect(cmp.hasErr('description')).toBe(true);
  });

  it('submit creates a new category and returns to the list', () => {
    cmp.name.set('Invoices'); cmp.description.set('x'.repeat(30));
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.view()).toBe('list');
  });

  it('submit updates when editing', () => {
    cmp.editId.set('c1');
    cmp.name.set('Invoices'); cmp.description.set('x'.repeat(30));
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['update']).toHaveBeenCalledWith('c1', expect.anything());
  });

  it('toggleLock replaces the row in place', () => {
    cmp.categories.set([{ id: 'c1', locked: false }] as never);
    cmp.toggleLock({ id: 'c1' } as never);
    expect(svc['toggleLock']).toHaveBeenCalledWith('c1');
    expect(cmp.categories()[0].locked).toBe(true);
  });

  it('deleteCategory deletes after confirmation', async () => {
    cmp.deleteCategory('c1');
    await Promise.resolve();
    expect(svc['delete']).toHaveBeenCalledWith('c1');
  });

  it('exportData / importData delegate to the service + base helpers', () => {
    cmp.exportData();
    expect(svc['export']).toHaveBeenCalled();
    expect(exportImport.downloadJson).toHaveBeenCalled();
  });
});
