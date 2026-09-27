import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './admin-api.service';

/** Une entrée du journal d'audit (« qui a fait quoi »). */
export type AuditEntry = {
  occurredAt: string;
  userId: string | null;
  username: string | null;
  roles: string | null;
  centerId: string | null;
  societeId: string | null;
  actionCode: string;
  entityType: string | null;
  entityId: string | null;
  libelle: string | null;
  httpMethod: string | null;
  routeTemplate: string | null;
  statusCode: number | null;
  durationMs: number | null;
  ipAddress: string | null;
};

export type AuditSearchQuery = {
  centerId?: string;
  societeId?: string;
  userId?: string;
  actionCode?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
};

/**
 * Journal d'audit : réservé à ADMIN (son propre centre — jamais un paramètre client) et SUPERADMIN (toute la
 * plateforme, filtrable). Jamais accessible à DIRECTION.
 */
@Injectable({providedIn: 'root'})
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  search(query: AuditSearchQuery): Observable<PagedResponse<AuditEntry>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.centerId) params = params.set('centerId', query.centerId);
    if (query.societeId) params = params.set('societeId', query.societeId);
    if (query.userId) params = params.set('userId', query.userId);
    if (query.actionCode) params = params.set('actionCode', query.actionCode);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    return this.http.get<PagedResponse<AuditEntry>>(`${this.base}/audit`, {params, withCredentials: true});
  }

  actionCodes(societeId?: string): Observable<string[]> {
    let params = new HttpParams();
    if (societeId) params = params.set('societeId', societeId);
    return this.http.get<string[]>(`${this.base}/audit/action-codes`, {params, withCredentials: true});
  }
}
