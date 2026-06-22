import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18nService } from '../../core/services/i18n.service';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { EmailAccountService } from '../../core/services/email-account.service';
import { EmailAccount } from '../../models/email-account.model';
import { ConstantType, CONSTANT_TYPE_META } from '../../models/automation.model';
import { MarketplaceAcquisition, PublishableConstant } from '../../models/marketplace.model';
import { MarketGlyphComponent } from './components/market-glyph.component';
import { PageContentComponent } from '../dashboard/components/page-content/page-content.component';

/** Configure surface for a buyer-owned PRIVATE install: value-only constants + account binding. */
@Component({
  selector: 'app-marketplace-configure',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarketGlyphComponent, PageContentComponent],
  template: `
    <app-page-content>
    <div class="mk-page mk-config" data-testid="marketplace-configure">
      <button class="mk-back" (click)="back()" data-testid="mk-back">
        <app-icon name="arrowLeft" /> {{ i18n.t('mkt_detail_back') }}
      </button>
      <div class="mk-eyebrow">{{ i18n.t('mkt_config_eyebrow') }}</div>
      <h1 class="mk-h1">{{ i18n.t('mkt_config_title') }}</h1>
      <p class="mk-lede">{{ i18n.t('mkt_config_subtitle') }}</p>

      @if (loading()) {
        <div class="mk-loading">{{ i18n.t('mkt_loading') }}</div>
      } @else if (acq(); as a) {
        <div class="mk-config-banner" style="margin-top:20px">
          <app-market-glyph [icon]="a.listing.icon" [color]="a.listing.color" [size]="42" />
          <div>
            <div class="nm">{{ a.listing.name }}</div>
            <div class="by">{{ i18n.t('mkt_by') }} {{ a.listing.author.name }}</div>
          </div>
          <span class="run" [class.paused]="a.installedStatus !== 'ACTIVE'">
            <span class="dot"></span>
            {{ a.installedStatus === 'ACTIVE' ? i18n.t('mkt_lib_status_active') : i18n.t('mkt_lib_status_paused') }}
          </span>
        </div>

        <div class="mk-locked" style="margin-top:18px">
          <span class="ic"><app-icon name="lock" /></span>
          <div class="t">{{ i18n.t('mkt_config_locked_title') }}</div>
          <div class="d">{{ i18n.t('mkt_config_locked_text') }}</div>
        </div>

        @if (constants().length) {
          <div class="mk-d-sec">
            <h3>{{ i18n.t('mkt_config_constants_title') }}</h3>
            <div class="mk-cv" data-testid="mk-cv">
              @for (c of constants(); track c.name) {
                <div class="mk-cv-row">
                  <span class="mk-cv-badge" [style.--ct]="typeColor(c.type)">
                    <app-icon [name]="typeIcon(c.type)" />
                  </span>
                  <div class="mk-cv-main">
                    <div class="mk-cv-top">
                      <span class="mk-cv-key">{{ c.name }}</span>
                      <span class="mk-cv-type">{{ c.type || 'text' }}</span>
                    </div>
                    @if (c.description) { <div class="mk-cv-desc">{{ c.description }}</div> }
                    <div class="mk-cv-field">
                      @if (c.type === 'boolean') {
                        <div class="mk-cv-toggle">
                          <button [class.on]="valueOf(c.name) === 'true'" (click)="setValue(c.name, 'true')"
                                  [attr.data-testid]="'mk-cv-true-' + c.name">true</button>
                          <button [class.on]="valueOf(c.name) !== 'true'" (click)="setValue(c.name, 'false')"
                                  [attr.data-testid]="'mk-cv-false-' + c.name">false</button>
                        </div>
                      } @else if (c.type === 'secret') {
                        <div class="mk-cv-input-wrap">
                          <input class="mk-cv-input mono" [type]="revealed(c.name) ? 'text' : 'password'"
                                 [value]="valueOf(c.name)" (input)="onInput(c.name, $event)"
                                 [attr.data-testid]="'mk-cv-input-' + c.name" />
                          <button class="mk-cv-reveal" (click)="toggleReveal(c.name)" type="button"
                                  [attr.data-testid]="'mk-cv-reveal-' + c.name">
                            <app-icon [name]="revealed(c.name) ? 'eyeOff' : 'eye'" />
                          </button>
                        </div>
                      } @else {
                        <input class="mk-cv-input" [type]="c.type === 'number' ? 'number' : 'text'"
                               [value]="valueOf(c.name)" (input)="onInput(c.name, $event)"
                               [attr.data-testid]="'mk-cv-input-' + c.name" />
                      }
                    </div>
                  </div>
                </div>
              }
            </div>
          </div>
        }

        <div class="mk-d-sec">
          <h3>{{ i18n.t('mkt_config_accounts_title') }}</h3>
          <p class="mk-lede" style="margin:0 0 12px">{{ i18n.t('mkt_config_accounts_hint') }}</p>
          @if (accounts().length) {
            <div class="mk-accounts" data-testid="mk-accounts">
              @for (acc of accounts(); track acc.id) {
                <div class="mk-acc" [class.on]="isBound(acc.id)" (click)="toggleAccount(acc.id)"
                     [attr.data-testid]="'mk-acc-' + acc.id">
                  <span class="box">@if (isBound(acc.id)) { <app-icon name="check" /> }</span>
                  <div>
                    <div class="em">{{ acc.email }}</div>
                    <div class="sub">{{ acc.displayName }}</div>
                  </div>
                </div>
              }
            </div>
          } @else {
            <div class="mk-d-body">{{ i18n.t('mkt_config_no_accounts') }}</div>
          }
        </div>

        <div class="mk-config-foot">
          @if (saved()) { <span class="mk-toast"><app-icon name="checkCircle" /> {{ i18n.t('mkt_config_saved') }}</span> }
          <span class="sp"></span>
          <button class="mk-cta ghost" (click)="save()" [disabled]="saving()" data-testid="mk-save">
            {{ saving() ? i18n.t('mkt_config_saving') : i18n.t('mkt_config_save') }}
          </button>
          <button class="mk-cta accent" (click)="activate()" [disabled]="saving() || !boundIds().length"
                  data-testid="mk-activate">
            <app-icon name="play" /> {{ i18n.t('mkt_config_activate') }}
          </button>
        </div>
      } @else {
        <div class="mk-error-box">{{ i18n.t('mkt_error') }}</div>
      }
    </div>
    </app-page-content>
  `,
})
export class MarketplaceConfigureComponent {
  protected i18n = inject(I18nService);
  private market = inject(MarketplaceService);
  private accountSvc = inject(EmailAccountService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private acqId = this.route.snapshot.paramMap.get('id') ?? '';
  acq = signal<MarketplaceAcquisition | null>(null);
  constants = signal<PublishableConstant[]>([]);
  accounts = signal<EmailAccount[]>([]);
  values = signal<Record<string, string>>({});
  boundIds = signal<string[]>([]);
  revealedKeys = signal<Set<string>>(new Set());
  loading = signal(true);
  saving = signal(false);
  saved = signal(false);

  typeIcon = (t: ConstantType | undefined) => CONSTANT_TYPE_META[t ?? 'text'].icon;
  typeColor = (t: ConstantType | undefined) => CONSTANT_TYPE_META[t ?? 'text'].color;

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    forkJoin({ lib: this.market.library(), accounts: this.accountSvc.list() }).subscribe({
      next: ({ lib, accounts }) => {
        this.accounts.set(accounts);
        const found = [...lib.installed, ...lib.purchased].find((x) => x.id === this.acqId);
        if (!found) {
          this.loading.set(false);
          return;
        }
        this.acq.set(found);
        this.market.getDetail(found.listingId).subscribe({
          next: (d) => {
            this.constants.set(d.publishableConstants);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  valueOf = (name: string) => this.values()[name] ?? '';
  setValue(name: string, v: string): void {
    this.values.set({ ...this.values(), [name]: v });
  }
  onInput(name: string, e: Event): void {
    this.setValue(name, (e.target as HTMLInputElement).value);
  }

  revealed = (name: string) => this.revealedKeys().has(name);
  toggleReveal(name: string): void {
    const s = new Set(this.revealedKeys());
    s.has(name) ? s.delete(name) : s.add(name);
    this.revealedKeys.set(s);
  }

  isBound = (id: string) => this.boundIds().includes(id);
  toggleAccount(id: string): void {
    const cur = this.boundIds();
    this.boundIds.set(cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]);
  }

  private constantInputs() {
    return this.constants().map((c) => ({
      name: c.name,
      value: this.valueOf(c.name),
      type: (c.type ?? 'text') as ConstantType,
    }));
  }

  /** Persists constants + account bindings together (shared by save & activate). */
  private persist() {
    return forkJoin([
      this.market.saveConstants(this.acqId, this.constantInputs()),
      this.market.bindAccounts(this.acqId, this.boundIds()),
    ]);
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.saved.set(false);
    this.persist().subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: () => this.saving.set(false),
    });
  }

  activate(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.saved.set(false);
    this.persist().subscribe({
      next: () => {
        this.market.activate(this.acqId).subscribe({
          next: () => {
            this.saving.set(false);
            this.router.navigate(['/dashboard/marketplace-library']);
          },
          error: () => this.saving.set(false),
        });
      },
      error: () => this.saving.set(false),
    });
  }

  back(): void {
    this.router.navigate(['/dashboard/marketplace-library']);
  }
}
