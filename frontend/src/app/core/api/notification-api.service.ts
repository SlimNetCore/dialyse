import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';

/** Alerte durable du journal du centre : même forme qu'un évènement temps réel, avec son identifiant et sa lecture. */
export interface AlerteServeur {
  id: string;
  type: string;
  centerId: string;
  payload: Record<string, string>;
  timestamp: string;
  lue: boolean;
}

/** Alertes durables du centre de l'utilisateur (celles destinées à ses rôles), retrouvées à la connexion. */
@Injectable({providedIn: 'root'})
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/notifications`;

  private static centre(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }

  liste(centerId: string, page = 0, size = 50): Observable<PagedResponse<AlerteServeur>> {
    const params = NotificationApiService.centre(centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<AlerteServeur>>(this.base, {params});
  }

  marquerLues(centerId: string, ids: string[]): Observable<void> {
    return this.http.post<void>(`${this.base}/lecture`, {ids}, {params: NotificationApiService.centre(centerId)});
  }

  toutMarquerLu(centerId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/lecture-tout`, null, {params: NotificationApiService.centre(centerId)});
  }
}
