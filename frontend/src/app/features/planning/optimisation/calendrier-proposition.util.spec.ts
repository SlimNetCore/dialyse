import {describe, expect, it} from 'vitest';
import {CaseCalendrier, JourCalendrier} from '../../../core/api/planning-optimisation-api.service';
import {
  classeJour,
  entetesJours,
  finDeSemaine,
  jourParDefaut,
  jourVide,
  lignesDuJour,
  semaineVoisine,
} from './calendrier-proposition.util';

const JOURS = ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'] as const;

function jour(index: number, surcharge: Partial<JourCalendrier> = {}): JourCalendrier {
  return {
    jour: JOURS[index], date: `2026-09-${27 + index}`, ferme: false, motifFermeture: null, requis: 0, manque: 0,
    patients: [], infirmiers: [], ...surcharge,
  };
}

const patient = {
  patientId: 'p',
  nom: 'Benali',
  generateurCode: 'A-G1',
  aRisque: false,
  deplace: false,
  temporaire: false
};

function ligne(salle: string, actifs: Record<number, Partial<JourCalendrier>> = {}): CaseCalendrier {
  return {
    semaineDebut: '2026-09-27', salleId: salle, salleNom: salle, salleOrdre: 1, creneauId: 'c', creneauLibelle: 'Matin',
    creneauOrdre: 1, jours: JOURS.map((_, i) => jour(i, actifs[i] ?? {})),
  };
}

describe('calendrier-proposition.util', () => {
  it('lit les colonnes de la semaine sur la première ligne', () => {
    const entetes = entetesJours([ligne('A', {2: {ferme: true, motifFermeture: 'Férié'}})]);

    expect(entetes).toHaveLength(7);
    expect(entetes[0]).toMatchObject({index: 0, jour: 'DIMANCHE', date: '2026-09-27'});
    expect(entetes[2]).toMatchObject({ferme: true, motifFermeture: 'Férié'});
    expect(entetesJours([])).toEqual([]);
  });

  it('reconnaît un jour vide, fermé ou occupé', () => {
    expect(jourVide(jour(0))).toBe(true);
    expect(jourVide(undefined)).toBe(true);
    expect(jourVide(jour(0, {ferme: true}))).toBe(false);
    expect(jourVide(jour(0, {patients: [patient]}))).toBe(false);
    expect(jourVide(jour(0, {infirmiers: [{nom: 'Sara', situation: 'PREVU'}]}))).toBe(false);
  });

  it('ne garde pour un jour que les lignes qui ont de l\'activité', () => {
    const cases = [ligne('A', {1: {patients: [patient]}}), ligne('B', {3: {patients: [patient]}})];

    expect(lignesDuJour(cases, 1).map((c) => c.salleId)).toEqual(['A']);
    expect(lignesDuJour(cases, 3).map((c) => c.salleId)).toEqual(['B']);
    expect(lignesDuJour(cases, 5)).toEqual([]);
  });

  it('choisit aujourd\'hui s\'il a de l\'activité, sinon le premier jour actif, sinon dimanche', () => {
    const cases = [ligne('A', {2: {patients: [patient]}, 4: {patients: [patient]}})];

    expect(jourParDefaut(cases, '2026-09-29')).toBe(2);
    expect(jourParDefaut(cases, '2026-09-28')).toBe(2);
    expect(jourParDefaut(cases, '2026-12-01')).toBe(2);
    expect(jourParDefaut([ligne('B')], '2026-09-29')).toBe(0);
    expect(jourParDefaut([], '2026-09-29')).toBe(0);
  });

  it('colore la case : fermée, vide, avec manque d\'infirmiers ou couverte', () => {
    expect(classeJour(jour(0, {ferme: true}))).toBe('closed');
    expect(classeJour(jour(0))).toBe('empty');
    expect(classeJour(undefined)).toBe('empty');
    expect(classeJour(jour(0, {patients: [patient], manque: 1}))).toBe('manque');
    expect(classeJour(jour(0, {patients: [patient], manque: 0}))).toBe('ok');
  });

  it('navigue entre les semaines de l\'horizon sans dépasser les extrémités', () => {
    const semaines = ['2026-09-27', '2026-10-04', '2026-10-11'];

    expect(semaineVoisine(semaines, '2026-10-04', 1)).toBe('2026-10-11');
    expect(semaineVoisine(semaines, '2026-10-04', -1)).toBe('2026-09-27');
    expect(semaineVoisine(semaines, '2026-09-27', -1)).toBeNull();
    expect(semaineVoisine(semaines, '2026-10-11', 1)).toBeNull();
    expect(semaineVoisine(semaines, null, 1)).toBeNull();
  });

  it('calcule la fin de semaine (samedi) sans effet de fuseau', () => {
    expect(finDeSemaine('2026-09-27')).toBe('2026-10-03');
    expect(finDeSemaine('2026-12-27')).toBe('2027-01-02');
  });
});
