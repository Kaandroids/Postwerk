import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminMarketplaceService } from '../../../../core/services/admin-marketplace.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  Listing,
  Review,
  ListingDetail,
  MarketplaceKpis,
  ListingModStatus,
  ListingFilters,
  ReviewFilters,
} from '../../../../models/admin-marketplace.model';

type Segment = 'listings' | 'reviews';
type ListingTab = 'overview' | 'reviews';

/**
 * Platform-staff Marketplace Moderation: one page with a Listings | Reviews segment toggle. Shared
 * KPI strip; per-segment toolbar + table + detail modal; gated moderation actions (MARKETPLACE_MODERATE).
 */
@Component({
  selector: 'app-admin-marketplace',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-marketplace.component.html',
  styleUrl: './admin-marketplace.component.scss',
})
export class AdminMarketplaceComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminMarketplaceService);
  private confirmDialog = inject(ConfirmDialogService);
  private router = inject(Router);
  private timers = managedTimers();

  readonly stars = [1, 2, 3, 4, 5];
  segment = signal<Segment>('listings');
  kpis = signal<MarketplaceKpis | null>(null);
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);
  busy = signal(false);
  error = signal('');

  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Listings segment ────────────────────────────────────────────────────────
  lRows = signal<Listing[]>([]);
  lTotal = signal(0);
  lTotalPages = signal(0);
  lPage = signal(0);
  lLoading = signal(true);
  lSearch = signal('');
  lStatus = signal<'' | ListingModStatus>('');
  lKind = signal<'' | 'AUTOMATION' | 'INTEGRATION'>('');
  lPricing = signal<'' | 'FREE' | 'PAID'>('');
  lSort = signal<'' | 'newest' | 'installs' | 'rating'>('newest');

  // ── Reviews segment ─────────────────────────────────────────────────────────
  rRows = signal<Review[]>([]);
  rTotal = signal(0);
  rTotalPages = signal(0);
  rPage = signal(0);
  rLoading = signal(true);
  rSearch = signal('');
  rRating = signal<number | ''>('');
  rStatus = signal<'' | 'VISIBLE' | 'HIDDEN'>('');
  rSort = signal<'' | 'newest' | 'lowest'>('newest');

  // ── Detail modals ────────────────────────────────────────────────────────────
  detailListing = signal<ListingDetail | null>(null);
  detailListingLoading = signal(false);
  listingTab = signal<ListingTab>('overview');
  detailReview = signal<Review | null>(null);

  readonly pageSize = 10;
  readonly canModerate = computed(() => this.identity.has('MARKETPLACE_MODERATE'));
  readonly pendingTotal = computed(() => (this.kpis()?.pendingListings ?? 0) + (this.kpis()?.pendingReviews ?? 0));
  readonly lHasFilters = computed(() => !!this.lSearch() || !!this.lStatus() || !!this.lKind() || !!this.lPricing());
  readonly rHasFilters = computed(() => !!this.rSearch() || this.rRating() !== '' || !!this.rStatus());

  ngOnInit() {
    this.segment.set(this.router.url.includes('reviews') ? 'reviews' : 'listings');
    this.loadKpis();
    this.stampUpdated();
    if (this.segment() === 'reviews') this.loadReviews(); else this.loadListings();
  }

  // ── Loading ──────────────────────────────────────────────────────────────────
  private listingFilters(): ListingFilters {
    return { search: this.lSearch() || undefined, status: this.lStatus() || undefined, kind: this.lKind() || undefined, pricing: this.lPricing() || undefined, sort: this.lSort() || undefined };
  }
  loadListings() {
    this.lLoading.set(true);
    this.error.set('');
    this.service.listings(this.listingFilters(), this.lPage(), this.pageSize).subscribe({
      next: p => { this.lRows.set(p.content); this.lTotal.set(p.totalElements); this.lTotalPages.set(p.totalPages); this.lLoading.set(false); },
      error: () => { this.lRows.set([]); this.lLoading.set(false); this.error.set(this.i18n.t('mod_load_failed')); },
    });
  }
  private reviewFilters(): ReviewFilters {
    return { search: this.rSearch() || undefined, rating: this.rRating() === '' ? undefined : Number(this.rRating()), status: this.rStatus() || undefined, sort: this.rSort() || undefined };
  }
  loadReviews() {
    this.rLoading.set(true);
    this.error.set('');
    this.service.reviews(this.reviewFilters(), this.rPage(), this.pageSize).subscribe({
      next: p => { this.rRows.set(p.content); this.rTotal.set(p.totalElements); this.rTotalPages.set(p.totalPages); this.rLoading.set(false); },
      error: () => { this.rRows.set([]); this.rLoading.set(false); this.error.set(this.i18n.t('mod_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }

  private stampUpdated() {
    const d = new Date(); const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }
  private loadActive() { if (this.segment() === 'reviews') this.loadReviews(); else this.loadListings(); }
  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.loadKpis(); this.loadActive(); this.stampUpdated();
    this.timers.set(() => this.refreshing.set(false), 600);
  }
  setSegment(s: Segment) {
    if (this.segment() === s) return;
    this.segment.set(s);
    // Only fetch when the target segment hasn't been loaded yet — switching back shouldn't refetch.
    if (s === 'reviews' ? this.rRows().length === 0 : this.lRows().length === 0) this.loadActive();
  }

  // ── Listing filters ──────────────────────────────────────────────────────────
  private lSearchTimer: ReturnType<typeof setTimeout> | null = null;
  onLSearch(e: Event) { this.lSearch.set((e.target as HTMLInputElement).value); if (this.lSearchTimer) this.timers.clear(this.lSearchTimer); this.lSearchTimer = this.timers.set(() => { this.lPage.set(0); this.loadListings(); }, 400); }
  onLStatus(e: Event) { this.lStatus.set((e.target as HTMLSelectElement).value as '' | ListingModStatus); this.lPage.set(0); this.loadListings(); }
  onLKind(e: Event) { this.lKind.set((e.target as HTMLSelectElement).value as '' | 'AUTOMATION' | 'INTEGRATION'); this.lPage.set(0); this.loadListings(); }
  onLPricing(e: Event) { this.lPricing.set((e.target as HTMLSelectElement).value as '' | 'FREE' | 'PAID'); this.lPage.set(0); this.loadListings(); }
  onLSort(e: Event) { this.lSort.set((e.target as HTMLSelectElement).value as '' | 'newest' | 'installs' | 'rating'); this.lPage.set(0); this.loadListings(); }
  lClearFilters() { this.lSearch.set(''); this.lStatus.set(''); this.lKind.set(''); this.lPricing.set(''); this.lPage.set(0); this.loadListings(); }
  lGoToPage(p: number) { if (p < 0 || p >= this.lTotalPages()) return; this.lPage.set(p); this.loadListings(); }

  // ── Review filters ───────────────────────────────────────────────────────────
  private rSearchTimer: ReturnType<typeof setTimeout> | null = null;
  onRSearch(e: Event) { this.rSearch.set((e.target as HTMLInputElement).value); if (this.rSearchTimer) this.timers.clear(this.rSearchTimer); this.rSearchTimer = this.timers.set(() => { this.rPage.set(0); this.loadReviews(); }, 400); }
  onRRating(e: Event) { const v = (e.target as HTMLSelectElement).value; this.rRating.set(v === '' ? '' : Number(v)); this.rPage.set(0); this.loadReviews(); }
  onRStatus(e: Event) { this.rStatus.set((e.target as HTMLSelectElement).value as '' | 'VISIBLE' | 'HIDDEN'); this.rPage.set(0); this.loadReviews(); }
  onRSort(e: Event) { this.rSort.set((e.target as HTMLSelectElement).value as '' | 'newest' | 'lowest'); this.rPage.set(0); this.loadReviews(); }
  rClearFilters() { this.rSearch.set(''); this.rRating.set(''); this.rStatus.set(''); this.rPage.set(0); this.loadReviews(); }
  rGoToPage(p: number) { if (p < 0 || p >= this.rTotalPages()) return; this.rPage.set(p); this.loadReviews(); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  initial(name: string): string { return (name?.trim()?.[0] ?? '?').toUpperCase(); }
  avatarHue(seed: string): number { const s = seed ?? ''; let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  statusTone(s: ListingModStatus): string { return s === 'TAKEN_DOWN' ? 'danger' : s === 'PAUSED' ? 'amber' : s === 'PRIVATE' ? 'slate' : 'green'; }
  statusLabel(s: ListingModStatus): string {
    return this.i18n.t(s === 'TAKEN_DOWN' ? 'mod_status_takendown' : s === 'PAUSED' ? 'mod_status_paused' : s === 'PRIVATE' ? 'mod_status_private' : 'mod_status_public');
  }
  kindTone(kind: string): string { return (kind ?? '').toUpperCase() === 'INTEGRATION' ? 'slate' : 'violet'; }
  priceLabel(l: { pricingModel: string; price: number }): string {
    if ((l.pricingModel ?? 'FREE').toUpperCase() === 'FREE' || !l.price) return this.i18n.t('mod_free');
    return '€' + l.price.toFixed(2);
  }
  ratingText(avg: number, count: number): string { return (avg ?? 0).toFixed(1) + ' (' + (count ?? 0) + ')'; }

  // ── Detail modals ────────────────────────────────────────────────────────────
  openListing(l: Listing) {
    this.listingTab.set('overview');
    this.detailListingLoading.set(true);
    this.detailReview.set(null);
    this.detailListing.set({ listing: l, description: null, reviews: [] });
    this.service.getListing(l.id).subscribe({
      next: d => { this.detailListing.set(d); this.detailListingLoading.set(false); },
      error: () => { this.detailListingLoading.set(false); },
    });
  }
  openReview(r: Review) { this.detailReview.set(r); this.detailListing.set(null); }
  closeDetail() { this.detailListing.set(null); this.detailReview.set(null); }
  setListingTab(t: ListingTab) { this.listingTab.set(t); }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.detailListing() || this.detailReview()) this.closeDetail(); }

  @HostListener('document:click')
  onDocClick() { this.openMenuId.set(null); }
  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewport() { this.openMenuId.set(null); }

  // ── Row menu ──────────────────────────────────────────────────────────────────
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);
  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 184) });
    this.openMenuId.set(id);
  }

  // ── Actions (MARKETPLACE_MODERATE) ──────────────────────────────────────────
  private flashMsg(m: string) { this.flash.set(m); if (this.flashTimer) this.timers.clear(this.flashTimer); this.flashTimer = this.timers.set(() => this.flash.set(''), 3200); }
  private applyListing(u: Listing) {
    this.lRows.update(list => list.map(x => x.id === u.id ? u : x));
    if (this.detailListing()?.listing.id === u.id) this.detailListing.update(d => d ? { ...d, listing: u } : d);
    this.loadKpis();
  }
  private applyReview(u: Review) {
    this.rRows.update(list => list.map(x => x.id === u.id ? u : x));
    if (this.detailReview()?.id === u.id) this.detailReview.set(u);
    if (this.detailListing()) this.detailListing.update(d => d ? { ...d, reviews: d.reviews.map(x => x.id === u.id ? u : x) } : d);
    this.loadKpis();
  }

  takeDown(l: Listing, event?: Event) {
    event?.stopPropagation();
    if (!this.canModerate() || this.busy()) return;
    const reason = typeof window !== 'undefined' ? window.prompt(this.i18n.t('mod_takedown_reason')) : '';
    if (reason === null) return; // cancelled
    this.busy.set(true);
    this.service.takeDown(l.id, reason && reason.trim() ? reason.trim() : null).subscribe({
      next: u => { this.busy.set(false); this.applyListing(u); this.flashMsg(this.i18n.t('mod_flash_takendown', { name: l.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('mod_action_failed')); },
    });
  }
  async restore(l: Listing, event?: Event) {
    event?.stopPropagation();
    if (!this.canModerate()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('mod_restore_title'), message: this.i18n.t('mod_restore_msg', { name: l.name }), confirmText: this.i18n.t('mod_restore'), cancelText: this.i18n.t('confirm_cancel'), tone: 'accent' });
    if (!ok) return;
    this.busy.set(true);
    this.service.restore(l.id).subscribe({
      next: u => { this.busy.set(false); this.applyListing(u); this.flashMsg(this.i18n.t('mod_flash_restored', { name: l.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('mod_action_failed')); },
    });
  }
  setFeatured(l: Listing, featured: boolean, event?: Event) {
    event?.stopPropagation();
    if (!this.canModerate() || this.busy()) return;
    this.busy.set(true);
    (featured ? this.service.feature(l.id) : this.service.unfeature(l.id)).subscribe({
      next: u => { this.busy.set(false); this.applyListing(u); this.flashMsg(this.i18n.t(featured ? 'mod_flash_featured' : 'mod_flash_unfeatured', { name: l.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('mod_action_failed')); },
    });
  }

  setReviewHidden(r: Review, hidden: boolean, event?: Event) {
    event?.stopPropagation();
    if (!this.canModerate() || this.busy()) return;
    this.busy.set(true);
    (hidden ? this.service.hideReview(r.id) : this.service.unhideReview(r.id)).subscribe({
      next: u => { this.busy.set(false); this.applyReview(u); this.flashMsg(this.i18n.t(hidden ? 'mod_flash_hidden' : 'mod_flash_unhidden')); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('mod_action_failed')); },
    });
  }
  deleteReview(r: Review, event?: Event) {
    event?.stopPropagation();
    if (!this.canModerate() || this.busy()) return;
    const reason = typeof window !== 'undefined' ? window.prompt(this.i18n.t('mod_delete_reason')) : '';
    if (reason === null) return;
    this.busy.set(true);
    this.service.deleteReview(r.id, reason && reason.trim() ? reason.trim() : null).subscribe({
      next: () => {
        this.busy.set(false);
        this.rRows.update(list => list.filter(x => x.id !== r.id));
        if (this.detailReview()?.id === r.id) this.closeDetail();
        if (this.detailListing()) this.detailListing.update(d => d ? { ...d, reviews: d.reviews.filter(x => x.id !== r.id) } : d);
        this.loadKpis();
        this.flashMsg(this.i18n.t('mod_flash_deleted'));
      },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('mod_action_failed')); },
    });
  }
}
