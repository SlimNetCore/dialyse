import {describe, expect, it} from 'vitest';
import {CasePresence, SemainePresence} from '../../core/api/infirmier-api.service';
import {
  absenceAnnulable, aujourdhuiUtc, chevauche, classeStatut, decalerMois, joursOrdonnes, moisCourant, trouverCase,
} from './presence.util';

const caseDe = (salleId: string, creneauId: string, jour: CasePresence['jour']): CasePresence => ({
  salleId, creneauId, jour, date: '2026-09-28', patients: 0, requis: 0, salleIsolement: false, statut: 'COUVERT',
  manque: 0, presents: [], absents: [],
});

describe('presence.util', () => {
  it('retrouve une case par salle, créneau et jour', () => {
    const semaine = {cases: [caseDe('s', 'c', 'LUNDI')]} as unknown as SemainePresence;

    expect(trouverCase(semaine, 's', 'c', 'LUNDI')).toBeDefined();
    expect(trouverCase(semaine, 's', 'c', 'MARDI')).toBeUndefined();
    expect(trouverCase(semaine, 'autre', 'c', 'LUNDI')).toBeUndefined();
  });

  it('associe une classe visuelle à chaque statut', () => {
    expect(classeStatut('SOUS_EFFECTIF')).toBe('under');
    expect(classeStatut('COUVERT')).toBe('covered');
    expect(classeStatut('SANS_PATIENT')).toBe('idle');
    expect(classeStatut('FERME')).toBe('closed');
    expect(classeStatut(undefined)).toBe('closed');
  });

  it('décale un mois en traversant les années', () => {
    expect(decalerMois('2026-10', 1)).toBe('2026-11');
    expect(decalerMois('2026-12', 1)).toBe('2027-01');
    expect(decalerMois('2026-01', -1)).toBe('2025-12');
    expect(decalerMois('2026-03', -15)).toBe('2024-12');
  });

  it('donne le mois courant en UTC', () => {
    expect(moisCourant(new Date('2026-10-02T23:30:00Z'))).toBe('2026-10');
  });

  it('ne propose de retirer qu\'une absence qui n\'a pas commencé', () => {
    expect(aujourdhuiUtc(new Date('2026-10-02T23:59:00Z'))).toBe('2026-10-02');
    expect(absenceAnnulable({debut: '2026-10-03'}, '2026-10-02')).toBe(true);
    expect(absenceAnnulable({debut: '2026-10-02'}, '2026-10-02')).toBe(false);
    expect(absenceAnnulable({debut: '2026-09-30'}, '2026-10-02')).toBe(false);
  });

  it('ordonne les jours du dimanche au samedi', () => {
    expect(joursOrdonnes(['VENDREDI', 'LUNDI', 'DIMANCHE'])).toEqual(['DIMANCHE', 'LUNDI', 'VENDREDI']);
  });

  it('détecte le chevauchement de deux affectations sur le même créneau', () => {
    const existantes = [{creneauId: 'matin', jours: ['LUNDI', 'MERCREDI'] as const}];

    expect(chevauche({creneauId: 'matin', jours: ['MERCREDI']}, existantes)).toBe(true);
    expect(chevauche({creneauId: 'soir', jours: ['MERCREDI']}, existantes)).toBe(false);
    expect(chevauche({creneauId: 'matin', jours: ['MARDI']}, existantes)).toBe(false);
    expect(chevauche({creneauId: 'matin', jours: ['MARDI']}, [])).toBe(false);
  });
});
