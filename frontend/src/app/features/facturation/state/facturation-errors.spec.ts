import {HttpErrorResponse} from '@angular/common/http';
import {describe, expect, it} from 'vitest';
import {facturationErrorState, synthesePrintError} from './facturation-errors';
import {exclusionEstValide} from './facturation-exclusion';

const http = (status: number, error: unknown) => new HttpErrorResponse({status, error});

describe('facturationErrorState', () => {
  it('409 numéro de facture déjà pris : message dédié', () => {
    expect(facturationErrorState(http(409, {code: 'NUMERO_DE_FACTURE_DEJA_PRIS', detail: 'FAC-1'})))
      .toEqual({error: 'FACTURATION.ERROR.NUMERO_DEJA_PRIS', errorParams: {detail: 'FAC-1'}});
  });

  it('422 séances sans forfait : nombre donné par le backend (entier ou liste)', () => {
    expect(facturationErrorState(http(422, {
      code: 'SEANCES_SANS_FORFAIT',
      seancesSansForfait: 3,
      detail: 'd'
    })).errorParams)
      .toEqual({count: 3, detail: 'd'});
    expect(facturationErrorState(http(422, {code: 'SEANCES_SANS_FORFAIT', seancesSansForfait: ['a', 'b']})).errorParams)
      .toEqual({count: 2, detail: ''});
  });

  it('400, 403 et 422 : le detail du backend est affiché', () => {
    for (const status of [400, 403, 422]) {
      expect(facturationErrorState(http(status, {detail: 'Délai de 60 jours dépassé'})))
        .toEqual({error: 'Délai de 60 jours dépassé', errorParams: null});
    }
  });

  it('403 sans detail : message dédié (centre différent de la session / profil insuffisant)', () => {
    expect(facturationErrorState(http(403, {})).error).toBe('FACTURATION.ERROR.FORBIDDEN');
  });

  it('422 mois clos : message avec le mois (propriété mois), repli sur le detail puis sur un message générique', () => {
    expect(facturationErrorState(http(422, {code: 'PERIODE_DE_FACTURATION_CLOTUREE', mois: '2026-08', detail: 'd'})))
      .toEqual({error: 'FACTURATION.ERROR.PERIODE_CLOTUREE', errorParams: {mois: '2026-08'}});
    expect(facturationErrorState(http(422, {code: 'PERIODE_DE_FACTURATION_CLOTUREE', detail: 'La periode 2026-08 est cloturee'})))
      .toEqual({error: 'La periode 2026-08 est cloturee', errorParams: null});
    expect(facturationErrorState(http(422, {code: 'PERIODE_DE_FACTURATION_CLOTUREE'})).error)
      .toBe('FACTURATION.ERROR.PERIODE_CLOTUREE_SANS_MOIS');
  });

  it('clôture mensuelle : 409, 422 comptable et 400 ont un message dédié', () => {
    const cas: Array<[number, string, string]> = [
      [409, 'PERIODE_DEJA_CLOTUREE', 'PERIODE_DEJA_CLOTUREE'],
      [409, 'PERIODE_NON_CLOTUREE', 'PERIODE_NON_CLOTUREE'],
      [422, 'PERIODE_COMPTABLE_CLOTUREE', 'PERIODE_COMPTABLE_CLOTUREE'],
      [400, 'MOTIF_DE_REOUVERTURE_OBLIGATOIRE', 'MOTIF_OBLIGATOIRE'],
      [400, 'MOIS_INVALIDE', 'MOIS_INVALIDE'],
      [400, 'ANNEE_INVALIDE', 'ANNEE_INVALIDE'],
    ];
    for (const [status, code, cle] of cas) {
      expect(facturationErrorState(http(status, {code, detail: 'texte du backend'})))
        .toEqual({error: `FACTURATION.ERROR.${cle}`, errorParams: null});
    }
  });

  it('sans detail, repli sur le message existant puis sur le message générique', () => {
    expect(facturationErrorState(http(500, {})).error).toContain('500');
    expect(facturationErrorState({}).error).toBe('FACTURATION.ERROR.GENERIC');
  });
});

describe('synthesePrintError', () => {
  it('403 : detail lu dans le blob d erreur JSON', async () => {
    const blob = new Blob([JSON.stringify({detail: 'Profil insuffisant'})], {type: 'application/json'});
    expect(await synthesePrintError(http(403, blob))).toEqual({message: 'Profil insuffisant', translatable: false});
  });

  it('403 sans detail lisible : message dédié ; autre erreur : message générique d impression', async () => {
    expect(await synthesePrintError(http(403, new Blob(['pas du json'])))).toEqual({
      message: 'FACTURATION.ERROR.SYNTHESE_FORBIDDEN',
      translatable: true
    });
    expect(await synthesePrintError(http(500, null))).toEqual({message: 'COMMON.PRINT_ERROR', translatable: true});
  });
});

describe('exclusionEstValide', () => {
  it('exige un motif, et un commentaire pour AUTRE', () => {
    expect(exclusionEstValide(null, '')).toBe(false);
    expect(exclusionEstValide('MALADIE', '')).toBe(true);
    expect(exclusionEstValide('AUTRE', '   ')).toBe(false);
    expect(exclusionEstValide('AUTRE', 'voyage imprévu')).toBe(true);
  });
});
