import {describe, expect, it} from 'vitest';
import type {Societe} from '../../../core/api/societe-api.service';
import {
  centresSummary,
  emptyCentreForm,
  emptySocieteForm,
  isCentreFormValid,
  isSocieteFormValid,
  location,
  MAX_LOGO_BYTES,
  precheckLogoFile,
  societeToForm,
  toCentrePayload,
  toSocietePayload,
} from './societe.util';

describe('societe.util', () => {
  it('transforme les champs vides en null et retire les espaces', () => {
    const payload = toSocietePayload({
      ...emptySocieteForm(), code: ' GHNE ', raisonSociale: ' Groupe ', email: '  ', telephone: ' 0555 12 34 56 ',
      piedDePage: ' SARL au capital de 1 000 000 DA ',
    });
    expect(payload).toMatchObject({
      code: 'GHNE', raisonSociale: 'Groupe', email: null, telephone: '0555 12 34 56', nif: null,
      piedDePage: 'SARL au capital de 1 000 000 DA',
    });
    expect(toSocietePayload(emptySocieteForm()).piedDePage).toBeNull();
  });

  it('construit le payload d\'un centre', () => {
    const payload = toCentrePayload({...emptyCentreForm(), code: 'C1', nom: ' Centre 1 ', ville: 'Alger'});
    expect(payload).toEqual({
      code: 'C1',
      nom: 'Centre 1',
      adresse: null,
      ville: 'Alger',
      wilaya: null,
      telephone: null,
      email: null,
      siteWeb: null,
    });
  });

  it('exige un code et un nom', () => {
    expect(isSocieteFormValid(emptySocieteForm())).toBe(false);
    expect(isSocieteFormValid({...emptySocieteForm(), code: 'S', raisonSociale: 'Société'})).toBe(true);
    expect(isCentreFormValid({...emptyCentreForm(), code: 'C'})).toBe(false);
    expect(isCentreFormValid({...emptyCentreForm(), code: 'C', nom: 'Centre'})).toBe(true);
  });

  it('remplit le formulaire depuis une société (null → chaîne vide)', () => {
    const societe = {
      code: 'GHNE',
      raisonSociale: 'Groupe',
      nif: null,
      email: 'a@b.dz',
      centres: []
    } as unknown as Societe;
    expect(societeToForm(societe)).toMatchObject({code: 'GHNE', nif: '', email: 'a@b.dz'});
  });

  it('contrôle le logo avant envoi', () => {
    expect(precheckLogoFile({type: 'image/png', size: 1000})).toBeNull();
    expect(precheckLogoFile({type: 'image/jpeg', size: 1000})).toBeNull();
    expect(precheckLogoFile({type: 'image/svg+xml', size: 1000})).toBe('FILE_TYPE');
    expect(precheckLogoFile({type: 'image/png', size: 0})).toBe('EMPTY');
    expect(precheckLogoFile({type: 'image/png', size: MAX_LOGO_BYTES + 1})).toBe('TOO_LARGE');
  });

  it('formate la localisation', () => {
    expect(location({ville: 'Annaba', wilaya: 'Annaba'})).toBe('Annaba · Annaba');
    expect(location({ville: null, wilaya: 'Alger'})).toBe('Alger');
    expect(location({ville: null, wilaya: null})).toBe('—');
  });

  it('résume le nombre de centres actifs', () => {
    expect(centresSummary({centres: [{actif: true}, {actif: false}, {actif: true}]} as unknown as Societe))
      .toEqual({actifs: 2, total: 3});
  });
});
