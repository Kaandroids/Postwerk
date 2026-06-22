import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { KnowledgeBaseService } from '../../../../core/services/knowledge-base.service';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { KbEntry, KbFieldRole, KnowledgeBase, KnowledgeBaseRequest } from '../../../../models/knowledge-base.model';
import { ParameterSet } from '../../../../models/parameter-set.model';
import { humanizeError } from '../../../../shared/utils/error.util';

type Screen = 'list' | 'config' | 'entries';

/**
 * The "Knowledge Base" / context tab: org-scoped reference data searched by the VECTOR_SEARCH node.
 * Three screens — list, config (schema = a parameter set + per-field embed/keyword roles + a unique
 * key), and entries (filled rows with inline add/edit + CSV import).
 */
@Component({
  selector: 'app-knowledge-bases-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, ButtonComponent, ErrorBannerComponent, EmptyStateComponent, PageContentComponent],
  templateUrl: './knowledge-bases-page.component.html',
  styleUrl: './knowledge-bases-page.component.scss',
})
export class KnowledgeBasesPageComponent extends CrudPageBase {
  private kbService = inject(KnowledgeBaseService);
  private paramSetService = inject(ParameterSetService);

  kbs = signal<KnowledgeBase[]>([]);
  paramSets = signal<ParameterSet[]>([]);
  screen = signal<Screen>('list');

  // Config form
  name = signal('');
  description = signal('');
  parameterSetId = signal('');
  roles = signal<Record<string, KbFieldRole>>({});
  uniqueField = signal('');

  // Entries
  activeKb = signal<KnowledgeBase | null>(null);
  entries = signal<KbEntry[]>([]);
  entryDraft = signal<Record<string, string>>({});
  entryEditId = signal<string | null>(null);
  importError = signal('');

  selectedParamSet = computed(() => this.paramSets().find(p => p.id === this.parameterSetId()) ?? null);
  fieldNames = computed<string[]>(() => (this.selectedParamSet()?.parameters ?? []).map(p => p.name));
  canSaveConfig = computed(() =>
    this.name().trim().length >= 2 && !!this.parameterSetId()
    && this.fieldNames().some(f => this.roles()[f]?.embed));

  activeFields = computed<string[]>(() => {
    const kb = this.activeKb();
    if (!kb) return [];
    const ps = this.paramSets().find(p => p.id === kb.parameterSetId);
    return (ps?.parameters ?? []).map(p => p.name);
  });

  constructor() {
    super();
    this.reloadKbs();
    this.paramSetService.list().subscribe({ next: d => this.paramSets.set(d), error: () => {} });
  }

  // ── Navigation ──────────────────────────────────────────────
  newKb(): void {
    this.resetForm();
    this.editId.set(null);
    this.screen.set('config');
  }

  editKb(kb: KnowledgeBase): void {
    this.editId.set(kb.id);
    this.name.set(kb.name);
    this.description.set(kb.description ?? '');
    this.parameterSetId.set(kb.parameterSetId);
    this.roles.set({ ...kb.fieldRoles });
    this.uniqueField.set(kb.uniqueField ?? '');
    this.error.set('');
    this.screen.set('config');
  }

  backToList(): void {
    this.editId.set(null);
    this.error.set('');
    this.screen.set('list');
  }

  // ── Config form ─────────────────────────────────────────────
  roleOf(field: string): KbFieldRole {
    return this.roles()[field] ?? { embed: false, keyword: false };
  }

  toggleRole(field: string, which: 'embed' | 'keyword'): void {
    this.roles.update(r => {
      const cur = r[field] ?? { embed: false, keyword: false };
      return { ...r, [field]: { ...cur, [which]: !cur[which] } };
    });
  }

  onParamSetChange(): void {
    const valid = new Set(this.fieldNames());
    this.roles.update(r => Object.fromEntries(Object.entries(r).filter(([k]) => valid.has(k))));
    if (!valid.has(this.uniqueField())) this.uniqueField.set('');
  }

  saveConfig(): void {
    this.error.set('');
    if (!this.canSaveConfig()) {
      this.error.set(this.i18n.t('kb_err_need_embed'));
      return;
    }
    const roles: Record<string, KbFieldRole> = {};
    for (const [k, v] of Object.entries(this.roles())) {
      if (v.embed || v.keyword) roles[k] = v;
    }
    const req: KnowledgeBaseRequest = {
      name: this.name().trim(),
      description: this.description().trim(),
      parameterSetId: this.parameterSetId(),
      fieldRoles: roles,
      uniqueField: this.uniqueField() || null,
    };
    this.loading.set(true);
    const op$ = this.editId() ? this.kbService.update(this.editId()!, req) : this.kbService.create(req);
    op$.subscribe({
      next: () => { this.loading.set(false); this.reloadKbs(); this.backToList(); },
      error: (e) => { this.loading.set(false); this.error.set(humanizeError(e, this.i18n.t('kb_err_save'))); },
    });
  }

