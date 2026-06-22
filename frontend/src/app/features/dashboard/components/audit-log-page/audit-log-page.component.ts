import { ChangeDetectionStrategy, Component, inject, signal, computed, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AuditLogService } from '../../../../core/services/audit-log.service';
import { AuditLog } from '../../../../models/audit-log.model';
import { PageContentComponent } from '../page-content/page-content.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ClickOutsideDirective } from '../../../../shared/directives/click-outside.directive';

// ── i18n keys needed (to be added to I18nService separately):
// audit_search_placeholder: 'Ereignis suchen…' / 'Search events…'
// audit_filter_kind: 'Art' / 'Kind'
// audit_filter_date: 'Zeitraum' / 'Date range'
// audit_export_csv: 'CSV exportieren' / 'Export CSV'
// audit_today: 'Heute' / 'Today'
// audit_yesterday: 'Gestern' / 'Yesterday'
// audit_day_count: '%count% Einträge' / '%count% entries'
// audit_actor: 'Akteur' / 'Actor'
// audit_device: 'Gerät' / 'Device'
// audit_location: 'Standort' / 'Location'
// audit_session: 'Sitzung' / 'Session'
// audit_payload: 'Payload' / 'Payload'
// audit_context: 'Kontext' / 'Context'
// audit_diff: 'Änderungen' / 'Changes'
// audit_diff_before: 'Vorher' / 'Before'
// audit_diff_after: 'Nachher' / 'After'

// ── Tone mapping for audit actions ──
interface AuditKind {
  icon: string;
  tone: 'slate' | 'amber' | 'red' | 'violet' | 'green';
  labelKey: string;
}

