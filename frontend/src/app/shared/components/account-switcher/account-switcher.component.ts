import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { I18nService } from '../../../core/services/i18n.service';
import { IconComponent } from '../icon/icon.component';

/** Dropdown selector for switching the active email account within the current workspace. */
@Component({
  selector: 'app-account-switcher',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button class="acct-switcher" type="button" [attr.aria-expanded]="open()" aria-label="Switch account" (click)="toggle($event)">
      <div class="acct-current">
        <span class="acct-typelabel"><app-icon name="inbox" /><span>{{ i18n.t('switcher_mailbox') }}</span></span>
        @if (workspace.activeAccount(); as acct) {
          <span class="acct-dot" [style.background]="acct.color"></span>
          <div class="acct-label">
            <span class="acct-name">{{ acct.displayName || acct.email }}</span>
            @if (acct.displayName) {
              <span class="acct-sub">{{ acct.email }}</span>
            }
          </div>
          <app-icon [name]="open() ? 'arrowUp' : 'arrowDown'" />
        } @else {
          <span class="acct-email acct-empty">{{ i18n.t('email_account_none') }}</span>
          <app-icon name="arrowDown" />
        }
      </div>

      @if (open()) {
        <div class="acct-dropdown" (click)="$event.stopPropagation()">
          @for (acct of workspace.accounts(); track acct.id) {
            <div
              class="acct-option"
              [attr.data-active]="acct.id === workspace.activeAccount()?.id ? '1' : '0'"
              (click)="select(acct.id)"
            >
              <span class="acct-opt-dot" [style.background]="acct.color"></span>
              <div class="acct-option-info">
                <span class="acct-option-name">{{ acct.displayName || acct.email }}</span>
                <span class="acct-option-email">{{ acct.email }}</span>
              </div>
              @if (acct.id === workspace.activeAccount()?.id) {
                <app-icon name="check" />
              }
            </div>
          }
          <button class="acct-add" type="button" (click)="addAccount()">
            <app-icon name="plus" />
            <span>{{ i18n.t('email_account_add') }}</span>
          </button>
        </div>
      }
    </button>
  `,
  styles: `
    :host { position: relative; display: inline-block; }

    .acct-switcher { appearance: none; border: 0; background: transparent; font-family: inherit; color: var(--fg); cursor: pointer; position: relative; text-align: left; }

    .acct-current {
      display: flex;
      align-items: center;
      gap: 10px;
      height: 36px;
      padding: 0 10px;
      border: 0.5px solid var(--border);
      border-radius: 8px;
      background: var(--bg-2);
      min-width: 200px;
      transition: background 0.12s, border-color 0.12s;
      &:hover { background: var(--bg); border-color: var(--border-strong); }
    }

    .acct-label {
      display: flex;
      flex-direction: column;
      min-width: 0;
      flex: 1;
      line-height: 1.2;
    }

    .acct-name {
      font-size: 12.5px;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 160px;
    }

    .acct-sub {
      font-size: 11px;
      color: var(--fg-subtle);
      font-family: var(--font-mono);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 160px;
    }

    .acct-email {
      font-size: 13px;
      font-weight: 500;
      max-width: 200px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .acct-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 12%, transparent); }
    .acct-empty { color: var(--fg-muted); }

    /* Compact mobile pill: replace the colour dot + account name with an icon +
       "Postfach" type label (the active mailbox is shown in the page header). */
    .acct-typelabel { display: none; align-items: center; gap: 10px; font-size: 12.5px; font-weight: 600; color: var(--fg); }
    @media (max-width: 639.98px) {
      /* Narrow phones only (< sm): the full account name of two pills won't fit,
         so collapse to an icon + short type label. From 640px up there's room
         for the normal switcher. */
      .acct-current { min-width: 0; height: 34px; padding: 0 9px; gap: 8px; }
      .acct-dot, .acct-label, .acct-email { display: none; }
      .acct-typelabel { display: inline-flex; }
      .acct-typelabel app-icon {
        display: inline-flex; align-items: center; justify-content: center;
        width: 22px; height: 22px; border-radius: 6px; flex-shrink: 0;
        color: var(--accent);
        background: color-mix(in srgb, var(--accent) 16%, var(--bg-2));
      }
    }

    .acct-dropdown {
      position: absolute;
      top: calc(100% + 6px);
      left: 50%;
      transform: translateX(-50%);
      min-width: 260px;
      background: var(--bg);
      border: 0.5px solid var(--border);
      border-radius: 10px;
      box-shadow: 0 8px 24px oklch(0 0 0 / 0.12);
      padding: 6px;
      z-index: 100;
    }

    .acct-opt-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
    .acct-option {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      border-radius: 7px;
      cursor: pointer;
      transition: background 0.12s;
      &:hover { background: var(--bg-2); }
      &[data-active="1"] { color: var(--accent); }
    }

    .acct-option-info {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .acct-option-name {
      font-size: 13px;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .acct-option-email {
      font-size: 11.5px;
      color: var(--fg-subtle);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .acct-add {
      appearance: none;
      border: none;
      background: transparent;
      font-family: inherit;
      width: 100%;
      text-align: left;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 10px;
      border-top: 0.5px solid var(--border);
      margin-top: 4px;
      font-size: 13px;
      color: var(--fg-muted);
      cursor: pointer;
      border-radius: 7px;
      transition: background 0.12s, color 0.12s;
      &:hover { background: var(--bg-2); color: var(--fg); }
    }
  `,
  host: {
    '(document:click)': 'closeDropdown()',
  },
})
export class AccountSwitcherComponent {
  protected workspace = inject(WorkspaceService);
  protected i18n = inject(I18nService);
  private router = inject(Router);

  open = signal(false);

  toggle(event: Event): void {
    event.stopPropagation();
    this.open.update(v => !v);
  }

  select(id: string): void {
    this.workspace.switchAccount(id);
    this.open.set(false);
  }

  addAccount(): void {
    this.open.set(false);
    this.router.navigate(['/dashboard/email-accounts']);
  }

  closeDropdown(): void {
    this.open.set(false);
  }
}
