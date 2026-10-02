import {describe, expect, it} from 'vitest';
import {CasePresence, SemainePresence} from '../../core/api/infirmier-api.service';
import {CellulePlanning, JourPlanning, JourSemaine, SemainePlanning} from '../../core/api/planning-api.service';
import {construireJournee, jourDeLaSemaine} from './medecin-dashboard.util';

const SALLE_A = {id: 'sA', nom: 'Salle A'};
const SALLE_B = {id: 'sB', nom: 'Salle B'};
const MATIN = {id: 'm', libelle: 'Matin', ordre: 1};
const SOIR = {id: 's', libelle: 'Soir', ordre: 2};

const jours = (ferme: JourSemaine | null, motif: string | null = null): JourPlanning[] =>
  (['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'] as JourSemaine[]).map((jour, i) => ({
    jour,
    date: `2026-09-${27 + i}`,
    ouvertHebdomadaire: jour !== ferme || motif !== null,
    fermetureMotif: jour === ferme ? motif : null,
  }));

const patient = (id: string, nom: string, aRisque = false) => ({patientId: id, nom, generateurCode: 'G01', aRisque});

const cellule = (salleId: string, creneauId: string, jour: JourSemaine, occupants: CellulePlanning['occupants']): CellulePlanning =>
  ({salleId, creneauId, jour, capacite: 2, occupants});

const casePresence = (salleId: string, creneauId: string, jour: JourSemaine, statut: CasePresence['statut'],
                      presents: string[], requis = 1): CasePresence => ({
  salleId, creneauId, jour, date: '2026-09-28', patients: 0, requis, salleIsolement: false, statut, manque: 0,
  presents: presents.map((nom) => ({
    infirmierId: nom,
    nom,
    habiliteIsolement: false,
    remplacant: false,
    remplacementId: null
  })),
  absents: [],
});

function planning(cellules: CellulePlanning[], joursPlanning = jours(null)): SemainePlanning {
  return {
    debut: '2026-09-27', fin: '2026-10-03', jours: joursPlanning, salles: [SALLE_A, SALLE_B], creneaux: [MATIN, SOIR],
    cellules, conflits: [], patientsAReplanifier: 0,
  };
}

function presence(cases: CasePresence[]): SemainePresence {
  return {
    debut: '2026-09-27', fin: '2026-10-03', jours: [], salles: [], creneaux: [], cases, conflits: [],
    patientsParInfirmier: 4, casesSousEffectif: 0,
  };
}

describe('medecin-dashboard.util', () => {
  it('donne le jour de la semaine d\'une date, la semaine commençant le dimanche', () => {
    expect(jourDeLaSemaine('2026-09-27')).toBe('DIMANCHE');
    expect(jourDeLaSemaine('2026-09-28')).toBe('LUNDI');
    expect(jourDeLaSemaine('2026-10-03')).toBe('SAMEDI');
  });

  it('croise patients et infirmiers du jour, créneau par créneau, sans les cases vides', () => {
    const journee = construireJournee(
      planning([
        cellule('sA', 'm', 'LUNDI', [patient('p1', 'Amine'), patient('p2', 'Zahra', true)]),
        cellule('sB', 's', 'LUNDI', [patient('p3', 'Yacine')]),
        cellule('sA', 'm', 'MARDI', [patient('p4', 'Autre jour')]),
      ]),
      presence([
        casePresence('sA', 'm', 'LUNDI', 'COUVERT', ['Benali']),
        casePresence('sB', 's', 'LUNDI', 'COUVERT', ['Cherif']),
      ]),
      '2026-09-28');

    expect(journee.jour).toBe('LUNDI');
    expect(journee.ferme).toBe(false);
    expect(journee.lignes.map((l) => `${l.creneauLibelle}/${l.salleNom}`)).toEqual(['Matin/Salle A', 'Soir/Salle B']);
    expect(journee.lignes[0].patients.map((p) => p.nom)).toEqual(['Amine', 'Zahra']);
    expect(journee.lignes[0].infirmiers.map((n) => n.nom)).toEqual(['Benali']);
    expect(journee.nbPatients).toBe(3);
    expect(journee.nbSousEffectif).toBe(0);
  });

  it('fait remonter les créneaux en sous-effectif et les compte', () => {
    const journee = construireJournee(
      planning([
        cellule('sA', 'm', 'LUNDI', [patient('p1', 'Amine')]),
        cellule('sB', 's', 'LUNDI', [patient('p2', 'Zahra')]),
      ]),
      presence([
        casePresence('sA', 'm', 'LUNDI', 'COUVERT', ['Benali']),
        casePresence('sB', 's', 'LUNDI', 'SOUS_EFFECTIF', [], 2),
      ]),
      '2026-09-28');

    expect(journee.lignes[0].salleNom).toBe('Salle B');
    expect(journee.lignes[0].statut).toBe('SOUS_EFFECTIF');
    expect(journee.nbSousEffectif).toBe(1);
  });

  it('signale un centre fermé (fermeture hebdomadaire ou datée) sans aucune ligne', () => {
    const hebdo = construireJournee(
      planning([cellule('sA', 'm', 'LUNDI', [patient('p1', 'Amine')])], jours('LUNDI')),
      presence([]), '2026-09-28');
    const ferie = construireJournee(
      planning([cellule('sA', 'm', 'LUNDI', [patient('p1', 'Amine')])], jours('LUNDI', 'Férié')),
      presence([]), '2026-09-28');

    expect(hebdo.ferme).toBe(true);
    expect(hebdo.lignes).toEqual([]);
    expect(ferie.ferme).toBe(true);
    expect(ferie.motifFermeture).toBe('Férié');
  });
});
