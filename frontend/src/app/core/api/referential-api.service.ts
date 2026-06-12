import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface RefItem {
  id: string;
  nom: string;
  code?: string;
  libelle?: string;
  unite?: string;
  adresse?: string;
  prenom?: string;
}
export interface CentrePayeurDetail {
  id: string;
  codeCentrePayeur: string;
  libelleCentrePayeur: string;
  adresseCentrePayeur?: string;
  codeAgence?: string;
  libelleAgence?: string;
  codeCaisse?: string;
  libelleCaisse?: string;
}

@Injectable({ providedIn: 'root' })
export class ReferentialApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/referentials`;

  private get(endpoint: string, centerId: string): Observable<RefItem[]> {
    return this.http.get<RefItem[]>(`${this.base}/${endpoint}`, {
      params: new HttpParams().set('centerId', centerId)
    });
  }

  getCentresPayeurs(centerId: string) { return this.get('centres-payeurs', centerId); }
  getCentresPayeursDetails(centerId: string): Observable<CentrePayeurDetail[]> {
    return this.http.get<CentrePayeurDetail[]>(`${this.base}/centres-payeurs-details`, {
      params: new HttpParams().set('centerId', centerId)
    });
  }
  getMedecins(centerId: string) { return this.get('medecins', centerId); }
  getSalles(centerId: string) { return this.get('salles', centerId); }
  getPositions(centerId: string) { return this.get('positions', centerId); }
  getTransporteurs(centerId: string) { return this.get('transporteurs', centerId); }
  getAgences(centerId: string) { return this.get('agences', centerId); }
  getCaisses(centerId: string) { return this.get('caisses', centerId); }
  getCategoriesTransport(centerId: string) { return this.get('categories-transport', centerId); }
  getForfaits(centerId: string) { return this.get('forfaits', centerId); }

  getArticles(centerId: string) {
    return this.get('articles', centerId);
  }
}
