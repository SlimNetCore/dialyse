import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type PatientType = 'VACANCIER' | 'NON_VACANCIER';
export type PecStatus = 'CREE' | 'VALIDEE' | 'CLOTUREE';

export type CreatePatientPayload = {
  nom: string;
  prenom: string;
  sexe: string;
  dateAdmission: string;
  dateNaissance?: string;
  numeroAssurance: string;
  typePatient: PatientType;
  attestationDebut?: string;
  attestationFin?: string;
  centerId: string;
  userId: string;
  codePatient?: string;
  civilite?: string;
  groupeSanguin?: string;
  nombreEnfants?: number;
  enSommeil?: boolean;
  lieuNaissance?: string;
  situationFamiliale?: string;
  profession?: string;
  adresse?: string;
  telPersonnel?: string;
  telMobile?: string;
  telBureau?: string;
  email?: string;
  etatPatient?: string;
  qualiteAssure?: string;
  observation?: string;
  sousKt?: boolean;
  epoEnabled?: boolean;
  epoDate?: string;
  ferEnabled?: boolean;
  ferDate?: string;
  photoBase64?: string;
  centrePayeurId?: string | null;
  medecinTraitantId?: string | null;
  salleId?: string | null;
  positionId?: string | null;
  transporteurAllerId?: string | null;
  transporteurRetourId?: string | null;
  categorieTransportId?: string | null;
  jourDimanche?: boolean;
  jourLundi?: boolean;
  jourMardi?: boolean;
  jourMercredi?: boolean;
  jourJeudi?: boolean;
  jourVendredi?: boolean;
  jourSamedi?: boolean;
  assureNom?: string;
  assurePrenom?: string;
  assureSexe?: string;
  assureDateNaissance?: string;
  assureTelPersonnel?: string;
  assureAdresse?: string;
  assureGroupeSanguin?: string;
  assureTelMobile?: string;
  assureTelBureau?: string;
  pecDateDebutDemande?: string;
  pecDateFinDemande?: string;
  pecForfaitDemandeId?: string;
};

export type CreatePecPayload = {
  patientId: string;
  centerId: string;
  userId: string;
  dateDebutDemande: string;
  dateFinDemande: string;
};

@Injectable({ providedIn: 'root' })
export class BackendApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1';

  createPatient(payload: CreatePatientPayload): Observable<{ id: string; centerId: string; typePatient: PatientType }> {
    return this.http.post<{ id: string; centerId: string; typePatient: PatientType }>(`${this.baseUrl}/patients`, payload);
  }

  listPatients(centerId: string, userId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId).set('userId', userId);
    return this.http.get<any[]>(`${this.baseUrl}/patients`, { params });
  }

  getPatient(patientId: string, centerId: string, userId: string): Observable<unknown> {
    const params = new HttpParams().set('centerId', centerId).set('userId', userId);
    return this.http.get<unknown>(`${this.baseUrl}/patients/${patientId}`, { params });
  }

  createPec(payload: CreatePecPayload): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec`, payload);
  }

  validatePec(pecId: string, centerId: string, userId: string): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/validate`, { centerId, userId });
  }

  validatePecAdmin(pecId: string, payload: { centerId: string; userId: string; dateDebutEffectif: string; dateFinEffectif: string; forfaitEffectifId?: string }): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/validate`, payload);
  }

  listPecs(centerId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<any[]>(`${this.baseUrl}/pec`, { params });
  }

  closePec(pecId: string, centerId: string, userId: string): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/close`, { centerId, userId });
  }

  sessionAllowed(pecId: string, centerId: string, userId: string): Observable<{ allowed: boolean }> {
    const params = new HttpParams().set('centerId', centerId).set('userId', userId);
    return this.http.get<{ allowed: boolean }>(`${this.baseUrl}/pec/${pecId}/session-allowed`, { params });
  }

  getDashboardStats(centerId: string, expirationDays: number): Observable<any> {
    const params = new HttpParams().set('centerId', centerId).set('expirationDays', expirationDays.toString());
    return this.http.get<any>(`${this.baseUrl}/dashboard/stats`, { params });
  }

  getReportingModels(): Observable<Array<{ code: string; label: string }>> {
    return this.http.get<Array<{ code: string; label: string }>>(`${this.baseUrl}/reporting/models`);
  }

  listReportTemplates(centerId: string, reportType?: string): Observable<any[]> {
    let params = new HttpParams().set('centerId', centerId);
    if (reportType) params = params.set('reportType', reportType);
    return this.http.get<any[]>(`${this.baseUrl}/reporting/templates`, { params });
  }

  createReportTemplate(payload: any): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/reporting/templates`, payload);
  }

  updateReportTemplate(id: string, payload: any): Observable<{ id: string }> {
    return this.http.put<{ id: string }>(`${this.baseUrl}/reporting/templates/${id}`, payload);
  }

  deleteReportTemplate(id: string, centerId: string): Observable<{ deleted: boolean }> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/reporting/templates/${id}`, { params });
  }

  renderReport(reportType: string, centerId: string, patientId: string): Observable<string> {
    const params = new HttpParams().set('centerId', centerId).set('patientId', patientId);
    return this.http.get(`${this.baseUrl}/reporting/render/${reportType}`, {
      params,
      responseType: 'text'
    });
  }

  renderReportByTemplate(templateId: string, centerId: string, patientId: string): Observable<string> {
    const params = new HttpParams().set('centerId', centerId).set('patientId', patientId);
    return this.http.get(`${this.baseUrl}/reporting/render/template/${templateId}`, {
      params,
      responseType: 'text'
    });
  }
}