  deleteKb(kb: KnowledgeBase): void {
    this.deleteWithConfirm('kb_delete_confirm', () => this.kbService.delete(kb.id), () => this.reloadKbs());
  }

  // ── Entries ─────────────────────────────────────────────────
  openEntries(kb: KnowledgeBase): void {
    this.activeKb.set(kb);
    this.entryDraft.set({});
    this.entryEditId.set(null);
    this.importError.set('');
    this.error.set('');
    this.kbService.listEntries(kb.id).subscribe({ next: e => this.entries.set(e), error: () => this.entries.set([]) });
    this.screen.set('entries');
  }

  draftValue(field: string): string {
    return this.entryDraft()[field] ?? '';
  }

  setDraft(field: string, value: string): void {
    this.entryDraft.update(d => ({ ...d, [field]: value }));
  }

  editEntry(entry: KbEntry): void {
    this.entryEditId.set(entry.id);
    const d: Record<string, string> = {};
    for (const f of this.activeFields()) {
      d[f] = entry.data[f] != null ? String(entry.data[f]) : '';
    }
    this.entryDraft.set(d);
  }

  cancelEntry(): void {
    this.entryEditId.set(null);
    this.entryDraft.set({});
  }

  saveEntry(): void {
    const kb = this.activeKb();
    if (!kb) return;
    const data: Record<string, unknown> = { ...this.entryDraft() };
    const op$ = this.entryEditId()
      ? this.kbService.updateEntry(kb.id, this.entryEditId()!, { data })
      : this.kbService.addEntry(kb.id, { data });
    op$.subscribe({
      next: () => { this.cancelEntry(); this.reloadEntries(); },
      error: (e) => this.error.set(humanizeError(e, this.i18n.t('kb_err_save'))),
    });
  }

  deleteEntry(entry: KbEntry): void {
    const kb = this.activeKb();
    if (!kb) return;
    this.deleteWithConfirm('kb_entry_delete_confirm', () => this.kbService.deleteEntry(kb.id, entry.id), () => this.reloadEntries());
  }

  cellValue(entry: KbEntry, field: string): string {
    const v = entry.data[field];
    return v != null ? String(v) : '';
  }

  // ── CSV import ──────────────────────────────────────────────
  importCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const kb = this.activeKb();
    if (!file || !kb) return;
    this.importError.set('');
    file.text().then(text => {
      const rows = this.parseCsv(text);
      if (rows.length === 0) {
        this.importError.set(this.i18n.t('kb_import_empty'));
        return;
      }
      this.kbService.import(kb.id, rows).subscribe({
        next: (res) => {
          this.reloadEntries();
          if (res.failed > 0) this.importError.set(this.i18n.t('kb_import_partial', { failed: '' + res.failed }));
        },
        error: () => this.importError.set(this.i18n.t('kb_import_error')),
      });
    }).finally(() => { input.value = ''; });
  }

  /** Minimal CSV: first line = headers, comma-separated; subsequent lines mapped to field-value rows. */
  private parseCsv(text: string): Record<string, unknown>[] {
    const lines = text.split(/\r?\n/).filter(l => l.trim().length > 0);
    if (lines.length < 2) return [];
    const headers = lines[0].split(',').map(h => h.trim());
    const rows: Record<string, unknown>[] = [];
    for (let i = 1; i < lines.length; i++) {
      const cells = lines[i].split(',');
      const row: Record<string, unknown> = {};
      headers.forEach((h, idx) => { row[h] = (cells[idx] ?? '').trim(); });
      rows.push(row);
    }
    return rows;
  }

  // ── List helpers ────────────────────────────────────────────
  paramSetName(id: string): string {
    return this.paramSets().find(p => p.id === id)?.name ?? '—';
  }

  roleSummary(kb: KnowledgeBase): string {
    return Object.entries(kb.fieldRoles).filter(([, r]) => r.embed).map(([k]) => k).join(', ');
  }

  private reloadKbs(): void {
    this.loadList(this.kbService.list(), this.kbs);
  }

  private reloadEntries(): void {
    const kb = this.activeKb();
    if (!kb) return;
    this.kbService.listEntries(kb.id).subscribe({ next: e => this.entries.set(e), error: () => {} });
    this.reloadKbs(); // keep the list's entryCount fresh
  }

  protected override resetForm(): void {
    this.name.set('');
    this.description.set('');
    this.parameterSetId.set('');
    this.roles.set({});
    this.uniqueField.set('');
    this.error.set('');
  }
}
