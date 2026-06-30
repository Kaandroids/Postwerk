import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { DocsSearchComponent } from './docs-search.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { DocsService } from '../../docs.service';

interface Entry { slug: string; title: string; section: string; node?: string }
interface Access {
  active(): number;
  flat(): Entry[];
  groups(): { section: string; items: Entry[] }[];
  isNode(e: Entry): boolean;
  iconFor(e: Entry): string;
  colorFor(e: Entry): string;
  onInput(v: string): void;
  onKey(e: KeyboardEvent): void;
  choose(e: Entry): void;
  globalIndex(g: { items: Entry[] }, i: number): number;
}

const INDEX: Entry[] = [
  { slug: 'nodes/filter', title: 'Filter', section: 'Nodes', node: 'FILTER' },
  { slug: 'guide/extract', title: 'Extract data', section: 'Guides' },
  { slug: 'misc/filtering-tips', title: 'Tips', section: 'Misc' },
];

const key = (k: string) => ({ key: k, preventDefault: vi.fn() } as unknown as KeyboardEvent);

describe('DocsSearchComponent', () => {
  let router: { navigate: ReturnType<typeof vi.fn> };
  let acc: Access;
  let closedCalled: boolean;

  beforeEach(() => {
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      imports: [DocsSearchComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: DocsService, useValue: { buildIndex: () => INDEX } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(DocsSearchComponent);
    closedCalled = false;
    fixture.componentInstance.closed.subscribe(() => { closedCalled = true; });
    acc = fixture.componentInstance as unknown as Access;
  });

  it('flat ranks matches by score (title-prefix > slug-substring)', () => {
    acc.onInput('filter');
    const flat = acc.flat();
    expect(flat.map(e => e.title)).toEqual(['Filter', 'Tips']); // 'Extract data' scores 0
  });

  it('groups the results by section', () => {
    acc.onInput('filter');
    const groups = acc.groups();
    expect(groups.map(g => g.section)).toEqual(['Nodes', 'Misc']);
  });

  it('isNode / iconFor / colorFor distinguish node entries', () => {
    expect(acc.isNode(INDEX[0])).toBe(true);
    expect(acc.isNode(INDEX[1])).toBe(false);
    expect(acc.iconFor(INDEX[1])).toBe('book');
    expect(acc.colorFor(INDEX[1])).toBe('var(--fg-subtle)');
    expect(typeof acc.iconFor(INDEX[0])).toBe('string');
  });

  it('onInput resets the active row to 0', () => {
    acc.onInput('filter');
    expect(acc.active()).toBe(0);
  });

  it('onKey moves the active row and clamps at the bounds', () => {
    acc.onInput('filter'); // 2 results
    acc.onKey(key('ArrowDown'));
    expect(acc.active()).toBe(1);
    acc.onKey(key('ArrowDown')); // clamp at last
    expect(acc.active()).toBe(1);
    acc.onKey(key('ArrowUp'));
    expect(acc.active()).toBe(0);
  });

  it('Enter chooses the active entry and navigates', () => {
    acc.onInput('filter');
    acc.onKey(key('Enter'));
    expect(router.navigate).toHaveBeenCalledWith(['/docs', 'nodes', 'filter']);
  });

  it('Escape closes the palette', () => {
    acc.onKey(key('Escape'));
    expect(closedCalled).toBe(true);
  });

  it('choose splits the slug into route segments', () => {
    acc.choose({ slug: 'guide/extract' } as Entry);
    expect(router.navigate).toHaveBeenCalledWith(['/docs', 'guide', 'extract']);
  });

  it('globalIndex maps a grouped item back to its flat position', () => {
    acc.onInput('filter');
    const groups = acc.groups();
    expect(acc.globalIndex(groups[0], 0)).toBe(0);
    expect(acc.globalIndex(groups[1], 0)).toBe(1);
  });
});
