import { ChangeDetectionStrategy, Component, input, computed, inject } from '@angular/core';
import { I18nService } from '../../../core/services/i18n.service';
import { IconComponent } from '../icon/icon.component';

interface PasswordRules {
  length: boolean;
  upper: boolean;
  number: boolean;
  symbol: boolean;
}

/** Visual password strength indicator that scores length, uppercase, number, and symbol rules. */
@Component({
  selector: 'app-strength-meter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (password()) {
      <div class="strength">
        <div class="strength-bar">
          @for (i of segments; track i) {
            <div class="strength-seg" [style.background]="i <= score() ? colors()[score()] : 'var(--border)'"></div>
          }
        </div>
        <div class="strength-label">
          <span>{{ labels()[score()] }}</span>
        </div>
        <div class="strength-rules">
          @for (rule of ruleList(); track rule.key) {
            <div class="rule" [attr.data-met]="rule.met ? '1' : '0'">
              <span class="rule-dot"><app-icon name="check" /></span>
              <span>{{ rule.label }}</span>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .strength { margin-top: 4px; display: flex; flex-direction: column; gap: 8px; }
    .strength-bar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
    .strength-seg { height: 4px; border-radius: 2px; background: var(--border); transition: background 0.2s; }
    .strength-label { display: flex; justify-content: space-between; font-size: 12px; color: var(--fg-muted); }
    .strength-rules { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; margin-top: 2px; }
    .rule { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--fg-subtle); }
    .rule[data-met="1"] { color: var(--success); }
    .rule-dot {
      width: 12px; height: 12px; border-radius: 50%;
      border: 0.5px solid var(--border-strong);
      display: grid; place-items: center; flex-shrink: 0;
    }
    .rule[data-met="1"] .rule-dot { background: var(--success); border-color: var(--success); }
    .rule[data-met="1"] .rule-dot ::ng-deep svg { opacity: 1; color: white; }
    .rule-dot ::ng-deep svg { opacity: 0; }
  `,
})
export class StrengthMeterComponent {
  private i18n = inject(I18nService);
  password = input('');
  segments = [1, 2, 3, 4];

  private rules = computed<PasswordRules>(() => {
    const pw = this.password();
    return {
      length: pw.length >= 8,
      upper: /[A-Z]/.test(pw),
      number: /\d/.test(pw),
      symbol: /[^A-Za-z0-9]/.test(pw),
    };
  });

  score = computed(() => {
    const r = this.rules();
    return [r.length, r.upper, r.number, r.symbol].filter(Boolean).length;
  });

  labels = computed(() => [
    this.i18n.t('pw_weak'),
    this.i18n.t('pw_weak'),
    this.i18n.t('pw_fair'),
    this.i18n.t('pw_good'),
    this.i18n.t('pw_strong'),
  ]);

  colors = computed(() => [
    'var(--border)',
    'var(--danger)',
    'var(--warning)',
    'var(--accent)',
    'var(--success)',
  ]);

  ruleList = computed(() => [
    { key: 'length', met: this.rules().length, label: this.i18n.t('pw_rule_length') },
    { key: 'upper', met: this.rules().upper, label: this.i18n.t('pw_rule_upper') },
    { key: 'number', met: this.rules().number, label: this.i18n.t('pw_rule_number') },
    { key: 'symbol', met: this.rules().symbol, label: this.i18n.t('pw_rule_symbol') },
  ]);
}
