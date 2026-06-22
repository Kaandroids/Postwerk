import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { IconComponent } from '../../../../../shared/components/icon/icon.component';
import { v } from '../../../../../shared/utils/event.util';

/** A single selectable variable option ({{key}} + human label). */
interface VarOption { key: string; label: string; }

/**
 * Free-text input with a searchable variable picker. Users may type any expression
 * (static text + multiple {{...}} tokens) and/or open the dropdown to insert an
 * available variable at the caret — preventing typos while keeping full flexibility.
 */
@Component({
  selector: 'app-variable-combobox',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './variable-combobox.component.html',
  styleUrl: './variable-combobox.component.scss',
})
export class VariableComboboxComponent {
  private host = inject(ElementRef<HTMLElement>);

  value = input<string>('');
  variables = input<VarOption[]>([]);
  placeholder = input<string>('');
  searchPlaceholder = input<string>('');
  emptyText = input<string>('');
  valueChange = output<string>();

  open = signal(false);
  query = signal('');

  private field = viewChild<ElementRef<HTMLInputElement>>('field');
  private caretPos = 0;

  readonly v = v;

  filtered = computed<VarOption[]>(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.variables();
    if (!q) return list;
    return list.filter(o => o.key.toLowerCase().includes(q) || o.label.toLowerCase().includes(q));
  });

  onInput(value: string): void {
    this.valueChange.emit(value);
  }

  /** Remembers the caret position so a picked variable is inserted in place. */
  syncCaret(el: HTMLInputElement): void {
    this.caretPos = el.selectionStart ?? el.value.length;
  }

  toggle(): void {
    this.open.update(o => !o);
    if (this.open()) this.query.set('');
  }

  close(): void {
    this.open.set(false);
  }

  /** Inserts {{key}} at the last known caret position and emits the new value. */
  pick(option: VarOption): void {
    const token = `{{${option.key}}}`;
    const current = this.value();
    const pos = Math.min(this.caretPos, current.length);
    const next = current.slice(0, pos) + token + current.slice(pos);
    this.caretPos = pos + token.length;
    this.valueChange.emit(next);
    this.close();
    const el = this.field()?.nativeElement;
    if (el) setTimeout(() => { el.focus(); el.setSelectionRange(this.caretPos, this.caretPos); });
  }

  /** Closes the popover when clicking outside the component. */
  @HostListener('document:click', ['$event'])
  onDocClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.close();
  }
}
