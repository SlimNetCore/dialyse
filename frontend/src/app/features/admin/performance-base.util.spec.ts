import {describe, expect, it} from 'vitest';
import {formatDuree, formatPart, formatTaille} from './performance-base.util';

describe('formatTaille', () => {
  it('choisit l\'unité lisible', () => {
    expect(formatTaille(512)).toBe('512 o');
    expect(formatTaille(8192)).toBe('8 Ko');
    expect(formatTaille(13 * 1024 * 1024 + 400_000)).toBe('13,4 Mo');
    expect(formatTaille(1.5 * 1024 ** 3)).toBe('1,5 Go');
    expect(formatTaille(250 * 1024 ** 2)).toBe('250 Mo');
  });

  it('refuse une valeur invalide', () => {
    expect(formatTaille(-5)).toBe('—');
    expect(formatTaille(Number.NaN)).toBe('—');
  });
});

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
