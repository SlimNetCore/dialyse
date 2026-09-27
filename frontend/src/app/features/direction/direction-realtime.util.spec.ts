import {describe, expect, it} from 'vitest';
import {DashboardChange} from '../../core/api/direction-api.service';
import {alertTransitions, delta, formatDelta, groupChanges, metricKey, timeAgo} from './direction-realtime.util';

const change = (centerId: string, family: string, name: string, before: number, after: number): DashboardChange =>
  ({centerId, centre: `Centre ${centerId}`, family, name, before, after});

describe('direction-realtime.util', () => {
  it('regroupe les changements par centre et par famille', () => {
    const groups = groupChanges([
      change('a', 'SEANCES', 'seances', 1, 2),
      change('a', 'FINANCE', 'caTtc', 10, 20),
      change('b', 'SEANCES', 'seances', 3, 4),
      change('a', 'FINANCE', 'factures', 1, 2),
    ]);
    expect(groups.map((g) => g.key)).toEqual(['a|SEANCES', 'a|FINANCE', 'b|SEANCES']);
    expect(groups[1].changes.map((c) => c.name)).toEqual(['caTtc', 'factures']);
  });

  it('calcule et formate la variation avec son signe', () => {
    expect(delta(change('a', 'FINANCE', 'caTtc', 100, 250))).toBe(150);
    expect(formatDelta(150)).toBe('+150');
    expect(formatDelta(-2)).toBe('-2');
    expect(formatDelta(0.125)).toBe('+0.13');
  });

  it('exprime l’ancienneté dans la langue, avec repli sur le français', () => {
    const now = new Date('2026-09-27T10:00:00Z').getTime();
    expect(timeAgo('2026-09-27T09:59:50Z', now, 'fr')).toMatch(/maintenant/i);
    expect(timeAgo('2026-09-27T09:57:00Z', now, 'fr')).toMatch(/3\s*min/);
    expect(timeAgo('2026-09-27T07:00:00Z', now, 'en')).toMatch(/3\s*hr/);
    expect(timeAgo('2026-09-25T10:00:00Z', now, 'kab')).toMatch(/2/); // kabyle inconnu d'Intl : français
  });

  it('réduit un indicateur préfixé à sa clé de traduction', () => {
    expect(metricKey('caHt:CNAS')).toBe('caHt');
    expect(metricKey('seances')).toBe('seances');
  });

  it('distingue les alertes apparues des alertes disparues', () => {
    const [group] = groupChanges([
      change('a', 'ALERTES', 'LOTS_PERIMES', 0, 2),
      change('a', 'ALERTES', 'STOCK_SOUS_SEUIL', 3, 0),
    ]);
    expect(alertTransitions(group)).toEqual([
      {code: 'LOTS_PERIMES', raised: true},
      {code: 'STOCK_SOUS_SEUIL', raised: false},
    ]);
  });
});
