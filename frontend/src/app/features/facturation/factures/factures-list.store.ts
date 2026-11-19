import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {Subscription} from 'rxjs';
import type {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {
  BackendApiService,
  DetailDeFacture,
  FactureChampDeTri,
  FactureEtat,
  FacturesQuery,
  PageDeFactures,
} from '../../../core/api/backend-api.service';
import {httpErrorStatus} from '../../../core/api/http-error-detail';

/** Tailles de page proposées (le serveur borne à 100, RG-FAC-046). */
export const TAILLES_DE_PAGE: readonly number[] = [10, 20, 50, 100];
const TAILLE_PAR_DEFAUT = 20;

const CHAMPS_TRIABLES: ReadonlySet<string> = new Set<FactureChampDeTri>([
  'dateFacturation', 'numeroFacture', 'patient', 'caisse', 'nombreDeSeances', 'montantTtc', 'montantRegle', 'reste', 'etat',
]);
const ETATS: ReadonlySet<string> = new Set<FactureEtat>(['NON_REGLEE', 'PARTIELLEMENT_REGLEE', 'REGLEE']);

/** Période de consultation, bornes incluses, au format `yyyy-MM-dd`. */
export type PeriodeDeFactures = { du: string; au: string };

/** Critères de la table (tri, filtres de colonne, page), sans la période. */
export type CriteresDeTable = Omit<FacturesQuery, 'du' | 'au'>;

/** Détail d'une facture dépliée : chargement, erreur (clé de traduction du message) ou prêt (RG-FAC-047). */
export type EtatDuDetail =
  | { etat: 'chargement' }
  | { etat: 'erreur'; message: string }
  | { etat: 'pret'; detail: DetailDeFacture };

/** Codes 400 du contrat (ProblemDetail `code`) traduits sous `FACTURATION.FACTURES.ERREURS.*`. */
const CODES_400: ReadonlySet<string> = new Set(['PERIODE_INVALIDE', 'TRI_INVALIDE', 'ETAT_INVALIDE', 'MONTANT_INVALIDE']);

/**
 * Message (clé de traduction) d'un échec de lecture : 403 accès refusé (rôle ou centre), 400 avec le code du contrat
 * s'il est connu sinon critères refusés, 404 facture introuvable (détail), sinon erreur générale.
 */
export function messageDErreur(err: unknown): string {
  const corps = err && typeof err === 'object' ? (err as { error?: unknown }).error : null;
  const code = corps && typeof corps === 'object' ? (corps as { code?: unknown }).code : null;
  switch (httpErrorStatus(err)) {
    case 403:
      return 'FACTURATION.FACTURES.ERREURS.ACCES_REFUSE';
    case 400:
      return typeof code === 'string' && CODES_400.has(code)
        ? `FACTURATION.FACTURES.ERREURS.${code}` : 'FACTURATION.FACTURES.ERREURS.REQUETE_INVALIDE';
    case 404:
      return 'FACTURATION.FACTURES.ERREURS.INTROUVABLE';
    default:
      return 'FACTURATION.FACTURES.ERREURS.GENERALE';
  }
}

const jour = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Du 1er au dernier jour du mois de `reference` (décalé de `decalage` mois). */
export function moisDe(reference: Date, decalage = 0): PeriodeDeFactures {
  const debut = new Date(reference.getFullYear(), reference.getMonth() + decalage, 1);
  const fin = new Date(reference.getFullYear(), reference.getMonth() + decalage + 1, 0);
  return {du: jour(debut), au: jour(fin)};
}

/** Une période est valide si ses deux bornes sont des jours et `du` ≤ `au` (le serveur borne en plus à 366 jours). */
export function periodeValide(periode: PeriodeDeFactures): boolean {
  const format = /^\d{4}-\d{2}-\d{2}$/;
  return format.test(periode.du) && format.test(periode.au) && periode.du <= periode.au;
}

function nombre(texte: string | undefined): number | null {
  const brut = (texte ?? '').trim().replace(',', '.');
  if (!brut) return null;
  const valeur = Number(brut);
  return Number.isFinite(valeur) ? valeur : null;
}

/** Filtre de plage `min..max` (une borne peut être vide) : bornes remises dans l'ordre si elles sont inversées. */
export function bornesDeMontant(filtre: string | undefined): { montantMin: number | null; montantMax: number | null } {
  if (!filtre || !filtre.includes('..')) return {montantMin: null, montantMax: null};
  const [bas, haut] = filtre.split('..');
  const min = nombre(bas);
  const max = nombre(haut);
  if (min !== null && max !== null && min > max) return {montantMin: max, montantMax: min};
  return {montantMin: min, montantMax: max};
}

const texte = (valeur: string | undefined): string | null => (valeur ?? '').trim() || null;

/**
 * Requête de la table (ids de colonnes) ⇒ critères de `GET /facturation/factures` : `numeroFacture`→`numero`,
 * `patient`, `caisse`→`caisseId`, `etat`, `montantTtc` (plage)→`montantMin`/`montantMax` ; un tri sur une colonne non
 * triable côté serveur est ignoré (tri par défaut du serveur).
 */
export function versCriteres(requete: NgTableRemoteQuery, tailleCourante: number): CriteresDeTable {
  const filtres = requete.filters ?? {};
  const sort = requete.sort?.direction && CHAMPS_TRIABLES.has(requete.sort.columnId)
    ? {champ: requete.sort.columnId as FactureChampDeTri, direction: requete.sort.direction}
    : null;
  const etat = texte(filtres['etat']);
  const taille = requete.page?.size && requete.page.size > 0 ? requete.page.size : tailleCourante;
  return {
    page: Math.max(0, requete.page?.index ?? 0),
    size: Math.min(100, taille),
    sort,
    numero: texte(filtres['numeroFacture']),
    patient: texte(filtres['patient']),
    caisseId: texte(filtres['caisse']),
    etat: etat && ETATS.has(etat) ? (etat as FactureEtat) : null,
    ...bornesDeMontant(filtres['montantTtc']),
  };
}

/**
 * État de l'écran « Factures » : période, critères de la table, page reçue et détail de la facture dépliée (une seule
 * à la fois). Le détail est mis en cache par facture ; le cache est vidé quand la période ou le centre changent.
 */
@Injectable()
export class FacturesListStore {
  private readonly api = inject(BackendApiService);

  readonly periode = signal<PeriodeDeFactures>(moisDe(new Date()));
  /** Même forme que `versCriteres` : la première requête de la table (sans filtre) ne relance pas la lecture. */
  readonly criteres = signal<CriteresDeTable>({
    page: 0, size: TAILLE_PAR_DEFAUT, sort: null, numero: null, patient: null, caisseId: null, etat: null,
    montantMin: null, montantMax: null,
  });
  readonly page = signal<PageDeFactures | null>(null);
  readonly chargement = signal(false);
  /** Clé de traduction de l'échec de la dernière lecture de la liste, `null` sinon. */
  readonly erreur = signal<string | null>(null);
  readonly factureDepliee = signal<string | null>(null);
  readonly details = signal<ReadonlyMap<string, EtatDuDetail>>(new Map());

  readonly lignes = computed(() => this.page()?.items ?? []);
  readonly total = computed(() => this.page()?.total ?? 0);
  readonly totaux = computed(() => this.page()?.totaux ?? null);
  readonly clesDepliees = computed<readonly unknown[]>(() => {
    const id = this.factureDepliee();
    return id ? [id] : [];
  });

  private centre: string | null = null;
  private derniereRequete: string | null = null;
  private liste?: Subscription;
  /** Lecture du détail en cours : une seule à la fois, la précédente est annulée (comme un `switchMap`). */
  private detailEnCours?: { factureId: string; abonnement: Subscription };

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.liste?.unsubscribe();
      this.detailEnCours?.abonnement.unsubscribe();
    });
  }

  /** Centre actif (au démarrage ou au changement de centre) : repart de la page 0, cache vidé. */
  changerDeCentre(centre: string | null): void {
    if (centre === this.centre) return;
    this.centre = centre;
    this.reinitialiser();
    this.charger();
  }

  /** Nouvelle période : page 0, détail replié, cache vidé (RG-FAC-046). Une période invalide n'est pas envoyée. */
  changerDePeriode(periode: PeriodeDeFactures): void {
    if (!periodeValide(periode)) return;
    const actuelle = this.periode();
    if (actuelle.du === periode.du && actuelle.au === periode.au) return;
    this.periode.set(periode);
    this.reinitialiser();
    this.charger();
  }

  /** Tri, filtres ou page changés dans la table. */
  appliquer(requete: NgTableRemoteQuery): void {
    this.criteres.set(versCriteres(requete, this.criteres().size));
    this.charger();
  }

  /** Recharge la page courante (après une erreur). */
  recharger(): void {
    this.derniereRequete = null;
    this.charger();
  }

  /** Clic de ligne : déplie la facture (repliant l'autre) ou la replie si elle l'était. */
  basculer(factureId: string): void {
    if (this.factureDepliee() === factureId) this.replier();
    else this.deplier(factureId);
  }

  deplier(factureId: string): void {
    this.factureDepliee.set(factureId);
    const connu = this.details().get(factureId);
    if (connu?.etat === 'pret' || connu?.etat === 'chargement') return;
    this.chargerDetail(factureId);
  }

  replier(): void {
    this.factureDepliee.set(null);
  }

  /** Nouvel essai de lecture du détail après une erreur. */
  reessayerDetail(factureId: string): void {
    this.chargerDetail(factureId);
  }

  private chargerDetail(factureId: string): void {
    const centre = this.centre;
    if (!centre) return;
    this.annulerDetailEnCours();
    this.poserDetail(factureId, {etat: 'chargement'});
    const abonnement = this.api.getFactureDetail(centre, factureId).subscribe({
      next: (detail) => this.poserDetail(factureId, {etat: 'pret', detail}),
      error: (err: unknown) => this.poserDetail(factureId, {etat: 'erreur', message: messageDErreur(err)}),
    });
    // une réponse synchrone (cache HTTP, test) a déjà terminé l'abonnement
    if (!abonnement.closed) this.detailEnCours = {factureId, abonnement};
  }

  /** Annule la lecture en cours et oublie son état « chargement » : la facture sera relue à son prochain dépli. */
  private annulerDetailEnCours(): void {
    const enCours = this.detailEnCours;
    this.detailEnCours = undefined;
    if (!enCours || enCours.abonnement.closed) return;
    enCours.abonnement.unsubscribe();
    if (this.details().get(enCours.factureId)?.etat === 'chargement') {
      const suivant = new Map(this.details());
      suivant.delete(enCours.factureId);
      this.details.set(suivant);
    }
  }

  private poserDetail(factureId: string, etat: EtatDuDetail): void {
    const suivant = new Map(this.details());
    suivant.set(factureId, etat);
    this.details.set(suivant);
  }

  private reinitialiser(): void {
    this.criteres.update((c) => ({...c, page: 0}));
    this.factureDepliee.set(null);
    this.annulerDetailEnCours();
    this.details.set(new Map());
    this.derniereRequete = null;
  }

  private charger(): void {
    const centre = this.centre;
    if (!centre) return;
    const requete: FacturesQuery = {...this.criteres(), ...this.periode()};
    const cle = JSON.stringify([centre, requete]);
    if (cle === this.derniereRequete) return;
    this.derniereRequete = cle;
    this.liste?.unsubscribe();
    this.chargement.set(true);
    this.erreur.set(null);
    this.liste = this.api.listFactures(centre, requete).subscribe({
      next: (page) => {
        this.page.set(page);
        this.chargement.set(false);
      },
      error: (err: unknown) => {
        this.page.set(null);
        this.erreur.set(messageDErreur(err));
        this.chargement.set(false);
        this.derniereRequete = null;
      },
    });
  }
}
