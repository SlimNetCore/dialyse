import {inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  LignePreferencePatient,
  LigneProfilInfirmier,
  PlanningPreferencesApiService,
  PreferencePayload,
  ProfilPayload,
  ReglagesOptimisation,
} from '../../../core/api/planning-preferences-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

interface PreferencesPlanningState {
  patients: PagedListState<LignePreferencePatient>;
  infirmiers: PagedListState<LigneProfilInfirmier>;
  reglages: ReglagesOptimisation | null;
  saving: boolean;
  error: string | null;
  successMessage: string | null;
}

const initialState: PreferencesPlanningState = {
  patients: createPagedListState<LignePreferencePatient>(),
  infirmiers: createPagedListState<LigneProfilInfirmier>(),
  reglages: null,
  saving: false,
  error: null,
  successMessage: null,
};

const CODES_CONNUS = ['PREFERENCE_CRENEAU_INCONNU', 'PREFERENCE_PATIENT_INTROUVABLE', 'PROFIL_INFIRMIER_INTROUVABLE'];

/** Clé i18n de l'erreur d'une action : code métier connu, valeurs refusées, sinon générique. */
export function preferencesErrorKey(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) return 'PLANNING.OPTIM.PREFS.ERR.GENERIC';
  const code: string | undefined = err.error?.code;
  if (code && CODES_CONNUS.includes(code)) return `PLANNING.OPTIM.PREFS.ERR.${code}`;
  if (err.status === 400) return 'PLANNING.OPTIM.PREFS.ERR.VALEURS';
  if (err.status === 403) return 'PLANNING.OPTIM.PREFS.ERR.DROITS';
  return 'PLANNING.OPTIM.PREFS.ERR.GENERIC';
}

/**
 * Préférences de planification du centre actif : préférences des patients (créneau, jours choisis par l'optimisation),
 * profils des infirmiers (temps partiel, compétences) — deux listes paginées par le serveur — et réglages du centre.
 */
export const PreferencesPlanningStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PreferencesPlanningStore'),
  withMethods((store, api = inject(PlanningPreferencesApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerPatients = async (pageIndex: number, pageSize: number): Promise<void> => {
      patchState(store, (s) => ({patients: {...s.patients, loading: true, pageIndex, pageSize}, error: null}));
      try {
        const page = await firstValueFrom(api.patients(centerId(), pageIndex, pageSize));
        patchState(store, (s) => ({
          patients: {...s.patients, rows: page.items ?? [], total: page.total ?? 0, loading: false},
        }));
      } catch (err) {
        patchState(store, (s) => ({patients: {...s.patients, loading: false}, error: preferencesErrorKey(err)}));
      }
    };

    const chargerInfirmiers = async (pageIndex: number, pageSize: number): Promise<void> => {
      patchState(store, (s) => ({infirmiers: {...s.infirmiers, loading: true, pageIndex, pageSize}, error: null}));
      try {
        const page = await firstValueFrom(api.infirmiers(centerId(), pageIndex, pageSize));
        patchState(store, (s) => ({
          infirmiers: {...s.infirmiers, rows: page.items ?? [], total: page.total ?? 0, loading: false},
        }));
      } catch (err) {
        patchState(store, (s) => ({infirmiers: {...s.infirmiers, loading: false}, error: preferencesErrorKey(err)}));
      }
    };

    /** Exécute un enregistrement, puis recharge ce qui en dépend ; renvoie vrai en cas de succès. */
    const enregistrer = async (action: () => Promise<unknown>, apres: () => Promise<void>): Promise<boolean> => {
      patchState(store, {saving: true, error: null, successMessage: null});
      try {
        await action();
        patchState(store, {saving: false, successMessage: 'PLANNING.OPTIM.PREFS.OK'});
        await apres();
        return true;
      } catch (err) {
        patchState(store, {saving: false, error: preferencesErrorKey(err)});
        return false;
      }
    };

    return {
      charger(): void {
        void chargerPatients(store.patients().pageIndex, store.patients().pageSize);
        void chargerInfirmiers(store.infirmiers().pageIndex, store.infirmiers().pageSize);
        void firstValueFrom(api.reglages(centerId()))
          .then((reglages) => patchState(store, {reglages}))
          .catch((err) => patchState(store, {error: preferencesErrorKey(err)}));
      },

      setPatientsPagination(pageIndex: number, pageSize: number): Promise<void> {
        return chargerPatients(pageIndex, pageSize);
      },

      setInfirmiersPagination(pageIndex: number, pageSize: number): Promise<void> {
        return chargerInfirmiers(pageIndex, pageSize);
      },

      enregistrerPreference(patientId: string, payload: PreferencePayload): Promise<boolean> {
        return enregistrer(() => firstValueFrom(api.enregistrerPreference(centerId(), patientId, payload)),
          () => chargerPatients(store.patients().pageIndex, store.patients().pageSize));
      },

      enregistrerProfil(infirmierId: string, payload: ProfilPayload): Promise<boolean> {
        return enregistrer(() => firstValueFrom(api.enregistrerProfil(centerId(), infirmierId, payload)),
          () => chargerInfirmiers(store.infirmiers().pageIndex, store.infirmiers().pageSize));
      },

      enregistrerReglages(reglages: ReglagesOptimisation): Promise<boolean> {
        return enregistrer(async () => {
          const enregistres = await firstValueFrom(api.enregistrerReglages(centerId(), reglages));
          patchState(store, {reglages: enregistres});
        }, async () => undefined);
      },

      /** Changement de centre : on oublie les données du centre précédent. */
      reinitialiser(): void {
        patchState(store, {...initialState});
      },
    };
  }),
);
