import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef,
  afterNextRender, computed, inject, signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';
import {
  ChatSegment, LP2_CHAT_AI1, LP2_CHAT_AI2, LP2_CHAT_NODES, LP2_CHAT_USER,
} from '../../data/chat-demo';
import { pickLang } from '../../data/landing.util';

/**
 * AI chat demo: types the user's prompt, then builds + test-runs a 4-node
 * pipeline through a 16-stage timeline and loops. Driven entirely by signals.
 */
@Component({
  selector: 'app-landing-chat-demo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RevealDirective],
  template: `
    <section class="lp2-section" id="chat" [style.padding-top]="'0'">
      <div class="lp2-wrap">
        <div class="lp2-section-head">
          <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_chat_eyebrow') }}</span>
          <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_chat_title') }}</h2>
          <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_chat_sub') }}</p>
        </div>
        <div class="lp2-chatdemo" appReveal [style.--rv-d]="'0.15s'">
          <div class="lp2-chat">
            <div class="lp2-chat-head">
              <span class="dot"></span>
              {{ pick(chatHead) }}
            </div>
            <div class="lp2-msg user" [attr.data-on]="stage() >= 1 ? '1' : '0'">
              {{ typedText() }}
              @if (stage() === 1) { <span class="lp2-cursor" [style.background]="'var(--bg)'"></span> }
            </div>
            @if (stage() === 2) {
              <div class="lp2-typing-dots">
                <i [style.--i]="0"></i><i [style.--i]="1"></i><i [style.--i]="2"></i>
              </div>
            }
            <div class="lp2-msg ai" [attr.data-on]="stage() >= 3 ? '1' : '0'">
              {{ ai1()[0] }}<b>{{ ai1()[1] }}</b>{{ ai1()[2] }}
            </div>
            <div class="lp2-msg ai" [attr.data-on]="stage() >= 13 ? '1' : '0'">
              {{ ai2()[0] }}<b>{{ ai2()[1] }}</b>{{ ai2()[2] }}
            </div>
          </div>
          <div class="lp2-pipe">
            <div class="lp2-pipe-head">
              {{ i18n.t('p2_chat_pipe') }}
              <span class="lp2-pipe-status" [attr.data-tone]="statusTone()">
                @if (statusTone() === 'busy') { <span class="lp2-mini-spin"></span> }
                {{ statusText() }}
              </span>
            </div>
            @if (stage() < 4) {
              <div class="lp2-pipe-empty">
                @for (line of emptyLines(); track $index) {
                  <span [style.display]="'block'">{{ line }}</span>
                }
              </div>
            } @else {
              @for (n of nodes; track $index) {
                @if ($index > 0) {
                  <div class="lp2-pipe-link" [attr.data-on]="stage() >= 4 + $index ? '1' : '0'" [attr.data-test]="stage() === 8 + $index ? '1' : '0'">
                    <span class="tok"></span>
                  </div>
                }
                <div class="lp2-node"
                     [attr.data-on]="stage() >= 4 + $index ? '1' : '0'"
                     [attr.data-state]="stage() === 4 + $index ? 'build' : 'done'"
                     [attr.data-ok]="stage() >= 8 + $index ? '1' : '0'">
                  <span class="nic">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path [attr.d]="n.icon"/></svg>
                  </span>
                  <span>
                    <span class="nt" [style.display]="'block'">{{ pick(n.t) }}</span>
                    <span class="nd" [style.display]="'block'">{{ pick(n.d) }}</span>
                  </span>
                  <span class="nok">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>
                  </span>
                </div>
              }
              <div class="lp2-pipe-result" [attr.data-on]="stage() >= 12 ? '1' : '0'">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>
                {{ i18n.t('p2_chat_result') }}
              </div>
              <span class="lp2-activate" [attr.data-on]="stage() >= 14 ? '1' : '0'" [attr.data-live]="stage() >= 15 ? '1' : '0'">
                @if (stage() >= 15) { <span class="ldot"></span> } @else { <span class="lp2-mini-spin"></span> }
                {{ stage() >= 15 ? i18n.t('p2_chat_live') : i18n.t('p2_chat_activate') }}
              </span>
            }
            <button class="lp2-replay" (click)="replay()">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 4v6h6M23 20v-6h-6M20.5 9A9 9 0 005.6 5.6L1 10m22 4l-4.6 4.4A9 9 0 013.5 15"/></svg>
              {{ i18n.t('p2_chat_replay') }}
            </button>
          </div>
        </div>
        <div class="lp2-cta-row" appReveal [style.--rv-d]="'0.2s'" [style.margin-top.px]="30" [style.align-items]="'center'">
          <a class="lp2-cta-primary" routerLink="/auth/register">
            {{ i18n.t('p2_chat_cta') }}
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
          </a>
          <span [style.font-size.px]="12.5" [style.color]="'var(--fg-subtle)'">{{ i18n.t('p2_chat_cta_hint') }}</span>
        </div>
      </div>
    </section>
  `,
})
export class LandingChatDemoComponent {
  protected i18n = inject(I18nService);
  private host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private destroyRef = inject(DestroyRef);

