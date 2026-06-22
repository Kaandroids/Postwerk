import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  input,
} from '@angular/core';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';

/** Rich text editor powered by Tiptap with toolbar actions for bold, italic, headings, lists, and links. */
@Component({
  selector: 'app-tiptap-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tiptap-editor.component.html',
  styleUrl: './tiptap-editor.component.scss',
})
export class TiptapEditorComponent implements OnInit, OnDestroy {
  @Input() content = '';
  @Input() placeholder = '';
  @Input() hasError = false;

  @Output() contentChange = new EventEmitter<string>();

  @ViewChild('editorEl', { static: true }) editorEl!: ElementRef<HTMLDivElement>;

  editor!: Editor;

  ngOnInit(): void {
    this.editor = new Editor({
      element: this.editorEl.nativeElement,
      extensions: [
        StarterKit.configure({
          heading: { levels: [1, 2, 3] },
          link: {
            openOnClick: false,
            HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
          },
        }),
        Placeholder.configure({
          placeholder: this.placeholder,
        }),
      ],
      content: this.content,
      onUpdate: ({ editor }) => {
        this.contentChange.emit(editor.getHTML());
      },
    });
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
  }

  setContent(html: string): void {
    if (this.editor) {
      this.editor.commands.setContent(html, { emitUpdate: false });
    }
  }

  // Toolbar actions
  toggleBold(): void {
    this.editor.chain().focus().toggleBold().run();
  }

  toggleItalic(): void {
    this.editor.chain().focus().toggleItalic().run();
  }

  toggleUnderline(): void {
    this.editor.chain().focus().toggleUnderline().run();
  }

  toggleHeading(level: 1 | 2 | 3): void {
    this.editor.chain().focus().toggleHeading({ level }).run();
  }

  toggleBulletList(): void {
    this.editor.chain().focus().toggleBulletList().run();
  }

  toggleOrderedList(): void {
    this.editor.chain().focus().toggleOrderedList().run();
  }

  setLink(): void {
    const prev = this.editor.getAttributes('link')['href'] ?? '';
    const url = prompt('URL', prev);
    if (url === null) return;
    if (url === '') {
      this.editor.chain().focus().unsetLink().run();
      return;
    }
    this.editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  }

  isActive(name: string, attrs?: Record<string, any>): boolean {
    return this.editor?.isActive(name, attrs) ?? false;
  }
}
