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