const AUDIT_KINDS: Record<string, AuditKind> = {
  USER_LOGIN:              { icon: 'signin',   tone: 'slate',  labelKey: 'audit_action_USER_LOGIN' },
  USER_LOGOUT:             { icon: 'signout',  tone: 'slate',  labelKey: 'audit_action_USER_LOGOUT' },
  USER_REGISTERED:         { icon: 'user',     tone: 'slate',  labelKey: 'audit_action_USER_REGISTERED' },
  USER_PROFILE_UPDATED:    { icon: 'user',     tone: 'slate',  labelKey: 'audit_action_USER_PROFILE_UPDATED' },
  USER_DATA_EXPORTED:      { icon: 'download', tone: 'slate',  labelKey: 'audit_action_USER_DATA_EXPORTED' },

  FILTER_CREATED:          { icon: 'filter',   tone: 'amber',  labelKey: 'audit_action_FILTER_CREATED' },
  FILTER_UPDATED:          { icon: 'filter',   tone: 'amber',  labelKey: 'audit_action_FILTER_UPDATED' },
  FILTER_DELETED:          { icon: 'filter',   tone: 'amber',  labelKey: 'audit_action_FILTER_DELETED' },
  CATEGORY_CREATED:        { icon: 'tagPlus',  tone: 'amber',  labelKey: 'audit_action_CATEGORY_CREATED' },
  CATEGORY_UPDATED:        { icon: 'tagEdit',  tone: 'amber',  labelKey: 'audit_action_CATEGORY_UPDATED' },
  CATEGORY_DELETED:        { icon: 'tagX',     tone: 'amber',  labelKey: 'audit_action_CATEGORY_DELETED' },

  USER_ACCOUNT_DELETED:    { icon: 'trash',    tone: 'red',    labelKey: 'audit_action_USER_ACCOUNT_DELETED' },
  LOGIN_FAILED:            { icon: 'signin',   tone: 'red',    labelKey: 'audit_action_LOGIN_FAILED' },
  LOGIN_LOCKED_OUT:        { icon: 'lock',     tone: 'red',    labelKey: 'audit_action_LOGIN_LOCKED_OUT' },
  DATA_DELETED_BY_RETENTION: { icon: 'trash',  tone: 'red',    labelKey: 'audit_action_DATA_DELETED_BY_RETENTION' },

  EMAIL_ACCESSED:          { icon: 'mail',     tone: 'violet',  labelKey: 'audit_action_EMAIL_ACCESSED' },
  EMAIL_SENT:              { icon: 'send',     tone: 'violet',  labelKey: 'audit_action_EMAIL_SENT' },
  EMAIL_FORWARDED:         { icon: 'forward',  tone: 'violet',  labelKey: 'audit_action_EMAIL_FORWARDED' },
  EMAIL_SYNCED:            { icon: 'sync',     tone: 'violet',  labelKey: 'audit_action_EMAIL_SYNCED' },

  EMAIL_ACCOUNT_CREATED:   { icon: 'mailbox',  tone: 'green',  labelKey: 'audit_action_EMAIL_ACCOUNT_CREATED' },
  EMAIL_ACCOUNT_UPDATED:   { icon: 'mailbox',  tone: 'green',  labelKey: 'audit_action_EMAIL_ACCOUNT_UPDATED' },
  EMAIL_ACCOUNT_DELETED:   { icon: 'mailbox',  tone: 'green',  labelKey: 'audit_action_EMAIL_ACCOUNT_DELETED' },
  CONSENT_UPDATED:         { icon: 'shield',   tone: 'green',  labelKey: 'audit_action_CONSENT_UPDATED' },
  USER_PASSWORD_CHANGED:   { icon: 'key',      tone: 'green',  labelKey: 'audit_action_USER_PASSWORD_CHANGED' },

  TOKEN_REFRESHED:         { icon: 'refresh',  tone: 'slate',  labelKey: 'audit_action_TOKEN_REFRESHED' },
  PASSWORD_RESET_REQUESTED: { icon: 'lock',    tone: 'amber',  labelKey: 'audit_action_PASSWORD_RESET_REQUESTED' },

  TEMPLATE_CREATED:        { icon: 'templates', tone: 'amber',  labelKey: 'audit_action_TEMPLATE_CREATED' },
  TEMPLATE_UPDATED:        { icon: 'templates', tone: 'amber',  labelKey: 'audit_action_TEMPLATE_UPDATED' },
  TEMPLATE_DELETED:        { icon: 'templates', tone: 'amber',  labelKey: 'audit_action_TEMPLATE_DELETED' },
  PARAMETER_SET_CREATED:   { icon: 'code',     tone: 'amber',  labelKey: 'audit_action_PARAMETER_SET_CREATED' },
  PARAMETER_SET_UPDATED:   { icon: 'code',     tone: 'amber',  labelKey: 'audit_action_PARAMETER_SET_UPDATED' },
  PARAMETER_SET_DELETED:   { icon: 'code',     tone: 'amber',  labelKey: 'audit_action_PARAMETER_SET_DELETED' },
  AUTOMATION_CREATED:      { icon: 'automations', tone: 'violet', labelKey: 'audit_action_AUTOMATION_CREATED' },
  AUTOMATION_UPDATED:      { icon: 'automations', tone: 'violet', labelKey: 'audit_action_AUTOMATION_UPDATED' },
  AUTOMATION_DELETED:      { icon: 'automations', tone: 'violet', labelKey: 'audit_action_AUTOMATION_DELETED' },
  AUTOMATION_ACTIVATED:    { icon: 'automations', tone: 'green',  labelKey: 'audit_action_AUTOMATION_ACTIVATED' },
  AUTOMATION_PAUSED:       { icon: 'automations', tone: 'amber',  labelKey: 'audit_action_AUTOMATION_PAUSED' },
  AUTOMATION_EXECUTED:     { icon: 'bolt',     tone: 'violet',  labelKey: 'audit_action_AUTOMATION_EXECUTED' },
  EMAIL_CATEGORIZED:       { icon: 'tag',      tone: 'violet',  labelKey: 'audit_action_EMAIL_CATEGORIZED' },
  AI_CHAT:                 { icon: 'sparkle',  tone: 'slate',   labelKey: 'audit_action_AI_CHAT' },
};

const AUDIT_ACTIONS = Object.keys(AUDIT_KINDS);

export interface DayGroup {
  key: string;
  header: string;
  isoDate: string;
  items: AuditLog[];
}

