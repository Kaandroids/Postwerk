import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import {
  NotificationItem,
  NotificationListResponse,
  NotificationPreference,
  UnreadCountResponse,
} from '../../models/notification.model';

/**
 * Holds the current user's notification inbox state (signals) and talks to `/api/v1/notifications`.
 * The DB is the source of truth; this loads on demand + polls the unread count. See
 * doc/NOTIFICATION_SYSTEM_DESIGN.md.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);

  readonly items = signal<NotificationItem[]>([]);
  readonly unreadCount = signal(0);
  readonly loading = signal(false);

  /** Loads a page of notifications (and refreshes the unread count from the same response). */
  async load(unreadOnly = false): Promise<void> {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.api.get<NotificationListResponse>(`/notifications?unread=${unreadOnly}&page=0&size=30`),
      );
      this.items.set(res.items);
      this.unreadCount.set(res.unreadCount);
    } catch {
      /* leave current state; next poll/open reconciles */
    } finally {
      this.loading.set(false);
    }
  }

  /** Cheap badge refresh (polled). */
  async refreshUnreadCount(): Promise<void> {
    try {
      const res = await firstValueFrom(this.api.get<UnreadCountResponse>('/notifications/unread-count'));
      this.unreadCount.set(res.count);
    } catch {
      /* ignore transient errors */
    }
  }

  async markRead(id: string): Promise<void> {
    const item = this.items().find(n => n.id === id);
    if (item && !item.read) this.unreadCount.update(c => Math.max(0, c - 1));
    this.items.update(list => list.map(n => (n.id === id ? { ...n, read: true } : n)));
    try {
      await firstValueFrom(this.api.patch(`/notifications/${id}/read`, {}));
    } catch {
      /* optimistic; next load reconciles */
    }
  }

  async markAllRead(): Promise<void> {
    this.items.update(list => list.map(n => ({ ...n, read: true })));
    this.unreadCount.set(0);
    try {
      await firstValueFrom(this.api.post('/notifications/read-all', {}));
    } catch {
      /* optimistic */
    }
  }

  getPreferences(): Promise<NotificationPreference[]> {
    return firstValueFrom(this.api.get<NotificationPreference[]>('/notifications/preferences'));
  }

  updatePreferences(prefs: NotificationPreference[]): Promise<NotificationPreference[]> {
    return firstValueFrom(this.api.put<NotificationPreference[]>('/notifications/preferences', prefs));
  }
}
