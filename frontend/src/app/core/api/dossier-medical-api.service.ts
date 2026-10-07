import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {map, Observable} from 'rxjs';
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
  conclusionMedicale: string | null;
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
  conclusionMedicale: string | null;
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

export type UniteFrequence = 'HEURE' | 'JOUR' | 'SEMAINE' | 'MOIS' | 'ANNEE';

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
  /** Poids sec cible (kg) prescrit par le médecin. */
  poidsSecCibleKg: number | null;
  typeDialyseurPrescrit: string | null;
  anticoagTypePrescrit: string | null;
  epoArticleId: string | null;
  epoArticleCode: string | null;
  epoArticleLibelle: string | null;
  epoDoseUi: number | null;
  epoVoie: string | null;
  epoFrequenceValeur: number | null;
  epoFrequenceUnite: UniteFrequence | null;
  ferArticleId: string | null;
  ferArticleCode: string | null;
  ferArticleLibelle: string | null;
  ferDoseMg: number | null;
  ferVoie: string | null;
  ferFrequenceValeur: number | null;
  ferFrequenceUnite: UniteFrequence | null;
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
  poidsSecCibleKg: number | null;
  typeDialyseurPrescrit: string | null;
  anticoagTypePrescrit: string | null;
  epoArticleId: string | null;
  epoDoseUi: number | null;
  epoVoie: string | null;
  epoFrequenceValeur: number | null;
  epoFrequenceUnite: UniteFrequence | null;
  ferArticleId: string | null;
  ferDoseMg: number | null;
  ferVoie: string | null;
  ferFrequenceValeur: number | null;
  ferFrequenceUnite: UniteFrequence | null;
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

export type LigneDemandeExamen = {
  id: string;
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelle: string | null;
  commentaire: string | null;
};

export type DemandeExamen = {
  id: string;
  patientId: string;
  centerId: string;
  prescripteurId: string | null;
  dateDemande: string;
  categorie: string;
  urgent: boolean;
  motif: string | null;
  statut: string;
  conclusion: string | null;
  lignes: LigneDemandeExamen[];
  createdAt: string;
  updatedAt: string;
};

export type LigneDemandeExamenPayload = {
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelle: string | null;
  commentaire: string | null;
};

export type CreateDemandeExamenPayload = {
  centerId: string;
  prescripteurId: string | null;
  dateDemande: string | null;
  categorie: string;
  urgent: boolean;
  motif: string | null;
  lignes: LigneDemandeExamenPayload[];
};

