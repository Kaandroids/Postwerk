import { inject, Injectable } from '@angular/core';
import { I18nService } from './i18n.service';

/** Provides locale-aware date, time, number, and byte-size formatting utilities. */
@Injectable({ providedIn: 'root' })
export class FormatService {
  private readonly i18n = inject(I18nService);

  private get locale(): string {
    return this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
  }

  compactNumber(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return n.toString();
  }

  /** Localized EUR currency from a plain euro amount (e.g. 19.99 → "€19.99" / "19,99 €"). */
  eur(value: number | null | undefined): string {
    return (value ?? 0).toLocaleString(this.locale, { style: 'currency', currency: 'EUR' });
  }

  /** Localized EUR currency from an integer euro-cent amount. */
  eurFromCents(cents: number | null | undefined): string {
    return this.eur((cents ?? 0) / 100);
  }

  /** Localized EUR currency from a micros amount (1 EUR = 1,000,000 micros). */
  eurFromMicros(micros: number | null | undefined): string {
    return this.eur((micros ?? 0) / 1_000_000);
  }

  /** Compact relative duration from a minute count: "now", "5m", "3h", "2d". */
  relativeMinutes(mins: number): string {
    if (mins < 1) return this.i18n.t('fmt_now');
    if (mins < 60) return `${mins}m`;
    if (mins < 1440) return `${Math.floor(mins / 60)}h`;
    return `${Math.floor(mins / 1440)}d`;
  }

  /** Compact relative time for a PAST ISO timestamp ("now" / "5m" / "3h" / "2d"). Null → "—". */
  relativePast(iso: string | null | undefined): string {
    if (!iso) return '—';
    return this.relativeMinutes(Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000)));
  }

  /** Compact relative time for a FUTURE ISO timestamp. Null → "—"; already-past → "now". */
  relativeFuture(iso: string | null | undefined): string {
    if (!iso) return '—';
    return this.relativeMinutes(Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 60000)));
  }

  /** Current wall-clock time as a localized HH:MM:SS string (for "updated at" stamps). */
  nowClock(): string {
    return new Date().toLocaleTimeString(this.locale, {
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  }

  date(date: string | null): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString(this.locale, {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  }

  dateTime(date: string | null): string {
    if (!date) return '—';
    return new Date(date).toLocaleString(this.locale, {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }

  dateTimeFull(date: string | null): string {
    if (!date) return '—';
    return new Date(date).toLocaleString(this.locale, {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  }
}
