import {describe, expect, it} from 'vitest';
import {actionPossible, actionValide, estModifiable, peutDeclarerParRole} from './absences-patients.util';

const vide = {motif: '' as const, commentaire: '', dateRattrapage: ''};

describe('absences-patients util', () => {
  it('seules les absences à qualifier ou qualifiées sont modifiables', () => {
    expect(['A_QUALIFIER', 'JUSTIFIEE', 'NON_JUSTIFIEE'].every((s) => estModifiable(s as never))).toBe(true);
    expect(estModifiable('RATTRAPEE')).toBe(false);
    expect(estModifiable('ANNULEE')).toBe(false);
  });

  it('réserve la correction d\'une absence qualifiée à l\'administrateur et au médecin', () => {
    expect(actionPossible('qualifier', 'A_QUALIFIER', false)).toBe(true);
    expect(actionPossible('annuler', 'A_QUALIFIER', false)).toBe(true);
    expect(actionPossible('qualifier', 'JUSTIFIEE', false)).toBe(false);
    expect(actionPossible('annuler', 'NON_JUSTIFIEE', false)).toBe(false);
    expect(actionPossible('qualifier', 'JUSTIFIEE', true)).toBe(true);
    expect(actionPossible('rattrapage', 'JUSTIFIEE', false)).toBe(true);
    expect(actionPossible('rattrapage', 'RATTRAPEE', true)).toBe(false);
    expect(actionPossible('annuler', 'ANNULEE', true)).toBe(false);
  });

  it('exige le motif, et un commentaire pour « Autre » ou pour une requalification', () => {
    expect(actionValide('qualifier', 'A_QUALIFIER', vide)).toBe(false);
    expect(actionValide('qualifier', 'A_QUALIFIER', {...vide, motif: 'MALADIE'})).toBe(true);
    expect(actionValide('qualifier', 'A_QUALIFIER', {...vide, motif: 'AUTRE'})).toBe(false);
    expect(actionValide('qualifier', 'A_QUALIFIER', {...vide, motif: 'AUTRE', commentaire: ' précision '})).toBe(true);
    expect(actionValide('qualifier', 'JUSTIFIEE', {...vide, motif: 'VOYAGE'})).toBe(false);
    expect(actionValide('qualifier', 'JUSTIFIEE', {...vide, motif: 'VOYAGE', commentaire: 'Contrôle'})).toBe(true);
  });

  it('exige la date de rattrapage et le commentaire d\'annulation', () => {
    expect(actionValide('rattrapage', 'A_QUALIFIER', vide)).toBe(false);
    expect(actionValide('rattrapage', 'A_QUALIFIER', {...vide, dateRattrapage: '2026-09-30'})).toBe(true);
    expect(actionValide('annuler', 'A_QUALIFIER', {...vide, commentaire: '  '})).toBe(false);
    expect(actionValide('annuler', 'A_QUALIFIER', {...vide, commentaire: 'Erreur de saisie'})).toBe(true);
  });

  it('réserve la déclaration d\'une absence à l\'administrateur, au secrétariat et à l\'infirmier, jamais au médecin seul', () => {
    const avec = (...roles: string[]) => peutDeclarerParRole((r) => roles.includes(r));

    expect(avec('ADMIN')).toBe(true);
    expect(avec('SECRETAIRE')).toBe(true);
    expect(avec('INFIRMIER')).toBe(true);
    expect(avec('MEDECIN')).toBe(false);
    expect(avec('MEDECIN', 'ADMIN')).toBe(true);
    expect(avec()).toBe(false);
  });
});
