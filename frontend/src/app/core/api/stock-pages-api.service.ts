import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PageServeur} from './page-serveur';
import {
  DemiJournee,
  LigneSortiePourSeances,
  PmpExplanationStep,
  SortiePourSeances,
  StatutSortiePourSeances,
  StockValoriseItem,
} from './stock-api.service';

/** Pièce de la liste paginée des sorties pour séances (sans ses lignes, avec leur nombre) — contrat E2. */
export interface PieceSortiePourSeancesResume {
  id: string;
  centerId: string;
  reference: string;
  jour: string;
  salle: string | null;
  demiJournee: DemiJournee | null;
  statut: StatutSortiePourSeances;
  enregistreeLe: string;
  enregistreePar: string | null;
  valideeLe: string | null;
  valideePar: string | null;
  nombreDeLignes: number;
}

/** Ligne d'une pièce avec le code et le libellé de l'article — contrat E3. */
export interface LigneSortiePourSeancesDetaillee extends LigneSortiePourSeances {
  articleCode: string | null;
  articleLibelle: string | null;
}

/** Page de l'explication du PMP : étapes paginées + résumé à côté de l'enveloppe — contrat E5. */
export interface PageDExplicationDuPmp extends PageServeur<PmpExplanationStep> {
  articleId: string;
  code: string;
  libelle: string;
  methode: string;
  quantiteFinale: number;
  valeurFinale: number;
  pmpFinal: number;
}

/** Valeurs de `SortiePourSeances.Statut` (filtre énuméré `statut` de E2). */
export const STATUTS_SORTIE_POUR_SEANCES: readonly StatutSortiePourSeances[] = ['ENREGISTREE', 'RAPPROCHEE'];

/**
 * Listes paginées côté serveur de l'état du stock (contrat `pagination-serveur`, E1, E2, E3, E5) :
 * `GET <liste>/page?centerId&page&size&sort&<filtres>` ⇒ `{items, total, page, size}`.
 */
@Injectable({providedIn: 'root'})
export class StockPagesApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/stock`;

  /** E1 — tri `code|libelle|quantite|pmp|valeur` ; filtres texte `code`, `libelle`, numériques `quantite`, `pmp`, `valeur`. */
  etatDuStock(centerId: string, parametres: Record<string, string>): Observable<PageServeur<StockValoriseItem>> {
    return this.http.get<PageServeur<StockValoriseItem>>(`${this.base}/dashboard/stock-valorise/page`,
      {params: {...parametres, centerId}});
  }

  /** E2 — tri `reference|jour|statut|lignes|enregistreeLe` ; filtres `reference` (texte), `statut` (énumération). */
  piecesPourSeances(centerId: string, parametres: Record<string, string>):
    Observable<PageServeur<PieceSortiePourSeancesResume>> {
    return this.http.get<PageServeur<PieceSortiePourSeancesResume>>(`${this.base}/sorties-pour-seances/page`,
      {params: {...parametres, centerId}});
  }

  /** E3 — tri `article|sortie|consomme|ecart` ; filtre `article` (code ou libellé). */
  lignesDeLaPiece(centerId: string, pieceId: string, parametres: Record<string, string>):
    Observable<PageServeur<LigneSortiePourSeancesDetaillee>> {
    return this.http.get<PageServeur<LigneSortiePourSeancesDetaillee>>(
      `${this.base}/sorties-pour-seances/${pieceId}/lignes/page`, {params: {...parametres, centerId}});
  }

  /** Pièce complète (avec toutes ses lignes) : `GET /sorties-pour-seances/{id}` existant (contrat E2). */
  pieceDeSortie(centerId: string, pieceId: string): Observable<SortiePourSeances> {
    return this.http.get<SortiePourSeances>(`${this.base}/sorties-pour-seances/${pieceId}`, {params: {centerId}});
  }

  /** E5 — aucun tri ni filtre (ordre chronologique du rejeu). */
  explicationDuPmp(centerId: string, articleId: string, page: number, size: number): Observable<PageDExplicationDuPmp> {
    return this.http.get<PageDExplicationDuPmp>(`${this.base}/dashboard/pmp-explain/${articleId}/page`,
      {params: {centerId, page: String(page), size: String(size)}});
  }
}

