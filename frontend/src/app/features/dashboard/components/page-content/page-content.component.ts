import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';

/** Content wrapper providing consistent padding, max-width, and shared page-header styles for dashboard pages. */
@Component({
  selector: 'app-page-content',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="dash-content">
      <ng-content />
    </div>
  `,
  styles: `
    .dash-content {
      padding: 28px 28px 64px;
      max-width: 1280px;
      width: 100%;
      margin: 0 auto;
      animation: fadeIn 0.3s ease both;
    }
    /* Opacity-only — must NOT animate transform. A filling (fill-mode: both) animation that
       touches transform makes .dash-content a permanent containing block for position:fixed
       descendants, which would trap page modals (admin user/org detail) inside the content
       column instead of letting their fixed scrim cover the whole viewport. */
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    /* ── Shared page header ─────────────────────────────────────── */
    .dash-page-head {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 24px;
      margin-bottom: 28px;
    }
    .dash-eyebrow {
      font-size: 12px;
      color: var(--fg-muted);
      margin-bottom: 6px;
    }
    .dash-title {
      font-family: var(--font-serif);
      font-weight: 400;
      font-size: clamp(28px, 6vw, 38px);
      letter-spacing: -0.02em;
      margin: 0;
      line-height: 1.05;
    }
    .dash-subtitle {
      font-size: 14px;
      color: var(--fg-muted);
      margin-top: 6px;
    }
    .dash-back {
      appearance: none;
      border: 0;
      background: transparent;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 0;
      margin-bottom: 8px;
      font: inherit;
      font-family: var(--font-mono);
      font-size: 12px;
      color: var(--fg-muted);
      cursor: pointer;
      transition: color 0.15s, transform 0.15s;
    }
    .dash-back:hover {
      color: var(--fg);
      transform: translateX(-2px);
    }

    /* Phones/small tablets: tighten padding and stack the header so the title
       and the action buttons don't fight for one row (and the wide "+ Add"
       button isn't cut off). Applies to every page using app-page-content. */
    @media (max-width: 767.98px) {
      .dash-content { padding: 20px 16px 48px; }
      .dash-page-head {
        flex-direction: column;
        align-items: stretch;
        gap: 16px;
      }
    }
  `,
})
export class PageContentComponent {}
