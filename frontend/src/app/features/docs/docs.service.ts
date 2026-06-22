import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Observable, of } from 'rxjs';
import { map, catchError, shareReplay } from 'rxjs/operators';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { I18nService } from '../../core/services/i18n.service';
import {
  getNodeLabelKey,
  NodeType,
} from '../../models/automation.model';
import {
  DOCS_NAV,
  DOC_ORDER,
  DocArticleRef,
  DocSection,
} from './docs.model';

export interface TocEntry {
  id: string;
  text: string;
  level: 2 | 3;
}

export interface RenderedArticle {
  html: SafeHtml;
  toc: TocEntry[];
}

export interface SearchEntry {
  slug: string;
  title: string;
  section: string;
  node?: NodeType;
}

const slugify = (s: string): string =>
  s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

const ALERT_RE = /^\[!(info|tip|warning|danger)\]/i;

@Injectable({ providedIn: 'root' })
export class DocsService {
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private i18n = inject(I18nService);

  private cache = new Map<string, Observable<RenderedArticle>>();

  /** Command-palette open state, shared by the shell and the home page. */
  readonly searchOpen = signal(false);

  /** Loads + renders the markdown article for `slug` in the active language. */
  loadArticle(slug: string): Observable<RenderedArticle> {
    const lang = this.i18n.lang();
    const key = `${lang}:${slug}`;
    let cached = this.cache.get(key);
    if (!cached) {
      cached = this.http
        .get(`/docs-content/${lang}/${slug}.md`, { responseType: 'text' })
        .pipe(
          map(md => this.render(md)),
          catchError(() => of(this.renderMissing())),
          shareReplay(1),
        );
      this.cache.set(key, cached);
    }
    return cached;
  }

  private render(md: string): RenderedArticle {
    const rawHtml = marked.parse(md, { async: false }) as string;
    const root = document.createElement('div');
    root.innerHTML = rawHtml;

    // Auto-anchor h2/h3 and collect the on-this-page table of contents.
    const toc: TocEntry[] = [];
    root.querySelectorAll('h2, h3').forEach(h => {
      const level = h.tagName === 'H2' ? 2 : 3;
      const text = (h.textContent ?? '').trim();
      const id = h.id || slugify(text);
      h.id = id;
      toc.push({ id, text, level: level as 2 | 3 });
    });

    // GitHub-style alert blockquotes -> styled callouts.
    root.querySelectorAll('blockquote').forEach(bq => {
      const first = bq.querySelector('p');
      const lead = (first?.textContent ?? '').trimStart();
      const m = lead.match(ALERT_RE);
      if (!m) return;
      bq.classList.add('md-callout');
      bq.setAttribute('data-v', m[1].toLowerCase());
      // Strip the "[!TYPE]" marker (and a following line break) from the body.
      if (first) {
        first.innerHTML = first.innerHTML.replace(/^\s*\[![a-z]+\]\s*(<br\s*\/?>)?/i, '');
        if (!first.textContent?.trim()) first.remove();
      }
    });

    // External links open in a new tab; internal /docs links are handled by the component.
    root.querySelectorAll('a[href]').forEach(a => {
      const href = a.getAttribute('href') ?? '';
      if (/^https?:\/\//i.test(href)) {
        a.setAttribute('target', '_blank');
        a.setAttribute('rel', 'noopener noreferrer');
      }
    });

    const clean = DOMPurify.sanitize(root.innerHTML, { ADD_ATTR: ['target'] });
    return { html: this.sanitizer.bypassSecurityTrustHtml(clean), toc };
  }

  private renderMissing(): RenderedArticle {
    const clean = DOMPurify.sanitize(`<p>${this.i18n.t('doc_missing')}</p>`);
    return { html: this.sanitizer.bypassSecurityTrustHtml(clean), toc: [] };
  }

  // ── Manifest helpers ──────────────────────────────────────────────────

  get nav(): DocSection[] {
    return DOCS_NAV;
  }

  /** Human title for a ref in the active language (node pages use the palette label). */
  title(ref: DocArticleRef): string {
    if (ref.node) return this.i18n.t(getNodeLabelKey(ref.node));
    return ref.title ? ref.title[this.i18n.lang()] : ref.slug;
  }

  resolveRef(slug: string): DocArticleRef | undefined {
    for (const s of DOCS_NAV) {
      const found = s.items.find(i => i.slug === slug);
      if (found) return found;
    }
    return undefined;
  }

  sectionOf(slug: string): DocSection | undefined {
    return DOCS_NAV.find(s => s.items.some(i => i.slug === slug));
  }

  prevNext(slug: string): { prev?: DocArticleRef; next?: DocArticleRef } {
    const idx = DOC_ORDER.indexOf(slug);
    if (idx < 0) return {};
    return {
      prev: idx > 0 ? this.resolveRef(DOC_ORDER[idx - 1]) : undefined,
      next: idx < DOC_ORDER.length - 1 ? this.resolveRef(DOC_ORDER[idx + 1]) : undefined,
    };
  }

  /** Flat search index for the command palette, titles resolved for the active language. */
  buildIndex(): SearchEntry[] {
    const out: SearchEntry[] = [];
    for (const s of DOCS_NAV) {
      const section = s.title[this.i18n.lang()];
      for (const i of s.items) {
        out.push({ slug: i.slug, title: this.title(i), section, node: i.node });
      }
    }
    return out;
  }
}