export type ObservationBiologique = {
  id: string;
  patientId: string;
  centerId: string;
  demandeExamenId: string | null;
  codeSystem: string;
  code: string;
  codeDisplay: string | null;
  valeurNum: number | null;
  unite: string | null;
  valeurTexte: string | null;
  datePrelevement: string;
  statut: string;
  source: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateObservationPayload = {
  centerId: string;
  demandeExamenId: string | null;
  codeSystem: string;
  code: string;
  codeDisplay: string | null;
  valeurNum: number | null;
  unite: string | null;
  valeurTexte: string | null;
  datePrelevement: string | null;
  statut: string | null;
};

export type AdministrationTraitement = {
  id: string;
  patientId: string;
  centerId: string;
  prescriptionMedicaleId: string | null;
  typeTraitement: string;
  molecule: string | null;
  dose: number | null;
  uniteDose: string | null;
  voie: string | null;
  dateAdministration: string;
  seanceId: string | null;
  administrePar: string | null;
  administree: boolean;
  motifNonAdministration: string | null;
  articleId: string | null;
  quantiteArticle: number | null;
  createdAt: string;
};

export type CreateAdministrationTraitementPayload = {
  centerId: string;
  prescriptionMedicaleId: string | null;
  typeTraitement: string;
  molecule: string | null;
  dose: number | null;
  uniteDose: string | null;
  voie: string | null;
  dateAdministration: string | null;
  seanceId: string | null;
  administrePar: string | null;
  administree: boolean;
  motifNonAdministration: string | null;
  articleId: string | null;
  quantiteArticle: number | null;
};

export type EvaluationCible = {
  code: string;
  valeur: number | null;
  unite: string;
  statut: string;
  borneMin: number | null;
  borneMax: number | null;
  referenceKdigo: string;
};

export type PointBiologique = {
  date: string;
  hbGDl: number | null;
  ferritineNgMl: number | null;
  cstfPct: number | null;
  albumineGDl: number | null;
};

export type SuiviAnemie = {
  dateDernierBilan: string | null;
  evaluations: EvaluationCible[];
  courbe: PointBiologique[];
  prescriptionActive: PrescriptionMedicale | null;
  administrationsRecentes: AdministrationTraitement[];
};

export type AlerteObservance = {
  id: string;
  patientId: string;
  centerId: string;
  typeTraitement: string;
  type: string;
  periodeDebut: string;
  periodeFin: string;
  /** Attendu et administré : en `uniteDose` quand elle est renseignée (quantités), sinon en nombre d'administrations. */
  dosesAttendues: number;
  dosesAdministrees: number;
  uniteDose?: string | null;
  /** Prescription qui explique l'alerte : dose à chaque administration et fréquence (absentes des anciennes alertes). */
  dosePrescrite?: number | null;
  frequenceValeur?: number | null;
  frequenceUnite?: string | null;
  message: string | null;
  createdAt: string;
  resolvedAt: string | null;
};

export type ObservanceTraitement = {
  periodeDebut: string;
  periodeFin: string;
  dosesAttendues: number;
  dosesAdministrees: number;
  dosesRestantes: number;
  joursRestants: number;
  /** Prescription avec dose : quantités attendue, administrée et restante sur la période (UI, mg). */
  uniteDose?: string | null;
  dosePrescrite?: number | null;
  doseAttendue?: number | null;
  doseAdministree?: number | null;
  doseRestante?: number | null;
};

export type ObservanceAnemie = {
  epo: ObservanceTraitement | null;
  fer: ObservanceTraitement | null;
};

export type ConstanteSeance = {
  seanceId: string;
  dateSeance: string;
  poidsAvantKg: number | null;
  poidsApresKg: number | null;
  taAvant: string | null;
  taApres: string | null;
  debitSangMlMin: number | null;
  ultrafiltrationMl: number | null;
  dureeMinutes: number | null;
};

export type LigneOrdonnance = {
  id: string;
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelle: string | null;
  posologie: string;
  voie: string | null;
  dureeJours: number | null;
  quantite: number | null;
  instructions: string | null;
};

export type Ordonnance = {
  id: string;
  patientId: string;
  centerId: string;
  medecinId: string | null;
  datePrescription: string;
  statut: string;
  numero: string | null;
  lignes: LigneOrdonnance[];
  createdAt: string;
  updatedAt: string;
  signedAt: string | null;
};

export type LigneOrdonnancePayload = {
  codeSystem: string | null;
  code: string | null;
  codeDisplay: string | null;
  libelle: string | null;
  posologie: string;
  voie: string | null;
  dureeJours: number | null;
  quantite: number | null;
  instructions: string | null;
};

export type CreateOrdonnancePayload = {
  centerId: string;
  medecinId: string | null;
  datePrescription: string | null;
  lignes: LigneOrdonnancePayload[];
};

export type DecisionRcp = {
  id: string;
  dateReunion: string;
  avis: string;
  compteRendu: string | null;
  prochaineDateRevue: string | null;
};

export type BilanPreGreffe = {
  id: string;
  patientId: string;
  centerId: string;
  statut: string;
  dateDebutBilan: string | null;
  dateInscriptionListeAttente: string | null;
  dateGreffe: string | null;
  groupeSanguinConfirme: string | null;
  typageHla: string | null;
  praClasseI: number | null;
  praClasseII: number | null;
  contreIndications: string | null;
  conclusionNephrologue: string | null;
  decisionsRcp: DecisionRcp[];
  createdAt: string;
  updatedAt: string;
};

export type EtapeBilanGreffe = {
  id: string;
  patientId: string;
  centerId: string;
  categorie: string;
  libelle: string;
  statut: string;
  dateRealisation: string | null;
  resultat: string | null;
  dateExpiration: string | null;
  demandeExamenId: string | null;
  serologieId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpdateEtapeBilanGreffePayload = {
  centerId: string;
  statut: string;
  dateRealisation: string | null;
  resultat: string | null;
  dateExpiration: string | null;
  demandeExamenId: string | null;
  serologieId: string | null;
};

export type DonneurVivant = {
  id: string;
  patientId: string;
  centerId: string;
  nom: string;
  prenom: string | null;
  dateNaissance: string | null;
  lienParente: string;
  telephone: string | null;
  groupeSanguin: string | null;
  typageHla: string | null;
  statutBilan: string;
  crossmatchResultat: string;
  dateCrossmatch: string | null;
  bilanRealise: string | null;
  contreIndications: string | null;
  decisionFinale: string | null;
  dateDecision: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UpsertDonneurVivantPayload = {
  centerId: string;
  nom: string;
  prenom: string | null;
  dateNaissance: string | null;
  lienParente: string;
  telephone: string | null;
  groupeSanguin: string | null;
  typageHla: string | null;
  statutBilan?: string;
  crossmatchResultat?: string;
  dateCrossmatch?: string | null;
  bilanRealise?: string | null;
  contreIndications?: string | null;
  decisionFinale?: string | null;
  dateDecision?: string | null;
};

export type EvaluationRisqueKdigo = {
  code: string;
  valeur: number | null;
  unite: string;
  niveau: string;
  referenceKdigo: string;
};

export type EvaluationEgfr = {
  egfrMlMin173m2: number | null;
  stade: string;
  referenceKdigo: string;
};

export type AlerteSerologieKdigo = {
  marqueur: string;
  resultat: string;
  message: string;
  referenceKdigo: string;
};

export type KdigoGreffe = {
  risqueImmunologique: EvaluationRisqueKdigo;
  fonctionRenale: EvaluationEgfr;
  alertesSerologiques: AlerteSerologieKdigo[];
};

/**
 * Client HTTP du dossier médical patient : dossier de base, abords vasculaires,
 * prescriptions (dont EPO/fer), résultats d'analyses, antécédents, allergies, sérologies,
 * demandes d'examen et observations biologiques (LOINC).
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

  listDemandesExamen(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<DemandeExamen>> {
    return this.http.get<PagedResponse<DemandeExamen>>(`${this.base}/${patientId}/demandes-examen`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createDemandeExamen(patientId: string, payload: CreateDemandeExamenPayload): Observable<DemandeExamen> {
    return this.http.post<DemandeExamen>(`${this.base}/${patientId}/demandes-examen`, payload);
  }

  demandeExamenPreleve(patientId: string, demandeId: string, centerId: string): Observable<DemandeExamen> {
    return this.http.put<DemandeExamen>(`${this.base}/${patientId}/demandes-examen/${demandeId}/preleve`, {centerId});
  }

  demandeExamenResultatDisponible(patientId: string, demandeId: string, centerId: string): Observable<DemandeExamen> {
    return this.http.put<DemandeExamen>(
      `${this.base}/${patientId}/demandes-examen/${demandeId}/resultat-disponible`, {centerId});
  }

  demandeExamenValider(patientId: string, demandeId: string, centerId: string, conclusion: string | null):
    Observable<DemandeExamen> {
    return this.http.put<DemandeExamen>(
      `${this.base}/${patientId}/demandes-examen/${demandeId}/valider`, {centerId, conclusion});
  }

  demandeExamenAnnuler(patientId: string, demandeId: string, centerId: string): Observable<DemandeExamen> {
    return this.http.put<DemandeExamen>(`${this.base}/${patientId}/demandes-examen/${demandeId}/annuler`, {centerId});
  }

  listObservationsByDemande(centerId: string, patientId: string, demandeId: string): Observable<ObservationBiologique[]> {
    return this.http.get<ObservationBiologique[]>(
      `${this.base}/${patientId}/demandes-examen/${demandeId}/observations`,
      {params: new HttpParams().set('centerId', centerId)});
  }

  createObservation(patientId: string, payload: CreateObservationPayload): Observable<ObservationBiologique> {
    return this.http.post<ObservationBiologique>(`${this.base}/${patientId}/observations`, payload);
  }

  listAdministrationsAnemie(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<AdministrationTraitement>> {
    return this.http.get<PagedResponse<AdministrationTraitement>>(`${this.base}/${patientId}/administrations-anemie`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createAdministrationAnemie(patientId: string, payload: CreateAdministrationTraitementPayload):
    Observable<AdministrationTraitement> {
    return this.http.post<AdministrationTraitement>(`${this.base}/${patientId}/administrations-anemie`, payload);
  }

  getActivePrescription(centerId: string, patientId: string, date: string | null): Observable<PrescriptionMedicale | null> {
    let params = new HttpParams().set('centerId', centerId);
    if (date) params = params.set('date', date);
    return this.http.get<PrescriptionMedicale>(`${this.base}/${patientId}/prescriptions/active`, {
      params,
      observe: 'response',
    }).pipe(map((res) => res.status === 204 ? null : res.body));
  }

  getObservanceAnemie(centerId: string, patientId: string): Observable<ObservanceAnemie> {
    return this.http.get<ObservanceAnemie>(`${this.base}/${patientId}/observance-anemie`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  getSuiviAnemie(centerId: string, patientId: string): Observable<SuiviAnemie> {
    return this.http.get<SuiviAnemie>(`${this.base}/${patientId}/suivi-anemie`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listAlertesObservance(centerId: string, patientId: string): Observable<AlerteObservance[]> {
    return this.http.get<AlerteObservance[]>(`${this.base}/${patientId}/alertes-observance`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  resoudreAlerteObservance(centerId: string, patientId: string, alerteId: string): Observable<AlerteObservance> {
    return this.http.put<AlerteObservance>(`${this.base}/${patientId}/alertes-observance/${alerteId}/resoudre`, {}, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listConstantes(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<ConstanteSeance>> {
    return this.http.get<PagedResponse<ConstanteSeance>>(`${this.base}/${patientId}/constantes`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  listOrdonnances(centerId: string, patientId: string, page: number, size: number):
    Observable<PagedResponse<Ordonnance>> {
    return this.http.get<PagedResponse<Ordonnance>>(`${this.base}/${patientId}/ordonnances`, {
      params: new HttpParams().set('centerId', centerId).set('page', page).set('size', size),
    });
  }

  createOrdonnance(patientId: string, payload: CreateOrdonnancePayload): Observable<Ordonnance> {
    return this.http.post<Ordonnance>(`${this.base}/${patientId}/ordonnances`, payload);
  }

  signerOrdonnance(patientId: string, ordonnanceId: string, centerId: string): Observable<Ordonnance> {
    return this.http.put<Ordonnance>(`${this.base}/${patientId}/ordonnances/${ordonnanceId}/signer`, {centerId});
  }

  marquerOrdonnanceImprimee(patientId: string, ordonnanceId: string, centerId: string): Observable<Ordonnance> {
    return this.http.put<Ordonnance>(`${this.base}/${patientId}/ordonnances/${ordonnanceId}/imprimer`, {centerId});
  }

  annulerOrdonnance(patientId: string, ordonnanceId: string, centerId: string): Observable<Ordonnance> {
    return this.http.put<Ordonnance>(`${this.base}/${patientId}/ordonnances/${ordonnanceId}/annuler`, {centerId});
  }

  exportFhir(centerId: string, patientId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/${patientId}/dossier-medical/export-fhir`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  // --- Greffe rénale ---

  getBilanPreGreffe(centerId: string, patientId: string): Observable<BilanPreGreffe> {
    return this.http.get<BilanPreGreffe>(`${this.base}/${patientId}/greffe/bilan`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  changerStatutBilanGreffe(patientId: string, centerId: string, statut: string): Observable<BilanPreGreffe> {
    return this.http.put<BilanPreGreffe>(`${this.base}/${patientId}/greffe/bilan/statut`, {centerId, statut});
  }

  mettreAJourBilanImmunologique(patientId: string, centerId: string, groupeSanguinConfirme: string | null,
                                typageHla: string | null, praClasseI: number | null, praClasseII: number | null):
    Observable<BilanPreGreffe> {
    return this.http.put<BilanPreGreffe>(`${this.base}/${patientId}/greffe/bilan/immunologique`,
      {centerId, groupeSanguinConfirme, typageHla, praClasseI, praClasseII});
  }

  mettreAJourNotesGreffe(patientId: string, centerId: string, contreIndications: string | null,
                         conclusionNephrologue: string | null): Observable<BilanPreGreffe> {
    return this.http.put<BilanPreGreffe>(`${this.base}/${patientId}/greffe/bilan/notes`,
      {centerId, contreIndications, conclusionNephrologue});
  }

  ajouterDecisionRcp(patientId: string, centerId: string, dateReunion: string, avis: string,
                     compteRendu: string | null, prochaineDateRevue: string | null): Observable<BilanPreGreffe> {
    return this.http.post<BilanPreGreffe>(`${this.base}/${patientId}/greffe/bilan/decisions-rcp`,
      {centerId, dateReunion, avis, compteRendu, prochaineDateRevue});
  }

  listEtapesBilanGreffe(centerId: string, patientId: string): Observable<EtapeBilanGreffe[]> {
    return this.http.get<EtapeBilanGreffe[]>(`${this.base}/${patientId}/greffe/etapes`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  genererEtapesStandard(patientId: string, centerId: string): Observable<EtapeBilanGreffe[]> {
    return this.http.post<EtapeBilanGreffe[]>(`${this.base}/${patientId}/greffe/etapes/generer-standard`, {}, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createEtapeBilanGreffe(patientId: string, centerId: string, categorie: string, libelle: string):
    Observable<EtapeBilanGreffe> {
    return this.http.post<EtapeBilanGreffe>(`${this.base}/${patientId}/greffe/etapes`, {centerId, categorie, libelle});
  }

  updateEtapeBilanGreffe(patientId: string, etapeId: string, payload: UpdateEtapeBilanGreffePayload):
    Observable<EtapeBilanGreffe> {
    return this.http.put<EtapeBilanGreffe>(`${this.base}/${patientId}/greffe/etapes/${etapeId}`, payload);
  }

  deleteEtapeBilanGreffe(patientId: string, etapeId: string, centerId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/greffe/etapes/${etapeId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  listDonneursVivants(centerId: string, patientId: string): Observable<DonneurVivant[]> {
    return this.http.get<DonneurVivant[]>(`${this.base}/${patientId}/greffe/donneurs`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createDonneurVivant(patientId: string, payload: UpsertDonneurVivantPayload): Observable<DonneurVivant> {
    return this.http.post<DonneurVivant>(`${this.base}/${patientId}/greffe/donneurs`, payload);
  }

  updateDonneurVivant(patientId: string, donneurId: string, payload: UpsertDonneurVivantPayload):
    Observable<DonneurVivant> {
    return this.http.put<DonneurVivant>(`${this.base}/${patientId}/greffe/donneurs/${donneurId}`, payload);
  }

  deleteDonneurVivant(patientId: string, donneurId: string, centerId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${patientId}/greffe/donneurs/${donneurId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  exportGreffePdf(centerId: string, patientId: string): Observable<Blob> {
    return this.http.get(`${this.base}/${patientId}/greffe/export-pdf`, {
      params: new HttpParams().set('centerId', centerId),
      responseType: 'blob',
    });
  }

  getKdigoGreffe(centerId: string, patientId: string): Observable<KdigoGreffe> {
    return this.http.get<KdigoGreffe>(`${this.base}/${patientId}/greffe/kdigo`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }
}
