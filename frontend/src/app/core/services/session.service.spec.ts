import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionService } from './session.service';
import { TokenService } from './token.service';
import { setupHttpService } from '../../../testing';

function validJwt(): string {
  return `h.${btoa(JSON.stringify({ exp: Date.now() / 1000 + 3600 }))}.s`;
}

describe('SessionService', () => {
  let service: SessionService;
  let httpMock: HttpTestingController;
  let token: TokenService;

  beforeEach(() => {
    localStorage.clear();
    ({ service, httpMock } = setupHttpService(SessionService));
    token = TestBed.inject(TokenService);
  });

  afterEach(() => httpMock.verify());

  it('resolves false (no request) when there is no refresh token', async () => {
    await expect(service.refreshSession()).resolves.toBe(false);
  });

  it('refreshes and persists the rotated tokens on success', async () => {
    token.saveTokens('a', 'r');
    const p = service.refreshSession();
    const req = httpMock.expectOne(r => r.url.endsWith('/auth/refresh'));
    expect(req.request.body).toEqual({ refreshToken: 'r' });
    req.flush({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 100, role: 'USER' });
    await expect(p).resolves.toBe(true);
    expect(token.getAccessToken()).toBe('a2');
    expect(token.getRefreshToken()).toBe('r2');
  });

  it('de-duplicates concurrent refreshes into a single request (single-flight)', async () => {
    token.saveTokens('a', 'r');
    const p1 = service.refreshSession();
    const p2 = service.refreshSession();
    // expectOne asserts exactly one HTTP request was issued for both callers.
    httpMock.expectOne(r => r.url.endsWith('/auth/refresh'))
      .flush({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 100, role: 'USER' });
    await expect(Promise.all([p1, p2])).resolves.toEqual([true, true]);
  });

  it('resolves false on a hard refresh failure', async () => {
    token.saveTokens('a', 'r');
    const p = service.refreshSession();
    httpMock.expectOne(r => r.url.endsWith('/auth/refresh'))
      .flush('no', { status: 401, statusText: 'Unauthorized' });
    await expect(p).resolves.toBe(false);
  });

  it('recovers (true) when another tab rotated the refresh token mid-flight', async () => {
    token.saveTokens(validJwt(), 'r');
    const p = service.refreshSession();
    // Simulate a sibling tab rotating the refresh token while our request was in flight.
    localStorage.setItem('refresh_token', 'r-rotated');
    httpMock.expectOne(r => r.url.endsWith('/auth/refresh'))
      .flush('no', { status: 401, statusText: 'Unauthorized' });
    await expect(p).resolves.toBe(true);
  });
});
