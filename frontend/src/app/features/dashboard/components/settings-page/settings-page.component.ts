import { ChangeDetectionStrategy, Component, inject, signal, computed, OnInit, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { I18nService, Lang } from '../../../../core/services/i18n.service';
import { UserService } from '../../../../core/services/user.service';
import { ApiService } from '../../../../core/services/api.service';
import { CookieConsentService } from '../../../../core/services/cookie-consent.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ToastService } from '../../../../core/services/toast.service';
import { NotificationPreference } from '../../../../models/notification.model';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { v } from '../../../../shared/utils/event.util';
import { humanizeError } from '../../../../shared/utils/error.util';

interface PaymentCard {
  id: string;
  brand: 'visa' | 'mastercard' | 'amex' | 'card';
  last4: string;
  name: string;
  exp: string;
  default: boolean;
}

/** User settings page with profile editing, password change, payment methods, billing, privacy, and account deletion. */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PageContentComponent, IconComponent, ErrorBannerComponent],
  templateUrl: './settings-page.component.html',
  styleUrls: ['./settings-page.component.scss'],
})
export class SettingsPageComponent implements OnInit {
  protected i18n = inject(I18nService);
  private userService = inject(UserService);
  private api = inject(ApiService);
  protected cookieConsentService = inject(CookieConsentService);
  private notifications = inject(NotificationService);
  private toast = inject(ToastService);
  private router = inject(Router);

  // Profile
  profileName = signal('');
  profileEmail = signal('');
  profileCompany = signal('');
  profilePhone = signal('');
  savingProfile = signal(false);
  profileSaved = signal(false);
  lastLoginAt = signal<string | null>(null);
  lastLoginIp = signal<string | null>(null);

  // Password
  currentPw = signal('');
  newPw = signal('');
  confirmPw = signal('');
  changingPw = signal(false);
  pwError = signal('');
  pwSuccess = signal(false);
  showPw = signal<Record<string, boolean>>({});

  pwScore = computed(() => {
    const p = this.newPw();
    let s = 0;
    if (p.length >= 8) s++;
    if (/[A-Z]/.test(p)) s++;
    if (/[0-9]/.test(p)) s++;
    if (/[^A-Za-z0-9]/.test(p)) s++;
    return s;
  });

  pwScoreLabel = computed(() => {
    const labels_de = ['Sehr schwach', 'Schwach', 'Mittel', 'Stark', 'Sehr stark'];
    const labels_en = ['Very weak', 'Weak', 'Fair', 'Strong', 'Excellent'];
    const labels = this.i18n.lang() === 'de' ? labels_de : labels_en;
    return labels[this.pwScore()];
  });

  // Payment
  cards = signal<PaymentCard[]>([]);
  cardModalOpen = signal(false);
  cardNum = signal('');
  cardName = signal('');
  cardExp = signal('');
  cardCvc = signal('');
  cardMakeDefault = signal(false);

  cardBrand = computed(() => {
    const n = this.cardNum().replace(/\s/g, '');
    if (/^4/.test(n)) return 'visa' as const;
    if (/^(5[1-5]|2[2-7])/.test(n)) return 'mastercard' as const;
    if (/^3[47]/.test(n)) return 'amex' as const;
    return 'card' as const;
  });

  cardLast4 = computed(() => this.cardNum().replace(/\s/g, '').slice(-4));

  // Billing
  billingKind = signal<'person' | 'company'>('company');
  billingCompany = signal('');
  billingVat = signal('');
  billingName = signal('');
  billingLine1 = signal('');
  billingLine2 = signal('');
  billingZip = signal('');
  billingCity = signal('');
  billingCountry = signal('DE');

  // Language
  langs: { code: Lang; name: string; sub: string; flag: string }[] = [
    { code: 'de', name: 'Deutsch', sub: 'German', flag: 'DE' },
    { code: 'en', name: 'English', sub: 'Englisch', flag: 'GB' },
  ];

  // Privacy
  marketingOptIn = signal(false);
  usageAnalytics = signal(true);

  // Notification preferences (category × channel matrix)
  notifPrefs = signal<NotificationPreference[]>([]);

  // Footer
  confirmDelete = signal(false);
  deleting = signal(false);
  exporting = signal(false);

  countries = [
    { code: 'DE', de: 'Deutschland', en: 'Germany' },
    { code: 'AT', de: 'Österreich', en: 'Austria' },
    { code: 'CH', de: 'Schweiz', en: 'Switzerland' },
    { code: 'NL', de: 'Niederlande', en: 'Netherlands' },
    { code: 'FR', de: 'Frankreich', en: 'France' },
    { code: 'IT', de: 'Italien', en: 'Italy' },
    { code: 'GB', de: 'Vereinigtes Königreich', en: 'United Kingdom' },
    { code: 'US', de: 'USA', en: 'United States' },
  ];

  @HostListener('document:keydown.escape')
  onEsc(): void {
    if (this.cardModalOpen()) this.cardModalOpen.set(false);
  }

  ngOnInit(): void {
    const p = this.userService.profile();
    if (p) {
      this.profileName.set(p.fullName);
      this.profileEmail.set(p.email);
      this.profileCompany.set(p.company ?? '');
      this.profilePhone.set(p.phone ?? '');
      if (p.lastLoginAt) this.lastLoginAt.set(new Date(p.lastLoginAt).toLocaleString());
      this.lastLoginIp.set(p.lastLoginIp);
    }
    this.loadNotifPrefs();
  }

