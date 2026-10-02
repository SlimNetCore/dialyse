import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  AbsenceFilters,
  AbsencePatient,
  AbsencePatientApiService,
  AbsenceSynthese,
  DeclarationAbsencePayload,
  MotifAbsence,
} from '../../core/api/absence-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';

type AbsencesPatientsState = PagedListState<AbsencePatient> & {
  filters: AbsenceFilters;
  synthese: AbsenceSynthese;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

export const EMPTY_FILTERS: AbsenceFilters = {statut: '', motif: '', from: '', to: ''};

const initialState: AbsencesPatientsState = {
  ...createPagedListState<AbsencePatient>(),
  filters: EMPTY_FILTERS,
  synthese: {aQualifier: 0, enRetard: 0},
  saving: false,
  successMessage: null,
  error: null,
};

const KNOWN_CODES = [
  'ABSENCE_EXISTANTE', 'ABSENCE_SEANCE_REALISEE', 'PATIENT_INTROUVABLE', 'ABSENCE_INTROUVABLE', 'ABSENCE_DATE_FUTURE',
  'ABSENCE_DELAI_DEPASSE', 'ABSENCE_COMMENTAIRE_REQUIS', 'ABSENCE_MOTIF_REQUIS', 'ABSENCE_NON_MODIFIABLE',
  'ABSENCE_CORRECTION_INTERDITE', 'ABSENCE_RATTRAPAGE_SANS_SEANCE', 'ABSENCE_RATTRAPAGE_ANTERIEUR',
  'ABSENCE_RATTRAPAGE_DATE_REQUISE', 'ABSENCE_DEJA_ANNULEE',
  'ABSENCE_PERIODE_FACTUREE',
];

/** Clé i18n de l'erreur d'une écriture : code métier connu, sinon message générique. */
export function absencePatientErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return KNOWN_CODES.includes(code) ? `ABSENCES.ERR.${code}` : 'ABSENCES.ERR.SAVE';
}

/** Absences de patients du centre actif : liste paginée filtrable, synthèse « à qualifier », écritures. */
export const AbsencesPatientsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AbsencesPatientsStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(AbsencePatientApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          forkJoin({
            liste: api.list(centerId(), store.filters(), page, size),
            synthese: api.synthese(centerId()),
          }).pipe(
            tap(({liste, synthese}) => patchState(store, {
              rows: liste.items ?? [], total: liste.total ?? 0, pageIndex: liste.page ?? page, synthese, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'ABSENCES.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    function saveFlow<T>(request: Observable<T>) {
      return request.pipe(
        tap(() => {
          patchState(store, {saving: false, successMessage: 'ABSENCES.SAVED_OK'});
          loadPage({page: store.pageIndex(), size: store.pageSize()});
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: absencePatientErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      loadPage,

      applyFilters(filters: AbsenceFilters): void {
        patchState(store, {filters});
        loadPage({page: 0, size: store.pageSize()});
      },

      declare: rxMethod<DeclarationAbsencePayload>(pipe(tap(begin),
        switchMap((payload) => saveFlow(api.declarer(centerId(), payload))))),

      qualify: rxMethod<{ id: string; motif: MotifAbsence; commentaire: string | null }>(pipe(tap(begin),
        switchMap((p) => saveFlow(api.qualifier(centerId(), p.id, p.motif, p.commentaire))))),

      makeUp: rxMethod<{ id: string; dateRattrapage: string }>(pipe(tap(begin),
        switchMap((p) => saveFlow(api.rattraper(centerId(), p.id, p.dateRattrapage))))),

      cancel: rxMethod<{ id: string; commentaire: string }>(pipe(tap(begin),
        switchMap((p) => saveFlow(api.annuler(centerId(), p.id, p.commentaire))))),

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