/** User-facing audit log page with search, kind/date filters, day-grouped timeline, and JSON detail expansion. */
@Component({
  selector: 'app-audit-log-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PageContentComponent, IconComponent, ClickOutsideDirective],
  templateUrl: './audit-log-page.component.html',
  styleUrl: './audit-log-page.component.scss',
})
export class AuditLogPageComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private auditLogService = inject(AuditLogService);

  // ── Data state ──
  protected allLogs = signal<AuditLog[]>([]);
  protected loading = signal(false);
  protected currentPage = signal(0);
  protected totalPages = signal(0);
  protected totalElements = signal(0);
  protected copied = signal(false);

  // ── Multi-expand ──
  protected openIds = signal<Set<string>>(new Set());

  // ── Toolbar filters ──
  protected searchQuery = signal('');
  protected kindFilter = signal('');
  protected dateFilter = signal('');
  protected memberFilter = signal('');
  protected kindDropdownOpen = signal(false);
  protected dateDropdownOpen = signal(false);
  protected memberDropdownOpen = signal(false);

  protected actions = AUDIT_ACTIONS;

  // ── Distinct members (actors) present in the loaded org audit, for the member filter ──
  protected members = computed(() => {
    const seen = new Map<string, string>();
    for (const l of this.allLogs()) {
      if (l.userId && !seen.has(l.userId)) {
        seen.set(l.userId, l.userName || l.userId);
      }
    }
    return Array.from(seen.entries())
      .map(([id, name]) => ({ id, name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  // ── Client-side filtered logs ──
  protected filteredLogs = computed(() => {
    let logs = this.allLogs();
    const kind = this.kindFilter();
    const member = this.memberFilter();
    const query = this.searchQuery().trim().toLowerCase();

    if (kind) {
      logs = logs.filter(l => l.action === kind);
    }

    if (member) {
      logs = logs.filter(l => l.userId === member);
    }

    if (query) {
      logs = logs.filter(l => {
        const k = AUDIT_KINDS[l.action];
        const labelKey = k ? this.i18n.t(k.labelKey) : l.action;
        const hay = [labelKey, l.action, l.detail, l.ipAddress, l.id, l.userName].join(' ').toLowerCase();
        return hay.includes(query);
      });
    }

    return logs;
  });

  // ── Day-grouped logs ──
  protected dayGroups = computed<DayGroup[]>(() => {
    const logs = this.filteredLogs();
    const map = new Map<string, AuditLog[]>();

    for (const log of logs) {
      const key = this.dayKey(log.createdAt);
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(log);
    }

    return Array.from(map.entries()).map(([key, items]) => ({
      key,
      header: this.dayHeader(items[0].createdAt),
      isoDate: this.formatIsoDate(items[0].createdAt),
      items,
    }));
  });

  ngOnInit(): void {
    this.loadLogs();
  }

  // ── Kind info helpers ──

  protected getKind(action: string): AuditKind {
    return AUDIT_KINDS[action] || { icon: 'shield', tone: 'slate' as const, labelKey: 'audit_action_' + action };
  }

  protected getTone(action: string): string {
    return this.getKind(action).tone;
  }

  protected getIcon(action: string): string {
    return this.getKind(action).icon;
  }

  // ── Expand / collapse ──

  protected toggleExpand(log: AuditLog): void {
    this.openIds.update(prev => {
      const next = new Set(prev);
      if (next.has(log.id)) {
        next.delete(log.id);
      } else {
        next.add(log.id);
      }
      return next;
    });
    this.copied.set(false);
  }

  protected isOpen(id: string): boolean {
    return this.openIds().has(id);
  }

  // ── Filter handlers ──

  protected onKindFilterChange(action: string): void {
    this.kindFilter.set(action);
    this.kindDropdownOpen.set(false);
  }

  protected onDateFilterChange(value: string): void {
    this.dateFilter.set(value);
    this.dateDropdownOpen.set(false);
  }

  protected onMemberFilterChange(userId: string): void {
    this.memberFilter.set(userId);
    this.memberDropdownOpen.set(false);
  }

  protected getMemberLabel(): string {
    const m = this.memberFilter();
    if (!m) return this.i18n.t('audit_all_members');
    return this.members().find(x => x.id === m)?.name ?? m;
  }

  protected getKindLabel(): string {
    const kind = this.kindFilter();
    if (!kind) return this.i18n.t('audit_filter_all');
    const k = AUDIT_KINDS[kind];
    return k ? this.i18n.t(k.labelKey) : kind;
  }

  protected getDateLabel(): string {
    const d = this.dateFilter();
    if (!d) return this.i18n.t('audit_filter_all');
    const lang = this.i18n.lang();
    switch (d) {
      case 'today': return lang === 'de' ? 'Heute' : 'Today';
      case '7d':    return lang === 'de' ? 'Letzte 7 Tage' : 'Last 7 days';
      case '30d':   return lang === 'de' ? 'Letzte 30 Tage' : 'Last 30 days';
      default:      return this.i18n.t('audit_filter_all');
    }
  }

  // ── CSV export placeholder ──

  protected exportCsv(): void {
    // Placeholder for CSV export
  }

  // ── Date helpers ──

  protected dayKey(dateStr: string): string {
    const d = new Date(dateStr);
    const pad = (n: number) => n < 10 ? '0' + n : '' + n;
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  protected dayHeader(dateStr: string): string {
    const d = new Date(dateStr);
    const now = new Date();
    const lang = this.i18n.lang();

    const sameDay = (a: Date, b: Date) =>
      a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

    if (sameDay(d, now)) return lang === 'de' ? 'Heute' : 'Today';

    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (sameDay(d, yesterday)) return lang === 'de' ? 'Gestern' : 'Yesterday';

    return d.toLocaleDateString(lang === 'de' ? 'de-DE' : 'en-US', {
      weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
    });
  }

  protected formatIsoDate(dateStr: string): string {
    return this.fmt.date(dateStr);
  }

  protected relTime(dateStr: string): string {
    const now = Date.now();
    const ts = new Date(dateStr).getTime();
    const diffSec = Math.round((now - ts) / 1000);
    const lang = this.i18n.lang();

    if (diffSec < 60) return lang === 'de' ? 'gerade eben' : 'just now';
    const m = Math.round(diffSec / 60);
    if (m < 60) return lang === 'de' ? `vor ${m} Min` : `${m} min ago`;
    const h = Math.round(m / 60);
    if (h < 24) return lang === 'de' ? `vor ${h} Std` : `${h} hr ago`;
    const day = Math.round(h / 24);
    return lang === 'de' ? `vor ${day} Tagen` : `${day} d ago`;
  }

  protected formatTime(dateStr: string): string {
    const d = new Date(dateStr);
    const pad = (n: number) => n < 10 ? '0' + n : '' + n;
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  protected formatDateFull(dateStr: string): string {
    return this.fmt.dateTimeFull(dateStr);
  }

  // ── Detail parsing ──

  protected parseDetail(detail: string | null): Record<string, unknown> | null {
    if (!detail) return null;
    try {
      const obj = JSON.parse(detail);
      if (typeof obj === 'object' && obj !== null) return obj;
    } catch { /* not JSON */ }
    // Try key=value pairs
    const pairs: Record<string, string> = {};
    const parts = detail.split(/[,;]\s*/);
    for (const part of parts) {
      const eqMatch = part.match(/^([^=:]+)[=:](.+)$/);
      if (eqMatch) {
        pairs[eqMatch[1].trim()] = eqMatch[2].trim();
      }
    }
    return Object.keys(pairs).length > 0 ? pairs : null;
  }

  protected getDetailEntries(detail: string | null): { key: string; value: string }[] {
    const parsed = this.parseDetail(detail);
    if (!parsed) return [];
    return Object.entries(parsed)
      .filter(([k]) => k !== 'before' && k !== 'after')
      .map(([k, v]) => ({
        key: k,
        value: typeof v === 'object' ? JSON.stringify(v) : String(v),
      }));
  }

  protected hasDiff(detail: string | null): boolean {
    const parsed = this.parseDetail(detail);
    return !!(parsed && parsed['before'] && parsed['after']);
  }

  protected getDiffKeys(detail: string | null): string[] {
    const parsed = this.parseDetail(detail);
    if (!parsed || !parsed['before'] || !parsed['after']) return [];
    const before = parsed['before'] as Record<string, unknown>;
    const after = parsed['after'] as Record<string, unknown>;
    return Array.from(new Set([...Object.keys(before), ...Object.keys(after)]));
  }

  protected getDiffValue(detail: string | null, side: 'before' | 'after', key: string): string {
    const parsed = this.parseDetail(detail);
    if (!parsed || !parsed[side]) return '\u2014';
    const obj = parsed[side] as Record<string, unknown>;
    const v = obj[key];
    if (v === undefined || v === null) return '\u2014';
    return typeof v === 'object' ? JSON.stringify(v) : String(v);
  }

  protected isDiffChanged(detail: string | null, key: string): boolean {
    const parsed = this.parseDetail(detail);
    if (!parsed || !parsed['before'] || !parsed['after']) return false;
    const before = parsed['before'] as Record<string, unknown>;
    const after = parsed['after'] as Record<string, unknown>;
    return JSON.stringify(before[key]) !== JSON.stringify(after[key]);
  }

  // ── JSON / clipboard ──

  protected getLogJson(log: AuditLog): string {
    return JSON.stringify({
      id: log.id,
      action: log.action,
      detail: log.detail,
      ipAddress: log.ipAddress,
      createdAt: log.createdAt,
    }, null, 2);
  }

  protected async copyJson(log: AuditLog): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.getLogJson(log));
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch { /* clipboard not available */ }
  }

  // ── Data loading ──

  private async loadLogs(): Promise<void> {
    this.loading.set(true);
    try {
      const page = await firstValueFrom(this.auditLogService.list(0, 200));
      this.allLogs.set(page.content);
      this.totalPages.set(page.totalPages);
      this.totalElements.set(page.totalElements);
    } finally {
      this.loading.set(false);
    }
  }
}
