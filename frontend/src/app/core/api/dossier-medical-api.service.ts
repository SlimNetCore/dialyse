import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type DossierMedicalPatient = {
  id: string;
  patientId: string;
  centerId: string;
  nephropathieInitiale: string | null;
  dateMiseEnDialyse: string | null;
  hepatiteBStatut: string | null;
  hepatiteCStatut: string | null;
  observationGlobale: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpsertDossierMedicalPatientPayload = {
  centerId: string;
  nephropathieInitiale: string | null;
  dateMiseEnDialyse: string | null;
  hepatiteBStatut: string | null;
  hepatiteCStatut: string | null;
  observationGlobale: string | null;
};

export type AbordVasculaire = {
  id: string;
  patientId: string;
  centerId: string;
  typeAbord: string;
  cote: string | null;
  localisation: string | null;
  dateCreation: string | null;
  dateFin: string | null;
  actif: boolean;
  complications: string | null;
  createdAt: string;
};

export type UpsertAbordVasculairePayload = {
  centerId: string;
  typeAbord: string;
  cote: string | null;
  localisation: string | null;
  dateCreation: string | null;
  dateFin: string | null;
  actif: boolean;
  complications: string | null;
};

export type PrescriptionMedicale = {
  id: string;
  patientId: string;
  centerId: string;
  datePrescription: string;
  medecinId: string | null;
  qbCible: number | null;
  qdCible: number | null;
  ufMaxMl: number | null;
  dureeCibleMin: number | null;
  typeDialyseurPrescrit: string | null;
  anticoagTypePrescrit: string | null;
  epoMolecule: string | null;
  epoDoseUi: number | null;
  epoVoie: string | null;
  epoFrequence: string | null;
  ferMolecule: string | null;
  ferDoseMg: number | null;
  ferVoie: string | null;
  ferFrequence: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpsertPrescriptionMedicalePayload = {
  centerId: string;
  datePrescription: string;
  medecinId: string | null;
  qbCible: number | null;
  qdCible: number | null;
  ufMaxMl: number | null;
  dureeCibleMin: number | null;
  typeDialyseurPrescrit: string | null;
  anticoagTypePrescrit: string | null;
  epoMolecule: string | null;
  epoDoseUi: number | null;
  epoVoie: string | null;
  epoFrequence: string | null;
  ferMolecule: string | null;
  ferDoseMg: number | null;
  ferVoie: string | null;
  ferFrequence: string | null;
};

export type ResultatAnalyse = {
  id: string;
  patientId: string;
  centerId: string;
  datePrelevement: string;
  hbGDl: number | null;
  htPct: number | null;
  plaquettes: number | null;
  ferritineNgMl: number | null;
  cstfPct: number | null;
  epoEndogeneMuiMl: number | null;
  ureePreMgDl: number | null;
  ureePostMgDl: number | null;
  creatinineMgDl: number | null;
  ktVMensuel: number | null;
  phosphoreMgDl: number | null;
  calciumMgDl: number | null;
  pthPgMl: number | null;
  albumineGDl: number | null;
  proteinesGDl: number | null;
  crpMgL: number | null;
  createdAt: string;
  updatedAt: string;
};

export type UpsertResultatAnalysePayload = Omit<ResultatAnalyse,
  'id' | 'patientId' | 'createdAt' | 'updatedAt'>;

export type PagedResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type EntityWriteResponse = {
  id: string;
  patientId: string;
  updatedAt: string;
};

export type Antecedent = {
  id: string;
  patientId: string;
  centerId: string;
  type: string;
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelleLibre: string | null;
  dateDebut: string;
  dateFin: string | null;
  statutClinique: string;
  severite: string | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpsertAntecedentPayload = {
  centerId: string;
  type: string;
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelleLibre: string | null;
  dateDebut: string;
  dateFin: string | null;
  statutClinique: string | null;
  severite: string | null;
  note: string | null;
};

export type Allergie = {
  id: string;
  patientId: string;
  centerId: string;
  codeSystem: string;
  code: string;
  codeDisplay: string | null;
  categorie: string;
  criticite: string;
  typeReaction: string;
  manifestations: string | null;
  dateConstatation: string;
  statutVerification: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateAllergiePayload = {
  centerId: string;
  codeSystem: string;
  code: string;
  codeDisplay: string | null;
  categorie: string;
  criticite: string;
  typeReaction: string;
  manifestations: string | null;
  dateConstatation: string | null;
  statutVerification: string | null;
};

export type UpdateAllergiePayload = {
  centerId: string;
  criticite: string;
  manifestations: string | null;
  statutVerification: string | null;
};

export type Serologie = {
  id: string;
  patientId: string;
  centerId: string;
  marqueur: string;
  resultat: string;
  titre: number | null;
  unite: string | null;
  datePrelevement: string;
  laboratoire: string | null;
  dateProchainControle: string | null;
  conduiteATenir: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateSerologiePayload = {
  centerId: string;
  marqueur: string;
  resultat: string;
  titre: number | null;
  unite: string | null;
  datePrelevement: string | null;
  laboratoire: string | null;
  dateProchainControle: string | null;
  conduiteATenir: string | null;
};

export type UpdateSerologiePayload = {
  centerId: string;
  resultat: string;
  conduiteATenir: string | null;
};

/**
 * Client HTTP du dossier médical patient : dossier de base, abords vasculaires,
 * prescriptions (dont EPO/fer), résultats d'analyses, antécédents, allergies et sérologies.
 * <p>
 * Aucune gestion d'erreur ici : `catchError` vit dans les stores (convention du projet).
 */
@Injectable({providedIn: 'root'})
export class DossierMedicalApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/patients`;

  getDossier(centerId: string, patientId: string): Observable<DossierMedicalPatient | null> {
    return this.http.get<DossierMedicalPatient>(`${this.base}/${patientId}/dossier-medical`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  upsertDossier(patientId: string, payload: UpsertDossierMedicalPatientPayload): Observable<EntityWriteResponse> {
    return this.http.post<EntityWriteResponse>(`${this.base}/${patientId}/dossier-medical`, payload);
  }

  listAbordsVasculaires(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<AbordVasculaire>> {
    return this.http.get<PagedResponse<AbordVasculaire>>(`${this.base}/${patientId}/abords-vasculaires`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createAbordVasculaire(patientId: string, payload: UpsertAbordVasculairePayload): Observable<EntityWriteResponse> {
    return this.http.post<EntityWriteResponse>(`${this.base}/${patientId}/abords-vasculaires`, payload);
  }

  updateAbordVasculaire(patientId: string, abordId: string, payload: UpsertAbordVasculairePayload):
    Observable<EntityWriteResponse> {
    return this.http.put<EntityWriteResponse>(`${this.base}/${patientId}/abords-vasculaires/${abordId}`, payload);
  }

  deleteAbordVasculaire(centerId: string, patientId: string, abordId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/abords-vasculaires/${abordId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listPrescriptions(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<PrescriptionMedicale>> {
    return this.http.get<PagedResponse<PrescriptionMedicale>>(`${this.base}/${patientId}/prescriptions`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createPrescription(patientId: string, payload: UpsertPrescriptionMedicalePayload): Observable<EntityWriteResponse> {
    return this.http.post<EntityWriteResponse>(`${this.base}/${patientId}/prescriptions`, payload);
  }

  updatePrescription(patientId: string, prescriptionId: string, payload: UpsertPrescriptionMedicalePayload):
    Observable<EntityWriteResponse> {
    return this.http.put<EntityWriteResponse>(`${this.base}/${patientId}/prescriptions/${prescriptionId}`, payload);
  }

  deletePrescription(centerId: string, patientId: string, prescriptionId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/prescriptions/${prescriptionId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listAnalyses(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<ResultatAnalyse>> {
    return this.http.get<PagedResponse<ResultatAnalyse>>(`${this.base}/${patientId}/analyses`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createAnalyse(patientId: string, payload: UpsertResultatAnalysePayload): Observable<EntityWriteResponse> {
    return this.http.post<EntityWriteResponse>(`${this.base}/${patientId}/analyses`, payload);
  }

  updateAnalyse(patientId: string, analyseId: string, payload: UpsertResultatAnalysePayload):
    Observable<EntityWriteResponse> {
    return this.http.put<EntityWriteResponse>(`${this.base}/${patientId}/analyses/${analyseId}`, payload);
  }

  deleteAnalyse(centerId: string, patientId: string, analyseId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/analyses/${analyseId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listAntecedents(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<Antecedent>> {
    return this.http.get<PagedResponse<Antecedent>>(`${this.base}/${patientId}/antecedents`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createAntecedent(patientId: string, payload: UpsertAntecedentPayload): Observable<Antecedent> {
    return this.http.post<Antecedent>(`${this.base}/${patientId}/antecedents`, payload);
  }

  updateAntecedent(patientId: string, antecedentId: string, payload: UpsertAntecedentPayload): Observable<Antecedent> {
    return this.http.put<Antecedent>(`${this.base}/${patientId}/antecedents/${antecedentId}`, payload);
  }

  resoudreAntecedent(patientId: string, antecedentId: string, centerId: string, dateResolution: string):
    Observable<Antecedent> {
    return this.http.put<Antecedent>(`${this.base}/${patientId}/antecedents/${antecedentId}/resoudre`, {
      centerId, dateResolution,
    });
  }

  deleteAntecedent(centerId: string, patientId: string, antecedentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/antecedents/${antecedentId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listAllergies(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<Allergie>> {
    return this.http.get<PagedResponse<Allergie>>(`${this.base}/${patientId}/allergies`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  listAllergiesCritiques(centerId: string, patientId: string): Observable<Allergie[]> {
    return this.http.get<Allergie[]>(`${this.base}/${patientId}/allergies/critiques`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createAllergie(patientId: string, payload: CreateAllergiePayload): Observable<Allergie> {
    return this.http.post<Allergie>(`${this.base}/${patientId}/allergies`, payload);
  }

  updateAllergie(patientId: string, allergieId: string, payload: UpdateAllergiePayload): Observable<Allergie> {
    return this.http.put<Allergie>(`${this.base}/${patientId}/allergies/${allergieId}`, payload);
  }

  deleteAllergie(centerId: string, patientId: string, allergieId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/allergies/${allergieId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listSerologies(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<Serologie>> {
    return this.http.get<PagedResponse<Serologie>>(`${this.base}/${patientId}/serologies`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  getSerologiesSynthese(centerId: string, patientId: string): Observable<Serologie[]> {
    return this.http.get<Serologie[]>(`${this.base}/${patientId}/serologies/synthese`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createSerologie(patientId: string, payload: CreateSerologiePayload): Observable<Serologie> {
    return this.http.post<Serologie>(`${this.base}/${patientId}/serologies`, payload);
  }

  updateSerologie(patientId: string, serologieId: string, payload: UpdateSerologiePayload): Observable<Serologie> {
    return this.http.put<Serologie>(`${this.base}/${patientId}/serologies/${serologieId}`, payload);
  }

  deleteSerologie(centerId: string, patientId: string, serologieId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/serologies/${serologieId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }
}
