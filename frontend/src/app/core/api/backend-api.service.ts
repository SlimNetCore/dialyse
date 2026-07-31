import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams, HttpResponse} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type PatientType = 'VACANCIER' | 'NON_VACANCIER';
export type PecStatus = 'CREE' | 'VALIDEE' | 'CLOTUREE';

export type PagedResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type ListQuery = {
  page: number;
  size: number;
  filters?: Record<string, string>;
};

export type CreatePatientPayload = {
  nom: string;
  prenom: string;
  sexe: string;
  dateAdmission: string;
  dateNaissance?: string;
  numeroAssurance: string;
  typePatient: PatientType;
  attestationId?: string;
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
  dateEvenementEtat?: string;
  qualiteAssure?: string;
  observation?: string;
  sousKt?: boolean;
  epoEnabled?: boolean;
  epoDate?: string;
  ferEnabled?: boolean;
  ferDate?: string;
  photoBase64?: string;
  centrePayeurId?: string | null;
  assureNumeroAssurance?: string;
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
  assureHistoryJson?: string;
  piecesJointesJson?: string;
  pecId?: string;
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

export type CreateSeancePayload = {
  centerId: string;
  patientId: string;
  dateSeance: string;
};

export type ScanSeanceQrPayload = {
  centerId: string;
  qrCode: string;
};

export type UpdateSeancePayload = {
  centerId: string;
  dateSeance?: string;
};

export type SeanceListItem = {
  id: string;
  centerId: string;
  patientId: string;
  patientCode?: string | null;
  patientNom?: string | null;
  patientPrenom?: string | null;
  dateSeance: string;
  status: string;
  createdAt?: string;
  validatedAt?: string;
  signedByInfirmierAt?: string;
  signedByMedecinAt?: string;
  forfait?: {
    id: string;
    code?: string | null;
    nom?: string | null;
    prix?: number | null;
    nombreSeances?: number | null;
  } | null;
};

export type SeanceSummary = {
  seance: {
    id: string;
    centerId: string;
    patientId: string;
    dateSeance: string;
    status: string;
    createdAt?: string;
    validatedAt?: string;
    signedByInfirmierAt?: string;
    signedByMedecinAt?: string;
  };
  patient: {
    id: string;
    codePatient?: string;
    nom?: string;
    prenom?: string;
    sexe?: string;
    dateNaissance?: string;
    numeroAssurance?: string;
    groupeSanguin?: string;
    telMobile?: string;
    medecinTraitantId?: string;
    salleId?: string;
    positionId?: string;
  };
  paramedical?: {
    poidsAvantKg?: number;
    poidsApresKg?: number;
    taAvant?: string;
    taApres?: string;
    dureeMinutes?: number;
    debitSangMlMin?: number;
    ultrafiltrationMl?: number;
    anticoagulant?: string;
    typeDialysat?: string;
    incidents?: string;
  } | null;
  medical?: {
    prescription?: string;
    toleranceSeance?: string;
    examenClinique?: string;
    resultatsBiologiques?: string;
    ajustementsTherapeutiques?: string;
    conclusionMedicale?: string;
  } | null;
  consommables?: Array<{
    articleId: string;
    articleCode?: string | null;
    articleLibelle?: string | null;
    articleUnite?: string | null;
    quantite?: number | null;
    valeurUnitaire?: number | null;
    totalValorise?: number | null;
  }>;
  consommablesTotalValorise?: number | null;
  forfait?: {
    id: string;
    code?: string | null;
    nom?: string | null;
    prix?: number | null;
    nombreSeances?: number | null;
  } | null;
};

export type SeanceJournalByDate = {
  dateSeance: string;
  patients: Array<{
    seanceId: string;
    patientId: string;
    dateSeance: string;
    status: string;
    patientCode?: string | null;
    patientNom?: string | null;
    patientPrenom?: string | null;
  }>;
  sortiesArticles: Array<{
    articleId: string;
    articleCode?: string | null;
    articleLibelle?: string | null;
    quantiteTotale: number;
  }>;
};

export type SeanceMonthlyDashboard = {
  year: number;
  month: number;
  expectedSeances: number;
  presenceCount: number;
  absenceCount: number;
  totalSeances: number;
  absenceDetails?: {
    formula?: string;
    periodStart?: string;
    periodEnd?: string;
    patientsConsidered?: number;
    blockedDays?: number;
    expectedFromSchedule?: number;
    presenceCount?: number;
    absenceCount?: number;
    expectedByWeekday?: Record<string, number>;
  };
  sexeDistribution: Record<string, number>;
  ageDistribution: Record<string, number>;
};

export type SeanceDashboardDetailItem = {
  patientId: string;
  patientNom?: string;
  patientPrenom?: string;
  dateSeance: string;
  weekday: string;
  scheduled: boolean;
  present: boolean;
  status: string;
};

export type SeanceDashboardDetailsResponse = {
  year: number;
  month: number;
  kind: 'presence' | 'absence';
  total: number;
  items: SeanceDashboardDetailItem[];
};

export type SeanceCalendarResponse = {
  year: number;
  month: number;
  holidays: Array<{ id: string; dayDate: string; label?: string | null }>;
  closures: Array<{ id: string; dayDate: string; reason?: string | null }>;
};

export type UpsertVoletParamedicalPayload = {
  centerId: string;
  poidsAvantKg?: number | null;
  poidsApresKg?: number | null;
  taAvant?: string | null;
  taApres?: string | null;
  dureeMinutes?: number | null;
  debitSangMlMin?: number | null;
  ultrafiltrationMl?: number | null;
  anticoagulant?: string | null;
  typeDialysat?: string | null;
  incidents?: string | null;
};

export type ValidateSeancePayload = {
  centerId: string;
  userId: string;
  consommations: Array<{
    articleId: string;
    quantite: number;
  }>;
};

export type UpsertVoletMedicalPayload = {
  centerId: string;
  prescription?: string | null;
  toleranceSeance?: string | null;
  examenClinique?: string | null;
  resultatsBiologiques?: string | null;
  ajustementsTherapeutiques?: string | null;
  conclusionMedicale?: string | null;
};

export type SignSeanceMedecinPayload = {
  centerId: string;
  userId: string;
};

export type ArticleStock = {
  id: string;
  centerId: string;
  code: string;
  libelle: string;
  unite?: string | null;
  stockQuantity?: number | null;
  pmpCourant?: number | null;
  active: boolean;
};

export type DashboardStats = {
  patientCount: number;
  pecCree: number;
  pecValidee: number;
  pecExpiring: number;
  attestationTotal: number;
  attestationExpiring: number;
};

export type PatientParamedicalStats = {
  seanceCount: number;
  avgPoidsAvantKg: number;
  avgPoidsApresKg: number;
  avgUfReelleMl: number;
  poidsEvolution: Array<Record<string, unknown>>;
  taEvolution: Array<Record<string, unknown>>;
};

export type PatientMedicalStats = {
  avgHbGDl: number;
  avgKtV: number;
  avgFerritineNgMl: number;
  hbTrend: Array<Record<string, unknown>>;
  epoTrend: Array<Record<string, unknown>>;
};

export type SummaryBucket = {
  code: string;
  label: string;
  count: number;
};

export type PatientSummary = {
  totalPatients: number;
  sexDistribution: SummaryBucket[];
  ageDistribution: SummaryBucket[];
  ktDistribution: SummaryBucket[];
};

export type PatientSummaryDetailItem = {
  patientId: string;
  codePatient: string;
  nom: string;
  prenom: string;
  sexe: string;
  etatPatient: string;
  dateEvenementEtat?: string | null;
  dateAdmission?: string | null;
  sousKt: boolean;
  inclusionReason: 'PERMANENT' | 'EVENT_MONTH';
};

export type PatientSummaryDetailsResponse = {
  month: string;
  total: number;
  items: PatientSummaryDetailItem[];
};

@Injectable({ providedIn: 'root' })
export class BackendApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  createPatient(payload: CreatePatientPayload): Observable<{ id: string; centerId: string; typePatient: PatientType }> {
    return this.http.post<{ id: string; centerId: string; typePatient: PatientType }>(`${this.baseUrl}/patients`, payload);
  }

  listPatients(centerId: string, userId: string, query: ListQuery): Observable<PagedResponse<any>> {
    const filters = query.filters ?? {};
    const dateRange = this.parseDateRange(filters['dateAdmission']);

    const payload: Record<string, unknown> = {
      centerId,
      page: query.page,
      size: query.size,
      code: filters['code']?.trim() || null,
      nom: filters['nom']?.trim() || null,
      prenom: filters['prenom']?.trim() || null,
      sexe: filters['sexe']?.trim() || null,
      dateAdmissionFrom: dateRange.from,
      dateAdmissionTo: dateRange.to,
      numeroAssurance: filters['numeroAssurance']?.trim() || null,
      etatPatient: filters['etatPatient']?.trim() || null,
      nonFacturable: this.parseBooleanNullable(filters['nonFacturable'])
    };

    void userId; // centerId seul suffit côté backend
    return this.http.post<PagedResponse<any>>(`${this.baseUrl}/patients/search`, payload);
  }

  getPatient(patientId: string, centerId: string, userId: string): Observable<unknown> {
    const params = new HttpParams().set('centerId', centerId).set('userId', userId);
    return this.http.get<unknown>(`${this.baseUrl}/patients/${patientId}`, { params });
  }

  updatePatient(patientId: string, payload: CreatePatientPayload): Observable<{ id: string; centerId: string; typePatient: PatientType }> {
    return this.http.put<{ id: string; centerId: string; typePatient: PatientType }>(`${this.baseUrl}/patients/${patientId}`, payload);
  }

  searchAssures(centerId: string, q?: string): Observable<any[]> {
    let params = new HttpParams().set('centerId', centerId);
    if (q && q.trim()) params = params.set('q', q.trim());
    return this.http.get<any[]>(`${this.baseUrl}/patients/assures`, { params });
  }

  assignAssureToPatient(centerId: string, patientId: string, numeroAssurance: string): Observable<any> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.post<any>(`${this.baseUrl}/patients/${patientId}/assures/${encodeURIComponent(numeroAssurance)}/affecter`, {}, {params});
  }

  updateAssure(centerId: string, numeroAssurance: string, payload: {
    nom?: string; prenom?: string; sexe?: string; dateNaissance?: string | null;
    telPersonnel?: string; telMobile?: string; telBureau?: string;
    adresse?: string; groupeSanguin?: string;
  }): Observable<any> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.put<any>(`${this.baseUrl}/patients/assures/${encodeURIComponent(numeroAssurance)}`, payload, {params});
  }

  updateAssureAssignment(centerId: string, patientId: string, assignmentId: string, payload: {
    dateDebutAffectation?: string | null;
    dateFinAffectation?: string | null;
  }): Observable<any> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.put<any>(`${this.baseUrl}/patients/${patientId}/assures/assignments/${assignmentId}`, payload, {params});
  }

  listPatientAssureHistory(centerId: string, patientId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<any[]>(`${this.baseUrl}/patients/${patientId}/assures/history`, {params});
  }

  createPec(payload: CreatePecPayload): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec`, payload);
  }

  createSeance(payload: CreateSeancePayload): Observable<{ id: string; status: string; dateSeance: string }> {
    return this.http.post<{ id: string; status: string; dateSeance: string }>(`${this.baseUrl}/seances`, payload);
  }

  listSeances(centerId: string): Observable<SeanceListItem[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<SeanceListItem[]>(`${this.baseUrl}/seances`, {params});
  }

  scanSeanceQr(payload: ScanSeanceQrPayload): Observable<{ id: string; status: string; dateSeance: string }> {
    return this.http.post<{ id: string; status: string; dateSeance: string }>(`${this.baseUrl}/seances/scan`, payload);
  }

  updateSeance(seanceId: string, payload: UpdateSeancePayload): Observable<{
    id: string;
    status: string;
    dateSeance: string
  }> {
    return this.http.put<{
      id: string;
      status: string;
      dateSeance: string
    }>(`${this.baseUrl}/seances/${seanceId}`, payload);
  }

  getSeanceSummary(seanceId: string, centerId: string): Observable<SeanceSummary> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<SeanceSummary>(`${this.baseUrl}/seances/${seanceId}`, {params});
  }

  getSeanceJournalByDate(centerId: string, dateSeance: string): Observable<SeanceJournalByDate> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('dateSeance', dateSeance);
    return this.http.get<SeanceJournalByDate>(`${this.baseUrl}/seances/journal`, {params});
  }

  getSeanceMonthlyDashboard(centerId: string, year: number, month: number): Observable<SeanceMonthlyDashboard> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('year', String(year))
      .set('month', String(month));
    return this.http.get<SeanceMonthlyDashboard>(`${this.baseUrl}/seances/dashboard`, {params});
  }

  getSeanceDashboardDetails(
    centerId: string,
    year: number,
    month: number,
    kind: 'presence' | 'absence',
  ): Observable<SeanceDashboardDetailsResponse> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('year', String(year))
      .set('month', String(month))
      .set('kind', kind);
    return this.http.get<SeanceDashboardDetailsResponse>(`${this.baseUrl}/seances/dashboard/details`, {params});
  }

  getSeanceCalendar(centerId: string, year: number, month: number): Observable<SeanceCalendarResponse> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('year', String(year))
      .set('month', String(month));
    return this.http.get<SeanceCalendarResponse>(`${this.baseUrl}/seances/calendar`, {params});
  }

  addSeanceHoliday(centerId: string, dayDate: string, label?: string): Observable<{ id: string; dayDate: string }> {
    return this.http.post<{ id: string; dayDate: string }>(`${this.baseUrl}/seances/calendar/holiday`, {
      centerId,
      dayDate,
      labelOrReason: label ?? null
    });
  }

  deleteSeanceHoliday(centerId: string, id: string): Observable<{ deleted: boolean }> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/seances/calendar/holiday/${id}`, {params});
  }

  addSeanceClosure(centerId: string, dayDate: string, reason?: string): Observable<{ id: string; dayDate: string }> {
    return this.http.post<{ id: string; dayDate: string }>(`${this.baseUrl}/seances/calendar/closure`, {
      centerId,
      dayDate,
      labelOrReason: reason ?? null
    });
  }

  deleteSeanceClosure(centerId: string, id: string): Observable<{ deleted: boolean }> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/seances/calendar/closure/${id}`, {params});
  }

  exportSeanceDashboard(centerId: string, year: number, month: number, format: 'csv' | 'pdf' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('year', String(year))
      .set('month', String(month))
      .set('format', format);
    return this.http.get(`${this.baseUrl}/seances/dashboard/export`, {params, responseType: 'blob'});
  }

  upsertVoletParamedical(seanceId: string, payload: UpsertVoletParamedicalPayload): Observable<{
    id: string;
    seanceId: string;
    updatedAt: string
  }> {
    return this.http.put<{
      id: string;
      seanceId: string;
      updatedAt: string
    }>(`${this.baseUrl}/seances/${seanceId}/volet-paramedical`, payload);
  }

  upsertVoletMedical(seanceId: string, payload: {
    centerId: string;
    prescription?: string | null;
    toleranceSeance?: string | null;
    examenClinique?: string | null;
    resultatsBiologiques?: string | null;
    ajustementsTherapeutiques?: string | null;
    conclusionMedicale?: string | null;
  }): Observable<{ id: string; seanceId: string; updatedAt: string }> {
    return this.http.put<{
      id: string;
      seanceId: string;
      updatedAt: string
    }>(`${this.baseUrl}/seances/${seanceId}/volet-medical`, payload);
  }

  validateSeance(seanceId: string, payload: ValidateSeancePayload): Observable<{
    id: string;
    status: string;
    validatedAt: string
  }> {
    return this.http.post<{
      id: string;
      status: string;
      validatedAt: string
    }>(`${this.baseUrl}/seances/${seanceId}/valider`, payload);
  }

  signSeanceByMedecin(seanceId: string, payload: SignSeanceMedecinPayload): Observable<{
    id: string;
    status: string;
    signedByMedecinAt: string;
  }> {
    return this.http.post<{
      id: string;
      status: string;
      signedByMedecinAt: string;
    }>(`${this.baseUrl}/seances/${seanceId}/signer-medecin`, payload);
  }

  listArticlesStock(centerId: string): Observable<ArticleStock[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<ArticleStock[]>(`${this.baseUrl}/stock/referentiel/articles`, {params});
  }

  validatePec(pecId: string, centerId: string, userId: string): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/validate`, {
      centerId,
      userId
    });
  }

  validatePecAdmin(pecId: string, payload: { centerId: string; userId: string; dateDebutEffectif: string; dateFinEffectif: string; forfaitEffectifId?: string }): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/validate`, payload);
  }

  listPecs(centerId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<any[]>(`${this.baseUrl}/pec`, { params });
  }

  listPecsDetailed(centerId: string, query: ListQuery): Observable<PagedResponse<any>> {
    const filters = query.filters ?? {};
    const payload = {
      centerId,
      page: query.page,
      size: query.size,
      code: filters['code']?.trim() || null,
      nom: filters['nom']?.trim() || null,
      assurance: filters['assurance']?.trim() || null,
      debut: filters['debut']?.trim() || null,
      fin: filters['fin']?.trim() || null,
      statut: filters['statut']?.trim() || null
    };
    return this.http.post<PagedResponse<any>>(`${this.baseUrl}/pec/pec-center/search`, payload);
  }

  listPecsByPatient(centerId: string, patientId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<any[]>(`${this.baseUrl}/pec/patient/${patientId}`, { params });
  }

  listAttestationsByPatient(centerId: string, patientId: string): Observable<any[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<any[]>(`${this.baseUrl}/pec/attestations/${patientId}`, { params });
  }

  listAttestationsByCenter(centerId: string, query: ListQuery): Observable<PagedResponse<any>> {
    const filters = query.filters ?? {};
    const payload = {
      centerId,
      page: query.page,
      size: query.size,
      code: filters['code']?.trim() || null,
      nom: filters['nom']?.trim() || null,
      assurance: filters['assurance']?.trim() || null,
      debut: filters['debut']?.trim() || null,
      fin: filters['fin']?.trim() || null
    };
    return this.http.post<PagedResponse<any>>(`${this.baseUrl}/pec/attestations-center/search`, payload);
  }

  closePec(pecId: string, centerId: string, userId: string): Observable<{ id: string; status: PecStatus }> {
    return this.http.post<{ id: string; status: PecStatus }>(`${this.baseUrl}/pec/${pecId}/close`, { centerId, userId });
  }

  deletePec(pecId: string, centerId: string, patientId?: string): Observable<{ deleted: boolean }> {
    let params = new HttpParams().set('centerId', centerId);
    if (patientId) params = params.set('patientId', patientId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/pec/${pecId}`, { params });
  }

  deleteAttestation(attestationId: string, centerId: string, patientId?: string): Observable<{ deleted: boolean }> {
    let params = new HttpParams().set('centerId', centerId);
    if (patientId) params = params.set('patientId', patientId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/pec/attestations/${attestationId}`, { params });
  }

  sessionAllowed(pecId: string, centerId: string, userId: string): Observable<{ allowed: boolean }> {
    const params = new HttpParams().set('centerId', centerId).set('userId', userId);
    return this.http.get<{ allowed: boolean }>(`${this.baseUrl}/pec/${pecId}/session-allowed`, { params });
  }

  getDashboardStats(centerId: string, expirationDays: number): Observable<DashboardStats> {
    const params = new HttpParams().set('centerId', centerId).set('expirationDays', expirationDays.toString());
    return this.http.get<DashboardStats>(`${this.baseUrl}/dashboard/stats`, {params});
  }

  getPatientSummary(centerId: string, month?: string): Observable<PatientSummary> {
    let params = new HttpParams().set('centerId', centerId);
    if (month) params = params.set('month', month);
    return this.http.get<PatientSummary>(`${this.baseUrl}/patients/summary`, {params});
  }

  getPatientSummaryDetails(centerId: string, month?: string): Observable<PatientSummaryDetailsResponse> {
    let params = new HttpParams().set('centerId', centerId);
    if (month) params = params.set('month', month);
    return this.http.get<PatientSummaryDetailsResponse>(`${this.baseUrl}/patients/summary/details`, {params});
  }

  getPatientParamedicalStats(centerId: string, patientId: string, from?: string, to?: string): Observable<PatientParamedicalStats> {
    let params = new HttpParams().set('centerId', centerId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<PatientParamedicalStats>(`${this.baseUrl}/patients/${patientId}/stats/paramedical`, {params});
  }

  getPatientMedicalStats(centerId: string, patientId: string, from?: string, to?: string): Observable<PatientMedicalStats> {
    let params = new HttpParams().set('centerId', centerId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<PatientMedicalStats>(`${this.baseUrl}/patients/${patientId}/stats/medical`, {params});
  }

  exportPatientStats(centerId: string, patientId: string, format: 'csv' | 'pdf', from?: string, to?: string): Observable<HttpResponse<Blob>> {
    let params = new HttpParams().set('centerId', centerId).set('format', format);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get(`${this.baseUrl}/patients/${patientId}/stats/export`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  // ─── Documents & Impression (Jasper) ──────────────────────────────

  /** Liste des modèles de documents pour un centre */
  listModelesDocument(centerId: string, typeDocument?: string): Observable<any[]> {
    let params = new HttpParams().set('centerId', centerId);
    if (typeDocument) params = params.set('typeDocument', typeDocument);
    return this.http.get<any[]>(`${this.baseUrl}/documents/modeles`, { params });
  }

  /** Créer un modèle de document */
  createModeleDocument(payload: any): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/documents/modeles`, payload);
  }

  /** Modifier un modèle de document */
  updateModeleDocument(id: string, payload: any): Observable<{ id: string }> {
    return this.http.put<{ id: string }>(`${this.baseUrl}/documents/modeles/${id}`, payload);
  }

  /** Supprimer un modèle de document */
  deleteModeleDocument(id: string, centerId: string): Observable<{ deleted: boolean }> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<{ deleted: boolean }>(`${this.baseUrl}/documents/modeles/${id}`, { params });
  }

  /** Types de documents disponibles */
  getDocumentTypes(): Observable<Array<{ code: string; label: string }>> {
    return this.http.get<any[]>(`${this.baseUrl}/documents/types`);
  }

  /**
   * Imprimer un document — appel principal.
   * @param centerId ID du centre
   * @param typeDocument FICHE_PATIENT, ATTESTATION, PEC, LISTE_PATIENTS, etc.
   * @param params Paramètres (patientId, pecId, etc.)
   * @param formatOverride Optionnel : surcharge le format du modèle (PDF, EXCEL, HTML)
   */
  printDocument(centerId: string, typeDocument: string, params: Record<string, string>, formatOverride?: string): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/documents/print`, {
      centerId,
      typeDocument,
      formatOverride: formatOverride || null,
      params
    }, { responseType: 'blob' });
  }

  /** Imprimer via un modèle spécifique */
  printDocumentById(modeleId: string, centerId: string, params: Record<string, string>, formatOverride?: string): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/documents/print/${modeleId}`, {
      centerId,
      typeDocument: null,
      formatOverride: formatOverride || null,
      params
    }, { responseType: 'blob' });
  }

  // ─── Utilitaires privés ───────────────────────────────────────────

  private parseDateRange(rawValue?: string): { from: string | null; to: string | null } {
    const raw = (rawValue ?? '').trim();
    if (!raw) return {from: null, to: null};
    const normalize = (v: string): string | null =>
      /^\d{4}-\d{2}-\d{2}$/.test(v.trim()) ? v.trim() : null;
    if (raw.includes('..')) {
      const [fromRaw = '', toRaw = ''] = raw.split('..', 2);
      return {from: normalize(fromRaw), to: normalize(toRaw)};
    }
    const normalized = normalize(raw);
    return {from: normalized, to: normalized};
  }

  private parseBooleanNullable(value?: string): boolean | null {
    const normalized = (value ?? '').trim().toLowerCase();
    if (!normalized) return null;
    if (['true', 'oui', '1'].includes(normalized)) return true;
    if (['false', 'non', '0'].includes(normalized)) return false;
    return null;
  }
}
