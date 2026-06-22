import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { I18nService } from '../../../../core/services/i18n.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { Bi } from '../../../../shared/data/node-docs.model';
import { DocsService } from '../../docs.service';
import { DOC_AUDIENCES, DOC_CATEGORIES, DOC_POPULAR, DocAudience, DocCategory } from '../../docs.model';

@Component({
  selector: 'app-docs-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './docs-home.component.html',
  styleUrl: './docs-home.component.scss',
})
export class DocsHomeComponent {
  protected i18n = inject(I18nService);
  protected docs = inject(DocsService);
  private router = inject(Router);

  protected readonly audiences = DOC_AUDIENCES;
  protected readonly popular = DOC_POPULAR;
  protected readonly aud = signal<DocAudience | null>(null);

  protected readonly categories = computed<DocCategory[]>(() => {
    const a = this.aud();
    return a ? DOC_CATEGORIES.filter(c => c.audience.includes(a)) : DOC_CATEGORIES;
  });

  constructor() {
    const title = inject(Title);
    const meta = inject(Meta);
    title.setTitle(`${this.i18n.t('brandName')} — ${this.i18n.t('doc_home_title')}`);
    meta.updateTag({ name: 'description', content: this.i18n.t('doc_home_lead') });
  }

  protected tr(bi: Bi): string {
    return bi[this.i18n.lang()];
  }

  protected go(slug: string): void {
    this.router.navigate(['/docs', ...slug.split('/')]);
  }

  protected openSearch(): void {
    this.docs.searchOpen.set(true);
  }
}
