import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { I18nService } from '../../../core/services/i18n.service';
import { AutomationService } from '../../../core/services/automation.service';
import { ParameterSetService } from '../../../core/services/parameter-set.service';
import { IconComponent } from '../icon/icon.component';
import { ParameterItem } from '../../../models/parameter-set.model';
import { humanizeError } from '../../utils/error.util';

/**
 * "Run now" overlay for an automation's MANUAL trigger. Self-contained: given only the automation id
 * + name it fetches the saved flow, finds the MANUAL trigger's parameter set and renders a type-aware
 * value form. On submit the entered values are POSTed to {@code /run} where they become {@code trigger.*}
 * variables. When the trigger has no parameter set it degrades to a simple "Run now?" confirmation.
 * Reused by both the automation editor toolbar and the automations list page.
 */
@Component({
  selector: 'app-manual-run-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './manual-run-dialog.component.html',
  styleUrl: './manual-run-dialog.component.scss',
})
export class ManualRunDialogComponent implements OnInit {
  protected i18n = inject(I18nService);
  private automationService = inject(AutomationService);
  private parameterSetService = inject(ParameterSetService);

  automationId = input.required<string>();
  automationName = input.required<string>();

  close = output<void>();
  /** Emitted once the run has been accepted by the backend (async execution started). */
  ran = output<void>();

  // ── State ───────────────────────────────────────────────────────────────────
  loading = signal(true);
  loadError = signal('');
  params = signal<ParameterItem[]>([]);
  /** Raw string-keyed form values (checkboxes stored as 'true'/'false'). */
  values = signal<Record<string, string>>({});
  attempted = signal(false);
  running = signal(false);
  runError = signal('');
  done = signal(false);

  ngOnInit(): void {
    this.load(this.automationId());
  }

  private load(id: string): void {
    this.loading.set(true);
    this.automationService.get(id).subscribe({
      next: (detail) => {
        const trigger = detail.nodes.find((n) => n.nodeType === 'TRIGGER');
        let paramSetId: string | undefined;
        try {
          const cfg = JSON.parse(trigger?.config || '{}');
          if (cfg.triggerMode === 'MANUAL') paramSetId = cfg.parameterSetId || undefined;
        } catch { /* ignore malformed config → no params */ }

        if (!paramSetId) {
          this.params.set([]);
          this.loading.set(false);
          return;
        }
        this.parameterSetService.get(paramSetId).subscribe({
          next: (ps) => {
            const fields = ps.parameters || [];
            this.params.set(fields);
            this.values.set(Object.fromEntries(fields.map((p) => [p.name, p.type === 'BOOLEAN' ? 'false' : ''])));
            this.loading.set(false);
          },
          // Parameter set deleted since save → fall back to a no-parameter run.
          error: () => { this.params.set([]); this.loading.set(false); },
        });
      },
      error: () => { this.loadError.set(this.i18n.t('auto_run_load_error')); this.loading.set(false); },
    });
  }

  setValue(name: string, value: string): void {
    this.values.update((v) => ({ ...v, [name]: value }));
  }

  onInput(name: string, event: Event): void {
    this.setValue(name, (event.target as HTMLInputElement | HTMLTextAreaElement).value);
  }

  onCheckbox(name: string, event: Event): void {
    this.setValue(name, (event.target as HTMLInputElement).checked ? 'true' : 'false');
  }

  /** Required scalar fields that are still empty (BOOLEAN is never "missing"). */
  private missingRequired(): ParameterItem[] {
    return this.params().filter((p) => p.required && p.type !== 'BOOLEAN' && !(this.values()[p.name] || '').trim());
  }

  isMissing(p: ParameterItem): boolean {
    return p.required && p.type !== 'BOOLEAN' && !(this.values()[p.name] || '').trim();
  }

  /** Builds the {@code trigger.*} payload, coercing each value to its declared type. */
  private buildPayload(): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const p of this.params()) {
      const raw = this.values()[p.name] ?? '';
      if (p.isList) {
        out[p.name] = raw.split('\n').map((s) => s.trim()).filter((s) => s.length > 0);
      } else if (p.type === 'BOOLEAN') {
        out[p.name] = raw === 'true';
      } else if (p.type === 'NUMBER') {
        out[p.name] = raw.trim() === '' ? null : Number(raw);
      } else if (p.type === 'OBJECT') {
        try { out[p.name] = raw.trim() === '' ? null : JSON.parse(raw); } catch { out[p.name] = raw; }
      } else {
        out[p.name] = raw;
      }
    }
    return out;
  }

  onScrim(): void {
    if (!this.running()) this.close.emit();
  }

  run(): void {
    if (this.running()) return;
    this.attempted.set(true);
    if (this.missingRequired().length > 0) return;

    this.running.set(true);
    this.runError.set('');
    const payload = this.params().length ? this.buildPayload() : undefined;
    this.automationService.runManually(this.automationId(), payload).subscribe({
      next: () => {
        this.running.set(false);
        this.done.set(true);
        this.ran.emit();
      },
      error: (err) => {
        this.running.set(false);
        this.runError.set(humanizeError(err, this.i18n.t('auto_run_failed')));
      },
    });
  }
}
