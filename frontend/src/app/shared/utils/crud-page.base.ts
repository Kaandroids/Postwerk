import { effect, inject, signal, WritableSignal } from '@angular/core';
import { Observable } from 'rxjs';
import { AiChatService } from '../../core/services/ai-chat.service';
import { I18nService } from '../../core/services/i18n.service';
import { ConfirmDialogService } from '../services/confirm-dialog.service';
import { ExportImportService } from '../../core/services/export-import.service';
import { ImportResult } from '../../models/import-result.model';
import { v } from './event.util';
import { humanizeError } from './error.util';

/**
 * Shared base for list/form CRUD pages (categories, templates, parameter-sets,
 * automations, integrations).
 *
 * Holds the common view-toggle state, error/loading signals, and the trivial
 * show/cancel handlers, plus shared list-mutation ({@link replaceInList}) and
 * confirm-then-delete ({@link deleteWithConfirm}) helpers. Each page implements
 * {@link resetForm} to clear its own form fields.
 */
export abstract class CrudPageBase {
  protected readonly i18n = inject(I18nService);
  protected readonly confirmDialog = inject(ConfirmDialogService);
  protected readonly exportImport = inject(ExportImportService);
  private readonly aiChat = inject(AiChatService);

  view = signal<'list' | 'form'>('list');
  editId = signal<string | null>(null);
  error = signal('');
  fieldErr = signal<Record<string, boolean>>({});
  loading = signal(false);

  readonly v = v;

  /** Clears page-specific form fields. */
  protected abstract resetForm(): void;

  hasErr(field: string): boolean {
    return !!this.fieldErr()[field];
  }

  showAddForm(): void {
    this.resetForm();
    this.editId.set(null);
    this.view.set('form');
  }

  cancel(): void {
    this.editId.set(null);
    this.view.set('list');
  }

  /** Subscribes to a list source and writes the result into {@code target}; errors are swallowed. */
  protected loadList<T>(source: Observable<T[]>, target: WritableSignal<T[]>): void {
    source.subscribe({ next: (data) => target.set(data), error: () => {} });
  }

  /**
   * Reloads the page's list whenever the AI assistant reports it mutated a resource of
   * {@code resourceType} (e.g. 'categories', 'automations'), so AI-created/updated/deleted
   * items appear without a manual page refresh. Call from the subclass constructor (it needs
   * an injection context for the underlying effect).
   */
  protected reloadOnAiMutation(resourceType: string, reload: () => void): void {
    let lastSeq = this.aiChat.resourceMutation().seq;
    effect(() => {
      const m = this.aiChat.resourceMutation();
      if (m.seq === lastSeq) return; // initial run / unrelated update
      lastSeq = m.seq;
      if (m.types.includes(resourceType)) reload();
    });
  }

  /** Downloads the export payload as a pretty-printed JSON file named {@code filename}. */
  protected exportJson(source: Observable<unknown[]>, filename: string): void {
    source.subscribe({ next: (data) => this.exportImport.downloadJson(data, filename) });
  }

  /**
   * Reads an uploaded JSON file and imports it via {@code importFn}; on success runs
   * {@code reload} and, if any rows failed, surfaces the failed count via {@link error}.
   * Parse/transport failures surface a generic error. All messages use the {@code errKey}
   * i18n key with a {@code failed} placeholder.
   */
  protected importJson(
    event: Event,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    importFn: (data: any[]) => Observable<ImportResult>,
    errKey: string,
    reload: () => void,
  ): void {
    this.exportImport
      .readJsonFile<unknown[]>(event)
      .then((data) => {
        importFn(data as unknown[]).subscribe({
          next: (result) => {
            reload();
            if (result.failed > 0) {
              this.error.set(this.i18n.t(errKey, { failed: '' + result.failed }));
            }
          },
          error: () => this.error.set(this.i18n.t(errKey, { failed: '?' })),
        });
      })
      .catch(() => this.error.set(this.i18n.t(errKey, { failed: '?' })));
  }

  /** Replaces the entry with the same {@code id} in a list signal, immutably. No-op if absent. */
  protected replaceInList<T extends { id: string }>(list: WritableSignal<T[]>, updated: T): void {
    const current = list();
    const idx = current.findIndex(x => x.id === updated.id);
    if (idx >= 0) {
      const copy = [...current];
      copy[idx] = updated;
      list.set(copy);
    }
  }

  /**
   * Shows the shared delete confirmation dialog and, on confirm, runs {@code action};
   * on success calls {@code onSuccess}, on error surfaces the message via {@link error}.
   *
   * @param confirmKey i18n key used for both the dialog title and message
   */
  protected async deleteWithConfirm(
    confirmKey: string,
    action: () => Observable<unknown>,
    onSuccess: () => void,
  ): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t(confirmKey),
      message: this.i18n.t(confirmKey),
      confirmText: this.i18n.t('confirm_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;
    action().subscribe({
      next: () => onSuccess(),
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }
}
