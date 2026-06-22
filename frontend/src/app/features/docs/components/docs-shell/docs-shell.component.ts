import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { I18nService } from '../../../../core/services/i18n.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { DocsHeaderComponent } from '../docs-header/docs-header.component';
import { getNodeColor, NodeType } from '../../../../models/automation.model';
import { DocsService } from '../../docs.service';
import { DocArticleRef } from '../../docs.model';
import { DocsSearchComponent } from '../docs-search/docs-search.component';

@Component({
  selector: 'app-docs-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, DocsHeaderComponent, DocsSearchComponent],
  templateUrl: './docs-shell.component.html',
  styleUrl: './docs-shell.component.scss',
})
export class DocsShellComponent implements OnDestroy {
  protected i18n = inject(I18nService);
  protected docs = inject(DocsService);
  private router = inject(Router);

  protected readonly nav = this.docs.nav;
  protected readonly searchOpen = this.docs.searchOpen;
  protected readonly drawerOpen = signal(false);
  protected readonly collapsed = signal<Set<string>>(new Set());

  constructor() {
    // Hide native scrollbars while the docs surface is mounted (scrolling still works).
    document.documentElement.classList.add('docs-active');
    // Close the mobile drawer on every navigation.
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => this.drawerOpen.set(false));
  }

  ngOnDestroy(): void {
    document.documentElement.classList.remove('docs-active');
  }

  @HostListener('document:keydown', ['$event'])
  onKey(e: KeyboardEvent): void {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.searchOpen.update(v => !v);
      return;
    }
    const el = e.target as HTMLElement;
    const typing = el && /^(input|textarea|select)$/i.test(el.tagName);
    if (e.key === '/' && !typing && !this.searchOpen()) {
      e.preventDefault();
      this.searchOpen.set(true);
    }
  }

  protected isCollapsed(id: string): boolean {
    return this.collapsed().has(id);
  }

  protected toggleSection(id: string): void {
    this.collapsed.update(set => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  protected link(slug: string): string[] {
    return ['/docs', ...slug.split('/')];
  }

  protected nodeColor(node: NodeType): string {
    return getNodeColor(node);
  }

  protected itemTitle(item: DocArticleRef): string {
    return this.docs.title(item);
  }
}
