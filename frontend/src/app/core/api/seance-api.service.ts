import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {IdResponse, Page} from './organisation-api.service';

export type SeanceContexte = {
  id: string;
  statut: string;
};

export type DerogationRegularisation = {
  id: string;
};

/**
 * Client HTTP du contexte Séance (`/api/v1/seances`, `/api/v1/derogations-regularisation`,
 * slices SE1-SE7).
 * <p>
 * SDK typé en attente de la bascule : les composants continuent d'utiliser le service
 * legacy (endpoints `/api/v1/seances` historiques) jusqu'au cutover. Aucune régression :
 * ce service n'est branché sur aucun composant.
 */
@Injectable({providedIn: 'root'})
export class SeanceApiService {
  private readonly http = inject(HttpClient);
  private readonly seances = `${environment.apiBaseUrl}/seances`;
  private readonly derogations = `${environment.apiBaseUrl}/derogations-regularisation`;

  // --- Séances (SE1) ---

  creer(patientId: string, centreId: string, pecId: string | null, dateSeance: string | null,
        typeSeance: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(this.seances,
      {patientId, centreId, pecId, dateSeance, typeSeance});
  }

  lister(centreId: string, page: number, size: number): Observable<Page<SeanceContexte>> {
    return this.http.get<Page<SeanceContexte>>(this.seances, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  listerDuJour(centreId: string, date: string, page: number,
               size: number): Observable<Page<SeanceContexte>> {
    return this.http.get<Page<SeanceContexte>>(`${this.seances}/jour`, {
      params: new HttpParams().set('centreId', centreId).set('date', date)
        .set('page', page).set('size', size),
    });
  }

  consulter(seanceId: string, centreId: string): Observable<SeanceContexte> {
    return this.http.get<SeanceContexte>(`${this.seances}/${seanceId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  // --- Validation / signature / régularisation (SE2, SE4) ---

  valider(seanceId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/validate`, {centreId});
  }

  signer(seanceId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/sign`, {centreId});
  }

  deverrouiller(seanceId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/deverrouiller`, {centreId});
  }

  debloquer(seanceId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/debloquer`, {centreId});
  }

  unlock(seanceId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/unlock`, {centreId});
  }

  // --- Forfait surchargé (SE5) ---

  declarerForfaitSurcharge(seanceId: string, centreId: string, forfaitId: string | null,
                           code: string | null, libelle: string | null, prixHt: number | null,
                           montantSurcharge: number | null,
                           auteur: string | null): Observable<void> {
    return this.http.post<void>(`${this.seances}/${seanceId}/forfait`, {
      centreId, forfaitId, code, libelle, prixHt, montantSurcharge, auteur,
    });
  }

  // --- Dérogations de régularisation (SE4) ---

  creerDerogation(seanceId: string, centreId: string, motif: string | null,
                  autorite: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(this.derogations, {seanceId, centreId, motif, autorite});
  }

  consulterDerogation(id: string, centreId: string): Observable<DerogationRegularisation> {
    return this.http.get<DerogationRegularisation>(`${this.derogations}/${id}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  consulterDerogationParSeance(seanceId: string,
                               centreId: string): Observable<DerogationRegularisation> {
    return this.http.get<DerogationRegularisation>(`${this.derogations}/seance/${seanceId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }
}
