import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
  id: number;
  severity: ToastSeverity;
  title?: string;
  message: string;
}

/**
 * Generic transient toast queue (the missing app-wide ephemeral-feedback mechanism). Separate from
 * the persistent notification inbox: toasts are session-only and auto-dismiss. See
 * doc/NOTIFICATION_SYSTEM_DESIGN.md.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(message: string, opts?: { severity?: ToastSeverity; title?: string; durationMs?: number }): void {
    const id = ++this.seq;
    const toast: Toast = { id, severity: opts?.severity ?? 'info', title: opts?.title, message };
    this.toasts.update(list => [...list, toast]);
    const duration = opts?.durationMs ?? 4000;
    if (duration > 0) setTimeout(() => this.dismiss(id), duration);
  }

  success(message: string, title?: string): void {
    this.show(message, { severity: 'success', title });
  }

  error(message: string, title?: string): void {
    this.show(message, { severity: 'danger', title, durationMs: 6000 });
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }
}
