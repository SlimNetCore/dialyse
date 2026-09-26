import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type CentreSociete = {
  id: string;
  code: string;
  nom: string;
  adresse: string | null;
  ville: string | null;
  wilaya: string | null;
  telephone: string | null;
  email: string | null;
  siteWeb: string | null;
  actif: boolean;
};

export type Societe = {
  id: string;
  code: string;
  raisonSociale: string;
  nif: string | null;
  nis: string | null;
  rc: string | null;
  adresse: string | null;
  ville: string | null;
  wilaya: string | null;
  telephone: string | null;
  email: string | null;
  siteWeb: string | null;
  piedDePage: string | null;
  hasLogo: boolean;
  actif: boolean;
  createdAt: string;
  centres: CentreSociete[];
};

export type SocietePage = {
  items: Societe[];
  total: number;
  page: number;
  size: number;
};

export type SocietePayload = {
  code: string;
  raisonSociale: string;
  nif: string | null;
  nis: string | null;
  rc: string | null;
  adresse: string | null;
  ville: string | null;
  wilaya: string | null;
  telephone: string | null;
  email: string | null;
  siteWeb: string | null;
  piedDePage: string | null;
};

export type CentrePayload = {
  code: string;
  nom: string;
  adresse: string | null;
  ville: string | null;
  wilaya: string | null;
  telephone: string | null;
  email: string | null;
  siteWeb: string | null;
};

/** Gestion des sociétés et de leurs centres — réservée au SUPERADMIN (contrôlé aussi côté serveur). */
@Injectable({providedIn: 'root'})
export class SocieteApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/societes`;

  list(search: string, page: number, size: number): Observable<SocietePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) params = params.set('q', search.trim());
    return this.http.get<SocietePage>(this.base, {params});
  }

  get(id: string): Observable<Societe> {
    return this.http.get<Societe>(`${this.base}/${id}`);
  }

  /** Une société ne se crée qu'avec son premier centre. */
  create(societe: SocietePayload, premierCentre: CentrePayload): Observable<Societe> {
    return this.http.post<Societe>(this.base, {societe, premierCentre});
  }

  update(id: string, societe: SocietePayload): Observable<Societe> {
    return this.http.put<Societe>(`${this.base}/${id}`, societe);
  }

  setActive(id: string, active: boolean): Observable<Societe> {
    return this.http.post<Societe>(`${this.base}/${id}/${active ? 'activer' : 'desactiver'}`, {});
  }

  /** Envoie le logo (PNG ou JPEG) ; le serveur contrôle le contenu et répond 422 `{violations}` s'il est refusé. */
  uploadLogo(id: string, file: File): Observable<Societe> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.put<Societe>(`${this.base}/${id}/logo`, body);
  }

  deleteLogo(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/logo`);
  }

  /** Logo sous forme de blob (l'appel est authentifié : une simple balise <img> ne suffirait pas hors même origine). */
  getLogo(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/logo`, {responseType: 'blob'});
  }

  addCentre(societeId: string, centre: CentrePayload): Observable<Societe> {
    return this.http.post<Societe>(`${this.base}/${societeId}/centres`, centre);
  }

  updateCentre(societeId: string, centreId: string, centre: CentrePayload): Observable<Societe> {
    return this.http.put<Societe>(`${this.base}/${societeId}/centres/${centreId}`, centre);
  }

  setCentreActive(societeId: string, centreId: string, active: boolean): Observable<Societe> {
    return this.http.post<Societe>(`${this.base}/${societeId}/centres/${centreId}/${active ? 'activer' : 'desactiver'}`, {});
  }

  transferCentre(societeId: string, centreId: string, societeCibleId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${societeId}/centres/${centreId}/transfert`, {societeCibleId});
  }
}
