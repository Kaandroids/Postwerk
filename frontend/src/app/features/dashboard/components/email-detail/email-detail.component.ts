import { ChangeDetectionStrategy, Component, inject, signal, computed, input, output } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';
import { ExportImportService } from '../../../../core/services/export-import.service';
import { Email, Attachment } from '../../../../models/email.model';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { relativeTime } from '../../../../shared/utils/relative-time.util';

/** Expanded-email detail pane: header, attachments, sanitized body, action buttons and automation traces. */
@Component({
  selector: 'app-email-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './email-detail.component.html',
  styleUrl: './email-detail.component.scss',
})
export class EmailDetailComponent {
  protected i18n = inject(I18nService);
  private sanitizer = inject(DomSanitizer);
  private workspace = inject(WorkspaceService);
  private emailService = inject(EmailService);
  private exportImport = inject(ExportImportService);

  readonly email = input.required<Email>();
  readonly showReply = input<boolean>(false);
  readonly showReprocess = input<boolean>(false);
  readonly showSimulate = input<boolean>(false);
  readonly reprocessing = input<boolean>(false);

  readonly reply = output<void>();
  readonly forward = output<void>();
  readonly star = output<void>();
  readonly deleted = output<void>();
  readonly reprocess = output<void>();
  readonly simulate = output<void>();

  protected attachmentsOpen = signal(false);
  protected traceOpen = signal<string | null>(null); // trace ID that is expanded

  private sanitizeCache = new Map<string, SafeHtml>();
  private static readonly SANITIZE_CACHE_MAX = 50;

  protected sanitizedHtml = computed<SafeHtml>(() => {
    const email = this.email();
    if (!email?.bodyHtml) return '';

    // Return cached result if already sanitized for this email
    const cached = this.sanitizeCache.get(email.id);
    if (cached !== undefined) return cached;

    // Force rel="noopener noreferrer" on target="_blank" links to prevent reverse
    // tabnabbing. Scoped to this sanitize call only: hook is added before and removed
    // after so it never affects other DOMPurify.sanitize usages.
    DOMPurify.addHook('afterSanitizeAttributes', (node) => {
      if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
        node.setAttribute('rel', 'noopener noreferrer');
      }
    });
    const clean = DOMPurify.sanitize(email.bodyHtml, {
      ALLOWED_TAGS: ['p', 'br', 'b', 'i', 'u', 'strong', 'em', 'a', 'ul', 'ol', 'li',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'pre', 'code',
        'table', 'thead', 'tbody', 'tr', 'th', 'td', 'img', 'span', 'div', 'hr'],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'target', 'rel',
        'colspan', 'rowspan', 'width', 'height', 'align', 'valign'],
      ALLOW_DATA_ATTR: false,
    });
    DOMPurify.removeHook('afterSanitizeAttributes');
    const result = this.sanitizer.bypassSecurityTrustHtml(clean);

    // Evict oldest entries if cache is full
    if (this.sanitizeCache.size >= EmailDetailComponent.SANITIZE_CACHE_MAX) {
      const firstKey = this.sanitizeCache.keys().next().value!;
      this.sanitizeCache.delete(firstKey);
    }
    this.sanitizeCache.set(email.id, result);

    return result;
  });

  protected linkedText = computed<SafeHtml>(() => {
    const email = this.email();
    if (!email?.bodyText) return '';
    const escaped = email.bodyText
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    const linked = escaped.replace(
      /(https?:\/\/[^\s<]+)/g,
      '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>'
    );
    return this.sanitizer.bypassSecurityTrustHtml(linked);
  });

  protected parsedAttachments = computed<Attachment[]>(() => {
    const email = this.email();
    if (!email?.attachments) return [];
    try {
      return JSON.parse(email.attachments) as Attachment[];
    } catch {
      return [];
    }
  });

  protected toggleTraceOpen(traceId: string): void {
    this.traceOpen.set(this.traceOpen() === traceId ? null : traceId);
  }

  protected getNodeTypeLabel(nodeType: string): string {
    const key = 'inbox_node_' + nodeType.toLowerCase();
    return this.i18n.t(key);
  }

  protected getTraceStatusLabel(status: string): string {
    switch (status) {
      case 'SUCCESS': return this.i18n.t('inbox_trace_success');
      case 'FAILED': return this.i18n.t('inbox_trace_failed');
      case 'RUNNING': return this.i18n.t('inbox_trace_running');
      default: return status;
    }
  }

  protected getNodeResultLabel(resultStatus: string): string {
    switch (resultStatus) {
      case 'MATCHED': return this.i18n.t('inbox_filter_matched');
      case 'NOT_MATCHED': return this.i18n.t('inbox_filter_not_matched');
      case 'CATEGORIZED': return this.i18n.t('inbox_categorized_as');
      case 'EXTRACTED': return this.i18n.t('inbox_extracted_values');
      case 'EXECUTED': return this.i18n.t('inbox_trace_success');
      case 'PASSED': return this.i18n.t('inbox_trace_success');
      case 'SKIPPED': return 'Skipped';
      case 'ERROR': return 'Error';
      default: return resultStatus;
    }
  }

  protected getRelativeTime(dateStr: string): string {
    return relativeTime(dateStr, this.i18n.lang() === 'de');
  }

  protected objectKeys(obj: any): string[] {
    if (!obj || typeof obj !== 'object') return [];
    return Object.keys(obj);
  }

  protected formatExtractValue(val: any): string {
    if (val == null) return '-';
    if (typeof val === 'object') return JSON.stringify(val);
    return String(val);
  }

  protected downloadAttachment(index: number, fileName: string): void {
    const accountId = this.workspace.activeAccount()?.id;
    const emailId = this.email().id;
    if (!accountId || !emailId) return;

    this.emailService.downloadAttachment(accountId, emailId, index).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) return;
        this.exportImport.downloadBlob(blob, fileName);
      },
    });
  }
}
