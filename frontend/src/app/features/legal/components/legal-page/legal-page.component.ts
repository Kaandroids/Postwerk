import { ChangeDetectionStrategy, Component, inject, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { I18nService } from '../../../../core/services/i18n.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { LandingNavComponent } from '../../../landing/components/landing-nav/landing-nav.component';
import { LandingFooterComponent } from '../../../landing/components/landing-footer/landing-footer.component';

const KIND_MAP: Record<string, { titleKey: string; bodyKey: string }> = {
  datenschutz: { titleKey: 'legal_privacy', bodyKey: 'legal_privacy_body' },
  impressum: { titleKey: 'legal_imprint', bodyKey: 'legal_imprint_body' },
  agb: { titleKey: 'legal_terms', bodyKey: 'legal_terms_body' },
};

/** Renders legal content pages (privacy policy, imprint, terms) based on the route parameter. */
@Component({
  selector: 'app-legal-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, LandingNavComponent, LandingFooterComponent],
  templateUrl: './legal-page.component.html',
  styleUrl: './legal-page.component.scss',
})
export class LegalPageComponent {
  protected i18n = inject(I18nService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private kind = toSignal(this.route.paramMap.pipe(map(p => p.get('kind') ?? 'datenschutz')), { initialValue: 'datenschutz' });

  title = computed(() => {
    const k = KIND_MAP[this.kind()];
    return k ? this.i18n.t(k.titleKey) : '';
  });

  body = computed(() => {
    const k = KIND_MAP[this.kind()];
    return k ? this.i18n.t(k.bodyKey) : '';
  });

  goBack(): void {
    this.router.navigate(['/landing']);
  }
}
