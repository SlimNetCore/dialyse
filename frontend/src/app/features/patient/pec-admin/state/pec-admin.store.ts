import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService} from '../../../../core/api/backend-api.service';
import {ReferentialApiService} from '../../../../core/api/referential-api.service';
import {DropdownItem} from '../../../../shared/searchable-select.component';

type PecAdminState = {
  pecs: any[];
  loading: boolean;
  validatingPec: any;
  forfaits: DropdownItem[];
  error: string | null;
};

const initialState: PecAdminState = {
  pecs: [],
  loading: false,
  validatingPec: null,
  forfaits: [],
  error: null
};

export const PecAdminStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PecAdminStore'),
  withMethods((store,
               api = inject(BackendApiService),
               refApi = inject(ReferentialApiService)
  ) => ({

    // ✅ Charger les PEC du centre
    loadPecs: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null})),
        switchMap(({centerId}) =>
          api.listPecs(centerId).pipe(
            tap((list: any[]) => patchState(store, {
              pecs: list,
              loading: false
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.message || err?.statusText || 'Erreur chargement PEC'
              });
              return of(null);
            })
          )
        )
      )
    ),

    // ✅ Charger les forfaits pour le centre
    loadForfaits: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId}) =>
          refApi.getForfaits(centerId).pipe(
            tap((list: any[]) => patchState(store, {
              forfaits: list.map((i: any) => ({
                ...i,
                id: i.id,
                label: `${i.code ?? ''} - ${i.nom}`
              }))
            })),
            catchError((err: any) => {
              patchState(store, {
                error: err?.error?.message || err?.statusText || 'Erreur forfaits'
              });
              return of(null);
            })
          )
        )
      )
    ),

    // ✅ Valider une PEC
    validatePec: rxMethod<{
      pecId: string;
      centerId: string;
      userId: string;
      dateDebutEffectif: string;
      dateFinEffectif: string;
      forfaitEffectifId?: string;
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({pecId, centerId, userId, dateDebutEffectif, dateFinEffectif, forfaitEffectifId}) =>
          api.validatePecAdmin(pecId, {
            centerId,
            userId,
            dateDebutEffectif,
            dateFinEffectif,
            forfaitEffectifId
          }).pipe(
            tap(() => {
              patchState(store, {validatingPec: null});
              // Recharger la liste après validation
              api.listPecs(centerId).pipe(
                tap((list: any[]) => patchState(store, {pecs: list})),
                catchError(() => of(null))
              ).subscribe();
            }),
            catchError((err: any) => {
              patchState(store, {
                error: err?.error?.detail || err?.statusText || 'Erreur validation'
              });
              return of(null);
            })
          )
        )
      )
    ),

    // ✅ Fermer une PEC
    closePec: rxMethod<{ pecId: string; centerId: string; userId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({pecId, centerId, userId}) =>
          api.closePec(pecId, centerId, userId).pipe(
            tap(() => {
              // Recharger la liste après fermeture
              api.listPecs(centerId).pipe(
                tap((list: any[]) => patchState(store, {pecs: list})),
                catchError(() => of(null))
              ).subscribe();
            }),
            catchError((err: any) => {
              patchState(store, {
                error: err?.error?.detail || err?.statusText || 'Erreur fermeture'
              });
              return of(null);
            })
          )
        )
      )
    ),

    setPecs(pecs: any[]): void {
      patchState(store, {pecs});
    },

    setLoading(loading: boolean): void {
      patchState(store, {loading});
    },

    setValidatingPec(validatingPec: any): void {
      patchState(store, {validatingPec});
    },

    setForfaits(forfaits: DropdownItem[]): void {
      patchState(store, {forfaits});
    }
  }))
);



