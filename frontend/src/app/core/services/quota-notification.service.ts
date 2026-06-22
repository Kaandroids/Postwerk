import { Injectable, signal } from '@angular/core';
import { QuotaError } from '../../models/usage.model';

/** Manages quota-exceeded notification state to trigger upgrade prompts in the UI. */
@Injectable({ providedIn: 'root' })
export class QuotaNotificationService {
  readonly quotaError = signal<QuotaError | null>(null);

  show(error: QuotaError): void {
    this.quotaError.set(error);
  }

  dismiss(): void {
    this.quotaError.set(null);
  }
}
