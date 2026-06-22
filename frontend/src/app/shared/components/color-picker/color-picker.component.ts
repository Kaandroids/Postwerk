import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Reusable color picker: a row of preset swatches plus a native custom-color input. */
@Component({
  selector: 'app-color-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './color-picker.component.html',
  styleUrl: './color-picker.component.scss',
})
export class ColorPickerComponent {
  value = input.required<string>();
  presets = input.required<string[]>();
  ariaLabel = input<string>('');

  valueChange = output<string>();

  onNative(e: Event): void {
    this.valueChange.emit((e.target as HTMLInputElement).value);
  }
}
