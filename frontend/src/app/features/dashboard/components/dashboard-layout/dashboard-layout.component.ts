import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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

/** Shell layout for the dashboard area, composing sidebar, topbar, AI chat panel, and routed content. */
@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent, AiChatPanelComponent, ConfirmDialogComponent, QuotaBannerComponent, ToastContainerComponent],
  template: `
    <div class="dash" [attr.data-sidebar]="layout.sidebarOpen() ? '1' : '0'">
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

  ngOnInit(): void {
    this.workspace.loadAccounts();
    this.userService.loadProfile();
  }
}
