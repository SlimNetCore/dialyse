import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {IdResponse, Page} from './organisation-api.service';

export type AlerteDirectionPilotage = {
  id: string;
  type: string;
  lue: boolean;
};

export type InstantaneMensuelPilotage = {
  id: string;
  annee: number;
  mois: number;
};

export type TableauDeBordPilotage = {
  centreId: string;
};

export type ExportSynthesePilotage = {
  id: string;
  statut: string;
};

/**
 * Client HTTP du contexte Pilotage-direction (`/api/v1/pilotage/*`, slices PD1-PD5).
 * <p>
 * SDK typé en attente de la bascule : les composants continuent d'utiliser
 * `DirectionApiService` (endpoints `/api/v1/direction/*`) jusqu'au cutover.
 * Aucune régression : ce service n'est branché sur aucun composant.
 */
@Injectable({providedIn: 'root'})
export class PilotageApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/pilotage`;

  // --- Tableau de bord (PD1) ---

  consulterTableauDeBord(centreId: string): Observable<TableauDeBordPilotage> {
    return this.http.get<TableauDeBordPilotage>(`${this.base}/tableau-de-bord`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  actualiserTableauDeBord(centreId: string,
                          indicateurs: string): Observable<TableauDeBordPilotage> {
    return this.http.post<TableauDeBordPilotage>(`${this.base}/tableau-de-bord`,
      {centreId, indicateurs});
  }

  // --- Instantanés mensuels (PD2) ---

  listerInstantanes(centreId: string, page: number,
                    size: number): Observable<Page<InstantaneMensuelPilotage>> {
    return this.http.get<Page<InstantaneMensuelPilotage>>(`${this.base}/instantanes-mensuels`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  consulterInstantane(instantaneId: string,
                      centreId: string): Observable<InstantaneMensuelPilotage> {
    return this.http.get<InstantaneMensuelPilotage>(
      `${this.base}/instantanes-mensuels/${instantaneId}`, {
        params: new HttpParams().set('centreId', centreId),
      });
  }

  capturerInstantane(centreId: string, annee: number, mois: number,
                     donnees: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/instantanes-mensuels`,
      {centreId, annee, mois, donnees});
  }

  // --- Alertes (PD3) ---

  listerAlertes(centreId: string, page: number,
                size: number): Observable<Page<AlerteDirectionPilotage>> {
    return this.http.get<Page<AlerteDirectionPilotage>>(`${this.base}/alertes`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  consulterAlerte(alerteId: string, centreId: string): Observable<AlerteDirectionPilotage> {
    return this.http.get<AlerteDirectionPilotage>(`${this.base}/alertes/${alerteId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  genererAlerte(centreId: string, type: string, seuil: string | null, message: string | null,
                date: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/alertes`,
      {centreId, type, seuil, message, date});
  }

  acquitterAlerte(alerteId: string, centreId: string): Observable<AlerteDirectionPilotage> {
    return this.http.post<AlerteDirectionPilotage>(`${this.base}/alertes/${alerteId}/acquittement`,
      {centreId});
  }

  // --- Exports synthèse (PD4) ---

  genererExport(centreId: string, periode: string, format: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/exports-synthese`,
      {centreId, periode, format});
  }

  telechargerExport(exportId: string, centreId: string): Observable<ExportSynthesePilotage> {
    return this.http.get<ExportSynthesePilotage>(`${this.base}/exports-synthese/${exportId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }
}
