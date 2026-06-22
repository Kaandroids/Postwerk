import { ChangeDetectionStrategy, Component, HostListener, inject } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';

/** Modal confirmation dialog with danger and accent tones, driven by ConfirmDialogService. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (dialog.isOpen()) {
      <div class="overlay" role="dialog" aria-modal="true" (click)="onOverlayClick($event)">
        <div class="card" (click)="$event.stopPropagation()">
          <div class="icon-circle" [attr.data-tone]="tone">
            <app-icon [name]="tone === 'danger' ? 'alert' : 'alert'" />
          </div>
          <h3 class="title">{{ dialog.options().title }}</h3>
          <p class="message">{{ dialog.options().message }}</p>
          <div class="actions">
            <button class="btn btn-ghost" (click)="dialog.resolve(false)">
              {{ dialog.options().cancelText || 'Abbrechen' }}
            </button>
            <button class="btn btn-confirm" [attr.data-tone]="tone" (click)="dialog.resolve(true)">
              {{ dialog.options().confirmText || 'Löschen' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.35);
      backdrop-filter: blur(4px);
      animation: fadeIn 0.15s ease-out;
    }

    .card {
      background: var(--bg);
      border: 0.5px solid var(--border);
      border-radius: var(--radius-lg, 12px);
      padding: 24px;
      max-width: 400px;
      width: calc(100% - 32px);
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
      animation: scaleIn 0.15s ease-out;
    }

    .icon-circle {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
      background: color-mix(in srgb, var(--danger) 12%, var(--bg-2));
      color: var(--danger);
    }
    .icon-circle[data-tone="accent"] {
      background: color-mix(in srgb, var(--accent) 12%, var(--bg-2));
      color: var(--accent);
    }

    .title {
      font-size: 16px;
      font-weight: 600;
      color: var(--fg);
      margin: 0;
    }

    .message {
      font-size: 14px;
      color: var(--fg-muted);
      margin: 8px 0 0;
      line-height: 1.5;
    }

    .actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 20px;
    }

    .btn {
      padding: 8px 16px;
      border-radius: var(--radius, 8px);
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      border: none;
      transition: opacity 0.15s;
    }
    .btn:hover { opacity: 0.85; }

    .btn-ghost {
      background: transparent;
      border: 1px solid var(--border);
      color: var(--fg);
    }

    .btn-confirm {
      background: var(--danger);
      color: #fff;
    }
    .btn-confirm[data-tone="accent"] {
      background: var(--accent);
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    @keyframes scaleIn {
      from { opacity: 0; transform: scale(0.95); }
      to { opacity: 1; transform: scale(1); }
    }
  `,
})
export class ConfirmDialogComponent {
  protected dialog = inject(ConfirmDialogService);

  protected get tone(): 'danger' | 'accent' {
    return this.dialog.options().tone || 'danger';
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.dialog.isOpen()) this.dialog.resolve(false);
  }

  @HostListener('document:keydown.enter')
  onEnter(): void {
    if (this.dialog.isOpen()) this.dialog.resolve(true);
  }

  onOverlayClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.dialog.resolve(false);
  }
}
