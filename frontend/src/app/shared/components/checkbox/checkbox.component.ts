import { ChangeDetectionStrategy, Component, input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';

/** Styled checkbox with projected label content and error state, implementing ControlValueAccessor. */
@Component({
  selector: 'app-checkbox',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <label class="check" [attr.data-error]="error() ? '1' : '0'">
      <input type="checkbox" [checked]="value" (change)="toggle($event)" />
      <span class="check-box"><app-icon name="check" /></span>
      <span><ng-content /></span>
    </label>
  `,
  styles: `
    .check {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      cursor: pointer;
      user-select: none;
      font-size: 13.5px;
      color: var(--fg-muted);
      line-height: 1.5;
    }
    .check input { display: none; }
    .check-box {
      flex-shrink: 0;
      width: 18px;
      height: 18px;
      border: 0.5px solid var(--border-strong);
      border-radius: 5px;
      background: var(--field-bg);
      display: grid;
      place-items: center;
      transition: all 0.15s;
      margin-top: 1px;
    }
    input:checked + .check-box {
      background: var(--accent);
      border-color: var(--accent);
    }
    input:checked + .check-box ::ng-deep svg { opacity: 1; }
    .check-box ::ng-deep svg { opacity: 0; color: var(--accent-fg); transition: opacity 0.15s; }
    .check:hover .check-box { border-color: var(--fg-muted); }
    .check[data-error="1"] .check-box { border-color: var(--danger); }
    :host ::ng-deep a {
      color: var(--fg);
      text-decoration: underline;
      text-decoration-color: var(--border-strong);
    }
    :host ::ng-deep a:hover { text-decoration-color: var(--fg); }
  `,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => CheckboxComponent),
    multi: true,
  }],
})
export class CheckboxComponent implements ControlValueAccessor {
  error = input(false);

  value = false;
  onChange: (v: boolean) => void = () => {};
  onTouched: () => void = () => {};

  toggle(event: Event): void {
    this.value = (event.target as HTMLInputElement).checked;
    this.onChange(this.value);
    this.onTouched();
  }

  writeValue(v: boolean): void { this.value = !!v; }
  registerOnChange(fn: (v: boolean) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
}
