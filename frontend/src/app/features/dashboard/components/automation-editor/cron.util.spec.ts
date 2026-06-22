import {
  parseCronTime,
  parseCronDay,
  parseCronDayOfMonth,
  parseTimeParts,
  buildDailyCron,
  buildWeeklyCron,
  buildMonthlyCron,
} from './cron.util';

describe('cron.util', () => {
  describe('parseCronTime', () => {
    it('reads HH:MM from minute/hour fields', () => {
      expect(parseCronTime('30 14 * * *')).toBe('14:30');
    });

    it('zero-pads single-digit hour and minute', () => {
      expect(parseCronTime('5 9 * * *')).toBe('09:05');
    });

    it('defaults to 09:00 when undefined', () => {
      expect(parseCronTime(undefined)).toBe('09:00');
    });

    it('defaults to 09:00 when too few fields', () => {
      expect(parseCronTime('5')).toBe('09:00');
    });
  });

  describe('parseCronDay', () => {
    it('reads the day-of-week field (index 4)', () => {
      expect(parseCronDay('0 9 * * 3')).toBe(3);
    });

    it('defaults to 1 when undefined or incomplete', () => {
      expect(parseCronDay(undefined)).toBe(1);
      expect(parseCronDay('0 9 * *')).toBe(1);
    });
  });

  describe('parseCronDayOfMonth', () => {
    it('reads the day-of-month field (index 2)', () => {
      expect(parseCronDayOfMonth('0 9 15 * *')).toBe(15);
    });

    it('defaults to 1 when undefined', () => {
      expect(parseCronDayOfMonth(undefined)).toBe(1);
    });
  });

  describe('parseTimeParts', () => {
    it('splits HH:MM into [hour, minute]', () => {
      expect(parseTimeParts('14:30')).toEqual([14, 30]);
    });
  });

  describe('builders', () => {
    it('buildDailyCron', () => {
      expect(buildDailyCron(9, 30)).toBe('30 9 * * *');
    });

    it('buildWeeklyCron', () => {
      expect(buildWeeklyCron(9, 30, 3)).toBe('30 9 * * 3');
    });

    it('buildMonthlyCron', () => {
      expect(buildMonthlyCron(9, 30, 15)).toBe('30 9 15 * *');
    });
  });

  describe('round-trip', () => {
    it('daily cron parses back to the same time', () => {
      const cron = buildDailyCron(14, 30);
      expect(parseCronTime(cron)).toBe('14:30');
    });

    it('weekly cron preserves the day', () => {
      const cron = buildWeeklyCron(8, 0, 5);
      expect(parseCronDay(cron)).toBe(5);
      expect(parseCronTime(cron)).toBe('08:00');
    });

    it('monthly cron preserves the day-of-month', () => {
      const cron = buildMonthlyCron(6, 15, 22);
      expect(parseCronDayOfMonth(cron)).toBe(22);
      expect(parseCronTime(cron)).toBe('06:15');
    });
  });
});
