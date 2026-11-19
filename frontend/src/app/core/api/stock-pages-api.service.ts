import {computed, DestroyRef, inject, Injectable, signal, WritableSignal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {catchError, defer, EMPTY, finalize, Observable, Subject, switchMap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {CorrespondanceDePage, PageServeur, parametresDePage} from './page-serveur';
import {
  DemiJournee,
  LigneSortiePourSeances,
  PmpExplanationStep,
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

  /** E5 — aucun tri ni filtre (ordre chronologique du rejeu). */
  explicationDuPmp(centerId: string, articleId: string, page: number, size: number): Observable<PageDExplicationDuPmp> {
    return this.http.get<PageDExplicationDuPmp>(`${this.base}/dashboard/pmp-explain/${articleId}/page`,
      {params: {centerId, page: String(page), size: String(size)}});
  }
}

/** Même tri et mêmes filtres (format API), quel que soit l'ordre des clés. */
export function memesParametres(a: Record<string, string>, b: Record<string, string>): boolean {
  const ka = Object.keys(a);
  return ka.length === Object.keys(b).length && ka.every((k) => a[k] === b[k]);
}

/**
 * État d'une liste `<ng-table>` en mode distant : page, taille, tri/filtres (format API), page reçue et chargement.
 * Les requêtes passent par un `switchMap` (une nouvelle demande annule la précédente) ; un nouveau tri ou filtre
 * ramène en première page ; {@link recharger} relit la page courante (après une écriture).
 * À créer dans un contexte d'injection (composant).
 */
export class ListeDistante<T, P extends PageServeur<T> = PageServeur<T>> {
  readonly pageIndex = signal(0);
  readonly pageSize: WritableSignal<number>;
  readonly colonnes = signal<Record<string, string>>({});
  readonly loading = signal(false);
  readonly page = signal<P | null>(null);
  readonly items = computed<T[]>(() => this.page()?.items ?? []);
  readonly total = computed(() => this.page()?.total ?? 0);
  private readonly demandes = new Subject<void>();

  constructor(private readonly correspondance: CorrespondanceDePage,
              charger: (parametres: Record<string, string>) => Observable<P> | null,
              taille = 20) {
    this.pageSize = signal(taille);
    this.demandes.pipe(
      switchMap(() => {
        const requete = charger({
          ...this.colonnes(), page: String(this.pageIndex()), size: String(this.pageSize()),
        });
        if (!requete) return EMPTY;
        return defer(() => {
          this.loading.set(true);
          return requete;
        }).pipe(catchError(() => EMPTY), finalize(() => this.loading.set(false)));
      }),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe((page) => this.page.set(page));
  }

  /** Requête de la table (`remoteQueryChange`) ⇒ nouvelle page, nouvelle taille ou nouveaux tri/filtres. */
  surRequete(requete: NgTableRemoteQuery): void {
    const {page, size, ...colonnes} = parametresDePage(requete, this.correspondance);
    const taille = Number(size);
    const index = Number(page);
    const colonnesChangees = !memesParametres(colonnes, this.colonnes());
    if (colonnesChangees) {
      this.colonnes.set(colonnes);
      this.pageIndex.set(0);
    }
    if (taille !== this.pageSize()) {
      this.pageSize.set(taille);
      this.pageIndex.set(0);
    } else if (!colonnesChangees && index !== this.pageIndex()) {
      this.pageIndex.set(index);
    } else if (!colonnesChangees) {
      return;
    }
    this.recharger();
  }

  /** Recharge la page courante (tri et filtres conservés). */
  recharger(): void {
    this.demandes.next();
  }

  /** Revient en première page et recharge (changement de contexte : autre pièce, autre inventaire). */
  reinitialiser(): void {
    this.pageIndex.set(0);
    this.recharger();
  }
}
