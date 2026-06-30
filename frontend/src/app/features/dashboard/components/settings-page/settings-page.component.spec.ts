import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { SettingsPageComponent } from './settings-page.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { UserService } from '../../../../core/services/user.service';
import { ApiService } from '../../../../core/services/api.service';
import { CookieConsentService } from '../../../../core/services/cookie-consent.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ToastService } from '../../../../core/services/toast.service';

const evt = (value: string) => ({ target: { value } } as unknown as Event);

describe('SettingsPageComponent', () => {
  let user: Record<string, ReturnType<typeof vi.fn>>;
  let api: { patch: ReturnType<typeof vi.fn> };
  let notifications: { getPreferences: ReturnType<typeof vi.fn>; updatePreferences: ReturnType<typeof vi.fn> };
  let cookie: { revokeConsent: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let cmp: SettingsPageComponent;

  beforeEach(() => {
    user = {
      updateProfile: vi.fn(() => Promise.resolve()),
      changePassword: vi.fn(() => Promise.resolve()),
      exportData: vi.fn(() => Promise.resolve()),
      deleteAccount: vi.fn(() => Promise.resolve()),
    };
    api = { patch: vi.fn(() => of({})) };
    notifications = { getPreferences: vi.fn(() => Promise.resolve([])), updatePreferences: vi.fn() };
    cookie = { revokeConsent: vi.fn() };
    toast = { error: vi.fn() };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      imports: [SettingsPageComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: UserService, useValue: { ...user, profile: signal(null) } },
        { provide: ApiService, useValue: api },
        { provide: CookieConsentService, useValue: cookie },
        { provide: NotificationService, useValue: notifications },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router },
      ],
    });
    cmp = TestBed.createComponent(SettingsPageComponent).componentInstance;
  });

  it('pwScore rates strength from length / case / digit / symbol', () => {
    cmp.newPw.set(''); expect(cmp.pwScore()).toBe(0);
    cmp.newPw.set('abcdefgh'); expect(cmp.pwScore()).toBe(1);
    cmp.newPw.set('Abcdefg1'); expect(cmp.pwScore()).toBe(3);
    cmp.newPw.set('Abcdefg1!'); expect(cmp.pwScore()).toBe(4);
  });

  it('pwScoreLabel reads the localized label for the score', () => {
    cmp.newPw.set('Abcdefg1!');
    expect(cmp.pwScoreLabel()).toBe('Excellent');
  });

  it('cardBrand detects the network from the number prefix', () => {
    cmp.cardNum.set('4111'); expect(cmp.cardBrand()).toBe('visa');
    cmp.cardNum.set('5500'); expect(cmp.cardBrand()).toBe('mastercard');
    cmp.cardNum.set('3400'); expect(cmp.cardBrand()).toBe('amex');
    cmp.cardNum.set('9999'); expect(cmp.cardBrand()).toBe('card');
  });

  it('cardLast4 takes the final four digits', () => {
    cmp.cardNum.set('4111 1111 1111 1234');
    expect(cmp.cardLast4()).toBe('1234');
  });

  it('formatCardNum groups in fours; formatCardExp inserts the slash', () => {
    cmp.formatCardNum(evt('4111111111111234'));
    expect(cmp.cardNum()).toBe('4111 1111 1111 1234');
    cmp.formatCardExp(evt('1225'));
    expect(cmp.cardExp()).toBe('12/25');
  });

  it('initials / brandLabel / brandClass helpers', () => {
    expect(cmp.initials('John Doe')).toBe('JD');
    expect(cmp.brandLabel('visa')).toBe('VISA');
    expect(cmp.brandLabel('other')).toBe('CARD');
    expect(cmp.brandClass('amex')).toContain('amex');
  });

  it('saveCard validates, adds the card, and enforces a single default', () => {
    cmp.cards.set([{ id: 'old', brand: 'card', last4: '0000', name: 'X', exp: '01/30', default: true } as never]);
    cmp.cardName.set(''); // invalid → no-op
    cmp.cardNum.set('4111 1111 1111 1234'); cmp.cardExp.set('12/25'); cmp.cardCvc.set('123');
    cmp.saveCard();
    expect(cmp.cards().length).toBe(1);

    cmp.cardName.set('Jane'); cmp.cardMakeDefault.set(true);
    cmp.saveCard();
    expect(cmp.cards().length).toBe(2);
    expect(cmp.cards().filter(c => c.default).length).toBe(1);
    expect(cmp.cardModalOpen()).toBe(false);
  });

  it('makeCardDefault / removeCard mutate the card list', () => {
    cmp.cards.set([{ id: 'a', default: true }, { id: 'b', default: false }] as never);
    cmp.makeCardDefault('b');
    expect(cmp.cards().find(c => c.id === 'b')?.default).toBe(true);
    expect(cmp.cards().find(c => c.id === 'a')?.default).toBe(false);
    cmp.removeCard('a');
    expect(cmp.cards().map(c => c.id)).toEqual(['b']);
  });

  it('changePassword validates length then match, otherwise calls the service', async () => {
    cmp.newPw.set('short'); cmp.confirmPw.set('short');
    await cmp.changePassword();
    expect(cmp.pwError()).toBe('settings_password_error_short');
    expect(user['changePassword']).not.toHaveBeenCalled();

    cmp.newPw.set('longenough1'); cmp.confirmPw.set('different1');
    await cmp.changePassword();
    expect(cmp.pwError()).toBe('settings_password_error_match');

    cmp.currentPw.set('old'); cmp.newPw.set('longenough1'); cmp.confirmPw.set('longenough1');
    await cmp.changePassword();
    expect(user['changePassword']).toHaveBeenCalledWith('old', 'longenough1');
    expect(cmp.pwSuccess()).toBe(true);
  });

  it('saveProfile pushes the profile fields', async () => {
    cmp.profileName.set('Jane'); cmp.profileCompany.set('Acme'); cmp.profilePhone.set('');
    await cmp.saveProfile();
    expect(user['updateProfile']).toHaveBeenCalledWith({ fullName: 'Jane', company: 'Acme', phone: undefined });
    expect(cmp.profileSaved()).toBe(true);
  });

  it('toggleMarketing optimistically flips and PATCHes consent', async () => {
    await cmp.toggleMarketing();
    expect(cmp.marketingOptIn()).toBe(true);
    expect(api.patch).toHaveBeenCalledWith('/users/me/consent', { marketingOptIn: true });
  });

  it('toggleNotifPref reverts and toasts on failure', async () => {
    cmp.notifPrefs.set([{ category: 'X', inApp: false, email: false } as never]);
    notifications.updatePreferences.mockRejectedValue(new Error('nope'));
    await cmp.toggleNotifPref('X', 'inApp');
    expect(cmp.notifPrefs()[0].inApp).toBe(false); // reverted
    expect(toast.error).toHaveBeenCalled();
  });

  it('resetCookieConsent / exportData / deleteAccount delegate to their services', async () => {
    cmp.resetCookieConsent();
    expect(cookie.revokeConsent).toHaveBeenCalled();
    await cmp.exportData();
    expect(user['exportData']).toHaveBeenCalled();
    await cmp.deleteAccount();
    expect(user['deleteAccount']).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
