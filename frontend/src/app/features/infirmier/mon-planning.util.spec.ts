import {describe, expect, it} from 'vitest';
import {CaseMonPlanning, CreneauPersonnel, MonPlanning} from '../../core/api/infirmier-api.service';
import {JourPlanning, JourSemaine} from '../../core/api/planning-api.service';
import {
  classeMaCase,
  jourParDefautMonPlanning,
  lignesMonJour,
  lignesMonPlanning,
  maCase,
  monCreneau,
} from './mon-planning.util';

const jour = (j: JourSemaine, date: string, ferme = false): JourPlanning =>
  ({jour: j, date, ouvertHebdomadaire: true, fermetureMotif: ferme ? 'Férié' : null});

const creneau = (j: JourSemaine, salleId: string, creneauId: string,
                 situation: CreneauPersonnel['situation'] = 'PREVU'): CreneauPersonnel =>
  ({date: '2026-10-05', jour: j, salleId, creneauId, situation});

function planning(mesCreneaux: CreneauPersonnel[], mesCases: CaseMonPlanning[] = []): MonPlanning {
  return {
    infirmier: {} as MonPlanning['infirmier'], debut: '2026-10-04', fin: '2026-10-10',
    salles: [{id: 's1', nom: 'Salle 1'}, {id: 's2', nom: 'Salle 2'}] as MonPlanning['salles'],
    creneaux: [{id: 'm', libelle: 'Matin'}, {id: 'a', libelle: 'Après-midi'}] as MonPlanning['creneaux'],
    mesCreneaux, mesCases,
    jours: [jour('DIMANCHE', '2026-10-04'), jour('LUNDI', '2026-10-05'), jour('MARDI', '2026-10-06', true),
      jour('MERCREDI', '2026-10-07')],
  };
}

describe('mon-planning.util', () => {
  it('ne garde que mes salles et créneaux, dans l\'ordre salle puis créneau, sans doublon', () => {
    const p = planning([
      creneau('MERCREDI', 's2', 'm'), creneau('LUNDI', 's1', 'a'), creneau('MERCREDI', 's1', 'a'),
    ]);

    expect(lignesMonPlanning(p).map((l) => `${l.salle.id}/${l.creneau.id}`)).toEqual(['s1/a', 's2/m']);
  });

  it('une affectation absente reste dans ma grille (je dois voir ce que je rate)', () => {
    const p = planning([creneau('LUNDI', 's1', 'm', 'ABSENT')]);

    expect(lignesMonPlanning(p)).toHaveLength(1);
    expect(monCreneau(p, 's1', 'm', 'LUNDI')?.situation).toBe('ABSENT');
    expect(monCreneau(p, 's1', 'm', 'MARDI')).toBeUndefined();
  });

  it('liste mes lignes d\'un jour donné', () => {
    const p = planning([creneau('LUNDI', 's1', 'm'), creneau('MERCREDI', 's2', 'a')]);

    expect(lignesMonJour(p, 'LUNDI').map((l) => l.salle.id)).toEqual(['s1']);
    expect(lignesMonJour(p, 'MARDI')).toEqual([]);
  });

  it('retrouve la charge de ma case', () => {
    const c: CaseMonPlanning = {
      date: '2026-10-05', jour: 'LUNDI', salleId: 's1', creneauId: 'm', patients: 6, requis: 2,
      salleIsolement: false, collegues: 1,
    };
    const p = planning([creneau('LUNDI', 's1', 'm')], [c]);

    expect(maCase(p, 's1', 'm', 'LUNDI')).toEqual(c);
    expect(maCase(p, 's2', 'm', 'LUNDI')).toBeUndefined();
  });

  it('choisit aujourd\'hui, sinon mon premier jour de travail, sinon le premier jour ouvert', () => {
    const p = planning([creneau('MERCREDI', 's1', 'm')]);

    expect(jourParDefautMonPlanning(p, '2026-10-05')).toBe('LUNDI');
    expect(jourParDefautMonPlanning(p, '2026-11-01')).toBe('MERCREDI');
    expect(jourParDefautMonPlanning(planning([]), '2026-11-01')).toBe('DIMANCHE');
    expect(jourParDefautMonPlanning({...planning([]), jours: []}, '2026-11-01')).toBeNull();
  });

  it('donne la classe de couleur de la case selon ma situation, le centre fermé primant', () => {
    expect(classeMaCase(creneau('LUNDI', 's1', 'm'), false)).toBe('prevu');
    expect(classeMaCase(creneau('LUNDI', 's1', 'm', 'REMPLACANT'), false)).toBe('remplacant');
    expect(classeMaCase(creneau('LUNDI', 's1', 'm', 'ABSENT'), false)).toBe('absent');
    expect(classeMaCase(undefined, false)).toBe('none');
    expect(classeMaCase(creneau('LUNDI', 's1', 'm'), true)).toBe('closed');
  });
});
