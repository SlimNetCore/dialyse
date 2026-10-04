import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';

export type TypeMouvementPatient =
  'ADMISSION' | 'SEJOUR_TEMPORAIRE' | 'REPRISE' | 'TRANSFERT' | 'DECES' | 'GREFFE' | 'GUERISON' | 'PLACE_LIBEREE';

export const TYPES_MOUVEMENT: readonly TypeMouvementPatient[] =
  ['ADMISSION', 'SEJOUR_TEMPORAIRE', 'REPRISE', 'TRANSFERT', 'DECES', 'GREFFE', 'GUERISON', 'PLACE_LIBEREE'];

export interface MouvementPatient {
  id: string;
  patientId: string;
  patientNom: string | null;
  patientCode: string | null;
  type: TypeMouvementPatient;
  dateEffet: string;
  etatPrecedent: string | null;
  etatNouveau: string | null;
  salle: string | null;
  creneau: string | null;
  generateur: string | null;
  /** Codes des jours de dialyse séparés par des virgules (ex. `LUNDI,MERCREDI`). */
  joursDialyse: string | null;
  automatique: boolean;
  creeLe: string;
}

export interface MouvementFilters {
  type: TypeMouvementPatient | '';
  from: string;
  to: string;
}

/** Historique des mouvements de patients du centre actif (liste paginée filtrable, lecture seule). */
@Injectable({providedIn: 'root'})
export class MouvementPatientApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/mouvements-patients`;

  list(centerId: string, filters: MouvementFilters, page: number, size: number): Observable<PagedResponse<MouvementPatient>> {
    let params = new HttpParams().set('centerId', centerId).set('page', page).set('size', size);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<PagedResponse<MouvementPatient>>(this.base, {params});
  }
}
