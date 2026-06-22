import { Injectable, signal, computed } from '@angular/core';

export interface CookieConsent {
  essential: boolean;
  analytics: boolean;
  marketing: boolean;
  timestamp: string;
}

/**
 * Tracks user cookie consent preferences (essential, analytics, marketing) using
 * localStorage, and exposes reactive signals for consent state across the application.
 */
@Injectable({ providedIn: 'root' })
export class CookieConsentService {
  private readonly STORAGE_KEY = 'cookie_consent';

  consent = signal<CookieConsent | null>(this.loadConsent());

  hasConsented = computed(() => this.consent() !== null);

  hasConsent(category: keyof Omit<CookieConsent, 'timestamp'>): boolean {
    const c = this.consent();
    if (!c) return false;
    return c[category];
  }

  acceptAll(): void {
    this.saveConsent({ essential: true, analytics: true, marketing: true, timestamp: new Date().toISOString() });
  }

  acceptEssentialOnly(): void {
    this.saveConsent({ essential: true, analytics: false, marketing: false, timestamp: new Date().toISOString() });
  }

  saveConsent(consent: CookieConsent): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(consent));
    this.consent.set(consent);
  }

  revokeConsent(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    this.consent.set(null);
  }

  private loadConsent(): CookieConsent | null {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    if (!stored) return null;
    try {
      return JSON.parse(stored) as CookieConsent;
    } catch {
      return null;
    }
  }
}
