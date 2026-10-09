import {CompteItem, LigneModeleItem, ModelePieceItem, SensEcriture} from '../../core/api/comptabilite-api.service';

/** Nombre de propositions affichées sous un champ de compte. */
export const PROPOSITIONS_MAX = 30;
export const LIGNES_MODELE_MAX = 20;
const NUMERO = /^[0-9A-Za-z]{1,20}$/;
const CODE_MODELE = /^[A-Z0-9_-]{1,20}$/;

/** Compte du plan qui porte ce numéro, s'il existe. */
export function compteDuPlan(comptes: readonly CompteItem[], numero: string): CompteItem | undefined {
  const cherche = numero.trim();
  return cherche ? comptes.find((c) => c.numero === cherche) : undefined;
}

/**
 * Comptes proposés pendant la frappe : ceux dont le numéro commence par le texte, puis ceux dont le libellé le
 * contient ; sans texte, le début du plan.
 */
export function filtrerComptes(comptes: readonly CompteItem[], texte: string): CompteItem[] {
  const filtre = texte.trim().toLowerCase();
  if (!filtre) return comptes.slice(0, PROPOSITIONS_MAX);
  const parNumero = comptes.filter((c) => c.numero.toLowerCase().startsWith(filtre));
  const parLibelle = comptes.filter((c) =>
    !c.numero.toLowerCase().startsWith(filtre) && c.libelle.toLowerCase().includes(filtre));
  return [...parNumero, ...parLibelle].slice(0, PROPOSITIONS_MAX);
}

/** Un numéro de compte comporte 1 à 20 lettres ou chiffres ; un libellé est obligatoire (150 caractères au plus). */
export function compteValide(numero: string, libelle: string): boolean {
  const texte = libelle.trim();
  return NUMERO.test(numero.trim()) && texte.length > 0 && texte.length <= 150;
}

// ─── Modèles de pièces ───────────────────────────────────────────────────────

export type ModeleFormModel = {
  code: string;
  libelle: string;
  journal: string;
  actif: boolean;
  lignes: LigneModeleItem[];
};

export function modeleVide(): ModeleFormModel {
  return {
    code: '', libelle: '', journal: '', actif: true,
    lignes: [{sens: 'DEBIT', compte: '', libelle: ''}, {sens: 'CREDIT', compte: '', libelle: ''}],
  };
}

export function modeleDepuis(modele: ModelePieceItem): ModeleFormModel {
  return {
    code: modele.code, libelle: modele.libelle, journal: modele.journal, actif: modele.actif,
    lignes: modele.lignes.map((l) => ({sens: l.sens, compte: l.compte, libelle: l.libelle ?? ''})),
  };
}

export type ErreurModele = 'CODE' | 'LIBELLE' | 'JOURNAL' | 'LIGNES' | 'SENS' | 'COMPTES';

/**
 * Ce qui empêche d'enregistrer un modèle : code mal formé, libellé ou journal absent, moins de 2 lignes (ou plus de
 * 20), pas de ligne au débit ou au crédit, compte absent du plan.
 */
export function erreursModele(m: ModeleFormModel, comptes: readonly CompteItem[],
                              journauxActifs: readonly string[]): ErreurModele[] {
  const erreurs: ErreurModele[] = [];
  if (!CODE_MODELE.test(m.code.trim().toUpperCase())) erreurs.push('CODE');
  if (!m.libelle.trim() || m.libelle.trim().length > 100) erreurs.push('LIBELLE');
  if (!journauxActifs.includes(m.journal)) erreurs.push('JOURNAL');
  if (m.lignes.length < 2 || m.lignes.length > LIGNES_MODELE_MAX) erreurs.push('LIGNES');
  const sens = new Set<SensEcriture>(m.lignes.map((l) => l.sens));
  if (!sens.has('DEBIT') || !sens.has('CREDIT')) erreurs.push('SENS');
  if (m.lignes.some((l) => !compteDuPlan(comptes, l.compte))) erreurs.push('COMPTES');
  return erreurs;
}

// ─── Saisie d'une pièce ──────────────────────────────────────────────────────

/** Montant saisi (virgule ou point), ou 0 s'il est vide ; `null` s'il n'est pas un nombre positif ou nul. */
export function montantSaisi(texte: string): number | null {
  const net = texte.trim().replace(/\s/g, '').replace(',', '.');
  if (!net) return 0;
  const valeur = Number(net);
  return Number.isFinite(valeur) && valeur >= 0 ? Math.round(valeur * 100) / 100 : null;
}

export type TotauxPiece = { debit: number; credit: number; equilibree: boolean; valide: boolean };

/**
 * Totaux d'une pièce en cours de saisie. Elle est valide quand tous les montants sont des nombres, qu'elle a au moins
 * un montant au débit et un au crédit, et que débit = crédit.
 */
export function totauxPiece(lignes: readonly LigneModeleItem[], montants: readonly string[]): TotauxPiece {
  let debit = 0;
  let credit = 0;
  let lisibles = true;
  lignes.forEach((ligne, i) => {
    const montant = montantSaisi(montants[i] ?? '');
    if (montant === null) {
      lisibles = false;
    } else if (ligne.sens === 'DEBIT') {
      debit += montant;
    } else {
      credit += montant;
    }
  });
  debit = Math.round(debit * 100) / 100;
  credit = Math.round(credit * 100) / 100;
  const equilibree = debit === credit;
  return {debit, credit, equilibree, valide: lisibles && equilibree && debit > 0};
}
