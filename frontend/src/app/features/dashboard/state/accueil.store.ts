import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {InfirmierApiService} from '../../../core/api/infirmier-api.service';
import {StockApiService} from '../../../core/api/stock-api.service';
import {GmaoApiService} from '../../../core/api/gmao-api.service';
import {AccueilModule, ACCUEIL_TUILES, ECHEANCE_PEC_JOURS, HORIZON_COUVERTURE_JOURS} from '../accueil-tuiles';

/** Valeurs des chiffres clés d'une tuile, dans l'ordre de sa définition ; null = indisponible (erreur de lecture). */
export type ValeursTuile = ReadonlyArray<number | null>;

type AccueilState = {
  valeurs: Partial<Record<AccueilModule, ValeursTuile>>;
  chargement: Partial<Record<AccueilModule, boolean>>;
};

const initialState: AccueilState = {valeurs: {}, chargement: {}};

const nombreDeChiffres = (module: AccueilModule): number =>
  ACCUEIL_TUILES.find((t) => t.module === module)?.chiffres.length ?? 0;

/**
 * Chiffres clés de l'Accueil, lus sur les API déjà utilisées par les tableaux de bord des modules (aucun nouvel appel
 * serveur). Chaque tuile se charge indépendamment : l'échec d'une lecture n'affiche que ses chiffres comme indisponibles.
 */
export const AccueilStore = signalStore(
  withState(initialState),
  withMethods((store,
               api = inject(BackendApiService),
               infirmiers = inject(InfirmierApiService),
               stock = inject(StockApiService),
               gmao = inject(GmaoApiService)) => {
    const sources: Record<AccueilModule, (centerId: string, now: Date) => Observable<number[]>> = {
      patients: (centerId) => api.getDashboardStats(centerId, ECHEANCE_PEC_JOURS).pipe(
        map((s) => [s.patientCount, s.pecExpiring])),
      seances: (centerId, now) => api.getSeanceMonthlyDashboard(centerId, now.getFullYear(), now.getMonth() + 1).pipe(
        map((d) => [d.totalSeances, d.absenceCount])),
      infirmiers: (centerId) => infirmiers.alertes(centerId, HORIZON_COUVERTURE_JOURS).pipe(
        map((alertes) => [alertes.length])),
      facturation: (centerId, now) => api.getFacturationDashboard(centerId, moisIso(now)).pipe(
        map((d) => [d.revenueTtc, d.createdInvoices])),
      reglement: (centerId, now) => api.getReglementDashboard(centerId, {
        year: now.getFullYear(),
        month: now.getMonth() + 1
      }).pipe(map((d) => [d.nonReglees, d.totalReste])),
      stock: (centerId) => stock.alertes(centerId).pipe(map((alertes) => [
        alertes.filter((a) => a.type !== 'PEREMPTION').length,
        alertes.filter((a) => a.type === 'PEREMPTION').length,
      ])),
      gmao: () => gmao.getStats().pipe(map((s) => [s.interventionsEnCours, s.interventionsARelancer])),
    };

    return {
      /** Charge les chiffres des seuls modules visibles du profil (jamais d'appel vers un module interdit). */
      charger(centerId: string, modules: ReadonlyArray<AccueilModule>, now: Date = new Date()): void {
        for (const module of modules) {
          patchState(store, (s) => ({chargement: {...s.chargement, [module]: true}}));
          sources[module](centerId, now).pipe(
            map((valeurs): ValeursTuile => valeurs.map((v) => (Number.isFinite(v) ? v : null))),
            catchError(() => of<ValeursTuile>(Array.from({length: nombreDeChiffres(module)}, () => null))),
          ).subscribe((valeurs) => patchState(store, (s) => ({
            valeurs: {...s.valeurs, [module]: valeurs},
            chargement: {...s.chargement, [module]: false},
          })));
        }
      },
    };
  }),
);

/** Mois AAAA-MM de la date, en heure locale. */
function moisIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}
