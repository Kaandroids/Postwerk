import { TestBed } from '@angular/core/testing';
import { WebhookEndpointService } from './webhook-endpoint.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('WebhookEndpointService', () => {
  let api: MockApi;
  let service: WebhookEndpointService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(WebhookEndpointService);
  });

  it('get() GETs the endpoint', () => {
    service.get('w1');
    expect(api.get).toHaveBeenCalledWith('/webhook-endpoints/w1');
  });

  it('regenerateToken() POSTs to the regenerate-token sub-path', () => {
    service.regenerateToken('w1');
    expect(api.post).toHaveBeenCalledWith('/webhook-endpoints/w1/regenerate-token', {});
  });

  it('setAuth() PUTs the auth request', () => {
    const req = { authType: 'HMAC' } as never;
    service.setAuth('w1', req);
    expect(api.put).toHaveBeenCalledWith('/webhook-endpoints/w1/auth', req);
  });

  it('generateSecret() POSTs to the generate-secret sub-path', () => {
    service.generateSecret('w1');
    expect(api.post).toHaveBeenCalledWith('/webhook-endpoints/w1/generate-secret', {});
  });
});
