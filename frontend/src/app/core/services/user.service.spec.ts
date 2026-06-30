import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { UserService } from './user.service';
import { TokenService } from './token.service';
import { ExportImportService } from './export-import.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('UserService', () => {
  let api: MockApi;
  let service: UserService;
  let token: { clearTokens: ReturnType<typeof vi.fn> };
  let exportImport: { downloadBlob: ReturnType<typeof vi.fn> };
  const profile = { id: 'u1', email: 'a@b.c', fullName: 'A' };

  beforeEach(() => {
    api = createMockApi();
    token = { clearTokens: vi.fn() };
    exportImport = { downloadBlob: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideMockApi(api),
        { provide: TokenService, useValue: token },
        { provide: ExportImportService, useValue: exportImport },
      ],
    });
    service = TestBed.inject(UserService);
  });

  it('loadProfile() GETs /users/me and caches the profile', async () => {
    api.get.mockReturnValue(of(profile));
    await service.loadProfile();
    expect(api.get).toHaveBeenCalledWith('/users/me');
    expect(service.profile()).toEqual(profile);
  });

  it('updateProfile() PUTs and refreshes the cached profile', async () => {
    const updated = { ...profile, fullName: 'B' };
    api.put.mockReturnValue(of(updated));
    const result = await service.updateProfile({ fullName: 'B' });
    expect(api.put).toHaveBeenCalledWith('/users/me', { fullName: 'B' });
    expect(result).toEqual(updated);
    expect(service.profile()).toEqual(updated);
  });

  it('changePassword() POSTs the current + new password', async () => {
    api.post.mockReturnValue(of(undefined));
    await service.changePassword('old', 'new');
    expect(api.post).toHaveBeenCalledWith('/users/me/change-password', { currentPassword: 'old', newPassword: 'new' });
  });

  it('deleteAccount() DELETEs the account and clears the tokens', async () => {
    api.delete.mockReturnValue(of(undefined));
    await service.deleteAccount();
    expect(api.delete).toHaveBeenCalledWith('/users/me');
    expect(token.clearTokens).toHaveBeenCalled();
  });

  it('exportData() downloads the response blob body', async () => {
    const blob = new Blob(['x']);
    api.getBlob.mockReturnValue(of({ body: blob }));
    await service.exportData();
    expect(api.getBlob).toHaveBeenCalledWith('/users/me/export');
    expect(exportImport.downloadBlob).toHaveBeenCalledWith(blob, 'postwerk-data-export.json');
  });

  it('exportData() does nothing when the response has no body', async () => {
    api.getBlob.mockReturnValue(of({ body: null }));
    await service.exportData();
    expect(exportImport.downloadBlob).not.toHaveBeenCalled();
  });
});
