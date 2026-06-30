import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { WorkspaceService } from './workspace.service';
import { EmailAccountService } from './email-account.service';
import { FolderService } from './folder.service';

describe('WorkspaceService', () => {
  let service: WorkspaceService;
  let emailAccounts: { list: ReturnType<typeof vi.fn> };
  let folders: { listFolders: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    emailAccounts = { list: vi.fn(() => of([])) };
    folders = { listFolders: vi.fn(() => of([])) };
    TestBed.configureTestingModule({
      providers: [
        { provide: EmailAccountService, useValue: emailAccounts },
        { provide: FolderService, useValue: folders },
      ],
    });
    service = TestBed.inject(WorkspaceService);
  });

  it('loadAccounts() stores accounts and activates the default one', () => {
    emailAccounts.list.mockReturnValue(of([{ id: 'a', isDefault: false }, { id: 'b', isDefault: true }]));
    service.loadAccounts();
    expect(service.accounts().length).toBe(2);
    expect(service.activeAccount()?.id).toBe('b');
  });

  it('loadAccounts() falls back to the first account when none is default', () => {
    emailAccounts.list.mockReturnValue(of([{ id: 'a', isDefault: false }]));
    service.loadAccounts();
    expect(service.activeAccount()?.id).toBe('a');
  });

  it('switchAccount() activates a known account and ignores an unknown id', () => {
    service.accounts.set([{ id: 'a' }, { id: 'b' }] as never);
    service.switchAccount('b');
    expect(service.activeAccount()?.id).toBe('b');
    service.switchAccount('zzz');
    expect(service.activeAccount()?.id).toBe('b');
  });

  it('loadFolders() loads folders for the active account', () => {
    service.activeAccount.set({ id: 'a' } as never);
    folders.listFolders.mockReturnValue(of([{ id: 'f1' }]));
    service.loadFolders();
    expect(folders.listFolders).toHaveBeenCalledWith('a');
    expect(service.folders().length).toBe(1);
  });

  it('loadFolders() is a no-op without an active account', () => {
    service.activeAccount.set(null);
    folders.listFolders.mockClear();
    service.loadFolders();
    expect(folders.listFolders).not.toHaveBeenCalled();
  });

  it('addFolder()/removeFolder() mutate the folders signal', () => {
    service.folders.set([{ id: 'f1' }] as never);
    service.addFolder({ id: 'f2' } as never);
    expect(service.folders().length).toBe(2);
    service.removeFolder('f1');
    expect(service.folders().map(f => f.id)).toEqual(['f2']);
  });
});
