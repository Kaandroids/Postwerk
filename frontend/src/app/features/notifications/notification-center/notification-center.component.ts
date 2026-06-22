import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, inject, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../core/services/i18n.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationItem, NotificationSeverity } from '../../../models/notification.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';

const POLL_INTERVAL_MS = 60_000;

/** Topbar notification bell: unread badge + dropdown inbox. Self-contained (SRP) — owns its open
 *  state, polling, and item interactions. See doc/NOTIFICATION_SYSTEM_DESIGN.md. */
@Component({
  selector: 'app-notification-center',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.scss',
})
export class NotificationCenterComponent {
  protected i18n = inject(I18nService);
  protected notifications = inject(NotificationService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);

  protected open = signal(false);

  constructor() {
    this.notifications.refreshUnreadCount();
    const timer = setInterval(() => this.notifications.refreshUnreadCount(), POLL_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  toggle(): void {
    const next = !this.open();
    this.open.set(next);
    if (next) this.notifications.load();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEsc(): void {
    this.open.set(false);
  }

  onItemClick(n: NotificationItem): void {
    if (!n.read) this.notifications.markRead(n.id);
    this.open.set(false);
    if (n.linkUrl) this.router.navigateByUrl(n.linkUrl);
  }

  markAllRead(): void {
    this.notifications.markAllRead();
  }

  title(n: NotificationItem): string {
    return this.i18n.t(n.titleKey, this.stringParams(n.params));
  }

  body(n: NotificationItem): string {
    return n.bodyKey ? this.i18n.t(n.bodyKey, this.stringParams(n.params)) : '';
  }

  severityIcon(severity: NotificationSeverity): string {
    switch (severity) {
      case 'SUCCESS': return 'checkCircle';
      case 'WARNING': return 'alert';
      case 'CRITICAL': return 'alertTriangle';
      case 'ACTION_REQUIRED': return 'bell';
      default: return 'info';
    }
  }

  relativeTime(iso: string): string {
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return '';
    const sec = Math.max(0, Math.floor((Date.now() - then) / 1000));
    if (sec < 60) return this.i18n.t('notif_time_now');
    const min = Math.floor(sec / 60);
    if (min < 60) return `${min}m`;
    const hr = Math.floor(min / 60);
    if (hr < 24) return `${hr}h`;
    const day = Math.floor(hr / 24);
    if (day < 7) return `${day}d`;
    return new Date(then).toLocaleDateString(this.i18n.lang());
  }

  private stringParams(params: Record<string, unknown>): Record<string, string> {
    const out: Record<string, string> = {};
    for (const key of Object.keys(params ?? {})) {
      out[key] = String(params[key] ?? '');
    }
    return out;
  }
}
