import {computed, Signal, signal} from '@angular/core';
import {
  IndicateursOptimisation,
  PerimetreOptimisation,
  VacationPlanifiee,
} from '../../../core/api/planning-optimisation-api.service';
import {PreferencePayload} from '../../../core/api/planning-preferences-api.service';

/** Intervalle (ms) entre deux lectures de l'avancement d'un calcul en cours. */
export const INTERVALLE_SUIVI_MS = 1500;

export type Tendance = 'mieux' | 'pire' | 'egal' | 'neutre';

/** Indicateurs comparés avant / après ; `neutre` : l'écart n'est ni bon ni mauvais en soi. */
export const INDICATEURS: readonly { cle: keyof IndicateursOptimisation; neutre?: boolean }[] = [
  {cle: 'generateursUtilises'},
  {cle: 'sallesOuvertes'},
  {cle: 'vacationsRequises'},
  {cle: 'placesInfirmierInutilisees'},
  {cle: 'patientsNonPlaces'},
  {cle: 'vacationsNonPourvues'},
  {cle: 'infirmiersMobilises', neutre: true},
  {cle: 'ecartCharge'},
  {cle: 'depassementsHebdo'},
];

export interface LigneIndicateur {
  cle: keyof IndicateursOptimisation;
  avant: number;
  apres: number;
  ecart: number;
  tendance: Tendance;
}

/** Une ligne par indicateur : valeurs avant / après, écart et tendance (moins est mieux, sauf indicateur neutre). */
export function lignesIndicateurs(avant: IndicateursOptimisation, apres: IndicateursOptimisation): LigneIndicateur[] {
  return INDICATEURS.map(({cle, neutre}) => {
    const ecart = apres[cle] - avant[cle];
    const tendance: Tendance = ecart === 0 ? 'egal' : neutre ? 'neutre' : ecart < 0 ? 'mieux' : 'pire';
    return {cle, avant: avant[cle], apres: apres[cle], ecart, tendance};
  });
}

/** Le périmètre déplace-t-il des patients ? */
export const placePatients = (p: PerimetreOptimisation): boolean => p === 'PATIENTS' || p === 'COMPLET';

/** Le périmètre planifie-t-il des infirmiers ? */
export const planifieInfirmiers = (p: PerimetreOptimisation): boolean => p !== 'PATIENTS' && p !== 'MAINTENANCE';

/** Le périmètre propose-t-il des déplacements temporaires (maintenance des générateurs) ? */
export const proposeTemporaires = (p: PerimetreOptimisation): boolean => p === 'MAINTENANCE';

/** Couverture et maintenance se planifient sur plusieurs semaines (dates réelles). */
export const horizonLibre = (p: PerimetreOptimisation): boolean => p === 'COUVERTURE' || p === 'MAINTENANCE';

/** Corps d'enregistrement d'une préférence : pas de créneau = '' dans le formulaire ; séances seulement si jours à choisir. */
export function payloadPreference(m: { creneauPrefereId: string; joursAChoisir: boolean; seancesParSemaine: number }):
  PreferencePayload {
  return {
    creneauPrefereId: m.creneauPrefereId || null,
    joursAChoisir: m.joursAChoisir,
    seancesParSemaine: m.joursAChoisir ? Number(m.seancesParSemaine) : null,
  };
}

/** Vacations que la proposition ajoute (l'infirmier n'était pas déjà prévu sur la case). */
export function vacationsNouvelles(vacations: readonly VacationPlanifiee[]): VacationPlanifiee[] {
  return vacations.filter((v) => !v.existante);
}

/** Aujourd'hui (UTC) au format `yyyy-MM-dd`. */
export function aujourdhui(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Pagination locale d'une liste déjà bornée (le détail d'une proposition arrive en un seul bloc). */
export interface PaginationLocale<T> {
  pageIndex: Signal<number>;
  pageSize: Signal<number>;
  total: Signal<number>;
  page: Signal<T[]>;

  aller(pageIndex: number, pageSize: number): void;
}

export function creerPaginationLocale<T>(source: Signal<readonly T[]>, taille = 10): PaginationLocale<T> {
  const pageIndex = signal(0);
  const pageSize = signal(taille);
  const total = computed(() => source().length);
  // Une liste qui rétrécit (nouvelle proposition) ne doit pas laisser l'utilisateur sur une page vide.
  const index = computed(() => Math.min(pageIndex(), Math.max(0, Math.ceil(total() / pageSize()) - 1)));
  const page = computed(() => source().slice(index() * pageSize(), index() * pageSize() + pageSize()));
  return {
    pageIndex: index,
    pageSize,
    total,
    page,
    aller(i, taillePage) {
      pageIndex.set(i);
      pageSize.set(taillePage);
    },
  };
}
