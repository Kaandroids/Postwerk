import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Reusable button component with primary and ghost variants, supporting disabled state. */
@Component({
  selector: 'app-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [class]="'btn btn-' + variant()"
      [disabled]="disabled()"
    >
      <ng-content />
    </button>
  `,
  styles: `
    .btn {
      appearance: none;
      height: 46px;
      padding: 0 18px;
      border: 0;
      border-radius: var(--radius);
      font: inherit;
      font-size: 15px;
      font-weight: 500;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.15s;
      width: 100%;
      white-space: nowrap;
    }
    .btn-primary {
      background: var(--fg);
      color: var(--bg);
    }
    .btn-primary:hover { transform: translateY(-1px); box-shadow: var(--shadow-card); }
    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }
    .btn-ghost {
      background: transparent;
      color: var(--fg);
      border: 0.5px solid var(--border-strong);
    }
    .btn-ghost:hover { background: var(--bg-2); }
  `,
})
export class ButtonComponent {
  variant = input<'primary' | 'ghost'>('primary');
  type = input<'button' | 'submit'>('button');
  disabled = input(false);
}
