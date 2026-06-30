import { TestBed } from '@angular/core/testing';
import { EmailAccountService } from './email-account.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('EmailAccountService', () => {
  let api: MockApi;
  let service: EmailAccountService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(EmailAccountService);
  });

  it('list() GETs the accounts collection', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith('/email-accounts');
  });

  it('get() GETs a single account', () => {
    service.get('1');
    expect(api.get).toHaveBeenCalledWith('/email-accounts/1');
  });

  it('create() POSTs and update() PUTs the request', () => {
    const req = { email: 'a@b.c' } as never;
    service.create(req);
    expect(api.post).toHaveBeenCalledWith('/email-accounts', req);
    service.update('1', req);
    expect(api.put).toHaveBeenCalledWith('/email-accounts/1', req);
  });

  it('delete() DELETEs the account', () => {
    service.delete('1');
    expect(api.delete).toHaveBeenCalledWith('/email-accounts/1');
  });

  it('setDefault() PATCHes the default sub-path', () => {
    service.setDefault('1');
    expect(api.patch).toHaveBeenCalledWith('/email-accounts/1/default', {});
  });

  it('testConnection() POSTs the connection settings', () => {
    const data = { host: 'h', port: 993, username: 'u', password: 'p', ssl: true, type: 'imap' as const };
    service.testConnection(data);
    expect(api.post).toHaveBeenCalledWith('/email-accounts/test-connection', data);
  });
});
