import {describe, expect, it} from 'vitest';
import {CellulePlanning, JourPlanning, SemainePlanning} from '../../core/api/planning-api.service';
import {
  absenceDe,
  cellule,
  dateDepuisAdresse,
  decalerJours,
  etatCellule,
  evenementRafraichitPlanning,
  jourFerme,
  jourParDefaut,
  lignesDuJour,
  peutDeclarerAbsence,
  classeOccupant,
  seanceRealiseeDe,
} from './planning.util';

const jour = (ouvert: boolean, motif: string | null): JourPlanning =>
  ({jour: 'LUNDI', date: '2026-09-28', ouvertHebdomadaire: ouvert, fermetureMotif: motif});

const cell = (capacite: number, occupants: number): CellulePlanning => ({
  salleId: 's', creneauId: 'c', jour: 'LUNDI', capacite,
  occupants: Array.from({length: occupants}, (_, i) => ({
    patientId: `p${i}`,
    nom: `P${i}`,
    generateurCode: null,
    aRisque: false
  })),
});

describe('planning.util', () => {
  it('décale une date de semaines entières, y compris à travers un changement d\'heure', () => {
    expect(decalerJours('2026-09-27', 7)).toBe('2026-10-04');
    expect(decalerJours('2026-03-29', -7)).toBe('2026-03-22');
    expect(decalerJours('2026-12-28', 7)).toBe('2027-01-04');
  });

  it('un jour est fermé s\'il n\'est pas ouvert chaque semaine ou s\'il a une fermeture datée', () => {
    expect(jourFerme(jour(true, null))).toBe(false);
    expect(jourFerme(jour(false, null))).toBe(true);
    expect(jourFerme(jour(true, 'Férié'))).toBe(true);
    expect(jourFerme(jour(true, ''))).toBe(true);
  });

  it('classe l\'état d\'une cellule', () => {
    expect(etatCellule(undefined)).toBe('closed');
    expect(etatCellule(cell(0, 0))).toBe('closed');
    expect(etatCellule(cell(2, 0))).toBe('empty');
    expect(etatCellule(cell(2, 1))).toBe('partial');
    expect(etatCellule(cell(2, 2))).toBe('full');
    expect(etatCellule(cell(1, 2))).toBe('over');
    expect(etatCellule(cell(0, 1))).toBe('over');
  });

  it('retrouve une cellule par salle, créneau et jour', () => {
    const semaine = {cellules: [cell(1, 0)]} as unknown as SemainePlanning;
    expect(cellule(semaine, 's', 'c', 'LUNDI')).toBeDefined();
    expect(cellule(semaine, 's', 'c', 'MARDI')).toBeUndefined();
    expect(cellule(semaine, 'autre', 'c', 'LUNDI')).toBeUndefined();
  });
});

