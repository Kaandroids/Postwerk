import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { WizardService } from '../../services/wizard.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { LandingNavComponent } from '../../../landing/components/landing-nav/landing-nav.component';
import { WizardChatComponent } from '../wizard-chat/wizard-chat.component';
import { WizardHowCardComponent } from '../wizard-how-card/wizard-how-card.component';
import { WizardFlowCanvasComponent } from '../wizard-flow-canvas/wizard-flow-canvas.component';

/**
 * Public onboarding wizard page. Hosts the landing nav + chat during the
 * chatting phase, then swaps to the interactive Foblex Flow canvas
 * ({@code WizardFlowCanvasComponent}) plus how-card / summary / CTA once the
 * automation is being built. Backed by {@code WizardService}.
 */
@Component({
  selector: 'app-wizard-flow-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent,
    LandingNavComponent,
    WizardChatComponent,
    WizardFlowCanvasComponent,
    WizardHowCardComponent,
  ],
  templateUrl: './wizard-flow-page.component.html',
  styleUrl: './wizard-flow-page.component.scss',
})
export class WizardFlowPageComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly wizard = inject(WizardService);
  private readonly router = inject(Router);

  readonly phase = computed(() => this.wizard.phase());
  readonly isChatting = computed(() => this.phase() === 'chatting');
  readonly isBuilding = computed(() => this.phase() === 'building');
  readonly isReady = computed(() => this.phase() === 'ready');

  readonly summaryText = computed(() => {
    const nodes = this.wizard.automationPlan()?.nodes?.length ?? 0;
    const edges = this.wizard.automationPlan()?.edges?.length ?? 0;
    if (this.i18n.lang() === 'de') {
      return `${nodes} Bausteine · ${edges} Verbindungen · 100 % automatisiert`;
    }
    return `${nodes} blocks · ${edges} connections · 100% automated`;
  });

  onRegister(): void {
    const sessionId = this.wizard.sessionId();
    if (sessionId) {
      sessionStorage.setItem('wizard_session_id', sessionId);
    }
    this.router.navigate(['/auth/register'], { queryParams: { from: 'wizard' } });
  }

  onContinueChat(): void {
    this.onRegister();
  }
}
