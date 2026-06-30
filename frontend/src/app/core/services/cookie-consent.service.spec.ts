import { TestBed } from '@angular/core/testing';
import { CookieConsentService } from './cookie-consent.service';

describe('CookieConsentService', () => {
  let service: CookieConsentService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(CookieConsentService);
  });

  it('starts with no consent', () => {
    expect(service.consent()).toBeNull();
    expect(service.hasConsented()).toBe(false);
    expect(service.hasConsent('analytics')).toBe(false);
  });

  it('acceptAll() grants every category and persists', () => {
    service.acceptAll();
    expect(service.hasConsented()).toBe(true);
    expect(service.hasConsent('analytics')).toBe(true);
    expect(service.hasConsent('marketing')).toBe(true);
    expect(JSON.parse(localStorage.getItem('cookie_consent')!).analytics).toBe(true);
  });

  it('acceptEssentialOnly() grants only essential', () => {
    service.acceptEssentialOnly();
    expect(service.hasConsent('essential')).toBe(true);
    expect(service.hasConsent('analytics')).toBe(false);
    expect(service.hasConsent('marketing')).toBe(false);
  });

  it('revokeConsent() clears stored consent', () => {
    service.acceptAll();
    service.revokeConsent();
    expect(service.consent()).toBeNull();
    expect(localStorage.getItem('cookie_consent')).toBeNull();
  });

  it('loads existing consent from storage on construction', () => {
    localStorage.setItem('cookie_consent', JSON.stringify({ essential: true, analytics: true, marketing: false, timestamp: 't' }));
    // The instance from beforeEach predates this write, so build a fresh one to exercise the load path.
    const built = new CookieConsentService();
    expect(built.hasConsented()).toBe(true);
    expect(built.hasConsent('analytics')).toBe(true);
    expect(built.hasConsent('marketing')).toBe(false);
  });

  it('treats malformed stored consent as none', () => {
    localStorage.setItem('cookie_consent', '{not json');
    const built = new CookieConsentService();
    expect(built.consent()).toBeNull();
  });
});
