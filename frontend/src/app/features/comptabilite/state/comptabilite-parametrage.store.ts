import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  ComptabiliteApiService,
  JournalCode,
  JournalItem,
  MappingComptableItem,
  SynchronisationStock,
} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {JOURNAUX_MAX} from './comptabilite.store';

type ParametrageState = PagedListState<JournalItem> & {
  /** Tous les journaux du centre, pour choisir celui de chaque opération. */
  journaux: JournalItem[];
  mapping: MappingComptableItem | null;
  saving: boolean;
  synchronisation: SynchronisationStock | null;
  successMessage: string | null;
  error: string | null;
};

const initialState: ParametrageState = {
  ...createPagedListState<JournalItem>(),
  journaux: [],
  mapping: null,
  saving: false,
  synchronisation: null,
  successMessage: null,
  error: null,
};

/** Codes d'erreur métier renvoyés par le serveur, traduits (clés `COMPTABILITE.PARAMETRAGE.ERR.<code>`). */
const KNOWN_ERRORS = ['JOURNAL_UTILISE', 'JOURNAL_INTROUVABLE', 'JOURNAL_AVEC_ECRITURES', 'JOURNAL_INCONNU'];

export function parametrageErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return KNOWN_ERRORS.includes(code) ? `COMPTABILITE.PARAMETRAGE.ERR.${code}` : 'COMPTABILITE.PARAMETRAGE.ERR.SAVE';
}

/**
 * Paramétrage comptable du centre actif : journaux, journal de chaque opération, comptes, et comptabilisation du
 * stock à la demande. Toute requête porte le centre courant.
 */
export const ComptabiliteParametrageStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ComptabiliteParametrageStore'),
  withComputed((store) => ({
    journauxActifs: computed(() => store.journaux().filter((j) => j.actif)),
  })),
  withMethods((store, api = inject(ComptabiliteApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    /** Recharge la page affichée et la liste complète après une modification des journaux. */
    function reloadJournaux(): Observable<unknown> {
      return forkJoin({
        page: api.getJournaux(centerId(), store.pageIndex(), store.pageSize()),
        tous: api.getJournaux(centerId(), 0, JOURNAUX_MAX),
      }).pipe(
        tap(({page, tous}) => patchState(store, {
          rows: page.items ?? [], total: page.total ?? 0, journaux: tous.items ?? [], loading: false,
        })),
      );
    }

    function echec(err: unknown) {
      patchState(store, {saving: false, loading: false, error: parametrageErrorKey(err)});
      return EMPTY;
    }

    return {
      load: rxMethod<void>(
        pipe(
          tap(() => patchState(store, {loading: true, error: null, synchronisation: null})),
          switchMap(() => {
            if (!centerId()) {
              patchState(store, {loading: false, rows: [], total: 0, journaux: [], mapping: null});
              return EMPTY;
            }
            return forkJoin({mapping: api.getMapping(centerId()), journaux: reloadJournaux()}).pipe(
              tap(({mapping}) => patchState(store, {mapping})),
              catchError(() => {
                patchState(store, {loading: false, error: 'COMPTABILITE.PARAMETRAGE.ERR.LOAD'});
                return EMPTY;
              }),
            );
          }),
        ),
      ),

      setJournauxPagination: rxMethod<{ pageIndex: number; pageSize: number }>(
        pipe(
          tap(({pageIndex, pageSize}) => patchState(store, {pageIndex, pageSize, loading: true})),
          switchMap(() => reloadJournaux().pipe(catchError(echec))),
        ),
      ),

      saveJournal: rxMethod<JournalItem>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((journal) => api.saveJournal(centerId(), journal).pipe(
            switchMap(() => reloadJournaux()),
            tap(() => patchState(store, {
              saving: false,
              successMessage: 'COMPTABILITE.PARAMETRAGE.JOURNAL_ENREGISTRE'
            })),
            catchError(echec),
          )),
        ),
      ),

      deleteJournal: rxMethod<JournalCode>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((code) => api.deleteJournal(centerId(), code).pipe(
            switchMap(() => reloadJournaux()),
            tap(() => patchState(store, {saving: false, successMessage: 'COMPTABILITE.PARAMETRAGE.JOURNAL_SUPPRIME'})),
            catchError(echec),
          )),
        ),
      ),

      saveMapping: rxMethod<Omit<MappingComptableItem, 'centerId'>>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((mapping) => api.saveMapping({...mapping, centerId: centerId()}).pipe(
            tap((saved) => patchState(store, {
              mapping: saved, saving: false, successMessage: 'COMPTABILITE.PARAMETRAGE.COMPTES_ENREGISTRES',
            })),
            catchError(echec),
          )),
        ),
      ),

      synchroniserStock: rxMethod<{ from: string; to: string }>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null, synchronisation: null})),
          switchMap(({from, to}) => api.synchroniserStock(centerId(), from, to).pipe(
            tap((synchronisation) => patchState(store, {synchronisation, saving: false})),
            catchError(() => {
              patchState(store, {saving: false, error: 'COMPTABILITE.PARAMETRAGE.ERR.SYNCHRONISATION'});
              return EMPTY;
            }),
          )),
        ),
      ),

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