  protected nodes = LP2_CHAT_NODES;
  protected chatHead = { de: 'KI-Assistent · neuer Workflow', en: 'AI assistant · new workflow' };

  protected stage = signal(0);
  protected chars = signal(0);
  private run = signal(0);

  private timers: ReturnType<typeof setTimeout>[] = [];
  private typeIv: ReturnType<typeof setInterval> | null = null;
  private alive = true;
  private started = false;

  protected userText = computed(() => pickLang(LP2_CHAT_USER, this.i18n.lang()));
  protected typedText = computed(() => this.userText().slice(0, this.chars()));
  protected ai1 = computed(() => this.seg(LP2_CHAT_AI1));
  protected ai2 = computed(() => this.seg(LP2_CHAT_AI2));
  protected emptyLines = computed(() => {
    const v = { de: 'Hier entsteht die Automation,\nsobald die KI baut.', en: 'The automation appears here\nas soon as the AI builds.' };
    return pickLang(v, this.i18n.lang()).split('\n');
  });

  protected statusTone = computed(() => {
    const s = this.stage();
    if (s >= 4 && s <= 11) return 'busy';
    if (s >= 12) return 'ok';
    return 'idle';
  });
  protected statusText = computed(() => {
    const s = this.stage();
    if (s >= 4 && s <= 7) return this.i18n.t('p2_chat_st_build').replace('{n}', String(s - 3));
    if (s >= 8 && s <= 11) return this.i18n.t('p2_chat_st_test');
    if (s >= 12 && s < 15) return '✓ ' + this.i18n.t('p2_chat_st_done');
    if (s >= 15) return '● ' + this.i18n.t('p2_chat_st_live');
    return this.i18n.t('p2_chat_st_ready');
  });

  protected pick = (v: { de: string; en: string }) => pickLang(v, this.i18n.lang());

  constructor() {
    afterNextRender(() => this.observe());
    this.destroyRef.onDestroy(() => { this.alive = false; this.clearTimers(); });
  }

  protected replay(): void {
    this.run.update((r) => r + 1);
    this.sequence();
  }

  private seg(s: ChatSegment): [string, string, string] {
    return this.i18n.lang() === 'de' ? s.de : s.en;
  }

  private observe(): void {
    const start = () => {
      if (this.started) return;
      this.started = true;
      this.sequence();
      cleanup();
    };
    const check = () => {
      const vh = window.innerHeight || document.documentElement.clientHeight;
      if (this.host.getBoundingClientRect().top < vh * 0.88) start();
    };
    let io: IntersectionObserver | null = null;
    if (typeof IntersectionObserver !== 'undefined') {
      io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) start(); }, { threshold: 0.15 });
      io.observe(this.host);
    }
    window.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);
    const cleanup = () => {
      io?.disconnect();
      window.removeEventListener('scroll', check);
      window.removeEventListener('resize', check);
    };
    this.destroyRef.onDestroy(cleanup);
    check();
  }

  private clearTimers(): void {
    this.timers.forEach(clearTimeout);
    this.timers = [];
    if (this.typeIv) { clearInterval(this.typeIv); this.typeIv = null; }
  }

  private sequence(): void {
    this.clearTimers();
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const f = reduced ? 0.05 : 1;
    const at = (ms: number, fn: () => void) => {
      this.timers.push(setTimeout(() => { if (this.alive) fn(); }, ms * f));
    };
    this.stage.set(0);
    this.chars.set(0);

    const text = this.userText();
    const typeMs = text.length * 22;
    at(300, () => { this.stage.set(1); this.typewriter(reduced); });
    const base = 300 + typeMs + 400;
    at(base, () => this.stage.set(2));
    at(base + 1200, () => this.stage.set(3));
    const b0 = base + 2100;
    for (let i = 0; i < 4; i++) at(b0 + i * 650, () => this.stage.set(4 + i));
    const t0 = b0 + 4 * 650 + 450;
    for (let i = 0; i < 4; i++) at(t0 + i * 560, () => this.stage.set(8 + i));
    const res = t0 + 4 * 560 + 300;
    at(res, () => this.stage.set(12));
    at(res + 700, () => this.stage.set(13));
    at(res + 1500, () => this.stage.set(14));
    at(res + 2700, () => this.stage.set(15));
    at(res + 8000, () => { this.run.update((r) => r + 1); this.sequence(); });
  }

  private typewriter(reduced: boolean): void {
    const text = this.userText();
    if (reduced) { this.chars.set(text.length); return; }
    this.typeIv = setInterval(() => {
      this.chars.update((c) => {
        if (c >= text.length) { if (this.typeIv) clearInterval(this.typeIv); return c; }
        return c + 1;
      });
    }, 22);
  }
}
