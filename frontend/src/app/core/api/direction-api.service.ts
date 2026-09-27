import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

/** Indicateurs d'un centre (ou totaux de la société). Un effectif inférieur au seuil d'anonymat vaut `null`. */
export type CentreStats = {
  centerId: string | null;
  nom: string;
  actif: boolean;
  patients: number | null;
  patientsSousKt: number | null;
  seances: number;
  factures: number;
  caHt: number;
  caTtc: number;
  encaisse: number;
  resteARecouvrer: number;
  tauxEncaissement: number | null;
};

export type MonthlyPoint = {
  mois: string;
  centerId: string;
  seances: number;
  caHt: number;
  caTtc: number;
};

/**
 * Totaux de la période de même durée précédant immédiatement la période affichée (activité et finances
 * uniquement) — pour calculer un delta à côté des indicateurs clés. `null` si la société n'a aucun centre.
 */
export type PeriodComparison = {
  from: string;
  to: string;
  seances: number;
  caHt: number;
  caTtc: number;
  encaisse: number;
  resteARecouvrer: number;
  tauxEncaissement: number | null;
};

export type DirectionOverview = {
  societeId: string;
  societeNom: string;
  from: string;
  to: string;
  generatedAt: string;
  seuilAnonymat: number;
  centres: CentreStats[];
  totaux: CentreStats;
  mensuel: MonthlyPoint[];
  periodePrecedente: PeriodComparison | null;
};

/** Patients évalués et répartition face à la cible KDIGO ; `null` sous le seuil d'anonymat. */
export type IndicatorMarker = {
  evalues: number | null;
  pctDansCible: number | null;
  pctSousCible: number | null;
  pctAuDessus: number | null;
};

export type ClinicalIndicators = {
  ktV: IndicatorMarker;
  hemoglobine: IndicatorMarker;
  phosphore: IndicatorMarker;
  pth: IndicatorMarker;
  albumine: IndicatorMarker;
  vhbPositifs: number | null;
  vhcPositifs: number | null;
  vihPositifs: number | null;
  patientsObservanceEnRetard: number | null;
  greffeListeAttente: number | null;
  greffeBilanEnCours: number | null;
  greffesPeriode: number | null;
};

export type StockIndicators = {
  articlesActifs: number;
  articlesSousSeuil: number;
  lotsPerimes: number;
  lotsPeremptionProche: number;
  valeurStock: number;
};

export type CentreIndicators = {
  centerId: string | null;
  nom: string | null;
  actif: boolean;
  clinique: ClinicalIndicators;
  stock: StockIndicators;
};

export type DirectionAlert = {
  centerId: string;
  centre: string;
  code: string;
  severity: 'WARNING' | 'CRITICAL';
  valeur: number | null;
};

/** Entrée de l'historique des alertes : `resolvedAt` nul tant que l'alerte est toujours active. */
export type AlertHistoryEntry = {
  centerId: string;
  centre: string;
  code: string;
  severity: 'WARNING' | 'CRITICAL';
  valeur: number | null;
  firstSeenAt: string;
  resolvedAt: string | null;
};

export type DirectionIndicators = {
  societeId: string;
  from: string;
  to: string;
  generatedAt: string;
  seuilAnonymat: number;
  centres: CentreIndicators[];
  totaux: CentreIndicators;
  alertes: DirectionAlert[];
};

export type SexeRow = {
  centerId: string;
  nom: string;
  masculin: number | null;
  feminin: number | null;
  autre: number | null;
};

export type AgeRow = {
  centerId: string;
  nom: string;
  tranches: { code: string; count: number | null }[];
};

/** `caisseCode` vide : patients sans caisse renseignée. */
export type CaisseRow = {
  centerId: string;
  centre: string;
  caisseCode: string;
  caisse: string | null;
  patients: number | null;
  seances: number;
  caHt: number;
};

export type CaisseTotal = {
  caisseCode: string;
  caisse: string | null;
  patients: number | null;
  seances: number;
  caHt: number;
};

export type AnemieRow = {
  centerId: string;
  nom: string;
  patientsEpo: number | null;
  patientsFer: number | null;
  administreesEpo: number;
  administreesFer: number;
  nonAdministrees: number;
  tauxAdministration: number | null;
  patientsSousEpo: number | null;
  patientsSousFer: number | null;
};

export type DirectionBreakdown = {
  societeId: string;
  from: string;
  to: string;
  generatedAt: string;
  seuilAnonymat: number;
  sexe: SexeRow[];
  ages: AgeRow[];
  caisses: CaisseRow[];
  caisseTotaux: CaisseTotal[];
  anemie: AnemieRow[];
};

