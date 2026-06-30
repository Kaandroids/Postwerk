import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NotificationService } from './notification.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('NotificationService', () => {
  let api: MockApi;
  let service: NotificationService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(NotificationService);
  });

  it('load() fills items + unread count and clears loading', async () => {
    const items = [{ id: 'n1', read: false }] as never;
    api.get.mockReturnValue(of({ items, unreadCount: 1 }));
    await service.load();
    expect(api.get).toHaveBeenCalledWith('/notifications?unread=false&page=0&size=30');
    expect(service.items()).toBe(items);
    expect(service.unreadCount()).toBe(1);
    expect(service.loading()).toBe(false);
  });

  it('load(true) requests the unread-only page', async () => {
    api.get.mockReturnValue(of({ items: [], unreadCount: 0 }));
    await service.load(true);
    expect(api.get).toHaveBeenCalledWith('/notifications?unread=true&page=0&size=30');
  });

  it('refreshUnreadCount() updates only the badge count', async () => {
    api.get.mockReturnValue(of({ count: 7 }));
    await service.refreshUnreadCount();
    expect(api.get).toHaveBeenCalledWith('/notifications/unread-count');
    expect(service.unreadCount()).toBe(7);
  });

  it('markRead() optimistically flips the item and decrements the unread count', async () => {
    service.items.set([{ id: 'n1', read: false }] as never);
    service.unreadCount.set(1);
    await service.markRead('n1');
    expect(service.items()[0].read).toBe(true);
    expect(service.unreadCount()).toBe(0);
    expect(api.patch).toHaveBeenCalledWith('/notifications/n1/read', {});
  });

  it('markRead() does not decrement below zero for an already-read item', async () => {
    service.items.set([{ id: 'n1', read: true }] as never);
    service.unreadCount.set(0);
    await service.markRead('n1');
    expect(service.unreadCount()).toBe(0);
  });

  it('markAllRead() flips every item and zeroes the count', async () => {
    service.items.set([{ id: 'n1', read: false }, { id: 'n2', read: false }] as never);
    service.unreadCount.set(2);
    await service.markAllRead();
    expect(service.items().every(n => n.read)).toBe(true);
    expect(service.unreadCount()).toBe(0);
    expect(api.post).toHaveBeenCalledWith('/notifications/read-all', {});
  });

  it('getPreferences()/updatePreferences() resolve via the preferences endpoint', async () => {
    const prefs = [{ type: 'X', email: true }] as never;
    api.get.mockReturnValue(of(prefs));
    await expect(service.getPreferences()).resolves.toBe(prefs);
    expect(api.get).toHaveBeenCalledWith('/notifications/preferences');

    api.put.mockReturnValue(of(prefs));
    await expect(service.updatePreferences(prefs)).resolves.toBe(prefs);
    expect(api.put).toHaveBeenCalledWith('/notifications/preferences', prefs);
  });
});
