import { Injectable, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { CrudPageBase } from './crud-page.base';
import { ConfirmDialogService } from '../services/confirm-dialog.service';
import { ExportImportService } from '../../core/services/export-import.service';
import { AiChatService } from '../../core/services/ai-chat.service';
import { provideStubI18n } from '../../../testing';

interface Row { id: string; name: string; }

/** Concrete subclass exposing the protected helpers so they can be exercised directly. */
@Injectable()
class TestPage extends CrudPageBase {
  resetCalls = 0;
  rows = signal<Row[]>([]);
  protected resetForm(): void { this.resetCalls++; }
  callReplace(updated: Row) { this.replaceInList(this.rows, updated); }
  callLoadList(src: Observable<Row[]>) { this.loadList(src, this.rows); }
  callDelete(key: string, action: () => Observable<unknown>, onSuccess: () => void) {
    return this.deleteWithConfirm(key, action, onSuccess);
  }
  callExport(src: Observable<unknown[]>, name: string) { this.exportJson(src, name); }
  callImport(e: Event, fn: (d: unknown[]) => Observable<{ failed: number }>, key: string, reload: () => void) {
    this.importJson(e, fn as never, key, reload);
  }
}

describe('CrudPageBase', () => {
  let page: TestPage;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let exportImport: { downloadJson: ReturnType<typeof vi.fn>; readJsonFile: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    confirm = { confirm: vi.fn() };
    exportImport = { downloadJson: vi.fn(), readJsonFile: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        TestPage,
        provideStubI18n(),
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: exportImport },
        { provide: AiChatService, useValue: {} },
      ],
    });
    page = TestBed.inject(TestPage);
  });

  it('showAddForm() resets the form and switches to the form view', () => {
    page.showAddForm();
    expect(page.resetCalls).toBe(1);
    expect(page.editId()).toBeNull();
    expect(page.view()).toBe('form');
  });

  it('cancel() returns to the list view', () => {
    page.view.set('form');
    page.editId.set('x');
    page.cancel();
    expect(page.view()).toBe('list');
    expect(page.editId()).toBeNull();
  });

  it('hasErr() reflects the field-error map', () => {
    page.fieldErr.set({ name: true });
    expect(page.hasErr('name')).toBe(true);
    expect(page.hasErr('other')).toBe(false);
  });

  it('replaceInList() replaces a matching row and no-ops when absent', () => {
    page.rows.set([{ id: '1', name: 'a' }, { id: '2', name: 'b' }]);
    page.callReplace({ id: '2', name: 'B' });
    expect(page.rows().find(r => r.id === '2')?.name).toBe('B');
    page.callReplace({ id: '9', name: 'Z' });
    expect(page.rows().length).toBe(2);
  });

  it('loadList() writes the source result into the target signal', () => {
    page.callLoadList(of([{ id: '1', name: 'a' }]));
    expect(page.rows().length).toBe(1);
  });

  it('deleteWithConfirm() runs the action + onSuccess when confirmed', async () => {
    confirm.confirm.mockResolvedValue(true);
    const action = vi.fn(() => of(undefined));
    const onSuccess = vi.fn();
    await page.callDelete('confirm_key', action, onSuccess);
    expect(action).toHaveBeenCalled();
    expect(onSuccess).toHaveBeenCalled();
  });

  it('deleteWithConfirm() does nothing when cancelled', async () => {
    confirm.confirm.mockResolvedValue(false);
    const action = vi.fn(() => of(undefined));
    await page.callDelete('confirm_key', action, vi.fn());
    expect(action).not.toHaveBeenCalled();
  });

  it('deleteWithConfirm() surfaces an error message when the action fails', async () => {
    confirm.confirm.mockResolvedValue(true);
    const action = () => new Observable(s => s.error(new Error('boom')));
    await page.callDelete('confirm_key', action, vi.fn());
    expect(page.error()).toBeTruthy();
  });

  it('exportJson() downloads the source payload', () => {
    page.callExport(of([{ a: 1 }]), 'rows.json');
    expect(exportImport.downloadJson).toHaveBeenCalledWith([{ a: 1 }], 'rows.json');
  });

  it('importJson() reloads on success and reports a non-zero failed count', async () => {
    exportImport.readJsonFile.mockResolvedValue([{ a: 1 }]);
    const reload = vi.fn();
    page.callImport({} as Event, () => of({ failed: 2 }), 'err_key', reload);
    await new Promise(r => setTimeout(r));
    expect(reload).toHaveBeenCalled();
    expect(page.error()).toBe('err_key'); // echo stub returns the key (no %failed% placeholder to fill)
  });
});
