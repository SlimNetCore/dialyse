import {describe, expect, it} from 'vitest';
import {
  generatorNeedsAttention,
  initials,
  parseDecimal,
  pendingWindow,
  QUICK_ARTICLES_MAX,
  quickArticleIds,
  todayIsoDate,
  toNullableText,
  weightLossKg,
} from './station.util';

describe('station.util', () => {
  it('formate la date du jour en heure locale (pas en UTC)', () => {
    expect(todayIsoDate(new Date(2026, 9, 4, 0, 30))).toBe('2026-10-04');
    expect(todayIsoDate(new Date(2026, 0, 9, 23, 59))).toBe('2026-01-09');
  });

  it('lit un nombre avec virgule ou point et ignore le vide ou l\'invalide', () => {
    expect(parseDecimal('71,2')).toBe(71.2);
    expect(parseDecimal(' 240 ')).toBe(240);
    expect(parseDecimal('')).toBeNull();
    expect(parseDecimal(null)).toBeNull();
    expect(parseDecimal('abc')).toBeNull();
  });

  it('propose à la régularisation les 7 jours qui précèdent aujourd\'hui, hier inclus', () => {
    expect(pendingWindow(new Date(2026, 9, 5, 8, 0))).toEqual({from: '2026-09-28', to: '2026-10-04'});
    expect(pendingWindow(new Date(2026, 0, 3))).toEqual({from: '2025-12-27', to: '2026-01-02'});
  });

  it('calcule la perte de poids d\'une séance passée', () => {
    expect(weightLossKg(72.4, 69.9)).toBe(2.5);
    expect(weightLossKg(70, null)).toBeNull();
    expect(weightLossKg(undefined, 69)).toBeNull();
  });

  it('compose les initiales de l\'avatar', () => {
    expect(initials('Dupont', 'Jean')).toBe('DJ');
    expect(initials(' benali', null)).toBe('B');
    expect(initials('', undefined)).toBe('?');
  });

  it('transforme un texte vide en null', () => {
    expect(toNullableText('  ')).toBeNull();
    expect(toNullableText(' hépariné ')).toBe('hépariné');
  });

  it('n\'alerte que pour un état de générateur connu et différent de « en service »', () => {
    expect(generatorNeedsAttention(undefined)).toBe(false);
    expect(generatorNeedsAttention('')).toBe(false);
    expect(generatorNeedsAttention('EN_SERVICE')).toBe(false);
    expect(generatorNeedsAttention('fonctionnel')).toBe(false);
    expect(generatorNeedsAttention('EN_MAINTENANCE')).toBe(true);
    expect(generatorNeedsAttention('HORS_SERVICE')).toBe(true);
  });

  it('classe les articles par quantité sortie décroissante et limite la liste', () => {
    const sorties = Array.from({length: 9}, (_, i) => ({articleId: `a${i}`, quantiteTotale: i}));
    const ids = quickArticleIds(sorties);
    expect(ids).toHaveLength(QUICK_ARTICLES_MAX);
    expect(ids[0]).toBe('a8');
    expect(ids[QUICK_ARTICLES_MAX - 1]).toBe('a3');
  });
});
