import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  viewChild,
  ElementRef,
  AfterViewChecked,
  effect,
  computed,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { WizardService } from '../../services/wizard.service';
import { WizardMessage } from '../../models/wizard.model';

@Component({
  selector: 'app-wizard-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  templateUrl: './wizard-chat.component.html',
  styleUrl: './wizard-chat.component.scss',
})
export class WizardChatComponent implements AfterViewChecked {
  protected readonly i18n = inject(I18nService);
  protected readonly wizard = inject(WizardService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly messagesContainer = viewChild<ElementRef>('messagesContainer');
  readonly composerInput = viewChild<ElementRef<HTMLTextAreaElement>>('composerInput');

  readonly inputText = signal('');
  readonly introCollapsed = signal(false);
  private shouldScroll = false;
  private readonly htmlCache = new Map<string, SafeHtml>();

  readonly messages = computed(() => this.wizard.messages());
  readonly isLoading = computed(() => this.wizard.isLoading());
  readonly toolCalls = computed(() => this.wizard.toolCalls());
  readonly hasTalked = computed(() => this.messages().length > 0);

  /** Floating ambient workflow tiles teasing the five node kinds. */
  readonly ambientTiles = [
    { k: 'trigger', icon: 'inbox', labelKey: 'wiz_amb_trigger', cls: 't1' },
    { k: 'classify', icon: 'tag', labelKey: 'wiz_amb_classify', cls: 't2' },
    { k: 'extract', icon: 'sparkle', labelKey: 'wiz_amb_extract', cls: 't3' },
    { k: 'filter', icon: 'filter', labelKey: 'wiz_amb_filter', cls: 't4' },
    { k: 'action', icon: 'send', labelKey: 'wiz_amb_reply', cls: 't5' },
  ];

  /** Headline split word-by-word for the staggered mask-reveal animation. */
  readonly introWords = computed(() => {
    const words = this.i18n
      .t('wiz_title_a')
      .trim()
      .split(/\s+/)
      .map((w) => ({ w, em: false }));
    words.push({ w: this.i18n.t('wiz_title_em') + this.i18n.t('wiz_title_b'), em: true });
    return words;
  });

  constructor() {
    effect(() => {
      this.wizard.messages();
      this.shouldScroll = true;
    });
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.shouldScroll = false;
      const el = this.messagesContainer()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }
  }

  onSend(): void {
    const text = this.inputText().trim();
    if (!text || this.isLoading()) return;

    this.introCollapsed.set(true);
    this.inputText.set('');
    this.resetComposerHeight();
    this.wizard.sendMessage(text);
  }

  /** Collapse the composer back to its single-line height after sending. */
  private resetComposerHeight(): void {
    setTimeout(() => {
      const el = this.composerInput()?.nativeElement;
      if (el) el.style.height = 'auto';
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSend();
    }
  }

  /** Grow the composer upward as text is typed, capped at 180px. */
  autoGrow(el: HTMLTextAreaElement): void {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 180) + 'px';
  }

  renderMarkdown(content: string): SafeHtml {
    if (this.htmlCache.has(content)) {
      return this.htmlCache.get(content)!;
    }
    try {
      const raw = marked.parse(content) as string;
      const clean = DOMPurify.sanitize(raw);
      const safe = this.sanitizer.bypassSecurityTrustHtml(clean);
      this.htmlCache.set(content, safe);
      return safe;
    } catch {
      // On failure, return escaped plain text — never bypass-trust raw, untrusted content.
      const escaped = content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
      return this.sanitizer.bypassSecurityTrustHtml(escaped);
    }
  }

  getToolLabel(tool: string): string {
    const labels: Record<string, string> = {
      propose_automation_plan: 'Analyzing...',
      create_category: 'Creating category',
      create_filter: 'Creating filter',
      create_parameter_set: 'Creating parameter set',
      create_template: 'Creating template',
      create_automation: 'Creating automation',
      update_automation_flow: 'Building flow',
    };
    return labels[tool] || tool;
  }
}
