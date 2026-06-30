import { TestBed } from '@angular/core/testing';
import { TokenService } from './token.service';

/** Builds a fake JWT carrying the given payload (only the middle segment is read). */
function jwt(payload: object): string {
  return `header.${btoa(JSON.stringify(payload))}.sig`;
}

describe('TokenService', () => {
  let service: TokenService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenService);
  });

  it('saveTokens() persists access/refresh (and role only when given)', () => {
    service.saveTokens('a', 'r');
    expect(service.getAccessToken()).toBe('a');
    expect(service.getRefreshToken()).toBe('r');
    expect(service.getRole()).toBe('USER'); // default when no role stored

    service.saveTokens('a2', 'r2', 'ADMIN');
    expect(service.getRole()).toBe('ADMIN');
    expect(service.isAdmin()).toBe(true);
  });

  it('clearTokens() removes everything (role falls back to USER)', () => {
    service.saveTokens('a', 'r', 'ADMIN');
    service.clearTokens();
    expect(service.getAccessToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.getRole()).toBe('USER');
    expect(service.isAdmin()).toBe(false);
  });

  it('isLoggedIn() is false without a token', () => {
    expect(service.isLoggedIn()).toBe(false);
  });

  it('isLoggedIn() is true for an unexpired token and false for an expired one', () => {
    const now = Date.now() / 1000;
    service.saveTokens(jwt({ exp: now + 3600 }), 'r');
    expect(service.isLoggedIn()).toBe(true);

    service.saveTokens(jwt({ exp: now - 10 }), 'r');
    expect(service.isLoggedIn()).toBe(false);
  });

  it('treats a token without exp as not expired, and a malformed token as expired', () => {
    service.saveTokens(jwt({ sub: 'x' }), 'r'); // no exp
    expect(service.isLoggedIn()).toBe(true);

    service.saveTokens('not-a-jwt', 'r');
    expect(service.isLoggedIn()).toBe(false);
  });
});
