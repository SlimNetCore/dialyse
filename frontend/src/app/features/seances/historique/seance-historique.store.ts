import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {HttpErrorResponse} from '@angular/common/http';
import {catchError, EMPTY, firstValueFrom, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, SeanceListItem} from '../../../core/api/backend-api.service';
import {dateRangeFilter, parseDateRange, toSeanceHistoryQuery} from './seance-historique.util';

type SortDirection = 'asc' | 'desc' | '';

export type SeanceHistoriqueState = {
  rows: SeanceListItem[];
  total: number;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
  /** Filtres de colonne ; `dateSeance` (« du..au ») porte aussi la période saisie au-dessus de la table. */
  filters: Record<string, string>;
  sortColumnId: string | null;
  sortDirection: SortDirection;
  /** Suppression en cours (bouton désactivé) et dernier message de succès. */
  deleting: boolean;
  successMessage: string | null;
};

const initialState: SeanceHistoriqueState = {
  rows: [],
  total: 0,
  pageIndex: 0,
  pageSize: 20,
  loading: false,
  error: null,
  filters: {},
  sortColumnId: null,
  sortDirection: '',
  deleting: false,
  successMessage: null,
};

const CODES_SUPPRESSION = ['SEANCE_INTROUVABLE', 'SEANCE_FACTUREE_NON_SUPPRIMABLE', 'SEANCE_SUPPRESSION_MOTIF_INVALIDE'];

/** Clé i18n de l'échec d'une suppression : refus métier connu, droits, sinon générique. */
export function suppressionErrorKey(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) return 'SEANCES.DELETE.ERR.GENERIC';
  const code: string | undefined = err.error?.code;
  if (code && CODES_SUPPRESSION.includes(code)) return `SEANCES.DELETE.ERR.${code}`;
  if (err.status === 403) return 'SEANCES.DELETE.ERR.DROITS';
  return 'SEANCES.DELETE.ERR.GENERIC';
}

/**
 * Historique des séances : la recherche, le filtre, le tri et la pagination sont faits par le serveur (jamais sur la
 * seule page affichée). Tout changement de tri ou de filtre revient à la première page.
 * Traçabilité : SeancesHistoriqueComponent → ce store → BackendApiService.listSeances → GET /api/v1/seances ;
 * suppression : BackendApiService.supprimerSeance → DELETE /api/v1/seances/{id} (SuppressionSeanceService).
 */
export const SeanceHistoriqueStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SeanceHistoriqueStore'),
  withComputed((store) => {
    const period = computed(() => parseDateRange(store.filters()['dateSeance']));
    return {
      /** Bornes de la période, lues dans le filtre de date de la colonne (une seule source). */
      periodFrom: computed(() => period().from ?? ''),
      periodTo: computed(() => period().to ?? ''),
    };
  }),
  withMethods((store, api = inject(BackendApiService)) => {
    const load = rxMethod<string>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null})),
        switchMap((centerId) =>
          api.listSeances(
            centerId,
            store.pageIndex(),
            store.pageSize(),
            toSeanceHistoryQuery(store.filters(), store.sortColumnId(), store.sortDirection()),
          ).pipe(
            tap((res) => patchState(store, {rows: res.items, total: res.total, loading: false})),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'SEANCES.HISTORY_LOAD_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );
    return {
      load,
      /**
       * La table ne sait pas afficher un tri qu'on lui redonne : à l'ouverture de l'écran, le tri revient au défaut
       * (date décroissante) pour que l'en-tête et le serveur restent d'accord.
       */
      resetSort(): void {
        patchState(store, {sortColumnId: null, sortDirection: ''});
      },
      /**
       * Requête de la table (tri, filtres, page) : un tri ou des filtres changés reviennent à la première page, sinon
       * la page demandée est chargée ; la requête précédente en vol est annulée (switchMap).
       */
      applyTableQuery(centerId: string, query: {
        filters: Record<string, string>;
        sortColumnId: string | null;
        sortDirection: SortDirection;
        pageIndex: number;
        pageSize: number;
      }): void {
        const filters = Object.fromEntries(Object.entries(query.filters ?? {}).filter(([, v]) => (v ?? '').trim() !== ''));
        const before = store.filters();
        const filtersChanged = Object.keys(filters).length !== Object.keys(before).length
          || Object.entries(filters).some(([k, v]) => before[k] !== v);
        const sortChanged = (query.sortColumnId ?? null) !== store.sortColumnId() || query.sortDirection !== store.sortDirection();
        const pageSize = query.pageSize > 0 ? Math.min(100, query.pageSize) : store.pageSize();
        const reset = filtersChanged || sortChanged || pageSize !== store.pageSize();
        patchState(store, {
          filters,
          sortColumnId: query.sortColumnId ?? null,
          sortDirection: query.sortDirection,
          pageSize,
          pageIndex: reset ? 0 : Math.max(0, query.pageIndex),
        });
        load(centerId);
      },
      /**
       * Nouvelle période (dates ISO, vide = sans borne) : écrite dans le filtre de date de la colonne (la puce de la
       * table la montre), retour à la première page puis rechargement.
       */
      setPeriod(centerId: string, periodFrom: string, periodTo: string): void {
        const autres = Object.fromEntries(Object.entries(store.filters()).filter(([k]) => k !== 'dateSeance'));
        const plage = dateRangeFilter(periodFrom, periodTo);
        patchState(store, {filters: plage ? {...autres, dateSeance: plage} : autres, pageIndex: 0});
        load(centerId);
      },
      reload(centerId: string): void {
        load(centerId);
      },
      /** Supprime une séance (administrateur) puis recharge la page ; renvoie vrai si elle a été supprimée. */
      async supprimer(centerId: string, seanceId: string, motif: string): Promise<boolean> {
        patchState(store, {deleting: true, error: null, successMessage: null});
        try {
          await firstValueFrom(api.supprimerSeance(seanceId, centerId, motif), {defaultValue: undefined});
          patchState(store, {deleting: false, successMessage: 'SEANCES.DELETE.OK'});
          load(centerId);
          return true;
        } catch (err) {
          patchState(store, {deleting: false, error: suppressionErrorKey(err)});
          return false;
        }
      },
    };
  }),
);
