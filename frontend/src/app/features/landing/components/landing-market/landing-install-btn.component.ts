import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';

/** Faux install button: idle → busy → done, with a fill bar. Preview-only. */
@Component({
  selector: 'app-landing-install-btn',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button class="lp2-install" [attr.data-state]="state()" (click)="click()">
      @if (state() === 'done') {
        ✓ {{ i18n.t('p2_mkt_installed') }}
      } @else if (state() === 'busy') {
        …
      } @else {
        {{ i18n.t('p2_mkt_install') }}
      }
      <span class="bar"></span>
    </button>
  `,
})
export class LandingInstallBtnComponent {
  protected i18n = inject(I18nService);
  protected state = signal<'idle' | 'busy' | 'done'>('idle');

  protected click(): void {
    if (this.state() !== 'idle') return;
    this.state.set('busy');
    setTimeout(() => this.state.set('done'), 1150);
  }
}
