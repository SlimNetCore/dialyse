import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface License {
  id: string;
  centerId: string;
  centerName: string | null;
  /** Société du centre (le propriétaire lit les licences par société puis par centre). */
  societeId?: string | null;
  societeName?: string | null;
  licenseKey: string;
  type: 'STANDARD' | 'TRIAL';
  maxUsers: number;
  validFrom: string;
  validUntil: string;
  status: 'ACTIVE' | 'REVOKED';
  activatedAt: string | null;
  lastOnlineCheckAt: string | null;
  revokedReason: string | null;
  createdAt: string;
}

export interface LicenseStatus {
  applicable: boolean;
  valid?: boolean;
  reason?: string | null;
  type?: 'STANDARD' | 'TRIAL';
  maxUsers?: number;
  expiresAt?: string;
  usedSeats?: number;
}

@Injectable({providedIn: 'root'})
export class LicenseApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listAll(): Observable<License[]> {
    return this.http.get<License[]>(`${this.base}/licenses`);
  }

  issue(payload: {
    centerId: string;
    type: string;
    maxUsers: number;
    validFrom: string;
    validUntil: string
  }): Observable<License> {
    return this.http.post<License>(`${this.base}/licenses/issue`, payload);
  }

  /** Attribue une licence à une société : une licence (donc une clé) par centre actif ou par centre sélectionné. */
  issueForSociete(payload: {
    societeId: string;
    /** Vide = tous les centres actifs de la société. */
    centerIds: string[];
    type: string;
    maxUsers: number;
    validFrom: string;
    validUntil: string;
  }): Observable<License[]> {
    return this.http.post<License[]>(`${this.base}/licenses/issue-societe`, payload);
  }

  revoke(id: string, reason: string): Observable<{ revoked: boolean }> {
    return this.http.post<{ revoked: boolean }>(`${this.base}/licenses/${id}/revoke`, {reason});
  }

  activate(payload: { centerId: string; licenseKey: string }): Observable<License> {
    return this.http.post<License>(`${this.base}/licenses/activate`, payload);
  }

  status(): Observable<LicenseStatus> {
    return this.http.get<LicenseStatus>(`${this.base}/licenses/status`);
  }
}
