import { TestBed } from '@angular/core/testing';
import { FolderService } from './folder.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('FolderService', () => {
  let api: MockApi;
  let service: FolderService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(FolderService);
  });

  it('listFolders() GETs the account folders', () => {
    service.listFolders('acc1');
    expect(api.get).toHaveBeenCalledWith('/email-accounts/acc1/folders');
  });

  it('createFolder() POSTs the folder name', () => {
    service.createFolder('acc1', 'Archive');
    expect(api.post).toHaveBeenCalledWith('/email-accounts/acc1/folders', { name: 'Archive' });
  });

  it('deleteFolder() DELETEs the folder', () => {
    service.deleteFolder('acc1', 'f1');
    expect(api.delete).toHaveBeenCalledWith('/email-accounts/acc1/folders/f1');
  });
});
