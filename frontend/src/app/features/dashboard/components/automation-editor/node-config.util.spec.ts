import {
  parseNodeConfig,
  triggerMode,
  delayMinutes,
  webhookMethod,
  categoryEntries,
  labelCategoryName,
  labelCategoryColor,
  filterChecks,
  extractions,
  NodeConfigView,
} from './node-config.util';
import { Category } from '../../../../models/category.model';

function node(config: string, nodeType = 'TRIGGER'): NodeConfigView {
  return { id: 'n1', nodeType: nodeType as NodeConfigView['nodeType'], label: 'x', config };
}

const categories = [
  { id: 'c1', name: 'Invoices', color: '#ff0000' },
  { id: 'c2', name: 'Spam', color: '#00ff00' },
] as Category[];

describe('node-config.util', () => {
  describe('parseNodeConfig', () => {
    it('parses valid JSON', () => {
      expect(parseNodeConfig(node('{"a":1}'), {})).toEqual({ a: 1 });
    });

    it('returns the fallback for invalid JSON', () => {
      expect(parseNodeConfig(node('not json'), { fallback: true })).toEqual({ fallback: true });
    });

    it('returns the fallback for an undefined node', () => {
      expect(parseNodeConfig(undefined, { fallback: true })).toEqual({ fallback: true });
    });

    it('treats an empty config as {}', () => {
      expect(parseNodeConfig(node(''), { x: 1 })).toEqual({});
    });
  });

  describe('triggerMode', () => {
    it('defaults to EMAIL', () => {
      expect(triggerMode(node('{}'))).toBe('EMAIL');
    });

    it('reads the configured mode', () => {
      expect(triggerMode(node('{"triggerMode":"WEBHOOK"}'))).toBe('WEBHOOK');
    });
  });

  describe('delayMinutes', () => {
    it('defaults to 30', () => {
      expect(delayMinutes(node('{}'))).toBe(30);
    });

    it('reads the configured delay (incl. 0)', () => {
      expect(delayMinutes(node('{"delayMinutes":0}'))).toBe(0);
      expect(delayMinutes(node('{"delayMinutes":120}'))).toBe(120);
    });
  });

  describe('webhookMethod', () => {
    it('defaults to POST', () => {
      expect(webhookMethod(node('{}'))).toBe('POST');
    });

    it('reads the configured method', () => {
      expect(webhookMethod(node('{"method":"GET"}'))).toBe('GET');
    });
  });

  describe('categoryEntries', () => {
    it('maps configured category ids to name + color', () => {
      const result = categoryEntries(categories, node('{"categoryIds":["c1","c2"]}'));
      expect(result).toEqual([
        { categoryId: 'c1', label: 'Invoices', color: '#ff0000' },
        { categoryId: 'c2', label: 'Spam', color: '#00ff00' },
      ]);
    });

    it('falls back to the id + muted color for an unknown category', () => {
      const result = categoryEntries(categories, node('{"categoryIds":["zzz"]}'));
      expect(result).toEqual([{ categoryId: 'zzz', label: 'zzz', color: 'var(--fg-muted)' }]);
    });

    it('returns [] when no categories are configured', () => {
      expect(categoryEntries(categories, node('{}'))).toEqual([]);
    });
  });

  describe('label category resolution', () => {
    it('resolves the label category name and color', () => {
      const n = node('{"categoryId":"c1"}', 'LABEL');
      expect(labelCategoryName(categories, n)).toBe('Invoices');
      expect(labelCategoryColor(categories, n)).toBe('#ff0000');
    });

    it('returns empty name and muted color when unset', () => {
      const n = node('{}', 'LABEL');
      expect(labelCategoryName(categories, n)).toBe('');
      expect(labelCategoryColor(categories, n)).toBe('var(--fg-muted)');
    });
  });

  describe('list accessors default to []', () => {
    it('filterChecks', () => {
      expect(filterChecks(node('{}'))).toEqual([]);
      expect(filterChecks(node('{"checks":[{"field":"subject"}]}')).length).toBe(1);
    });

    it('extractions', () => {
      expect(extractions(node('{}'))).toEqual([]);
    });
  });
});
