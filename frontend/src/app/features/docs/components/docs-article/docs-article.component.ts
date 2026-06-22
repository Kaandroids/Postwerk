import {
  ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect,
  inject, signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title, Meta } from '@angular/platform-browser';
import { I18nService } from '../../../../core/services/i18n.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { NodeReferenceComponent } from '../../../../shared/components/node-reference/node-reference.component';
import {
  NodeType, getNodeColor, getNodeIcon, getNodeLabelKey,
} from '../../../../models/automation.model';
import { NODE_DOCS, NODE_DOC_ORDER } from '../../../../shared/data/node-docs.data';
import { DocsService, RenderedArticle } from '../../docs.service';
import { DOC_AUDIENCES, DocArticleRef } from '../../docs.model';

@Component({
  selector: 'app-docs-article',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, NodeReferenceComponent],
  templateUrl: './docs-article.component.html',
  styleUrl: './docs-article.component.scss',
})
export class DocsArticleComponent {
  protected i18n = inject(I18nService);
  protected docs = inject(DocsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);
  private title = inject(Title);
  private meta = inject(Meta);

  private readonly pm = toSignal(this.route.paramMap);
  protected readonly slug = computed(() => {
    const p = this.pm();
    return p ? `${p.get('section')}/${p.get('topic')}` : '';
  });

  protected readonly ref = computed<DocArticleRef | undefined>(() => this.docs.resolveRef(this.slug()));
  protected readonly isOverview = computed(() => this.slug() === 'nodes/overview');
  protected readonly nodeType = computed<NodeType | null>(() => this.ref()?.node ?? null);
  protected readonly section = computed(() => this.docs.sectionOf(this.slug()));
  protected readonly prevNext = computed(() => this.docs.prevNext(this.slug()));

  protected readonly article = signal<RenderedArticle | null>(null);
  protected readonly toc = computed(() => this.article()?.toc ?? []);
  protected readonly activeHeading = signal<string>('');
  protected readonly feedback = signal<'none' | 'yes' | 'no'>('none');

  protected readonly nodeGrid = NODE_DOC_ORDER;

  constructor() {
    // Load + render markdown when the slug or language changes (skip node pages).
    effect((onCleanup) => {
      const s = this.slug();
      this.i18n.lang(); // re-render on language switch
      this.feedback.set('none');
      this.activeHeading.set('');
      if (!s || this.nodeType() || this.isOverview()) {
        this.article.set(null);
        this.setMeta();
        return;
      }
      const sub = this.docs.loadArticle(s).subscribe(a => {
        this.article.set(a);
        this.setMeta();
        if (a.toc.length) this.activeHeading.set(a.toc[0].id);
      });
      onCleanup(() => sub.unsubscribe());
    });

    // Node pages: set page metadata too.
    effect(() => {
      if (this.nodeType() || this.isOverview()) this.setMeta();
    });
  }

  private setMeta(): void {
    const t = this.ref() ? this.docs.title(this.ref()!) : this.i18n.t('doc_tag');
    this.title.setTitle(`${t} — ${this.i18n.t('brandName')} ${this.i18n.t('doc_tag')}`);
    const sec = this.section()?.title[this.i18n.lang()] ?? '';
    this.meta.updateTag({ name: 'description', content: `${t} · ${sec} · ${this.i18n.t('brandName')}` });
  }

  // ── Node helpers ──────────────────────────────────────────────────────
  protected nodeTitle(t: NodeType): string { return this.i18n.t(getNodeLabelKey(t)); }
  protected nodeColor(t: NodeType): string { return getNodeColor(t); }
  protected nodeIcon(t: NodeType): string { return getNodeIcon(t); }
  protected nodeSummary(t: NodeType): string { return NODE_DOCS[t]?.summary[this.i18n.lang()] ?? ''; }

  protected audienceLabel(): string {
    const a = this.ref()?.audience?.[0];
    return a ? DOC_AUDIENCES.find(x => x.id === a)?.label[this.i18n.lang()] ?? '' : '';
  }

  protected goNode(t: NodeType): void {
    this.router.navigate(['/docs', 'nodes', t.toLowerCase()]);
  }

  protected refTitle(r: DocArticleRef): string { return this.docs.title(r); }
  protected link(slug: string): string[] { return ['/docs', ...slug.split('/')]; }

  /** Intercept internal /docs links inside rendered markdown and route via the SPA. */
  protected onContentClick(e: MouseEvent): void {
    const a = (e.target as HTMLElement).closest('a');
    if (!a) return;
    const href = a.getAttribute('href') ?? '';
    if (href.startsWith('/docs')) {
      e.preventDefault();
      this.router.navigateByUrl(href);
    }
  }

  protected scrollTo(id: string, e: Event): void {
    e.preventDefault();
    const el = this.host.nativeElement.querySelector(`#${CSS.escape(id)}`) as HTMLElement | null;
    if (el) {
      window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 78, behavior: 'smooth' });
      this.activeHeading.set(id);
    }
  }

  @HostListener('window:scroll')
  onScroll(): void {
    const headings = this.toc();
    if (!headings.length) return;
    let current = headings[0].id;
    for (const h of headings) {
      const el = this.host.nativeElement.querySelector(`#${CSS.escape(h.id)}`) as HTMLElement | null;
      if (el && el.getBoundingClientRect().top < 140) current = h.id;
    }
    if (current !== this.activeHeading()) this.activeHeading.set(current);
  }
}