  private async loadNotifPrefs(): Promise<void> {
    try {
      this.notifPrefs.set(await this.notifications.getPreferences());
    } catch {
      /* leave empty; section just renders no rows */
    }
  }

  categoryLabel(category: string): string {
    return this.i18n.t('notif_cat_' + category.toLowerCase());
  }

  async toggleNotifPref(category: string, channel: 'inApp' | 'email'): Promise<void> {
    const prev = this.notifPrefs();
    const next = prev.map(p => (p.category === category ? { ...p, [channel]: !p[channel] } : p));
    this.notifPrefs.set(next);
    try {
      this.notifPrefs.set(await this.notifications.updatePreferences(next));
    } catch {
      this.notifPrefs.set(prev);
      this.toast.error(this.i18n.t('notif_pref_save_failed'));
    }
  }

  initials(name: string): string {
    return name.split(' ').map(s => s[0]).slice(0, 2).join('').toUpperCase();
  }

  toggleShowPw(key: string): void {
    this.showPw.update(s => ({ ...s, [key]: !s[key] }));
  }

  readonly v = v;

  // Profile
  async saveProfile(): Promise<void> {
    this.savingProfile.set(true);
    this.profileSaved.set(false);
    try {
      await this.userService.updateProfile({
        fullName: this.profileName(),
        company: this.profileCompany() || undefined,
        phone: this.profilePhone() || undefined,
      });
      this.profileSaved.set(true);
      setTimeout(() => this.profileSaved.set(false), 2000);
    } finally {
      this.savingProfile.set(false);
    }
  }

  // Password
  async changePassword(): Promise<void> {
    this.pwError.set('');
    this.pwSuccess.set(false);
    if (this.newPw().length < 8) { this.pwError.set(this.i18n.t('settings_password_error_short')); return; }
    if (this.newPw() !== this.confirmPw()) { this.pwError.set(this.i18n.t('settings_password_error_match')); return; }

    this.changingPw.set(true);
    try {
      await this.userService.changePassword(this.currentPw(), this.newPw());
      this.pwSuccess.set(true);
      this.currentPw.set(''); this.newPw.set(''); this.confirmPw.set('');
      setTimeout(() => this.pwSuccess.set(false), 3000);
    } catch (err: any) {
      const msg = humanizeError(err, '');
      this.pwError.set(msg.includes('incorrect') ? this.i18n.t('settings_password_error_wrong') : (msg || 'Error'));
    } finally {
      this.changingPw.set(false);
    }
  }

  // Payment
  formatCardNum(e: Event): void {
    const raw = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 19);
    this.cardNum.set(raw.replace(/(.{4})/g, '$1 ').trim());
  }

  formatCardExp(e: Event): void {
    const d = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 4);
    this.cardExp.set(d.length > 2 ? d.slice(0, 2) + '/' + d.slice(2) : d);
  }

  openCardModal(): void {
    this.cardNum.set(''); this.cardName.set(''); this.cardExp.set(''); this.cardCvc.set('');
    this.cardMakeDefault.set(false); this.cardModalOpen.set(true);
  }

  saveCard(): void {
    if (this.cardLast4().length < 4 || !this.cardName() || this.cardExp().length < 5 || this.cardCvc().length < 3) return;
    const newCard: PaymentCard = {
      id: Date.now().toString(),
      brand: this.cardBrand(),
      last4: this.cardLast4(),
      name: this.cardName(),
      exp: this.cardExp(),
      default: this.cardMakeDefault(),
    };
    this.cards.update(cs => {
      const next = [...cs, newCard];
      if (newCard.default) return next.map(c => ({ ...c, default: c.id === newCard.id }));
      return next;
    });
    this.cardModalOpen.set(false);
  }

  makeCardDefault(id: string): void {
    this.cards.update(cs => cs.map(c => ({ ...c, default: c.id === id })));
  }

  removeCard(id: string): void {
    this.cards.update(cs => cs.filter(c => c.id !== id));
  }

  brandClass(brand: string): string {
    if (brand === 'visa') return 'set-card-brand set-card-brand-visa';
    if (brand === 'mastercard') return 'set-card-brand set-card-brand-mc';
    if (brand === 'amex') return 'set-card-brand set-card-brand-amex';
    return 'set-card-brand';
  }

  brandLabel(brand: string): string {
    if (brand === 'visa') return 'VISA';
    if (brand === 'mastercard') return '';
    if (brand === 'amex') return 'AMEX';
    return 'CARD';
  }

  // Privacy
  async toggleMarketing(): Promise<void> {
    const newValue = !this.marketingOptIn();
    this.marketingOptIn.set(newValue);
    try {
      await firstValueFrom(this.api.patch('/users/me/consent', { marketingOptIn: newValue }));
    } catch { this.marketingOptIn.set(!newValue); }
  }

  async toggleUsage(): Promise<void> {
    const newValue = !this.usageAnalytics();
    this.usageAnalytics.set(newValue);
    try {
      await firstValueFrom(this.api.patch('/users/me/consent', { usageAnalytics: newValue }));
    } catch { this.usageAnalytics.set(!newValue); }
  }

  resetCookieConsent(): void {
    this.cookieConsentService.revokeConsent();
  }

  // Danger zone
  async exportData(): Promise<void> {
    this.exporting.set(true);
    try { await this.userService.exportData(); } finally { this.exporting.set(false); }
  }

  async deleteAccount(): Promise<void> {
    this.deleting.set(true);
    try {
      await this.userService.deleteAccount();
      this.router.navigate(['/auth/login']);
    } catch { this.deleting.set(false); }
  }
}
