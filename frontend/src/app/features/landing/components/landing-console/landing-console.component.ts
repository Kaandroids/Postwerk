import {
  ChangeDetectionStrategy, Component, DestroyRef, HostListener,
  afterNextRender, computed, inject, signal,
} from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { LP2_RUNS } from '../../data/console-runs';
import { VIA_KEY, pickLang } from '../../data/landing.util';

type Phase = 'arrive' | 'scan' | 'classify' | 'act' | 'ok' | 'leave';

/** Hero "Autopilot console" — cycles automation runs through a phase state machine. */
@Component({
  selector: 'app-landing-console',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'lp2-console-wrap lp2-fade-up', '[style.--d]': "'0.35s'" },
  template: `
    <div class="lp2-console" [style.--rx]="rx()" [style.--ry]="ry()">
      <div class="lp2-console-head">
        <span class="live"></span>
        <span class="label">{{ i18n.t('p2_console_label') }}</span>
      </div>
      <div class="lp2-incoming">
        @for (k of [idx()]; track k) {
          <div class="lp2-in-card" [attr.data-phase]="phase()" [attr.data-leaving]="phase() === 'leave' ? '1' : '0'">
            <span class="beam"></span>
            <div class="lp2-in-top">
              <div class="lp2-avatar" [style.background]="run().grad">{{ run().init }}</div>
              <div style="min-width:0">
                <div class="lp2-in-from">{{ run().from }}</div>
                <div class="lp2-in-subj">{{ pick(run().subj) }}</div>
              </div>
              <span class="lp2-via" [attr.data-via]="run().via">{{ i18n.t(viaKey(run().via)) }}</span>
            </div>
            <div class="lp2-in-status">
              @if (phase() === 'arrive' || phase() === 'scan') {
                <span class="spin"></span>{{ i18n.t('p2_console_scan') }}
              }
              @if (showChip()) {
                <span class="lp2-chip">{{ pick(run().chip) }}</span>
              }
              @if (showAct()) {
                <span class="act">{{ pick(run().action) }}</span>
              }
              @if (phase() === 'ok' || phase() === 'leave') {
                <span class="ok">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M20 6L9 17l-5-5"/></svg>
                </span>
              }
            </div>
          </div>
        }
      </div>
      <div class="lp2-done">
        <div class="lp2-done-label">{{ i18n.t('p2_console_recent') }}</div>
        @for (di of doneList(); track $index) {
          <div class="lp2-done-row">
            <span class="lp2-via sm" [attr.data-via]="runs[di].via">{{ i18n.t(viaKey(runs[di].via)) }}</span>
            <span class="who">{{ runs[di].from }}</span>
            <span class="what">{{ pick(runs[di].subj) }}</span>
            <span class="lp2-tag" [attr.data-act]="runs[di].act">{{ pick(runs[di].tag) }}</span>
          </div>
        }
      </div>
    </div>
  `,
})
export class LandingConsoleComponent {
  protected i18n = inject(I18nService);
  private destroyRef = inject(DestroyRef);

  protected runs = LP2_RUNS;
  protected idx = signal(0);
  protected phase = signal<Phase>('arrive');
  protected doneList = signal<number[]>([2, 3]);

  protected rx = signal('0deg');
  protected ry = signal('0deg');

  protected run = computed(() => this.runs[this.idx()]);
  protected showChip = computed(() => ['classify', 'act', 'ok', 'leave'].includes(this.phase()));
  protected showAct = computed(() => ['act', 'ok', 'leave'].includes(this.phase()));

  protected pick = (v: { de: string; en: string }) => pickLang(v, this.i18n.lang());
  protected viaKey = (v: keyof typeof VIA_KEY) => VIA_KEY[v];

  constructor() {
    afterNextRender(() => this.run0());
  }

  private run0(): void {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    const wait = (p: Phase, ms: number) => { timer = setTimeout(() => { if (alive) step(p); }, reduced ? 60 : ms); };
    const step = (p: Phase) => {
      this.phase.set(p);
      if (p === 'arrive') wait('scan', 700);
      else if (p === 'scan') wait('classify', 1500);
      else if (p === 'classify') wait('act', 1000);
      else if (p === 'act') wait('ok', 1100);
      else if (p === 'ok') wait('leave', 600);
      else if (p === 'leave') {
        timer = setTimeout(() => {
          if (!alive) return;
          const i = this.idx();
          this.doneList.update((d) => [i, ...d].slice(0, 3));
          this.idx.set((i + 1) % this.runs.length);
          step('arrive');
        }, reduced ? 60 : 460);
      }
    };
    step('arrive');
    this.destroyRef.onDestroy(() => { alive = false; clearTimeout(timer); });
  }

  @HostListener('mousemove', ['$event'])
  onMove(e: MouseEvent): void {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5;
    const y = (e.clientY - r.top) / r.height - 0.5;
    this.rx.set((x * 5).toFixed(2) + 'deg');
    this.ry.set((-y * 5).toFixed(2) + 'deg');
  }

  @HostListener('mouseleave')
  onLeave(): void {
    this.rx.set('0deg');
    this.ry.set('0deg');
  }
}
