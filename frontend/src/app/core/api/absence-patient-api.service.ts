import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';

export type StatutAbsence = 'A_QUALIFIER' | 'JUSTIFIEE' | 'NON_JUSTIFIEE' | 'RATTRAPEE' | 'ANNULEE';
export type MotifAbsence =
  'HOSPITALISATION'
  | 'MALADIE'
  | 'VOYAGE'
  | 'TRANSPORT'
  | 'FAMILIAL'
  | 'REFUS_PATIENT'
  | 'DECES'
  | 'AUTRE'
  | 'NON_JUSTIFIEE';
export type SourceAbsence = 'AUTOMATIQUE' | 'DECLAREE';

export const STATUTS_ABSENCE: readonly StatutAbsence[] =
  ['A_QUALIFIER', 'JUSTIFIEE', 'NON_JUSTIFIEE', 'RATTRAPEE', 'ANNULEE'];
export const MOTIFS_ABSENCE: readonly MotifAbsence[] = [
  'HOSPITALISATION', 'MALADIE', 'VOYAGE', 'TRANSPORT', 'FAMILIAL', 'REFUS_PATIENT', 'DECES', 'AUTRE', 'NON_JUSTIFIEE',
];

export interface AbsencePatient {
  id: string;
  patientId: string;
  patientNom: string | null;
  dateSeance: string;
  source: SourceAbsence;
  statut: StatutAbsence;
  motif: MotifAbsence | null;
  commentaire: string | null;
  forfaitLibelle: string | null;
  prixTtc: number;
  tauxTva: number;
  montantHt: number;
  declareeLe: string | null;
  qualifieeLe: string | null;
  dateRattrapage: string | null;
  enRetard: boolean;
}

/** Absence d'une séance du planning de la semaine (patient + jour). */
export interface AbsenceSemaine {
  absenceId: string;
  patientId: string;
  dateSeance: string;
  statut: StatutAbsence;
  motif: MotifAbsence | null;
}

/** Séance réalisée : validée par l'infirmier à la présence du patient (un patient, un jour). */
export interface SeanceRealisee {
  patientId: string;
  dateSeance: string;
}

export interface SuiviSemaine {
  absences: AbsenceSemaine[];
  seancesRealisees: SeanceRealisee[];
}

export interface AbsenceSynthese {
  aQualifier: number;
  enRetard: number;
}

/** Résultat d'un rattrapage de détection : absences créées et absences annulées (séance saisie après coup). */
export interface RattrapageDetection {
  creees: number;
  annulees: number;
}

export interface AbsenceFilters {
  statut: StatutAbsence | '';
  motif: MotifAbsence | '';
  from: string;
  to: string;
}

export interface DeclarationAbsencePayload {
  patientId: string;
  dateSeance: string;
  motif: MotifAbsence | null;
  commentaire: string | null;
}

/** Suivi des absences de patients du centre actif (liste paginée filtrable, déclaration, qualification…). */
@Injectable({providedIn: 'root'})
export class AbsencePatientApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/absences-patients`;

  private static centre(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }

  list(centerId: string, filters: AbsenceFilters, page: number, size: number): Observable<PagedResponse<AbsencePatient>> {
    let params = AbsencePatientApiService.centre(centerId).set('page', page).set('size', size);
    if (filters.statut) params = params.set('statut', filters.statut);
    if (filters.motif) params = params.set('motif', filters.motif);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<PagedResponse<AbsencePatient>>(this.base, {params});
  }

  /** Absences non annulées et séances réalisées de la semaine contenant `date` (grille du planning, 7 jours). */
  semaine(centerId: string, date?: string): Observable<SuiviSemaine> {
    let params = AbsencePatientApiService.centre(centerId);
    if (date) params = params.set('date', date);
    return this.http.get<SuiviSemaine>(`${this.base}/semaine`, {params});
  }

  synthese(centerId: string): Observable<AbsenceSynthese> {
    return this.http.get<AbsenceSynthese>(`${this.base}/synthese`, {params: AbsencePatientApiService.centre(centerId)});
  }

  declarer(centerId: string, payload: DeclarationAbsencePayload): Observable<AbsencePatient> {
    return this.http.post<AbsencePatient>(this.base, payload, {params: AbsencePatientApiService.centre(centerId)});
  }

  qualifier(centerId: string, id: string, motif: MotifAbsence, commentaire: string | null): Observable<AbsencePatient> {
    return this.http.put<AbsencePatient>(`${this.base}/${id}/qualification`, {motif, commentaire},
      {params: AbsencePatientApiService.centre(centerId)});
  }

  rattraper(centerId: string, id: string, dateRattrapage: string): Observable<AbsencePatient> {
    return this.http.put<AbsencePatient>(`${this.base}/${id}/rattrapage`, {dateRattrapage},
      {params: AbsencePatientApiService.centre(centerId)});
  }

  /** Rattrapage des absences jamais détectées sur une période passée (administrateur du centre). */
  rattraperDetection(centerId: string, from: string, to: string): Observable<RattrapageDetection> {
    return this.http.post<RattrapageDetection>(`${this.base}/rattrapage-detection`, {from, to},
      {params: AbsencePatientApiService.centre(centerId)});
  }

  annuler(centerId: string, id: string, commentaire: string): Observable<AbsencePatient> {
    return this.http.put<AbsencePatient>(`${this.base}/${id}/annulation`, {commentaire},
      {params: AbsencePatientApiService.centre(centerId)});
  }
}
