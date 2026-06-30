import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ParameterSetsPageComponent } from './parameter-sets-page.component';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Logic-only spec for the parameter-sets CRUD page (extends CrudPageBase). */
describe('ParameterSetsPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let exportImport: { downloadJson: ReturnType<typeof vi.fn>; readJsonFile: ReturnType<typeof vi.fn> };
  let cmp: ParameterSetsPageComponent;

  const flush = () => new Promise<void>(r => setTimeout(r, 0));

  beforeEach(() => {
    svc = {
      list: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 'p1' })),
      update: vi.fn(() => of({ id: 'p1' })),
      delete: vi.fn(() => of(undefined)),
      toggleLock: vi.fn(() => of({ id: 'p1', locked: true })),
      export: vi.fn(() => of([])),
      import: vi.fn(() => of({ failed: 0 })),
    };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    exportImport = { downloadJson: vi.fn(), readJsonFile: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ParameterSetsPageComponent],
      providers: [
        { provide: ParameterSetService, useValue: svc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: exportImport },
      ],
    });
    cmp = TestBed.createComponent(ParameterSetsPageComponent).componentInstance;
  });

  it('loads parameter sets on construction', () => {
    expect(svc['list']).toHaveBeenCalled();
  });

  it('canSave needs a 3+ char name and at least one non-reserved parameter', () => {
    expect(cmp.canSave()).toBe(false);
    cmp.name.set('Inv');
    cmp.addParameter();
    expect(cmp.canSave()).toBe(true);
    cmp.updateParameter(0, 'name', 'subject'); // reserved
    expect(cmp.canSave()).toBe(false);
  });

  it('addParameter / removeParameter mutate the list', () => {
    cmp.addParameter();
    cmp.addParameter();
    expect(cmp.parameters().length).toBe(2);
    cmp.removeParameter(0);
    expect(cmp.parameters().length).toBe(1);
  });

  it('updateParameter clears children when leaving OBJECT type', () => {
    cmp.addParameter();
    cmp.updateParameter(0, 'type', 'OBJECT');
    cmp.addChildParameter(0);
    expect(cmp.parameters()[0].children.length).toBe(1);
    cmp.updateParameter(0, 'type', 'TEXT');
    expect(cmp.parameters()[0].children.length).toBe(0);
  });

  it('toggleIsList / toggleRequired flip the flags', () => {
    cmp.addParameter();
    cmp.toggleIsList(0);
    expect(cmp.parameters()[0].isList).toBe(true);
    cmp.toggleRequired(0);
    expect(cmp.parameters()[0].required).toBe(true);
  });

  it('child parameter helpers add / update / require / remove', () => {
    cmp.addParameter();
    cmp.addChildParameter(0);
    cmp.updateChildParameter(0, 0, 'name', 'street');
    expect(cmp.parameters()[0].children[0].name).toBe('street');
    cmp.toggleChildRequired(0, 0);
    expect(cmp.parameters()[0].children[0].required).toBe(true);
    cmp.removeChildParameter(0, 0);
    expect(cmp.parameters()[0].children.length).toBe(0);
  });

  it('editParameterSet loads the row into the form', () => {
    cmp.editParameterSet({
      id: 'p1', name: 'Address',
      parameters: [{ name: 'city', type: 'TEXT', description: '', positiveExample: '', negativeExample: '' }],
    } as never);
    expect(cmp.editId()).toBe('p1');
    expect(cmp.name()).toBe('Address');
    expect(cmp.parameters().length).toBe(1);
    expect(cmp.view()).toBe('form');
  });

  it('submit validates required fields before calling the service', () => {
    cmp.name.set(''); // too short + no params
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('name')).toBe(true);
    expect(cmp.hasErr('parameters')).toBe(true);
  });

  it('submit flags reserved parameter names', () => {
    cmp.name.set('Inv');
    cmp.addParameter();
    cmp.updateParameter(0, 'name', 'subject');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('param_0_name')).toBe(true);
  });

  it('submit creates a valid parameter set and returns to the list', () => {
    cmp.name.set('Invoices');
    cmp.addParameter();
    cmp.updateParameter(0, 'name', 'amount');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.view()).toBe('list');
  });

  it('submit updates when editing', () => {
    cmp.editId.set('p1');
    cmp.name.set('Invoices');
    cmp.addParameter();
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['update']).toHaveBeenCalledWith('p1', expect.anything());
  });

  it('toggleLock replaces the row in place', () => {
    cmp.parameterSets.set([{ id: 'p1', locked: false }] as never);
    svc['toggleLock'].mockReturnValue(of({ id: 'p1', locked: true }) as never);
    cmp.toggleLock({ id: 'p1' } as never);
    expect(svc['toggleLock']).toHaveBeenCalledWith('p1');
    expect(cmp.parameterSets()[0].locked).toBe(true);
  });

  it('deleteParameterSet deletes after confirmation', async () => {
    cmp.deleteParameterSet('p1');
    await flush();
    expect(svc['delete']).toHaveBeenCalledWith('p1');
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
