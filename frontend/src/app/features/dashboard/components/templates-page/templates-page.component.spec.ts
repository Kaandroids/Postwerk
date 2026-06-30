import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TemplatesPageComponent } from './templates-page.component';
import { TemplateService } from '../../../../core/services/template.service';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Logic-only spec for the templates CRUD page (extends CrudPageBase). */
describe('TemplatesPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let paramSvc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let exportImport: { downloadJson: ReturnType<typeof vi.fn>; readJsonFile: ReturnType<typeof vi.fn> };
  let cmp: TemplatesPageComponent;

  const flush = () => new Promise<void>(r => setTimeout(r, 0));

  beforeEach(() => {
    svc = {
      list: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 't1' })),
      update: vi.fn(() => of({ id: 't1' })),
      delete: vi.fn(() => of(undefined)),
      toggleLock: vi.fn(() => of({ id: 't1', locked: true })),
      export: vi.fn(() => of([])),
      import: vi.fn(() => of({ failed: 0 })),
    };
    paramSvc = { list: vi.fn(() => of([])) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    exportImport = { downloadJson: vi.fn(), readJsonFile: vi.fn() };
    TestBed.configureTestingModule({
      imports: [TemplatesPageComponent],
      providers: [
        { provide: TemplateService, useValue: svc },
        { provide: ParameterSetService, useValue: paramSvc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: exportImport },
        { provide: DomSanitizer, useValue: { bypassSecurityTrustHtml: (v: string) => v } },
      ],
    });
    cmp = TestBed.createComponent(TemplatesPageComponent).componentInstance;
  });

  it('loads templates and parameter sets on construction', () => {
    expect(svc['list']).toHaveBeenCalled();
    expect(paramSvc['list']).toHaveBeenCalled();
  });

  it('detectedParams extracts unique {{tokens}} from subject + body', () => {
    cmp.subject.set('Hi {{firstName}}');
    cmp.body.set('Hello {{lastName}} and {{firstName}}');
    expect(cmp.detectedParams()).toEqual(['firstName', 'lastName']);
  });

  it('selectedParameterSet resolves by id', () => {
    cmp.parameterSets.set([{ id: 'ps1', name: 'PS' }] as never);
    expect(cmp.selectedParameterSet()).toBeNull();
    cmp.parameterSetId.set('ps1');
    expect(cmp.selectedParameterSet()).toMatchObject({ id: 'ps1' });
  });

  it('sanitizedBody returns a placeholder for empty body and sanitizes otherwise', () => {
    cmp.body.set('');
    expect(cmp.sanitizedBody()).toBe('tpl_preview_empty_body');
    cmp.body.set('<b>x</b>');
    expect(cmp.sanitizedBody()).toContain('<b>x</b>');
  });

  it('canSave requires name, subject and body', () => {
    expect(cmp.canSave()).toBe(false);
    cmp.name.set('Welcome'); cmp.subject.set('Subj'); cmp.body.set('Body');
    expect(cmp.canSave()).toBe(true);
  });

  it('editTemplate loads the row into the form', () => {
    cmp.editTemplate({ id: 't1', name: 'Welcome', subject: 'S', body: 'B', parameterSetId: 'ps1' } as never);
    expect(cmp.editId()).toBe('t1');
    expect(cmp.name()).toBe('Welcome');
    expect(cmp.useParameterSet()).toBe(true);
    expect(cmp.parameterSetId()).toBe('ps1');
    expect(cmp.view()).toBe('form');
  });

  it('submit validates required fields before calling the service', () => {
    cmp.name.set(''); cmp.subject.set(''); cmp.body.set('');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('name')).toBe(true);
    expect(cmp.hasErr('subject')).toBe(true);
    expect(cmp.hasErr('body')).toBe(true);
  });

  it('submit creates a new template and returns to the list', () => {
    cmp.name.set('Welcome'); cmp.subject.set('S'); cmp.body.set('B');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.view()).toBe('list');
  });

  it('submit updates when editing', () => {
    cmp.editId.set('t1');
    cmp.name.set('Welcome'); cmp.subject.set('S'); cmp.body.set('B');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['update']).toHaveBeenCalledWith('t1', expect.anything());
  });

  it('switchEditorMode toggles the editor mode', () => {
    expect(cmp.editorMode()).toBe('visual');
    cmp.switchEditorMode('html');
    expect(cmp.editorMode()).toBe('html');
    cmp.switchEditorMode('visual');
    expect(cmp.editorMode()).toBe('visual');
  });

  it('onUseParameterSetChange clears the selection when unchecked', () => {
    cmp.parameterSetId.set('ps1');
    cmp.onUseParameterSetChange({ target: { checked: false } } as unknown as Event);
    expect(cmp.useParameterSet()).toBe(false);
    expect(cmp.parameterSetId()).toBeNull();
    cmp.onUseParameterSetChange({ target: { checked: true } } as unknown as Event);
    expect(cmp.useParameterSet()).toBe(true);
  });

  it('onTiptapChange writes the body', () => {
    cmp.onTiptapChange('<p>hi</p>');
    expect(cmp.body()).toBe('<p>hi</p>');
  });

  it('toggleLock replaces the row in place', () => {
    cmp.templates.set([{ id: 't1', locked: false }] as never);
    svc['toggleLock'].mockReturnValue(of({ id: 't1', locked: true }) as never);
    cmp.toggleLock({ id: 't1' } as never);
    expect(svc['toggleLock']).toHaveBeenCalledWith('t1');
    expect(cmp.templates()[0].locked).toBe(true);
  });

  it('deleteTemplate deletes after confirmation', async () => {
    cmp.deleteTemplate('t1');
    await flush();
    expect(svc['delete']).toHaveBeenCalledWith('t1');
  });

  it('exportData delegates to the service + download helper', () => {
    cmp.exportData();
    expect(svc['export']).toHaveBeenCalled();
    expect(exportImport.downloadJson).toHaveBeenCalled();
  });

  it('importData reads the file and imports it', async () => {
    exportImport.readJsonFile.mockResolvedValue([{ name: 'x' }]);
    svc['import'].mockReturnValue(of({ failed: 0 }) as never);
    cmp.importData({ target: {} } as Event);
    expect(exportImport.readJsonFile).toHaveBeenCalled();
    await flush();
    expect(svc['import']).toHaveBeenCalled();
  });
});
