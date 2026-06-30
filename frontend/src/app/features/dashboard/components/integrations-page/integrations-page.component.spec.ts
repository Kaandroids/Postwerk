import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { IntegrationsPageComponent } from './integrations-page.component';
import { AutomationService } from '../../../../core/services/automation.service';
import { I18nService } from '../../../../core/services/i18n.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Logic-only spec for the integrations CRUD page (kind=INTEGRATION, extends CrudPageBase). */
describe('IntegrationsPageComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: IntegrationsPageComponent;
  let navSpy: ReturnType<typeof vi.fn>;

  const flush = () => new Promise<void>(r => setTimeout(r, 0));

  beforeEach(() => {
    svc = {
      listIntegrations: vi.fn(() => of([])),
      create: vi.fn(() => of({ id: 'i1' })),
      delete: vi.fn(() => of(undefined)),
    };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [IntegrationsPageComponent],
      providers: [
        provideRouter([]),
        { provide: AutomationService, useValue: svc },
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: AiChatService, useValue: { resourceMutation: signal({ types: [], seq: 0 }) } },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ExportImportService, useValue: { downloadJson: vi.fn(), readJsonFile: vi.fn() } },
      ],
    });
    cmp = TestBed.createComponent(IntegrationsPageComponent).componentInstance;
    navSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
  });

  it('loads integrations on construction', () => {
    expect(svc['listIntegrations']).toHaveBeenCalled();
  });

  it('filtered applies the search query', () => {
    cmp.integrations.set([
      { id: 'i1', name: 'Stripe', description: 'pay' },
      { id: 'i2', name: 'Slack', description: 'chat' },
    ] as never);
    expect(cmp.filtered().map(a => a.id)).toEqual(['i1', 'i2']);
    cmp.searchQuery.set('slack');
    expect(cmp.filtered().map(a => a.id)).toEqual(['i2']);
  });

  it('submit validates the name before calling the service', () => {
    cmp.name.set('');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).not.toHaveBeenCalled();
    expect(cmp.hasErr('name')).toBe(true);
  });

  it('submit creates an INTEGRATION and opens the editor', () => {
    cmp.name.set('My Integration');
    cmp.submit({ preventDefault() {} } as Event);
    expect(svc['create']).toHaveBeenCalledWith(expect.objectContaining({ kind: 'INTEGRATION' }));
    expect(cmp.view()).toBe('list');
    expect(navSpy).toHaveBeenCalledWith(['/dashboard/automations', 'i1', 'edit']);
  });

  it('openEditor navigates to the editor route', () => {
    cmp.openEditor({ id: 'i9' } as never);
    expect(navSpy).toHaveBeenCalledWith(['/dashboard/automations', 'i9', 'edit']);
  });

  it('deleteIntegration deletes after confirmation', async () => {
    cmp.deleteIntegration('i1');
    await flush();
    expect(svc['delete']).toHaveBeenCalledWith('i1');
  });
});
