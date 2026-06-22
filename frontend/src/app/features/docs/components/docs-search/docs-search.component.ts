import {
  ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject,
  input, output, signal, viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { getNodeColor, getNodeIcon } from '../../../../models/automation.model';
import { DocsService, SearchEntry } from '../../docs.service';
import { DOC_POPULAR } from '../../docs.model';

interface ResultGroup {
  section: string;
  items: SearchEntry[];
}

@Component({
  selector: 'app-docs-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './docs-search.component.html',
  styleUrl: './docs-search.component.scss',
})
export class DocsSearchComponent {
  protected i18n = inject(I18nService);
  private docs = inject(DocsService);
  private router = inject(Router);

  readonly open = input(false);
  readonly closed = output<void>();

  private readonly inputEl = viewChild<ElementRef<HTMLInputElement>>('inputEl');

  protected readonly q = signal('');
  protected readonly active = signal(0);

  private readonly index = computed(() => this.docs.buildIndex());

  protected readonly flat = computed<SearchEntry[]>(() => {
    const query = this.q().trim().toLowerCase();
    if (!query) {
      // Empty query → suggested links from the popular list.
      const idx = this.index();
      return DOC_POPULAR
        .map(p => idx.find(e => e.slug === p.to))
        .filter((e): e is SearchEntry => !!e);
    }
    const scored = this.index()
      .map(e => ({ e, s: this.score(e, query) }))
      .filter(x => x.s > 0)
      .sort((a, b) => b.s - a.s)
      .slice(0, 24);
    return scored.map(x => x.e);
  });

  protected readonly groups = computed<ResultGroup[]>(() => {
    const out: ResultGroup[] = [];
    for (const item of this.flat()) {
      let g = out.find(x => x.section === item.section);
      if (!g) { g = { section: item.section, items: [] }; out.push(g); }
      g.items.push(item);
    }
    return out;
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.q.set('');
        this.active.set(0);
        queueMicrotask(() => this.inputEl()?.nativeElement.focus());
      }
    });
    // Keep the active index in range as results change.
    effect(() => {
      const n = this.flat().length;
      if (this.active() >= n) this.active.set(Math.max(0, n - 1));
    });
  }

  private score(e: SearchEntry, q: string): number {
    const t = e.title.toLowerCase();
    if (t.startsWith(q)) return 100;
    if (t.includes(q)) return 60;
    if (e.slug.includes(q)) return 30;
    if (e.section.toLowerCase().includes(q)) return 15;
    return 0;
  }

  protected isNode(e: SearchEntry): boolean {
    return !!e.node;
  }

  protected iconFor(e: SearchEntry): string {
    return e.node ? getNodeIcon(e.node) : 'book';
  }

  protected colorFor(e: SearchEntry): string {
    return e.node ? getNodeColor(e.node) : 'var(--fg-subtle)';
  }

  protected onInput(value: string): void {
    this.q.set(value);
    this.active.set(0);
  }

  protected onKey(e: KeyboardEvent): void {
    const items = this.flat();
    if (e.key === 'Escape') { this.close(); return; }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      this.active.update(i => Math.min(items.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      this.active.update(i => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const sel = items[this.active()];
      if (sel) this.choose(sel);
    }
  }

  protected choose(e: SearchEntry): void {
    this.router.navigate(['/docs', ...e.slug.split('/')]);
    this.close();
  }

  protected close(): void {
    this.closed.emit();
  }

  protected globalIndex(group: ResultGroup, i: number): number {
    // Flat index of a grouped item, for active-row highlighting.
    const flat = this.flat();
    return flat.indexOf(group.items[i]);
  }
}
