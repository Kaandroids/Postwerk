import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { I18nService } from '../../../core/services/i18n.service';

type Cmd = 'bold' | 'italic' | 'underline' | 'insertUnorderedList' | 'insertOrderedList';

/**
 * Lightweight WYSIWYG rich-text editor (bold/italic/underline, heading, quote, lists, link, image).
 * Produces an HTML string via {@link valueChange}; rendering must be sanitized (Angular `[innerHTML]`).
 * Image upload (button / drag&drop / paste) inlines images as Base64 data URLs.
 */
@Component({
  selector: 'app-mk-rich-text',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="mk-rt" data-testid="mk-rich-text">
      <div class="mk-rt-toolbar">
        <button type="button" class="mk-rt-btn" [class.on]="active().bold" title="Fett"
                (mousedown)="prevent($event)" (click)="exec('bold')" data-testid="mk-rt-bold">
          <b>B</b>
        </button>
        <button type="button" class="mk-rt-btn" [class.on]="active().italic" title="Kursiv"
                (mousedown)="prevent($event)" (click)="exec('italic')" data-testid="mk-rt-italic">
          <i>I</i>
        </button>
        <button type="button" class="mk-rt-btn" [class.on]="active().underline" title="Unterstrichen"
                (mousedown)="prevent($event)" (click)="exec('underline')" data-testid="mk-rt-underline">
          <u>U</u>
        </button>
        <span class="mk-rt-div"></span>
        <button type="button" class="mk-rt-btn" title="Überschrift"
                (mousedown)="prevent($event)" (click)="format('h3')" data-testid="mk-rt-h3">H</button>
        <button type="button" class="mk-rt-btn" title="Zitat"
                (mousedown)="prevent($event)" (click)="format('blockquote')" data-testid="mk-rt-quote">”</button>
        <span class="mk-rt-div"></span>
        <button type="button" class="mk-rt-btn" [class.on]="active().ul" title="Aufzählung"
                (mousedown)="prevent($event)" (click)="exec('insertUnorderedList')" data-testid="mk-rt-ul">•—</button>
        <button type="button" class="mk-rt-btn" [class.on]="active().ol" title="Nummeriert"
                (mousedown)="prevent($event)" (click)="exec('insertOrderedList')" data-testid="mk-rt-ol">1.</button>
        <span class="mk-rt-div"></span>
        <button type="button" class="mk-rt-btn" title="Link"
                (mousedown)="prevent($event)" (click)="link()" data-testid="mk-rt-link">
          <app-icon name="globe" />
        </button>
        <button type="button" class="mk-rt-btn" title="Bild"
                (mousedown)="prevent($event)" (click)="pickImage()" data-testid="mk-rt-image">
          <app-icon name="image" />
        </button>
      </div>

      <div #editor class="mk-rt-editor mk-rich" contenteditable="true"
           [attr.data-empty]="empty() ? '1' : null"
           [attr.data-placeholder]="placeholder() || i18n.t('mkt_publish_description_placeholder')"
           (input)="sync()" (blur)="sync()"
           (keyup)="refresh()" (mouseup)="refresh()"
           (paste)="onPaste($event)" (drop)="onDrop($event)" (dragover)="prevent($event)"
           data-testid="mk-rt-editor"></div>

      <input #file type="file" accept="image/*" multiple hidden (change)="onFiles($event)" />
    </div>
  `,
})
export class MkRichTextComponent {
  protected i18n = inject(I18nService);

  value = input<string>('');
  placeholder = input<string>('');
  valueChange = output<string>();

  private editor = viewChild.required<ElementRef<HTMLDivElement>>('editor');
  private fileInput = viewChild.required<ElementRef<HTMLInputElement>>('file');

  empty = signal(true);
  active = signal({ bold: false, italic: false, underline: false, ul: false, ol: false });

  private savedRange: Range | null = null;
  private lastSet = '';

  constructor() {
    // Push external value into the contentEditable element without clobbering the caret.
    effect(() => {
      const v = this.value();
      const el = this.editor().nativeElement;
      if (v !== this.lastSet && document.activeElement !== el) {
        this.lastSet = v;
        el.innerHTML = v || '';
        this.empty.set(!el.textContent?.trim());
      }
    });
  }

  prevent(e: Event): void {
    e.preventDefault();
  }

  exec(cmd: Cmd): void {
    document.execCommand(cmd, false);
    this.editor().nativeElement.focus();
    this.sync();
    this.refresh();
  }

  format(tag: 'h3' | 'blockquote'): void {
    document.execCommand('formatBlock', false, tag);
    this.editor().nativeElement.focus();
    this.sync();
  }

  link(): void {
    const url = window.prompt(this.i18n.t('mkt_rt_link_prompt'));
    if (!url) return;
    document.execCommand('createLink', false, url);
    this.sync();
  }

  pickImage(): void {
    this.saveRange();
    this.fileInput().nativeElement.click();
  }

  onFiles(e: Event): void {
    const input = e.target as HTMLInputElement;
    this.handleFiles(input.files);
    input.value = '';
  }

  onPaste(e: ClipboardEvent): void {
    const items = e.clipboardData?.items;
    if (!items) return;
    const images = Array.from(items)
      .filter((i) => i.type.startsWith('image/'))
      .map((i) => i.getAsFile())
      .filter((f): f is File => !!f);
    if (images.length) {
      e.preventDefault();
      this.saveRange();
      this.handleFiles(images);
    }
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.saveRange();
    this.handleFiles(e.dataTransfer?.files ?? null);
  }

  refresh(): void {
    this.active.set({
      bold: this.state('bold'),
      italic: this.state('italic'),
      underline: this.state('underline'),
      ul: this.state('insertUnorderedList'),
      ol: this.state('insertOrderedList'),
    });
  }

  sync(): void {
    const el = this.editor().nativeElement;
    this.empty.set(!el.textContent?.trim());
    this.lastSet = el.innerHTML;
    this.valueChange.emit(el.innerHTML);
  }

  private state(cmd: string): boolean {
    try {
      return document.queryCommandState(cmd);
    } catch {
      return false;
    }
  }

  private saveRange(): void {
    const sel = window.getSelection();
    if (sel && sel.rangeCount && this.editor().nativeElement.contains(sel.anchorNode)) {
      this.savedRange = sel.getRangeAt(0).cloneRange();
    }
  }

  private handleFiles(files: FileList | File[] | null): void {
    if (!files) return;
    Array.from(files)
      .filter((f) => f.type.startsWith('image/'))
      .forEach((f) => {
        const reader = new FileReader();
        reader.onload = () => this.insertImage(String(reader.result), f.name);
        reader.readAsDataURL(f);
      });
  }

  private insertImage(dataUrl: string, alt: string): void {
    const el = this.editor().nativeElement;
    el.focus();
    const sel = window.getSelection();
    if (this.savedRange && sel) {
      sel.removeAllRanges();
      sel.addRange(this.savedRange);
    }
    const img = `<img src="${dataUrl}" alt="${alt.replace(/"/g, '')}" />`;
    document.execCommand('insertHTML', false, img);
    this.savedRange = null;
    this.sync();
  }
}