/** Un indicateur qui a changé (temps réel). */
export type DashboardChange = {
  centerId: string;
  centre: string;
  family: string;
  name: string;
  before: number;
  after: number;
};

/** Événement diffusé à la direction : uniquement des indicateurs agrégés (la relecture passe par l'API). */
export type DashboardChangedEvent = {
  type: 'DASHBOARD_CHANGED';
  societeId: string;
  at: string;
  changes: DashboardChange[];
};

/** Mois figé (instantané mensuel des tableaux de bord). */
export type SnapshotInfo = {
  mois: string;
  generatedAt: string;
};

/**
 * Contenu complet d'un instantané mensuel figé : mêmes formes que les endpoints en direct, pour réutiliser les
 * mêmes composants d'affichage. `breakdown` est absent (`null`) sur les instantanés créés avant son ajout.
 */
export type Snapshot = {
  mois: string;
  generatedAt: string;
  overview: DirectionOverview;
  indicators: DirectionIndicators;
  breakdown: DirectionBreakdown | null;
};

export type DirectionAccount = {
  userId: string;
  username: string;
  fullName: string | null;
  email: string | null;
  active: boolean;
};

export type CreateDirectionAccount = {
  username: string;
  fullName?: string;
  email?: string;
  password: string;
};

/** Tableau de bord de la direction (lecture seule, agrégats anonymes) et comptes direction d'une société. */
@Injectable({providedIn: 'root'})
export class DirectionApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  overview(from?: string, to?: string): Observable<DirectionOverview> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<DirectionOverview>(`${this.baseUrl}/direction/overview`, {params, withCredentials: true});
  }

  indicators(from?: string, to?: string): Observable<DirectionIndicators> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<DirectionIndicators>(`${this.baseUrl}/direction/indicators`, {params, withCredentials: true});
  }

  breakdown(from?: string, to?: string): Observable<DirectionBreakdown> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<DirectionBreakdown>(`${this.baseUrl}/direction/breakdown`, {params, withCredentials: true});
  }

  listSnapshots(): Observable<SnapshotInfo[]> {
    return this.http.get<SnapshotInfo[]>(`${this.baseUrl}/direction/snapshots`, {withCredentials: true});
  }

  /** Contenu d'un mois déjà figé (404 s'il ne l'est pas encore : voir `ensureSnapshot`). */
  getSnapshot(mois: string): Observable<Snapshot> {
    return this.http.get<Snapshot>(`${this.baseUrl}/direction/snapshots/${mois}`, {withCredentials: true});
  }

  /** Historique récent des alertes (apparitions et résolutions), du plus récemment actif au plus ancien. */
  alertsHistory(limit = 50): Observable<AlertHistoryEntry[]> {
    return this.http.get<AlertHistoryEntry[]>(`${this.baseUrl}/direction/alerts/history`,
      {params: new HttpParams().set('limit', limit), withCredentials: true});
  }

  /** Fige le mois écoulé s'il ne l'est pas encore (idempotent). */
  ensureSnapshot(mois: string): Observable<unknown> {
    return this.http.post<unknown>(`${this.baseUrl}/direction/snapshots/${mois}`, {}, {withCredentials: true});
  }

  downloadReport(mois: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/direction/snapshots/${mois}/report`,
      {responseType: 'blob', withCredentials: true});
  }

  /** Rapport PDF (en-tête, pied de page, toutes les statistiques) de la période affichée. */
  downloadLiveReport(from?: string, to?: string): Observable<Blob> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get(`${this.baseUrl}/direction/report`, {params, responseType: 'blob', withCredentials: true});
  }

  listAccounts(societeId: string): Observable<DirectionAccount[]> {
    return this.http.get<DirectionAccount[]>(`${this.baseUrl}/societes/${societeId}/direction-accounts`,
      {withCredentials: true});
  }

  createAccount(societeId: string, payload: CreateDirectionAccount): Observable<DirectionAccount> {
    return this.http.post<DirectionAccount>(`${this.baseUrl}/societes/${societeId}/direction-accounts`, payload,
      {withCredentials: true});
  }

  setAccountActive(societeId: string, userId: string, active: boolean): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/societes/${societeId}/direction-accounts/${userId}/${active ? 'activer' : 'desactiver'}`, {},
      {withCredentials: true});
  }

  resetAccountPassword(societeId: string, userId: string, password: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/societes/${societeId}/direction-accounts/${userId}/password`,
      {password}, {withCredentials: true});
  }
}
