import { ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnInit, Output, computed, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { I18nService } from '../../../../../core/services/i18n.service';
import { AutomationService } from '../../../../../core/services/automation.service';
import { IconComponent } from '../../../../../shared/components/icon/icon.component';
import {
  AutomationConstant,
  ConstantType,
  CONSTANT_TYPE_META,
  NodeType,
  getNodeColor,
  getNodeIcon,
} from '../../../../../models/automation.model';
import { v } from '../../../../../shared/utils/event.util';

/** Minimal structural view of an editor node needed for constant-usage display. */
export interface ConstantUsageNode {
  nodeType: NodeType;
  label: string;
  config: string;
}

/**
 * Self-contained "Constants / variables" modal for the automation editor.
 *
 * <p>Owns all modal-internal UI state (inline add/edit editor, secret reveal, copy
 * feedback, usage popover) and the auto-save call to the backend. Behaviour is unchanged
 * from the inline implementation previously embedded in {@code AutomationEditorComponent}.</p>
 *
 * <p>Node-graph concerns stay with the parent: {@link stripReferences} is emitted when a
 * constant is deleted (the parent owns the node signal and rewrites configs), and node data
 * is passed in via {@link nodes} for read-only usage counting. The persisted constant list
 * is emitted via {@link constantsChanged} so the parent can update its automation state.</p>
 */
@Component({
  selector: 'app-constants-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, IconComponent],
  templateUrl: './constants-modal.component.html',
  styleUrls: ['./constants-modal.component.scss'],
})
export class ConstantsModalComponent implements OnInit {
  protected i18n = inject(I18nService);
  private automationService = inject(AutomationService);

  /** Template helper: reads an input/textarea value from a DOM event. */
  protected readonly v = v;

  @Input({ required: true }) automationId!: string;
  /** Authoritative constant list used to seed the editable draft when the modal opens. */
  @Input() constants: AutomationConstant[] = [];
  /** Read-only node snapshot used to count/list where each constant is referenced. */
  @Input() nodes: ConstantUsageNode[] = [];

  /** Requests the parent to close the modal. */
  @Output() close = new EventEmitter<void>();
  /** Emits a deleted constant's name so the parent can strip references from node configs. */
  @Output() stripReferences = new EventEmitter<string>();
  /** Emits the server-persisted constant list so the parent can update its automation state. */
  @Output() constantsChanged = new EventEmitter<AutomationConstant[]>();

  private static readonly CONSTANT_NAME_RE = /^[A-Za-z0-9_]+$/;

  // ── Modal state ────────────────────────────────────────────
  constantsDraft = signal<AutomationConstant[]>([]);
  savingConstants = signal(false);
  /** Which row is in the inline editor: a row index, 'new', or null (none). */
  constantEditIndex = signal<number | 'new' | null>(null);
  /** Indices whose secret value is temporarily revealed in display rows. */
  revealedSecrets = signal<Set<number>>(new Set());
  /** The token currently showing "copied" feedback, e.g. 'const.API_ENDPOINT'. */
  copiedToken = signal<string | null>(null);
  // Inline editor working fields
  cEditName = signal('');
  cEditType = signal<ConstantType>('text');
  cEditValue = signal('');
  cEditDesc = signal('');
  cEditTouched = signal(false);
  cEditRevealSecret = signal(false);
  readonly constantTypeOrder: ConstantType[] = ['text', 'number', 'boolean', 'url', 'secret'];
  /** Which usage popover is open (constant name), or null. */
  usagePopoverFor = signal<string | null>(null);

  ngOnInit(): void {
    this.constantsDraft.set((this.constants ?? []).map(c => ({ ...c })));
    this.constantEditIndex.set(null);
    this.revealedSecrets.set(new Set());
  }

  constantTypeColor(type: ConstantType): string {
    return (CONSTANT_TYPE_META[type] ?? CONSTANT_TYPE_META.text).color;
  }

  constantTypeIcon(type: ConstantType): string {
    return (CONSTANT_TYPE_META[type] ?? CONSTANT_TYPE_META.text).icon;
  }

  constantTypeLabel(type: ConstantType): string {
    return this.i18n.t('auto_const_type_' + type);
  }

  constantToken(name: string): string {
    return '{{const.' + (name || 'KEY') + '}}';
  }

  /** Normalises free-typed text into a CONSTANT_CASE key (UPPER_SNAKE). */
  normalizeConstantKey(raw: string): string {
    return (raw || '')
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_+/, '')
      .slice(0, 40);
  }

  isConstantNameValid(name: string): boolean {
    return ConstantsModalComponent.CONSTANT_NAME_RE.test((name || '').trim());
  }

  // ── Inline editor (add / edit) ─────────────────────────────
  startAddConstant(): void {
    this.cEditName.set('');
    this.cEditType.set('text');
    this.cEditValue.set('');
    this.cEditDesc.set('');
    this.cEditTouched.set(false);
    this.cEditRevealSecret.set(false);
    this.constantEditIndex.set('new');
  }

  startEditConstant(index: number): void {
    const c = this.constantsDraft()[index];
    if (!c) return;
    this.cEditName.set(c.name);
    this.cEditType.set(c.type);
    this.cEditValue.set(c.type === 'secret' ? '' : (c.value ?? ''));
    this.cEditDesc.set(c.description ?? '');
    this.cEditTouched.set(false);
    this.cEditRevealSecret.set(false);
    this.constantEditIndex.set(index);
  }

  cancelEditConstant(): void {
    this.constantEditIndex.set(null);
  }

  selectConstantType(type: ConstantType): void {
    this.cEditType.set(type);
    if (type === 'boolean' && this.cEditValue() !== 'true' && this.cEditValue() !== 'false') {
      this.cEditValue.set('false');
    }
  }

  /** The normalized editor key, used for live token preview and validation. */
  cEditNormName = computed(() => this.normalizeConstantKey(this.cEditName()));

  /** Validation error key for the inline editor, or '' when valid. */
  cEditError = computed<string>(() => {
    if (!this.cEditTouched()) return '';
    const name = this.cEditNormName();
    if (!name) return 'auto_const_err_required';
    const editing = this.constantEditIndex();
    const dup = this.constantsDraft().some((c, i) => i !== editing && c.name === name);
    return dup ? 'auto_const_err_exists' : '';
  });

  cEditValid(): boolean {
    const name = this.cEditNormName();
    if (!name) return false;
    const editing = this.constantEditIndex();
    return !this.constantsDraft().some((c, i) => i !== editing && c.name === name);
  }

  commitConstant(): void {
    this.cEditTouched.set(true);
    if (!this.cEditValid()) return;
    const type = this.cEditType();
    const entry: AutomationConstant = {
      name: this.cEditNormName(),
      type,
      value: type === 'secret' ? '' : this.cEditValue(),
      description: this.cEditDesc().trim() || undefined,
    };
    const editing = this.constantEditIndex();
    let updated: AutomationConstant[];
    if (editing === 'new') {
      updated = [entry, ...this.constantsDraft()];
    } else if (typeof editing === 'number') {
      const prev = this.constantsDraft()[editing];
      // Preserve a stored secret when the editor value was left blank.
      if (type === 'secret' && !this.cEditValue().trim()) {
        entry.hasValue = prev?.hasValue;
      }
      updated = this.constantsDraft().map((c, i) => (i === editing ? entry : c));
    } else {
      return;
    }
    this.constantsDraft.set(updated);
    this.constantEditIndex.set(null);
    this.persistConstants(updated);
  }

  deleteConstant(index: number): void {
    const removed = this.constantsDraft()[index];
    const updated = this.constantsDraft().filter((_, i) => i !== index);
    this.constantsDraft.set(updated);
    if (this.constantEditIndex() === index) this.constantEditIndex.set(null);
    if (removed) this.stripReferences.emit(removed.name);
    this.persistConstants(updated);
  }

  /** Persists the current draft to the backend (the modal auto-saves on each change). */
  private persistConstants(list: AutomationConstant[]): void {
    const payload = list.map(c => ({
      name: c.name,
      value: c.value ?? '',
      type: c.type,
      description: c.description ?? null,
    }));
    this.savingConstants.set(true);
    this.automationService.updateConstants(this.automationId, payload).subscribe({
      next: (detail) => {
        this.constantsChanged.emit(detail.constants);
        this.constantsDraft.set(detail.constants.map(c => ({ ...c })));
        this.savingConstants.set(false);
      },
      error: () => this.savingConstants.set(false),
    });
  }

  // ── Display-row helpers ────────────────────────────────────
  isSecretRevealed(index: number): boolean {
    return this.revealedSecrets().has(index);
  }

  toggleSecretReveal(index: number): void {
    this.revealedSecrets.update(set => {
      const next = new Set(set);
      next.has(index) ? next.delete(index) : next.add(index);
      return next;
    });
  }

  copyConstantToken(name: string): void {
    const token = this.constantToken(name);
    navigator.clipboard?.writeText(token).catch(() => {});
    this.copiedToken.set('const.' + name);
    setTimeout(() => { if (this.copiedToken() === 'const.' + name) this.copiedToken.set(null); }, 1400);
  }

  /** Counts how many nodes reference {{const.NAME}} and lists them for the usage popover. */
  constantUsage(name: string): { count: number; nodes: { label: string; color: string; icon: string }[] } {
    if (!name) return { count: 0, nodes: [] };
    const key = `const.${name}`;
    const re = new RegExp('\\{\\{\\s*const\\.' + name + '\\s*\\}\\}');
    const referencesConstant = (raw: string): boolean => {
      if (re.test(raw)) return true;
      try {
        const cfg = JSON.parse(raw || '{}');
        return Array.isArray(cfg.sourceVariables) && cfg.sourceVariables.includes(key);
      } catch { return false; }
    };
    const nodes = (this.nodes ?? [])
      .filter(n => referencesConstant(n.config || ''))
      .map(n => ({
        label: n.label || n.nodeType,
        color: getNodeColor(n.nodeType),
        icon: getNodeIcon(n.nodeType),
      }));
    return { count: nodes.length, nodes };
  }

  toggleUsagePopover(name: string): void {
    this.usagePopoverFor.update(cur => (cur === name ? null : name));
  }
}
