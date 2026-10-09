import {describe, expect, it} from 'vitest';
import {formatDuree, formatPart} from './performance-base.util';

describe('formatDuree', () => {
  it('affiche les millisecondes, les secondes puis les minutes', () => {
    expect(formatDuree(2.34)).toBe('2,3 ms');
    expect(formatDuree(120.4)).toBe('120 ms');
    expect(formatDuree(3400)).toBe('3,4 s');
    expect(formatDuree(125000)).toBe('2 min 05 s');
  });

  it('refuse une valeur invalide', () => {
    expect(formatDuree(-1)).toBe('—');
    expect(formatDuree(Number.NaN)).toBe('—');
  });
});

describe('formatPart', () => {
  it('formate un pourcentage et signale une part infime', () => {
    expect(formatPart(12.345)).toBe('12,3 %');
    expect(formatPart(0.04)).toBe('< 0,1 %');
    expect(formatPart(0)).toBe('0 %');
  });
});
