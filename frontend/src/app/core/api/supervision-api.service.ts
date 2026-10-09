import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './admin-api.service';

/** Classement des requêtes : cumul de temps, durée moyenne d'un appel, nombre d'appels. */
export type TriRequetes = 'TEMPS_TOTAL' | 'TEMPS_MOYEN' | 'APPELS';

export type NiveauRequete = 'NORMAL' | 'ATTENTION' | 'CRITIQUE';

/** Raison pour laquelle la mesure n'est pas exploitable (absente quand elle l'est). */
export type RaisonIndisponible = 'BASE_NON_POSTGRESQL' | 'EXTENSION_ABSENTE' | 'PRELOAD_ABSENT';

export type StatutSupervision = {
  disponible: boolean;
  raison: RaisonIndisponible | null;
  reinitialiseLe: string | null;
  tempsTotalMs: number;
};

/** Piste d'amélioration d'une requête ; le texte est `SUPERVISION.CONSEIL.<code>` (traduit), `valeur` en est le chiffre. */
export type ConseilRequete = {
  code: string;
  niveau: 'ACTION' | 'INFO';
  valeur: string;
};

export type VerdictBalayage = 'INDEX_RECOMMANDE' | 'TABLE_PETITE' | 'INDEX_PRESENT' | 'SANS_FILTRE';

export type BalayageComplet = {
  table: string;
  filtre: string;
  colonnes: string[];
  lignesTable: number;
  indexExistants: string[];
  verdict: VerdictBalayage;
  /** Ordre CREATE INDEX à étudier (seulement pour INDEX_RECOMMANDE). */
  indexSuggere: string | null;
};

export type AnalyseRequete = {
  disponible: boolean;
  raison: 'NON_SELECT' | 'PLAN_IMPOSSIBLE' | null;
  requete: string;
  planTexte: string | null;
  coutTotal: number;
  indexUtilises: number;
  balayagesComplets: BalayageComplet[];
};

export type NiveauSante = 'CRITIQUE' | 'ATTENTION' | 'INFO';
export type StatutPartitionnement = 'OK' | 'A_SURVEILLER' | 'A_ETUDIER';

export type AlerteSante = { code: string; niveau: NiveauSante; cible: string; valeur: string };

export type TableSante = {
  nom: string;
  tailleOctets: number;
  lignes: number;
  mortesPct: number;
  scansComplets: number;
  scansIndex: number;
  dernierVacuum: string | null;
  partitionnement: StatutPartitionnement;
};

export type IndexInutilise = { table: string; index: string; tailleOctets: number };

export type SanteBase = {
  disponible: boolean;
  raison: 'BASE_NON_POSTGRESQL' | null;
  tailleOctets: number;
  cachePct: number;
  connexions: number;
  connexionsMax: number;
  statsReset: string | null;
  tables: TableSante[];
  indexInutilises: IndexInutilise[];
  alertes: AlerteSante[];
};

export type RequeteBase = {
  id: string;
  /** SQL normalisé : les valeurs sont remplacées par $1, $2… (aucune donnée de patient). */
  requete: string;
  appels: number;
  tempsTotalMs: number;
  tempsMoyenMs: number;
  tempsMaxMs: number;
  lignes: number;
  partTempsTotalPct: number;
  niveau: NiveauRequete;
  conseils: ConseilRequete[];
};

/**
 * Performance de la base — réservée au propriétaire (SUPERADMIN, contrôlé aussi côté serveur) : requêtes les plus
 * coûteuses relevées par pg_stat_statements, et remise à zéro des compteurs.
 */
@Injectable({providedIn: 'root'})
export class SupervisionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/supervision/base-donnees`;

  statut(): Observable<StatutSupervision> {
    return this.http.get<StatutSupervision>(`${this.base}/statut`, {withCredentials: true});
  }

  requetes(tri: TriRequetes, page: number, size: number): Observable<PagedResponse<RequeteBase>> {
    const params = new HttpParams().set('tri', tri).set('page', page).set('size', size);
    return this.http.get<PagedResponse<RequeteBase>>(`${this.base}/requetes`, {params, withCredentials: true});
  }

  /** Plan d'exécution d'une requête mesurée, établi sans l'exécuter (l'identifiant seul est transmis, jamais du SQL). */
  analyse(id: string): Observable<AnalyseRequete> {
    return this.http.get<AnalyseRequete>(`${this.base}/requetes/${encodeURIComponent(id)}/analyse`,
      {withCredentials: true});
  }

  sante(): Observable<SanteBase> {
    return this.http.get<SanteBase>(`${this.base}/sante`, {withCredentials: true});
  }

  reinitialiser(): Observable<void> {
    return this.http.post<void>(`${this.base}/reinitialisation`, null, {withCredentials: true});
  }
}
