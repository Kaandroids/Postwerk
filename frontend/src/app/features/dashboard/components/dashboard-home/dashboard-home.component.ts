import { ChangeDetectionStrategy, Component, inject, computed, signal, effect } from '@angular/core';
import { Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { PageContentComponent } from '../page-content/page-content.component';

/** Dashboard home page displaying welcome state, quick actions, and recent email summary. */
@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, DecimalPipe],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss',
})
export class DashboardHomeComponent {
  protected i18n = inject(I18nService);
  protected workspace = inject(WorkspaceService);
  private router = inject(Router);
  private emailService = inject(EmailService);

  hasAccount = computed(() => this.workspace.accounts().length > 0);

  // Real data from active account
  received = signal(0);
  replied = signal(0);
  forwarded = signal(0);
  review = signal(0);

  constructor() {
    effect(() => {
      const acct = this.workspace.activeAccount();
      if (acct) {
        this.emailService.list(acct.id, { page: 0, size: 1 }).subscribe({
          next: (page) => this.received.set(page.totalElements),
          error: () => this.received.set(0),
        });
      } else {
        this.received.set(0);
      }
    });
  }

  monthLabel = computed(() => {
    const lang = this.i18n.lang();
    return new Date().toLocaleDateString(lang === 'de' ? 'de-DE' : 'en-US', { month: 'long', year: 'numeric' });
  });

  automationPct = computed(() => {
    const r = this.received();
    if (r === 0) return 0;
    return Math.round(((this.replied() + this.forwarded()) / r) * 100);
  });

  repliedPct = computed(() => {
    const r = this.received();
    return r > 0 ? Math.round((this.replied() / r) * 100) : 0;
  });

  forwardedPct = computed(() => {
    const r = this.received();
    return r > 0 ? Math.round((this.forwarded() / r) * 100) : 0;
  });

  goToAddAccount(): void {
    this.router.navigate(['/dashboard/email-accounts']);
  }

  goToInbox(): void {
    this.router.navigate(['/dashboard/emails']);
  }
}
