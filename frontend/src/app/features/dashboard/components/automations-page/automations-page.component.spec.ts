import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AutomationsPageComponent } from './automations-page.component';
import { AutomationService } from '../../../../core/services/automation.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Logic-only spec for the automations CRUD list page (extends CrudPageBase). */
describe('AutomationsPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let exportImport: { downloadJson: ReturnType<typeof vi.fn>; readJsonFile: ReturnType<typeof vi.fn> };
  let cmp: AutomationsPageComponent;
  let navSpy: ReturnType<typeof vi.fn>;

  const flush = () => new Promise<void>(r => setTimeout(r, 0));
  const auto = (o: Record<string, unknown>) => ({
    id: 'a1', name: 'A', description: '', color: '#fff', status: 'PAUSED',
    nodeCount: 0, totalExecutions: 0, successCount: 0, ...o,
  });

  beforeEach(() => {
    svc = {
      list: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 'a1' })),
      update: vi.fn(() => of({ id: 'a1' })),
      delete: vi.fn(() => of(undefined)),
      toggleLock: vi.fn(() => of({ id: 'a1', locked: true })),
      updateStatus: vi.fn(() => of({ id: 'a1' })),
      export: vi.fn(() => of([])),
      import: vi.fn(() => of({ failed: 0 })),
    };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    exportImport = { downloadJson: vi.fn(), readJsonFile: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AutomationsPageComponent],
      providers: [
        provideRouter([]),
        { provide: AutomationService, useValue: svc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: exportImport },
      ],
    });
    cmp = TestBed.createComponent(AutomationsPageComponent).componentInstance;
    navSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
  });

  it('loads automations on construction', () => {
    expect(svc['list']).toHaveBeenCalled();
  });

  it('count computeds reflect the list', () => {
    cmp.automations.set([
      auto({ id: 'a1', status: 'ACTIVE', nodeCount: 2 }),
      auto({ id: 'a2', status: 'TESTING', nodeCount: 3 }),
      auto({ id: 'a3', status: 'PAUSED', nodeCount: 1 }),
    ] as never);
    expect(cmp.activeCount()).toBe(1);
    expect(cmp.testingCount()).toBe(1);
    expect(cmp.pausedCount()).toBe(1);
    expect(cmp.totalNodes()).toBe(6);
  });

  it('filterOptions / activeFilterLabelKey / pickFilter drive the status filter', () => {
    cmp.automations.set([auto({ id: 'a1', status: 'ACTIVE' })] as never);
    expect(cmp.filterOptions()[0]).toMatchObject({ value: 'ALL', count: 1 });
    expect(cmp.activeFilterLabelKey()).toBe('auto_filter_all');
    cmp.pickFilter('ACTIVE');
    expect(cmp.statusFilter()).toBe('ACTIVE');
    expect(cmp.filterSheetOpen()).toBe(false);
    expect(cmp.activeFilterLabelKey()).toBe('auto_status_active');
  });

  it('filteredAutomations applies status + search filters', () => {
    cmp.automations.set([
      auto({ id: 'a1', name: 'Invoices', status: 'ACTIVE' }),
      auto({ id: 'a2', name: 'Newsletter', status: 'PAUSED' }),
    ] as never);
    cmp.statusFilter.set('ACTIVE');
    expect(cmp.filteredAutomations().map(a => a.id)).toEqual(['a1']);
    cmp.statusFilter.set('ALL');
    cmp.searchQuery.set('news');
    expect(cmp.filteredAutomations().map(a => a.id)).toEqual(['a2']);
  });

  it('getSuccessRate computes a percentage and guards divide-by-zero', () => {
    expect(cmp.getSuccessRate(auto({ totalExecutions: 0 }) as never)).toBe(0);
    expect(cmp.getSuccessRate(auto({ totalExecutions: 10, successCount: 5 }) as never)).toBe(50);
  });

  it('editAutomation loads the row into the form', () => {
    cmp.editAutomation(auto({ id: 'a1', name: 'Invoices', description: 'd', color: '#abc' }) as never);
    expect(cmp.editId()).toBe('a1');
    expect(cmp.name()).toBe('Invoices');
    expect(cmp.description()).toBe('d');
    expect(cmp.color()).toBe('#abc');
    expect(cmp.view()).toBe('form');
  });

  it('openEditor navigates to the editor route', () => {
    cmp.openEditor(auto({ id: 'a1' }) as never);
    expect(navSpy).toHaveBeenCalledWith(['/dashboard/automations', 'a1', 'edit']);
  });

  it('submit validates the name before calling the service', () => {
    cmp.name.set('');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('name')).toBe(true);
  });

  it('submit creates a new automation and returns to the list', () => {
    cmp.name.set('Invoices');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.view()).toBe('list');
  });

  it('submit updates when editing', () => {
    cmp.editId.set('a1');
    cmp.name.set('Invoices');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['update']).toHaveBeenCalledWith('a1', expect.anything());
  });

  it('toggleStatus flips ACTIVE to PAUSED via updateStatus', () => {
    cmp.toggleStatus(auto({ id: 'a1', status: 'ACTIVE' }) as never);
    expect(svc['updateStatus']).toHaveBeenCalledWith('a1', 'PAUSED');
  });

  it('setStatus is a no-op when unchanged and updates otherwise', () => {
    cmp.setStatus(auto({ id: 'a1', status: 'ACTIVE' }) as never, 'ACTIVE');
    expect(svc['updateStatus']).not.toHaveBeenCalled();
    cmp.setStatus(auto({ id: 'a1', status: 'ACTIVE' }) as never, 'PAUSED');
    expect(svc['updateStatus']).toHaveBeenCalledWith('a1', 'PAUSED');
  });

  it('toggleStatusMenu toggles the open menu id', () => {
    cmp.toggleStatusMenu(auto({ id: 'a1' }) as never);
    expect(cmp.statusMenuOpen()).toBe('a1');
    cmp.toggleStatusMenu(auto({ id: 'a1' }) as never);
    expect(cmp.statusMenuOpen()).toBeNull();
  });

  it('runNow opens the manual-run dialog', () => {
    const a = auto({ id: 'a1' });
    cmp.runNow(a as never);
    expect(cmp.manualRunFor()).toBe(a);
  });

  it('toggleLock replaces the row in place', () => {
    cmp.automations.set([auto({ id: 'a1', locked: false })] as never);
    svc['toggleLock'].mockReturnValue(of(auto({ id: 'a1', locked: true })) as never);
    cmp.toggleLock(auto({ id: 'a1' }) as never);
    expect(svc['toggleLock']).toHaveBeenCalledWith('a1');
    expect((cmp.automations()[0] as { locked: boolean }).locked).toBe(true);
  });

  it('deleteAutomation deletes after confirmation', async () => {
    cmp.deleteAutomation('a1');
    await flush();
    expect(svc['delete']).toHaveBeenCalledWith('a1');
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
