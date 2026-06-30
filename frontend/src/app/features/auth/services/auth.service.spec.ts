import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { TokenService } from '../../../core/services/token.service';
import { setupHttpService } from '../../../../testing';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let token: TokenService;

  beforeEach(() => {
    localStorage.clear();
    ({ service, httpMock } = setupHttpService(AuthService));
    token = TestBed.inject(TokenService);
  });

  afterEach(() => httpMock.verify());

  const tokens = { accessToken: 'a', refreshToken: 'r', expiresIn: 1, role: 'USER' };

  it('isValidEmail() accepts well-formed addresses and rejects junk', () => {
    expect(service.isValidEmail('user@example.com')).toBe(true);
    expect(service.isValidEmail('  user@example.com  ')).toBe(true);
    expect(service.isValidEmail('nope')).toBe(false);
    expect(service.isValidEmail('a@b')).toBe(false);
  });

  it('login() saves tokens and returns success', async () => {
    const p = service.login('a@b.c', 'pw');
    const req = httpMock.expectOne(r => r.url.endsWith('/auth/login'));
    expect(req.request.body).toEqual({ email: 'a@b.c', password: 'pw' });
    req.flush(tokens);
    await expect(p).resolves.toEqual({ success: true });
    expect(token.getAccessToken()).toBe('a');
  });

  it('login() flags an unverified account (403 EMAIL_NOT_VERIFIED)', async () => {
    const p = service.login('a@b.c', 'pw');
    httpMock.expectOne(r => r.url.endsWith('/auth/login'))
      .flush({ code: 'EMAIL_NOT_VERIFIED', email: 'a@b.c' }, { status: 403, statusText: 'Forbidden' });
    await expect(p).resolves.toEqual({ success: false, needsVerification: true, email: 'a@b.c' });
  });

  it('login() returns an error message on other failures', async () => {
    const p = service.login('a@b.c', 'pw');
    httpMock.expectOne(r => r.url.endsWith('/auth/login'))
      .flush({ message: 'bad' }, { status: 401, statusText: 'Unauthorized' });
    const result = await p;
    expect(result.success).toBe(false);
    expect(result.error).toBeTruthy();
  });

  it('register() returns the email and issues no tokens', async () => {
    const p = service.register({ fullName: 'A', email: 'a@b.c', password: 'pw', marketingOptIn: false, termsAccepted: true });
    const req = httpMock.expectOne(r => r.url.endsWith('/auth/register'));
    req.flush({ verificationRequired: true, email: 'a@b.c' });
    await expect(p).resolves.toEqual({ success: true, email: 'a@b.c' });
    expect(token.getAccessToken()).toBeNull();
  });

  it('verifyEmail() saves tokens on success', async () => {
    const p = service.verifyEmail('tok');
    httpMock.expectOne(r => r.url.endsWith('/auth/verify-email')).flush(tokens);
    await expect(p).resolves.toEqual({ success: true });
    expect(token.getAccessToken()).toBe('a');
  });

  it('resendVerification()/resetPassword()/confirmPasswordReset() resolve success', async () => {
    const p1 = service.resendVerification('a@b.c', 'de');
    httpMock.expectOne(r => r.url.endsWith('/auth/resend-verification')).flush({});
    await expect(p1).resolves.toEqual({ success: true });

    const p2 = service.resetPassword('a@b.c');
    httpMock.expectOne(r => r.url.endsWith('/auth/reset-password')).flush({});
    await expect(p2).resolves.toEqual({ success: true });

    const p3 = service.confirmPasswordReset('tok', 'newpw');
    httpMock.expectOne(r => r.url.endsWith('/auth/reset-password/confirm')).flush({});
    await expect(p3).resolves.toEqual({ success: true });
  });

  it('confirmPasswordReset() surfaces an error on failure', async () => {
    const p = service.confirmPasswordReset('tok', 'newpw');
    httpMock.expectOne(r => r.url.endsWith('/auth/reset-password/confirm'))
      .flush({ message: 'expired' }, { status: 400, statusText: 'Bad Request' });
    const result = await p;
    expect(result.success).toBe(false);
    expect(result.error).toBeTruthy();
  });

  it('logout() posts the refresh token and clears tokens even on error', async () => {
    token.saveTokens('a', 'r', 'USER');
    const p = service.logout();
    const req = httpMock.expectOne(r => r.url.endsWith('/auth/logout'));
    expect(req.request.body).toEqual({ refreshToken: 'r' });
    req.flush('boom', { status: 500, statusText: 'Server Error' });
    await p;
    expect(token.getAccessToken()).toBeNull();
    expect(token.getRefreshToken()).toBeNull();
  });
});
