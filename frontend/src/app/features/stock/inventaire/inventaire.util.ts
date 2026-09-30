import {Inventaire, LigneInventaire} from '../../../core/api/inventaire-api.service';

/** Motifs d'écart proposés (codes stockés, libellés traduits). */
export const MOTIFS_ECART = ['CASSE', 'PEREMPTION', 'ERREUR_SAISIE', 'PERTE', 'NON_ENREGISTRE', 'RETOUR', 'AUTRE'] as const;

export type FiltreLignes = 'TOUTES' | 'A_COMPTER' | 'ECARTS' | 'COMPTEES';

/** Lignes visibles selon le filtre et la recherche (code, libellé, n° de lot). */
export function filtrerLignes(lignes: LigneInventaire[], filtre: FiltreLignes, recherche: string): LigneInventaire[] {
  const terme = recherche.trim().toLowerCase();
  return lignes.filter((l) => {
    if (filtre === 'A_COMPTER' && l.quantiteComptee !== null) return false;
    if (filtre === 'COMPTEES' && l.quantiteComptee === null) return false;
    if (filtre === 'ECARTS' && !(l.ecart !== null && l.ecart !== 0)) return false;
    if (!terme) return true;
    return [l.articleCode, l.articleLibelle, l.numeroLot].some((v) => (v ?? '').toLowerCase().includes(terme));
  });
}

/** Avancement du comptage, en pourcentage entier. */
export function progression(inv: Pick<Inventaire, 'lignes' | 'comptees'> | null): number {
  if (!inv || inv.lignes === 0) return 0;
  return Math.round((inv.comptees / inv.lignes) * 100);
}

/** Alerte de péremption d'un lot : périmé, à moins de 90 jours, ou sans objet. */
export function etatPeremption(date: string | null, today: Date = new Date()): 'EXPIRE' | 'PROCHE' | null {
  if (!date) return null;
  const peremption = new Date(`${date}T00:00:00`);
  const jour = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const jours = Math.round((peremption.getTime() - jour.getTime()) / 86_400_000);
  if (jours < 0) return 'EXPIRE';
  return jours <= 90 ? 'PROCHE' : null;
}

/** Écart calculé côté écran pendant la saisie (avant l'enregistrement). */
export function ecart(ligne: LigneInventaire, saisie: number | null): number | null {
  return saisie === null ? null : Number((saisie - ligne.quantiteTheorique).toFixed(3));
}

