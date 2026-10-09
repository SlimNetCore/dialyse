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

  reinitialiser(): Observable<void> {
    return this.http.post<void>(`${this.base}/reinitialisation`, null, {withCredentials: true});
  }
}
