import { TestBed } from '@angular/core/testing';
import { SecretService } from './secret.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('SecretService', () => {
  let api: MockApi;
  let service: SecretService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(SecretService);
  });

  it('list() GETs the secrets collection', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith('/secrets');
  });

  it('create() POSTs the request', () => {
    const req = { name: 'API_KEY', value: 'x' } as never;
    service.create(req);
    expect(api.post).toHaveBeenCalledWith('/secrets', req);
  });

  it('update() PUTs to the id path', () => {
    const req = { name: 'API_KEY', value: 'y' } as never;
    service.update('1', req);
    expect(api.put).toHaveBeenCalledWith('/secrets/1', req);
  });

  it('delete() DELETEs the secret', () => {
    service.delete('1');
    expect(api.delete).toHaveBeenCalledWith('/secrets/1');
  });
});
