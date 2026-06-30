import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { KnowledgeBasesPageComponent } from './knowledge-bases-page.component';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Logic-only spec for the knowledge-bases page (list / config / entries screens, extends CrudPageBase). */
describe('KnowledgeBasesPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let paramSvc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: KnowledgeBasesPageComponent;

  const flush = () => new Promise<void>(r => setTimeout(r, 0));
  const ps = { id: 'ps1', name: 'Schema', parameters: [{ name: 'title' }, { name: 'body' }] };
  const kb = {
    id: 'kb1', name: 'KB', description: 'D', parameterSetId: 'ps1',
    fieldRoles: { title: { embed: true, keyword: false } }, uniqueField: 'title', entryCount: 0, locked: false,
  };

  beforeEach(() => {
    svc = {
      list: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 'kb1' })),
      update: vi.fn(() => of({ id: 'kb1' })),
      delete: vi.fn(() => of(undefined)),
      listEntries: vi.fn(() => of([])),
      addEntry: vi.fn(() => of({ id: 'e1' })),
      updateEntry: vi.fn(() => of({ id: 'e1' })),
      deleteEntry: vi.fn(() => of(undefined)),
      import: vi.fn(() => of({ failed: 0 })),
    };
    paramSvc = { list: vi.fn(() => of([])) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [KnowledgeBasesPageComponent],
      providers: [
        { provide: KnowledgeBaseService, useValue: svc },
        { provide: ParameterSetService, useValue: paramSvc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: { downloadJson: vi.fn(), readJsonFile: vi.fn() } },
      ],
    });
    cmp = TestBed.createComponent(KnowledgeBasesPageComponent).componentInstance;
  });

  it('loads knowledge bases and parameter sets on construction', () => {
    expect(svc['list']).toHaveBeenCalled();
    expect(paramSvc['list']).toHaveBeenCalled();
  });

  it('selectedParamSet / fieldNames resolve from the chosen parameter set', () => {
    cmp.paramSets.set([ps] as never);
    cmp.parameterSetId.set('ps1');
    expect(cmp.selectedParamSet()).toMatchObject({ id: 'ps1' });
    expect(cmp.fieldNames()).toEqual(['title', 'body']);
  });

  it('canSaveConfig requires a name, a parameter set and at least one embed field', () => {
    cmp.paramSets.set([ps] as never);
    cmp.parameterSetId.set('ps1');
    cmp.name.set('KB');
    expect(cmp.canSaveConfig()).toBe(false);
    cmp.roles.set({ title: { embed: true, keyword: false } });
    expect(cmp.canSaveConfig()).toBe(true);
  });

  it('roleOf returns a default role for unknown fields and toggleRole flips it', () => {
    expect(cmp.roleOf('title')).toEqual({ embed: false, keyword: false });
    cmp.toggleRole('title', 'embed');
    expect(cmp.roleOf('title').embed).toBe(true);
  });

  it('onParamSetChange prunes roles and uniqueField not in the new field set', () => {
    cmp.paramSets.set([ps] as never);
    cmp.parameterSetId.set('ps1');
    cmp.roles.set({ title: { embed: true, keyword: false }, ghost: { embed: true, keyword: false } });
    cmp.uniqueField.set('ghost');
    cmp.onParamSetChange();
    expect(Object.keys(cmp.roles())).toEqual(['title']);
    expect(cmp.uniqueField()).toBe('');
  });

  it('newKb / editKb / backToList drive the screen state', () => {
    cmp.newKb();
    expect(cmp.screen()).toBe('config');
    expect(cmp.editId()).toBeNull();
    cmp.editKb(kb as never);
    expect(cmp.editId()).toBe('kb1');
    expect(cmp.name()).toBe('KB');
    expect(cmp.parameterSetId()).toBe('ps1');
    expect(cmp.uniqueField()).toBe('title');
    expect(cmp.screen()).toBe('config');
    cmp.backToList();
    expect(cmp.screen()).toBe('list');
    expect(cmp.editId()).toBeNull();
  });

  it('saveConfig blocks an invalid config', () => {
    cmp.name.set(''); // canSaveConfig false
    cmp.saveConfig();
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.error()).toBe('kb_err_need_embed');
  });

  it('saveConfig creates a valid config and returns to the list', () => {
    cmp.paramSets.set([ps] as never);
    cmp.parameterSetId.set('ps1');
    cmp.name.set('KB');
    cmp.roles.set({ title: { embed: true, keyword: false } });
    cmp.saveConfig();
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.screen()).toBe('list');
  });

  it('saveConfig updates when editing', () => {
    cmp.paramSets.set([ps] as never);
    cmp.parameterSetId.set('ps1');
    cmp.name.set('KB');
    cmp.roles.set({ title: { embed: true, keyword: false } });
    cmp.editId.set('kb1');
    cmp.saveConfig();
    expect(svc['update']).toHaveBeenCalledWith('kb1', expect.anything());
  });

  it('deleteKb deletes after confirmation', async () => {
    cmp.deleteKb(kb as never);
    await flush();
    expect(svc['delete']).toHaveBeenCalledWith('kb1');
  });

  it('openEntries loads the entries and switches screen', () => {
    cmp.openEntries(kb as never);
    expect(svc['listEntries']).toHaveBeenCalledWith('kb1');
    expect(cmp.activeKb()).toBe(kb);
    expect(cmp.screen()).toBe('entries');
  });

  it('draftValue / setDraft manage the entry draft', () => {
    expect(cmp.draftValue('title')).toBe('');
    cmp.setDraft('title', 'Hello');
    expect(cmp.draftValue('title')).toBe('Hello');
  });

  it('editEntry loads the row into the draft', () => {
    cmp.paramSets.set([ps] as never);
    cmp.activeKb.set(kb as never);
    cmp.editEntry({ id: 'e1', data: { title: 'T', body: 'B' } } as never);
    expect(cmp.entryEditId()).toBe('e1');
    expect(cmp.entryDraft()).toEqual({ title: 'T', body: 'B' });
  });

  it('cancelEntry clears the edit state', () => {
    cmp.entryEditId.set('e1');
    cmp.setDraft('title', 'x');
    cmp.cancelEntry();
    expect(cmp.entryEditId()).toBeNull();
    expect(cmp.entryDraft()).toEqual({});
  });

  it('saveEntry adds a new entry when not editing', () => {
    cmp.activeKb.set(kb as never);
    cmp.setDraft('title', 'New');
    cmp.saveEntry();
    expect(svc['addEntry']).toHaveBeenCalledWith('kb1', expect.objectContaining({ data: expect.anything() }));
  });

  it('saveEntry updates an existing entry when editing', () => {
    cmp.activeKb.set(kb as never);
    cmp.entryEditId.set('e1');
    cmp.setDraft('title', 'Edited');
    cmp.saveEntry();
    expect(svc['updateEntry']).toHaveBeenCalledWith('kb1', 'e1', expect.anything());
  });

  it('deleteEntry deletes after confirmation', async () => {
    cmp.activeKb.set(kb as never);
    cmp.deleteEntry({ id: 'e1', data: {} } as never);
    await flush();
    expect(svc['deleteEntry']).toHaveBeenCalledWith('kb1', 'e1');
  });

  it('cellValue stringifies entry values', () => {
    expect(cmp.cellValue({ data: { title: 'T' } } as never, 'title')).toBe('T');
    expect(cmp.cellValue({ data: {} } as never, 'title')).toBe('');
  });

  it('paramSetName / roleSummary format list helpers', () => {
    cmp.paramSets.set([ps] as never);
    expect(cmp.paramSetName('ps1')).toBe('Schema');
    expect(cmp.paramSetName('missing')).toBe('—');
    expect(cmp.roleSummary(kb as never)).toBe('title');
  });

  it('importCsv parses the file and imports the rows', async () => {
    cmp.activeKb.set(kb as never);
    const evt = {
      target: { files: [{ text: () => Promise.resolve('title,body\nAlice,Bob') }], value: 'x' },
    } as unknown as Event;
    cmp.importCsv(evt);
    await flush();
    expect(svc['import']).toHaveBeenCalledWith('kb1', [{ title: 'Alice', body: 'Bob' }]);
  });
});