describe('planning.util — absences et vue du jour', () => {
  const jours: JourPlanning[] = [
    {jour: 'DIMANCHE', date: '2026-09-27', ouvertHebdomadaire: true, fermetureMotif: null},
    {jour: 'LUNDI', date: '2026-09-28', ouvertHebdomadaire: true, fermetureMotif: null},
    {jour: 'MARDI', date: '2026-09-29', ouvertHebdomadaire: false, fermetureMotif: null},
  ];
  const semaine = (cellules: CellulePlanning[]): SemainePlanning => ({
    debut: '2026-09-27', fin: '2026-10-03', jours,
    salles: [{id: 's1', nom: 'Salle 1'}, {id: 's2', nom: 'Salle 2'}],
    creneaux: [{id: 'c1', libelle: 'Matin', ordre: 1}],
    cellules, conflits: [], patientsAReplanifier: 0,
  });

  it('autorise la déclaration pour un jour ouvert passé ou du jour, jamais futur ni fermé', () => {
    expect(peutDeclarerAbsence('2026-09-28', '2026-09-28', false)).toBe(true);
    expect(peutDeclarerAbsence('2026-09-27', '2026-09-28', false)).toBe(true);
    expect(peutDeclarerAbsence('2026-09-29', '2026-09-28', false)).toBe(false);
    expect(peutDeclarerAbsence('2026-09-27', '2026-09-28', true)).toBe(false);
  });

  it('retrouve l\'absence d\'un patient à une date précise', () => {
    const absences = [{
      absenceId: 'a',
      patientId: 'p1',
      dateSeance: '2026-09-28',
      statut: 'JUSTIFIEE' as const,
      motif: null
    }];
    expect(absenceDe(absences, 'p1', '2026-09-28')?.absenceId).toBe('a');
    expect(absenceDe(absences, 'p1', '2026-09-29')).toBeUndefined();
    expect(absenceDe(absences, 'p2', '2026-09-28')).toBeUndefined();
  });

  it('retrouve la séance réalisée (validée) d\'un patient à une date précise', () => {
    const seances = [{patientId: 'p1', dateSeance: '2026-09-28'}];
    expect(seanceRealiseeDe(seances, 'p1', '2026-09-28')).toBeDefined();
    expect(seanceRealiseeDe(seances, 'p1', '2026-09-29')).toBeUndefined();
    expect(seanceRealiseeDe(seances, 'p2', '2026-09-28')).toBeUndefined();
  });

  it('choisit aujourd\'hui s\'il est dans la semaine, sinon le premier jour ouvert', () => {
    expect(jourParDefaut(semaine([]), '2026-09-28')).toBe('LUNDI');
    expect(jourParDefaut(semaine([]), '2026-12-01')).toBe('DIMANCHE');
  });

  it('ne garde du jour que les salles et créneaux utiles', () => {
    const vide = {...cell(0, 0), salleId: 's2', creneauId: 'c1', jour: 'LUNDI' as const};
    const utile = {...cell(3, 1), salleId: 's1', creneauId: 'c1', jour: 'LUNDI' as const};
    const autreJour = {...cell(3, 1), salleId: 's1', creneauId: 'c1', jour: 'MARDI' as const};

    const lignes = lignesDuJour(semaine([vide, utile, autreJour]), 'LUNDI');

    expect(lignes.map((l) => l.salle.id)).toEqual(['s1']);
    expect(lignes[0].cellule.capacite).toBe(3);
  });
});

describe('classeOccupant', () => {
  it('distingue une séance réalisée hors du planning actuel du patient', () => {
    expect(classeOccupant({aRisque: false, realiseeHorsPlanning: true}, true, undefined)).toBe('done hors');
    expect(classeOccupant({aRisque: true, realiseeHorsPlanning: false}, true, undefined)).toBe('done');
  });

  it('colore une absence selon son statut, puis le risque', () => {
    expect(classeOccupant({aRisque: true}, false, {statut: 'NON_JUSTIFIEE'} as never)).toBe('absent non_justifiee');
    expect(classeOccupant({aRisque: true}, false, undefined)).toBe('risk');
    expect(classeOccupant({aRisque: false}, false, undefined)).toBe('');
  });

  it('recharge le planning pour les évènements qui le changent (séance, absence, déplacement, placement)', () => {
    for (const type of ['SEANCE_CREATED', 'SEANCE_VALIDATED', 'SEANCE_SUPPRIMEE', 'SEANCE_MEDICAL_SAVED', 'PATIENT_SCANNED',
      'PATIENT_UPDATED', 'SEANCES_DEPLACEES', 'GENERATEUR_INDISPONIBLE', 'INFIRMIER_ABSENCE_DECLAREE',
      'INFIRMIER_ABSENCE_ENREGISTREE', 'ABSENCES_A_QUALIFIER']) {
      expect(evenementRafraichitPlanning(type), type).toBe(true);
    }
    expect(evenementRafraichitPlanning('SAISIE_INFIRMIER', {saisie: 'ABSENCE'})).toBe(true);
  });

  it('ne recharge pas le planning pour les évènements sans effet sur lui', () => {
    for (const type of ['STOCK_MOVEMENT_CHANGED', 'PEC_VALIDATED', 'OPTIMISATION_PROPOSITION', 'INFIRMIER_SOUS_EFFECTIF']) {
      expect(evenementRafraichitPlanning(type), type).toBe(false);
    }
    expect(evenementRafraichitPlanning('SAISIE_INFIRMIER', {saisie: 'PARAMEDICAL'})).toBe(false);
    expect(evenementRafraichitPlanning('SAISIE_INFIRMIER')).toBe(false);
  });
});


describe('dateDepuisAdresse', () => {
  it('accepte un jour valide et ignore le reste', () => {
    expect(dateDepuisAdresse('2026-10-19')).toBe('2026-10-19');
    expect(dateDepuisAdresse('19/10/2026')).toBeNull();
    expect(dateDepuisAdresse('2026-13-45')).toBeNull();
    expect(dateDepuisAdresse(null)).toBeNull();
  });
});
