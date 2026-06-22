import { ChangeDetectionStrategy, Component, input, signal, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';

/** Password input field with show/hide toggle, implementing ControlValueAccessor for reactive forms. */
@Component({
  selector: 'app-password-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="input-wrap">
      <input
        class="input"
        [type]="shown() ? 'text' : 'password'"
        [placeholder]="placeholder()"
        [attr.aria-label]="ariaLabel()"
        [attr.data-error]="error() ? '1' : '0'"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
      <button
        type="button"
        class="input-toggle"
        (click)="shown.set(!shown())"
        [attr.aria-label]="shown() ? 'Hide password' : 'Show password'"
        tabindex="-1"
      >
        <app-icon [name]="shown() ? 'eyeOff' : 'eye'" />
      </button>
    </div>
  `,
  styles: `
    .input-wrap { position: relative; display: flex; align-items: center; }
    .input {
      appearance: none;
      width: 100%;
      height: 44px;
      padding: 0 44px 0 14px;
      border: 0.5px solid var(--border-strong);
      border-radius: var(--radius);
      background: var(--field-bg);
      color: var(--field-fg);
      font: inherit;
      font-size: 15px;
      transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
    }
    .input::placeholder { color: var(--fg-subtle); }
    .input:hover { background: var(--field-bg-hover); }
    .input:focus {
      outline: none;
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-soft);
    }
    .input[data-error="1"] { border-color: var(--danger); }
    .input[data-error="1"]:focus { box-shadow: 0 0 0 3px oklch(0.58 0.20 25 / 0.15); }
    .input-toggle {
      position: absolute;
      right: 6px;
      top: 50%;
      transform: translateY(-50%);
      appearance: none;
      border: 0;
      background: transparent;
      color: var(--fg-muted);
      width: 32px;
      height: 32px;
      border-radius: 6px;
      cursor: pointer;
      display: grid;
      place-items: center;
    }
    .input-toggle:hover { color: var(--fg); background: var(--bg-2); }
  `,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => PasswordInputComponent),
    multi: true,
  }],
})
export class PasswordInputComponent implements ControlValueAccessor {
  placeholder = input('');
  ariaLabel = input('');
  error = input(false);

  shown = signal(false);
  value = '';
  onChange: (v: string) => void = () => {};
  onTouched: () => void = () => {};

  onInput(event: Event): void {
    this.value = (event.target as HTMLInputElement).value;
    this.onChange(this.value);
  }

  writeValue(v: string): void { this.value = v ?? ''; }
  registerOnChange(fn: (v: string) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
}
