import {describe, expect, it} from 'vitest';
import {
  ETATS_APRES_INTERVENTION,
  ETATS_AVANT_INTERVENTION,
  dureeLabel,
  dureeMinutes,
  localDateToUtcIso,
  localInputToUtcIso,
  prioriteTone,
  utcIsoToLocalDate,
  STATUTS_INDISPONIBLES_PATIENT,
  statutEquipementTone,
} from './gmao-options.util';

describe('états d\'équipement liés aux interventions', () => {
  it('ne permet jamais de réformer depuis une intervention : « À réformer » seulement après', () => {
    expect(ETATS_AVANT_INTERVENTION).not.toContain('REFORME');
    expect(ETATS_AVANT_INTERVENTION).not.toContain('A_REFORMER');
    expect(ETATS_APRES_INTERVENTION).toContain('A_REFORMER');
    expect(ETATS_APRES_INTERVENTION).not.toContain('REFORME');
  });

  it('un équipement en service reste affectable ; « À réformer » ne l\'est plus', () => {
    expect(STATUTS_INDISPONIBLES_PATIENT).not.toContain('EN_SERVICE');
    expect(STATUTS_INDISPONIBLES_PATIENT).toContain('A_REFORMER');
    expect(statutEquipementTone('A_REFORMER')).toBe('danger');
  });
});

describe('durée d\'intervention', () => {
  it('calcule la durée entre début et fin à la minute', () => {
    expect(dureeMinutes('2026-03-10T08:30:00', '2026-03-10T10:45:00')).toBe(135);
    expect(dureeLabel(135)).toBe('2 h 15');
    expect(dureeLabel(45)).toBe('45 min');
  });

  it('retourne 0 pour des dates manquantes ou inversées', () => {
    expect(dureeMinutes(null, '2026-03-10T10:45:00')).toBe(0);
    expect(dureeMinutes('2026-03-10T10:45:00', '2026-03-10T08:30:00')).toBe(0);
    expect(dureeLabel(0)).toBe('');
  });
});

describe('conversion UTC <-> heure locale', () => {
  it('envoie un instant UTC équivalent à la saisie locale et le restitue à l\'identique', () => {
    const iso = localInputToUtcIso('2026-03-10T08:30');
    expect(iso.endsWith('Z')).toBe(true);
    expect(new Date(iso).getTime()).toBe(new Date('2026-03-10T08:30').getTime());
  });

  it('conserve le jour saisi pour une date seule (aller-retour)', () => {
    expect(utcIsoToLocalDate(localDateToUtcIso('2026-03-10'))).toBe('2026-03-10');
  });
});

describe('priorité d\'intervention', () => {
  it('colore les priorités haute et urgente', () => {
    expect(prioriteTone('NORMALE')).toBe('neutral');
    expect(prioriteTone('HAUTE')).toBe('warning');
    expect(prioriteTone('URGENTE')).toBe('danger');
  });
});
