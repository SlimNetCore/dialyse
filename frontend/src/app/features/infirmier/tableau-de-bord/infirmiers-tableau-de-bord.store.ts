import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, forkJoin, map, Observable, of, pipe, switchMap, tap} from 'rxjs';
import {
  AbsenceInfirmier,
  AlertePresence,
  ChargeInfirmier,
  Infirmier,
  InfirmierApiService,
} from '../../../core/api/infirmier-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {aujourdhuiUtc, moisCourant} from '../presence.util';

/** Horizon (jours) de la couverture affichée : la semaine qui vient. */
export const HORIZON_COUVERTURE = 7;
/** Taille maximale d'une page côté serveur (Pagination.TAILLE_MAXIMALE) : une seule requête par source. */
export const TAILLE_LECTURE = 100;

export interface AbsenceEnCours extends AbsenceInfirmier {
  nom: string;
}

export interface SyntheseCharge {
  infirmiers: number;
  moyenne: number;
  remplacements: number;
  joursAbsence: number;
  plusCharge: ChargeInfirmier | null;
  moinsCharge: ChargeInfirmier | null;
}

type InfirmiersTableauDeBordState = {
  aujourdhui: string;
  mois: string;
  alertes: AlertePresence[];
  charge: ChargeInfirmier[];
  chargeTotal: number;
  chargeMoyenne: number;
  absences: AbsenceInfirmier[];
  infirmiers: Infirmier[];
  loading: boolean;
  error: string | null;
};

const initialState: InfirmiersTableauDeBordState = {
  aujourdhui: aujourdhuiUtc(),
  mois: moisCourant(),
  alertes: [],
  charge: [],
  chargeTotal: 0,
  chargeMoyenne: 0,
  absences: [],
  infirmiers: [],
  loading: false,
  error: null,
};

function nomComplet(i: Infirmier | undefined, repli: string): string {
  return i ? (i.prenom ? `${i.prenom} ${i.nom}` : i.nom) : repli;
}

/**
 * Tableau de bord des infirmiers (lecture seule) : couverture de la semaine (alertes de sous-effectif), synthèse de la
 * charge du mois et absences en cours. Agrège des API déjà appelées par les écrans de travail (alertes de présence,
 * charge mensuelle, liste des absences, référentiel) ; aucune API dédiée. Fourni par la page (un état par ouverture).
 * Une source en échec n'empêche pas l'affichage des autres : elle est vide et l'erreur est signalée.
 */
export const InfirmiersTableauDeBordStore = signalStore(
  withState(initialState),
  withDevtools('InfirmiersTableauDeBordStore'),
  withComputed((store) => ({
    alertesTriees: computed(() =>
      [...store.alertes()].sort((a, b) => a.date.localeCompare(b.date) || b.manque - a.manque)),
    infirmiersManquants: computed(() => store.alertes().reduce((s, a) => s + a.manque, 0)),
    synthese: computed<SyntheseCharge>(() => {
      const charge = store.charge();
      const parTotal = [...charge].sort((a, b) => b.total - a.total);
      return {
        infirmiers: store.chargeTotal(),
        moyenne: Math.round(store.chargeMoyenne() * 10) / 10,
        remplacements: charge.reduce((s, c) => s + c.remplacements, 0),
        joursAbsence: charge.reduce((s, c) => s + c.joursAbsence, 0),
        plusCharge: parTotal[0] ?? null,
        moinsCharge: parTotal.length > 1 ? parTotal[parTotal.length - 1] : null,
      };
    }),
    absencesEnCours: computed<AbsenceEnCours[]>(() => {
      const jour = store.aujourdhui();
      const infirmiers = store.infirmiers();
      return store.absences()
        .filter((a) => a.debut <= jour && jour <= a.fin)
        .sort((a, b) => a.fin.localeCompare(b.fin))
        .map((a) => ({...a, nom: nomComplet(infirmiers.find((i) => i.id === a.infirmierId), a.infirmierId)}));
    }),
  })),
  withMethods((store, api = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    let enEchec = false;
    const ouVide = <T>(source: Observable<T>, vide: T): Observable<T> => source.pipe(catchError(() => {
      enEchec = true;
      return of(vide);
    }));

    return {
      /** Charge les quatre sources du centre actif ; `aujourdhui` (yyyy-MM-dd) fixe le jour de référence. */
      charger: rxMethod<string | void>(
        pipe(
          tap((aujourdhui) => {
            enEchec = false;
            const jour = aujourdhui || aujourdhuiUtc();
            patchState(store, {loading: true, error: null, aujourdhui: jour, mois: jour.slice(0, 7)});
          }),
          switchMap(() => {
            const centre = shell.currentCenterId() ?? '';
            return forkJoin({
              alertes: ouVide(api.alertes(centre, HORIZON_COUVERTURE), [] as AlertePresence[]),
              charge: ouVide(api.charge(centre, store.mois(), 0, TAILLE_LECTURE),
                {items: [], total: 0, page: 0, size: TAILLE_LECTURE, moyenne: 0}),
              absences: ouVide(api.listAbsences(centre, 0, TAILLE_LECTURE).pipe(map((r) => r.items ?? [])),
                [] as AbsenceInfirmier[]),
              infirmiers: ouVide(api.list(centre, 0, TAILLE_LECTURE).pipe(map((r) => r.items ?? [])),
                [] as Infirmier[]),
            }).pipe(
              tap(({alertes, charge, absences, infirmiers}) => patchState(store, {
                alertes: alertes ?? [],
                charge: charge.items ?? [],
                chargeTotal: charge.total ?? 0,
                chargeMoyenne: charge.moyenne ?? 0,
                absences,
                infirmiers,
                loading: false,
                error: enEchec ? 'INFIRMIER.ERR.LOAD' : null,
              })),
            );
          }),
        ),
      ),
    };
  }),
);
