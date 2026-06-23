import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';
import { LandingNavComponent } from '../landing-nav/landing-nav.component';
import { LandingHeroComponent } from '../landing-hero/landing-hero.component';
import { LandingTickerComponent } from '../landing-ticker/landing-ticker.component';
import { LandingPillarsComponent } from '../landing-pillars/landing-pillars.component';
import { LandingChatDemoComponent } from '../landing-chat-demo/landing-chat-demo.component';
import { LandingHowComponent } from '../landing-how/landing-how.component';
import { LandingMarketComponent } from '../landing-market/landing-market.component';
import { LandingSupervisedComponent } from '../landing-supervised/landing-supervised.component';
import { LandingStudioComponent } from '../landing-studio/landing-studio.component';
import { LandingCtaComponent } from '../landing-cta/landing-cta.component';
import { LandingFooterComponent } from '../landing-footer/landing-footer.component';

/** Root page component composing all landing v2 sections into a single scrollable view. */
@Component({
  selector: 'app-landing-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LandingNavComponent,
    LandingHeroComponent,
    LandingTickerComponent,
    LandingPillarsComponent,
    LandingChatDemoComponent,
    LandingHowComponent,
    LandingMarketComponent,
    LandingSupervisedComponent,
    LandingStudioComponent,
    LandingCtaComponent,
    LandingFooterComponent,
  ],
  template: `
    <div class="lp2">
      <app-landing-nav />
      <app-landing-hero />
      <app-landing-ticker />
      <app-landing-pillars />
      <app-landing-chat-demo />
      <app-landing-how />
      <app-landing-market />
      <app-landing-supervised />
      <app-landing-studio />
      <app-landing-cta />
      <app-landing-footer />
    </div>
  `,
  styleUrls: [
    './landing-page.component.scss',
    './landing-v2-sections.scss',
    './landing-v2-showcase.scss',
  ],
  encapsulation: ViewEncapsulation.None,
})
export class LandingPageComponent {}
