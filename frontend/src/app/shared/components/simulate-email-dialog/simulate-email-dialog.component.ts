import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../core/services/i18n.service';
import { AutomationService } from '../../../core/services/automation.service';
import { IconComponent } from '../icon/icon.component';
import { Automation, TestModeResult } from '../../../models/automation.model';
import { humanizeError } from '../../utils/error.util';

/**
 * "Simulate this email" overlay. Given a synced email, the user picks one of their email-triggered
 * automations; the email is run through it in dry-run (no side effects) and the actions it WOULD take
 * are shown. The run is also recorded as a test-mode result, so the user can later rate it (and the
 * automation's accuracy statistics improve). Launched from the email detail pane.
 */
@Component({
  selector: 'app-simulate-email-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink],
  templateUrl: './simulate-email-dialog.component.html',
  styleUrl: './simulate-email-dialog.component.scss',
})
export class SimulateEmailDialogComponent implements OnInit {
  protected i18n = inject(I18nService);
  private automationService = inject(AutomationService);

  readonly emailId = input.required<string>();
  readonly emailSubject = input<string>('');

  readonly close = output<void>();

  loading = signal(true);
  loadError = signal('');
  automations = signal<Automation[]>([]);

  selected = signal<Automation | null>(null);
  running = signal(false);
  runError = signal('');
  result = signal<TestModeResult | null>(null);

  ngOnInit(): void {
    this.automationService.list().subscribe({
      next: (list) => {
        // Only email-triggered automations make sense to simulate against an email.
        this.automations.set(list.filter((a) => a.kind !== 'INTEGRATION' && a.type === 'EMAIL'));
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(this.i18n.t('sim_load_error'));
        this.loading.set(false);
      },
    });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return this.i18n.t('sim_status_active');
      case 'TESTING': return this.i18n.t('sim_status_testing');
      default: return this.i18n.t('sim_status_paused');
    }
  }

  pick(a: Automation): void {
    if (this.running()) return;
    this.selected.set(a);
    this.running.set(true);
    this.runError.set('');
    this.result.set(null);
    this.automationService.simulateEmail(a.id, this.emailId()).subscribe({
      next: (res) => {
        this.result.set(res);
        this.running.set(false);
      },
      error: (err) => {
        this.runError.set(humanizeError(err, this.i18n.t('sim_failed')));
        this.running.set(false);
        this.selected.set(null);
      },
    });
  }

  /** Back to the automation list to try a different one. */
  reset(): void {
    this.selected.set(null);
    this.result.set(null);
    this.runError.set('');
  }

  onScrim(): void {
    if (!this.running()) this.close.emit();
  }
}
