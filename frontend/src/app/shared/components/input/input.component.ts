import { ChangeDetectionStrategy, Component, input, output, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Reusable text input with label, auxiliary text, and error state, implementing ControlValueAccessor for reactive forms. */
@Component({
  selector: 'app-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      @if (label()) {
        <label class="field-label" [attr.for]="inputId()">
          <span>{{ label() }}</span>
          @if (aux()) {
            <span class="field-label-aux">{{ aux() }}</span>
          }
        </label>
      }
      <input
        class="input"
        [id]="inputId()"
        [type]="type()"
        [placeholder]="placeholder()"
        [autocomplete]="autocomplete()"
        [attr.data-error]="error() ? '1' : '0'"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
    </div>
  `,
  styles: `
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field-label {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      font-size: 13px;
      font-weight: 500;
      color: var(--fg);
    }
    .field-label-aux {
      font-size: 12px;
      font-weight: 400;
      color: var(--fg-subtle);
    }
    .input {
      appearance: none;
      width: 100%;
      height: 44px;
      padding: 0 14px;
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
  `,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => InputComponent),
    multi: true,
  }],
})
export class InputComponent implements ControlValueAccessor {
  label = input('');
  aux = input('');
  type = input('text');
  placeholder = input('');
  autocomplete = input('');
  inputId = input('');
  error = input(false);

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
