import { ChangeDetectionStrategy, Component, effect, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { AiChatPanelComponent } from '../ai-chat-panel/ai-chat-panel.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { QuotaBannerComponent } from '../../../../shared/components/quota-banner/quota-banner.component';
import { ToastContainerComponent } from '../../../../shared/components/toast-container/toast-container.component';
import { LayoutService } from '../../../../core/services/layout.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { UserService } from '../../../../core/services/user.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { ViewportService } from '../../../../core/services/viewport.service';

/** Shell layout for the dashboard area, composing sidebar, topbar, AI chat panel, and routed content. */
@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent, AiChatPanelComponent, ConfirmDialogComponent, QuotaBannerComponent, ToastContainerComponent],
  template: `
    <div class="dash" [attr.data-sidebar]="layout.sidebarOpen() ? '1' : '0'">
      <div class="dash-scrim" (click)="layout.sidebarOpen.set(false)"></div>
      <app-sidebar />
      <div class="dash-main">
        <app-topbar />
        <app-quota-banner />
        <router-outlet />
      </div>
    </div>
    @defer (when chatService.isOpen()) {
      <app-ai-chat-panel />
    }
    <app-confirm-dialog />
    <app-toast-container />
  `,
  styleUrl: './dashboard-layout.component.scss',
})
export class DashboardLayoutComponent implements OnInit {
  protected layout = inject(LayoutService);
  protected chatService = inject(AiChatService);
  private workspace = inject(WorkspaceService);
  private userService = inject(UserService);
  private viewport = inject(ViewportService);
  private router = inject(Router);

  constructor() {
    // The sidebar is a persistent rail on desktop but an overlay drawer below lg.
    // Mirror the viewport so it defaults open on desktop and closed on smaller
    // screens, and resets sensibly whenever the layout crosses the breakpoint.
    // (Within a mode, the user's own toggle is preserved — this only reacts when
    // isDesktop() actually flips.)
    effect(() => {
      this.layout.sidebarOpen.set(this.viewport.isDesktop());
    });

    // On small screens the drawer overlays content, so close it after navigating.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (!this.viewport.isDesktop()) this.layout.sidebarOpen.set(false);
      });
  }

  ngOnInit(): void {
    this.workspace.loadAccounts();
    this.userService.loadProfile();
  }
}
