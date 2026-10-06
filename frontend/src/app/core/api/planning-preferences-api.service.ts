import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';
import {JourSemaine} from './planning-api.service';

export type CompetenceInfirmier = 'PEDIATRIE' | 'CATHETER';
export const COMPETENCES_INFIRMIER: readonly CompetenceInfirmier[] = ['PEDIATRIE', 'CATHETER'];
export type QualificationInfirmier = 'INFIRMIER' | 'AIDE_SOIGNANT' | string;

/** Bornes acceptées par le serveur pour les préférences, profils et réglages de planification. */
export const BORNES_PREFERENCES = {
  seances: {min: 1, max: 7},
  taux: {min: 10, max: 100},
  heuresVacation: {min: 1, max: 12},
  heuresHebdo: {min: 10, max: 60},
  repos: {min: 0, max: 6},
} as const;

/** Préférence de planification d'un patient : créneau souhaité, jours choisis par l'optimisation. */
export interface PreferencePatient {
  patientId: string;
  creneauPrefereId: string | null;
  seancesParSemaine: number | null;
  joursAChoisir: boolean;
}

export interface LignePreferencePatient {
  patientId: string;
  nom: string;
  jours: JourSemaine[];
  creneauActuelId: string | null;
  preference: PreferencePatient;
}

export type PreferencePayload = Omit<PreferencePatient, 'patientId'>;

/** Profil de planification d'un infirmier : temps partiel (taux) et compétences particulières. */
export interface ProfilInfirmier {
  infirmierId: string;
  tauxActivite: number;
  competences: CompetenceInfirmier[];
}

export interface LigneProfilInfirmier {
  infirmierId: string;
  nom: string;
  qualification: QualificationInfirmier;
  profil: ProfilInfirmier;
}

export type ProfilPayload = Omit<ProfilInfirmier, 'infirmierId'>;

/** Réglages de planification du centre (contraintes de personnel, replanification automatique nocturne). */
export interface ReglagesOptimisation {
  replanificationAuto: boolean;
  heuresParVacation: number;
  heuresHebdoTempsPlein: number;
  reposHebdoMin: number;
}

/** Préférences des patients, profils des infirmiers et réglages de planification du centre actif. */
@Injectable({providedIn: 'root'})
export class PlanningPreferencesApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/planning/preferences`;

  private static centre(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }

  patients(centerId: string, page: number, size: number): Observable<PagedResponse<LignePreferencePatient>> {
    const params = PlanningPreferencesApiService.centre(centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<LignePreferencePatient>>(`${this.base}/patients`, {params});
  }

  enregistrerPreference(centerId: string, patientId: string, payload: PreferencePayload): Observable<PreferencePatient> {
    return this.http.put<PreferencePatient>(`${this.base}/patients/${patientId}`, payload,
      {params: PlanningPreferencesApiService.centre(centerId)});
  }

  infirmiers(centerId: string, page: number, size: number): Observable<PagedResponse<LigneProfilInfirmier>> {
    const params = PlanningPreferencesApiService.centre(centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<LigneProfilInfirmier>>(`${this.base}/infirmiers`, {params});
  }

  enregistrerProfil(centerId: string, infirmierId: string, payload: ProfilPayload): Observable<ProfilInfirmier> {
    return this.http.put<ProfilInfirmier>(`${this.base}/infirmiers/${infirmierId}`, payload,
      {params: PlanningPreferencesApiService.centre(centerId)});
  }

  reglages(centerId: string): Observable<ReglagesOptimisation> {
    return this.http.get<ReglagesOptimisation>(`${this.base}/reglages`,
      {params: PlanningPreferencesApiService.centre(centerId)});
  }

  enregistrerReglages(centerId: string, reglages: ReglagesOptimisation): Observable<ReglagesOptimisation> {
    return this.http.put<ReglagesOptimisation>(`${this.base}/reglages`, reglages,
      {params: PlanningPreferencesApiService.centre(centerId)});
  }
}
