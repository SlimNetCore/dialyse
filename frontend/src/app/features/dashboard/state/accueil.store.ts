import {DestroyRef, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {Observable, of, Subscription} from 'rxjs';
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
  /** Modules dont la lecture a été refusée par le serveur (403) : leur tuile est masquée, pas affichée avec des tirets. */
  nonAccessibles: Partial<Record<AccueilModule, boolean>>;
};

const initialState: AccueilState = {valeurs: {}, chargement: {}, nonAccessibles: {}};

/** Résultat d'une lecture : les valeurs de la tuile, ou le refus d'accès du serveur. */
type Lecture = { valeurs: ValeursTuile; refusee: boolean; enEchec?: boolean };

const nombreDeChiffres = (module: AccueilModule): number =>
  ACCUEIL_TUILES.find((t) => t.module === module)?.chiffres.length ?? 0;

const indisponible = (module: AccueilModule): ValeursTuile =>
  Array.from({length: nombreDeChiffres(module)}, () => null);

const estUnRefus = (erreur: unknown): boolean => erreur instanceof HttpErrorResponse && erreur.status === 403;

/**
 * Chiffres clés de l'Accueil, lus sur les API déjà utilisées par les tableaux de bord des modules (aucun nouvel appel
 * serveur). Chaque tuile se charge indépendamment : l'échec d'une lecture n'affiche que ses chiffres comme
 * indisponibles, et un refus d'accès (403) masque la tuile.
 */
export const AccueilStore = signalStore(
  withState(initialState),
  withMethods((store,
               api = inject(BackendApiService),
               infirmiers = inject(InfirmierApiService),
               stock = inject(StockApiService),
               gmao = inject(GmaoApiService),
               destroyRef = inject(DestroyRef)) => {
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

    /** Lecture en cours de chaque module : une nouvelle lecture annule la précédente (centre changé, rafraîchissement). */
    const lectures = new Map<AccueilModule, Subscription>();
    destroyRef.onDestroy(() => lectures.forEach((lecture) => lecture.unsubscribe()));

    return {
      /**
       * Charge les chiffres des modules demandés (les tuiles visibles du profil : même règle de rôle que le menu). Le
       * menu ne porte pas toutes les règles du serveur (Stock et GMAO n'y exigent aucun rôle) : un module que le serveur
       * refuse (403) est marqué non accessible et sa tuile disparaît. Une lecture encore en cours pour le même module
       * est annulée : la réponse tardive d'un centre précédent n'écrase jamais les chiffres du centre courant.
       * {@code silencieux} : rafraîchissement en direct, sans indicateur de chargement ; s'il échoue autrement que par
       * un refus (500, réseau), les chiffres déjà affichés sont conservés au lieu d'être remplacés par des tirets.
       */
      charger(centerId: string, modules: ReadonlyArray<AccueilModule>, now: Date = new Date(),
              options: { silencieux?: boolean } = {}): void {
        for (const module of modules) {
          lectures.get(module)?.unsubscribe();
          if (!options.silencieux) {
            patchState(store, (s) => ({chargement: {...s.chargement, [module]: true}}));
          }
          lectures.set(module, sources[module](centerId, now).pipe(
            map((valeurs): Lecture => ({
              valeurs: valeurs.map((v) => (Number.isFinite(v) ? v : null)),
              refusee: false,
            })),
            catchError((erreur: unknown) => of<Lecture>({
              valeurs: indisponible(module), refusee: estUnRefus(erreur), enEchec: true,
            })),
          ).subscribe(({valeurs, refusee, enEchec}) => patchState(store, (s) => {
            const conserver = !!options.silencieux && !!enEchec && !refusee && s.valeurs[module] !== undefined;
            return {
              valeurs: conserver ? s.valeurs : {...s.valeurs, [module]: valeurs},
              chargement: {...s.chargement, [module]: false},
              nonAccessibles: {...s.nonAccessibles, [module]: refusee},
            };
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
