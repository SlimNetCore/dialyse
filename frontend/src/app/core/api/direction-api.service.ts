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
