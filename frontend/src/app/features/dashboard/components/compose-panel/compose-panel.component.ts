import {
  ChangeDetectionStrategy, Component, inject, signal, computed,
  output, input, ViewChild, ElementRef
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';
import { Email, DraftAttachment, ComposeEmail } from '../../../../models/email.model';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { TiptapEditorComponent } from '../../../../shared/components/tiptap-editor/tiptap-editor.component';

export type ComposeMode = 'compose' | 'reply' | 'forward' | 'draft';

/** Email compose/reply/forward panel with rich text editor, CC/BCC fields, and attachment support. */
@Component({
  selector: 'app-compose-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, TiptapEditorComponent],
  templateUrl: './compose-panel.component.html',
  styleUrl: './compose-panel.component.scss',
})
export class ComposePanelComponent {
  protected i18n = inject(I18nService);
  private workspace = inject(WorkspaceService);
  private emailService = inject(EmailService);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly closed = output<void>();
  readonly sent = output<void>();

  isOpen = signal(false);
  mode = signal<ComposeMode>('compose');
  to = signal('');
  cc = signal('');
  bcc = signal('');
  subject = signal('');
  body = signal('');
  inReplyTo = signal('');
  replyToEmailId = signal('');
  draftId = signal<string | null>(null);
  attachments = signal<DraftAttachment[]>([]);
  sending = signal(false);
  showCcBcc = signal(false);

  protected modeTitle = computed(() => {
    switch (this.mode()) {
      case 'compose': return this.i18n.t('compose_new');
      case 'reply': return this.i18n.t('compose_reply');
      case 'forward': return this.i18n.t('compose_forward');
      case 'draft': return this.i18n.t('compose_draft');
    }
  });

  protected canSend = computed(() => {
    return this.to().trim().length > 0 && this.subject().trim().length > 0 && !this.sending();
  });

  openCompose(): void {
    this.resetFields();
    this.mode.set('compose');
    this.isOpen.set(true);
  }

  openReply(email: Email): void {
    this.resetFields();
    this.mode.set('reply');
    this.to.set(email.fromAddress || '');
    this.subject.set(email.subject?.startsWith('Re:') ? email.subject : `Re: ${email.subject || ''}`);
    this.inReplyTo.set(email.messageId || '');

    const date = email.receivedAt ? new Date(email.receivedAt).toLocaleString() : '';
    const sender = email.fromPersonal || email.fromAddress || '';
    const quoteHeader = this.i18n.t('compose_quote_on', { date, sender });
    const quoted = email.bodyHtml || email.bodyText || '';
    this.body.set(`<br><br><div style="border-left:2px solid var(--border);padding-left:12px;color:var(--fg-muted)">${quoteHeader}<br>${quoted}</div>`);

    this.isOpen.set(true);
  }

  openForward(email: Email): void {
    this.resetFields();
    this.mode.set('forward');
    this.subject.set(email.subject?.startsWith('Fwd:') ? email.subject : `Fwd: ${email.subject || ''}`);

    const date = email.receivedAt ? new Date(email.receivedAt).toLocaleString() : '';
    const sender = email.fromPersonal || email.fromAddress || '';
    const quoteHeader = this.i18n.t('compose_quote_on', { date, sender });
    const body = email.bodyHtml || email.bodyText || '';
    this.body.set(`<br><br><div style="border-left:2px solid var(--border);padding-left:12px;color:var(--fg-muted)">${quoteHeader}<br>${body}</div>`);

    this.isOpen.set(true);
  }

  openDraft(email: Email): void {
    this.resetFields();
    this.mode.set('draft');
    this.draftId.set(email.id);
    this.to.set(email.toAddresses || '');
    this.cc.set(email.ccAddresses || '');
    this.bcc.set(email.bccAddresses || '');
    this.subject.set(email.subject || '');
    this.body.set(email.bodyHtml || '');
    this.inReplyTo.set('');
    if (this.cc() || this.bcc()) this.showCcBcc.set(true);

    // Load draft attachments
    const accountId = this.workspace.activeAccount()?.id;
    if (accountId && email.id) {
      this.emailService.listAttachments(accountId, email.id).subscribe({
        next: (atts) => this.attachments.set(atts),
      });
    }

    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.closed.emit();
  }

  protected onSend(): void {
    const accountId = this.workspace.activeAccount()?.id;
    if (!accountId) return;

    this.sending.set(true);
    const request = this.buildRequest(false);

    this.emailService.send(accountId, request).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.emit();
        this.close();
      },
      error: () => {
        this.sending.set(false);
      },
    });
  }

  protected onSaveDraft(): void {
    const accountId = this.workspace.activeAccount()?.id;
    if (!accountId) return;

    const request = this.buildRequest(true);

    if (this.draftId()) {
      this.emailService.updateDraft(accountId, this.draftId()!, request).subscribe({
        next: (res) => {
          this.draftId.set(res.id);
        },
      });
    } else {
      this.emailService.saveDraft(accountId, request).subscribe({
        next: (res) => {
          this.draftId.set(res.id);
        },
      });
    }
  }

  protected onDiscard(): void {
    const accountId = this.workspace.activeAccount()?.id;
    if (accountId && this.draftId()) {
      this.emailService.deleteDraft(accountId, this.draftId()!).subscribe();
    }
    this.close();
  }

  protected onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    if (file.size > 10 * 1024 * 1024) return; // 10MB

    const accountId = this.workspace.activeAccount()?.id;
    if (!accountId) return;

    // Ensure draft exists first
    if (!this.draftId()) {
      const request = this.buildRequest(true);
      this.emailService.saveDraft(accountId, request).subscribe({
        next: (res) => {
          this.draftId.set(res.id);
          this.uploadFile(accountId, res.id!, file);
        },
      });
    } else {
      this.uploadFile(accountId, this.draftId()!, file);
    }

    input.value = '';
  }

  protected removeAttachment(att: DraftAttachment): void {
    const accountId = this.workspace.activeAccount()?.id;
    if (!accountId || !this.draftId()) return;

    this.emailService.deleteAttachment(accountId, this.draftId()!, att.id).subscribe({
      next: () => {
        this.attachments.update(list => list.filter(a => a.id !== att.id));
      },
    });
  }

  protected formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected onBodyChange(html: string): void {
    this.body.set(html);
  }

  private uploadFile(accountId: string, draftId: string, file: File): void {
    this.emailService.uploadAttachment(accountId, draftId, file).subscribe({
      next: (att) => {
        this.attachments.update(list => [...list, att]);
      },
    });
  }

  private buildRequest(isDraft: boolean): ComposeEmail {
    return {
      to: this.to(),
      cc: this.cc(),
      bcc: this.bcc(),
      subject: this.subject(),
      body: this.body(),
      inReplyTo: this.inReplyTo(),
      replyToEmailId: this.draftId() || '',
      isDraft,
    };
  }

  private resetFields(): void {
    this.to.set('');
    this.cc.set('');
    this.bcc.set('');
    this.subject.set('');
    this.body.set('');
    this.inReplyTo.set('');
    this.replyToEmailId.set('');
    this.draftId.set(null);
    this.attachments.set([]);
    this.showCcBcc.set(false);
    this.sending.set(false);
  }
}
