import { Injectable, signal } from '@angular/core';

/**
 * Controls the global sidebar open/close state via a reactive signal.
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  sidebarOpen = signal(true);

  toggleSidebar(): void {
    this.sidebarOpen.update(v => !v);
  }
}
