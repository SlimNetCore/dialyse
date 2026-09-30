import {firstValueFrom, Observable} from 'rxjs';
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

/**
 * Ouvre un PDF dans un nouvel onglet (impression depuis la visionneuse du navigateur). L'onglet est ouvert avant
 * la requête pour ne pas être bloqué ; si le navigateur refuse, le PDF est téléchargé.
 * @returns {@code null} si le document est ouvert, sinon le message d'erreur du serveur (ou {@code ''}).
 */
export function openPdf(source: Observable<Blob>, filename: string): Promise<string | null> {
  const tab = window.open('', '_blank');
  return firstValueFrom(source).then((blob) => {
    const url = URL.createObjectURL(new Blob([blob], {type: 'application/pdf'}));
    if (tab) {
      tab.location.href = url;
    } else {
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return null;
  }).catch(async (err: unknown) => {
    tab?.close();
    return blobErrorMessage(err);
  });
}

/** Message d'un ProblemDetail renvoyé dans une réponse attendue en binaire (blob). */
async function blobErrorMessage(err: unknown): Promise<string> {
  const body = (err as { error?: unknown })?.error;
  if (!(body instanceof Blob)) return '';
  try {
    const json = JSON.parse(await body.text()) as { detail?: string };
    return json.detail ?? '';
  } catch {
    return '';
  }
}




