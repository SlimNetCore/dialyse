import {describe, expect, it} from 'vitest';
import {currentMonthIso, dateRangeFilter, parseDateRange, parseMonth, toSeanceHistoryQuery} from './seance-historique.util';

describe('seance-historique.util', () => {
  it('lit un jour ou une plage de dates, et ignore une saisie invalide', () => {
    expect(parseDateRange('2026-10-04')).toEqual({from: '2026-10-04', to: '2026-10-04'});
    expect(parseDateRange('2026-10-01..2026-10-31')).toEqual({from: '2026-10-01', to: '2026-10-31'});
    expect(parseDateRange('2026-10-01..')).toEqual({from: '2026-10-01', to: null});
    expect(parseDateRange('..2026-10-31')).toEqual({from: null, to: '2026-10-31'});
    expect(parseDateRange('hier')).toEqual({from: null, to: null});
    expect(parseDateRange(undefined)).toEqual({from: null, to: null});
  });

  it('traduit les filtres des colonnes en critères serveur', () => {
    const query = toSeanceHistoryQuery({patient: '  dupont jean ', status: 'validee'}, 'patient', 'asc');
    expect(query).toEqual({
      from: null,
      to: null,
      status: 'VALIDEE',
      q: 'dupont jean',
      sortBy: 'patient',
      sortDir: 'asc'
    });
  });

  it('transmet plusieurs statuts choisis dans la liste, sans doublon ni valeur inconnue', () => {
    expect(toSeanceHistoryQuery({status: 'CREE,signee,NOPE,CREE'}, null, '').status).toBe('CREE,SIGNEE');
  });

  it('la plage de dates de la colonne est envoyée au serveur', () => {
    expect(toSeanceHistoryQuery({dateSeance: '2026-10-01..2026-10-31'}, null, ''))
      .toMatchObject({from: '2026-10-01', to: '2026-10-31'});
  });

  it('ignore un statut inconnu et un texte vide', () => {
    const query = toSeanceHistoryQuery({patient: '   ', status: 'NOPE'}, null, '');
    expect(query.status).toBeNull();
    expect(query.q).toBeNull();
  });

  it('trie par date décroissante par défaut et refuse une colonne non triable côté serveur', () => {
    expect(toSeanceHistoryQuery({}, null, '')).toMatchObject({sortBy: 'dateSeance', sortDir: 'desc'});
    expect(toSeanceHistoryQuery({}, 'forfait', 'asc')).toMatchObject({sortBy: 'dateSeance', sortDir: 'desc'});
    expect(toSeanceHistoryQuery({}, 'status', 'desc')).toMatchObject({sortBy: 'status', sortDir: 'desc'});
  });

  it('la période « du … au … » s\'écrit dans le filtre de date de la colonne (une seule source)', () => {
    expect(dateRangeFilter('2026-10-01', '2026-10-31')).toBe('2026-10-01..2026-10-31');
    expect(dateRangeFilter('2026-10-01', '')).toBe('2026-10-01..');
    expect(dateRangeFilter('', '2026-10-31')).toBe('..2026-10-31');
    expect(dateRangeFilter('', '')).toBe('');
    expect(dateRangeFilter('nope', '')).toBe('');
    expect(toSeanceHistoryQuery({dateSeance: dateRangeFilter('2026-10-01', '')}, null, ''))
      .toMatchObject({from: '2026-10-01', to: null});
  });

  it('lit un mois « AAAA-MM »', () => {
    expect(parseMonth('2026-10')).toEqual({year: 2026, month: 10});
    expect(parseMonth('2026-13')).toBeNull();
    expect(parseMonth('')).toBeNull();
    expect(parseMonth(null)).toBeNull();
    expect(currentMonthIso(new Date(2026, 9, 5))).toBe('2026-10');
  });
});
