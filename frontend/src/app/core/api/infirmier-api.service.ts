import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';
import {CreneauRef, JourPlanning, JourSemaine, SalleRef} from './planning-api.service';

export type QualificationInfirmier = 'INFIRMIER' | 'MAJOR' | 'AIDE_SOIGNANT';
export type TypeAbsence = 'CONGE' | 'MALADIE' | 'FORMATION' | 'AUTRE';
export type StatutCasePresence = 'FERME' | 'SANS_PATIENT' | 'COUVERT' | 'SOUS_EFFECTIF';
export type TypeConflitPresence = 'DOUBLE_AFFECTATION' | 'NON_HABILITE';
export type RaisonRemplacement =
  'SALLE_CONNUE' | 'CRENEAU_HABITUEL' | 'CHARGE_FAIBLE' | 'JOUR_LIBRE' | 'HABILITE_ISOLEMENT' | 'DOUBLE_VACATION';

export const QUALIFICATIONS: readonly QualificationInfirmier[] = ['INFIRMIER', 'MAJOR', 'AIDE_SOIGNANT'];
export const TYPES_ABSENCE: readonly TypeAbsence[] = ['CONGE', 'MALADIE', 'FORMATION', 'AUTRE'];

/** Roulement d'un infirmier : salle, créneau et jours de travail. */
export interface AffectationInfirmier {
  id: string;
  salleId: string;
  creneauId: string;
  jours: JourSemaine[];
}

export interface AffectationInfirmierPayload {
  salleId: string;
  creneauId: string;
  jours: JourSemaine[];
}

/** Compte utilisateur relié à une fiche infirmier. */
export interface CompteInfirmier {
  id: string;
  username: string;
  nomComplet: string | null;
  actif: boolean;
}

export interface Infirmier {
  id: string;
  matricule: string;
  nom: string;
  prenom: string | null;
  telephone: string | null;
  qualification: QualificationInfirmier;
  habiliteIsolement: boolean;
  actif: boolean;
  affectations: AffectationInfirmier[];
  /** Absent si la fiche n'a pas d'accès à l'application. */
  compte: CompteInfirmier | null;
}

export interface CreerComptePayload {
  identifiant: string;
  email: string | null;
}

/** Fiche reliée au compte créé et mot de passe temporaire (communiqué une seule fois). */
export interface CompteCree {
  infirmier: Infirmier;
  motDePasseTemporaire: string;
}

export type SituationPersonnelle = 'PREVU' | 'REMPLACANT' | 'ABSENT';

export interface CreneauPersonnel {
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
  situation: SituationPersonnelle;
}

/** Planning personnel d'une semaine pour l'infirmier connecté (« mon planning »). */
export interface MonPlanning {
  infirmier: Infirmier;
  debut: string;
  fin: string;
  salles: SalleRef[];
  creneaux: CreneauRef[];
  mesCreneaux: CreneauPersonnel[];
}

export interface MonAbsencePayload {
  debut: string;
  fin: string;
  type: TypeAbsence;
  motif: string | null;
}

export interface InfirmierPayload {
  matricule: string;
  nom: string;
  prenom: string | null;
  telephone: string | null;
  qualification: QualificationInfirmier;
  habiliteIsolement: boolean;
}

export interface AbsenceInfirmier {
  id: string;
  infirmierId: string;
  debut: string;
  fin: string;
  type: TypeAbsence;
  motif: string | null;
}

export interface AbsenceInfirmierPayload {
  infirmierId: string;
  debut: string;
  fin: string;
  type: TypeAbsence;
  motif: string | null;
}

export interface PresentCase {
  infirmierId: string;
  nom: string;
  habiliteIsolement: boolean;
  remplacant: boolean;
  remplacementId: string | null;
}

export interface AbsentCase {
  infirmierId: string;
  nom: string;
  type: TypeAbsence;
}

export interface CasePresence {
  salleId: string;
  creneauId: string;
  jour: JourSemaine;
  date: string;
  patients: number;
  requis: number;
  salleIsolement: boolean;
  statut: StatutCasePresence;
  manque: number;
  presents: PresentCase[];
  absents: AbsentCase[];
}

export interface ConflitPresence {
  type: TypeConflitPresence;
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
  infirmier: string;
}

export interface SemainePresence {
  debut: string;
  fin: string;
  jours: JourPlanning[];
  salles: SalleRef[];
  creneaux: CreneauRef[];
  cases: CasePresence[];
  conflits: ConflitPresence[];
  patientsParInfirmier: number;
  casesSousEffectif: number;
}

export interface AlertePresence {
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
  patients: number;
  requis: number;
  manque: number;
  absents: string[];
}

export interface CandidatRemplacement {
  infirmierId: string;
  nom: string;
  qualification: QualificationInfirmier;
  habiliteIsolement: boolean;
  score: number;
  raisons: RaisonRemplacement[];
  seancesSemaine: number;
}

export interface RemplacementPayload {
  date: string;
  salleId: string;
  creneauId: string;
  infirmierId: string;
  remplaceId: string | null;
}

export interface ChargeInfirmier {
  infirmierId: string;
  nom: string;
  seances: number;
  remplacements: number;
  joursAbsence: number;
  total: number;
}

export interface ChargePage extends PagedResponse<ChargeInfirmier> {
  moyenne: number;
}

/**
 * Personnel soignant du centre actif : référentiel des infirmiers et de leur roulement, absences, planning de
 * présence, remplaçants et charge mensuelle. Le centre est toujours transmis (isolation multi-centre) ; les listes
 * sont paginées.
 */
@Injectable({providedIn: 'root'})
export class InfirmierApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/infirmiers`;

  private static centre(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }

  list(centerId: string, page: number, size: number): Observable<PagedResponse<Infirmier>> {
    return this.http.get<PagedResponse<Infirmier>>(this.base,
      {params: InfirmierApiService.centre(centerId).set('page', page).set('size', size)});
  }

  create(centerId: string, payload: InfirmierPayload): Observable<Infirmier> {
    return this.http.post<Infirmier>(this.base, payload, {params: InfirmierApiService.centre(centerId)});
  }

  update(centerId: string, id: string, payload: InfirmierPayload): Observable<Infirmier> {
    return this.http.put<Infirmier>(`${this.base}/${id}`, payload, {params: InfirmierApiService.centre(centerId)});
  }

  setActif(centerId: string, id: string, actif: boolean): Observable<Infirmier> {
    return this.http.post<Infirmier>(`${this.base}/${id}/${actif ? 'reactiver' : 'desactiver'}`, null,
      {params: InfirmierApiService.centre(centerId)});
  }

  /** Comptes infirmier du centre qu'aucune fiche n'utilise encore. */
  comptesLiables(centerId: string, page: number, size: number): Observable<PagedResponse<CompteInfirmier>> {
    return this.http.get<PagedResponse<CompteInfirmier>>(`${this.base}/comptes-liables`,
      {params: InfirmierApiService.centre(centerId).set('page', page).set('size', size)});
  }

  lierCompte(centerId: string, infirmierId: string, userId: string): Observable<Infirmier> {
    return this.http.post<Infirmier>(`${this.base}/${infirmierId}/compte/lier`, {userId},
      {params: InfirmierApiService.centre(centerId)});
  }

  creerCompte(centerId: string, infirmierId: string, payload: CreerComptePayload): Observable<CompteCree> {
    return this.http.post<CompteCree>(`${this.base}/${infirmierId}/compte/creer`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  delierCompte(centerId: string, infirmierId: string): Observable<Infirmier> {
    return this.http.delete<Infirmier>(`${this.base}/${infirmierId}/compte`,
      {params: InfirmierApiService.centre(centerId)});
  }

  monPlanning(centerId: string, date?: string): Observable<MonPlanning> {
    let params = InfirmierApiService.centre(centerId);
    if (date) params = params.set('date', date);
    return this.http.get<MonPlanning>(`${this.base}/moi/planning`, {params});
  }

  mesAbsences(centerId: string, page: number, size: number): Observable<PagedResponse<AbsenceInfirmier>> {
    return this.http.get<PagedResponse<AbsenceInfirmier>>(`${this.base}/moi/absences`,
      {params: InfirmierApiService.centre(centerId).set('page', page).set('size', size)});
  }

  declarerMonAbsence(centerId: string, payload: MonAbsencePayload): Observable<AbsenceInfirmier> {
    return this.http.post<AbsenceInfirmier>(`${this.base}/moi/absences`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  annulerMonAbsence(centerId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/moi/absences/${id}`, {params: InfirmierApiService.centre(centerId)});
  }

  addAffectation(centerId: string, infirmierId: string, payload: AffectationInfirmierPayload):
    Observable<AffectationInfirmier> {
    return this.http.post<AffectationInfirmier>(`${this.base}/${infirmierId}/affectations`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  updateAffectation(centerId: string, infirmierId: string, affectationId: string,
                    payload: AffectationInfirmierPayload): Observable<AffectationInfirmier> {
    return this.http.put<AffectationInfirmier>(`${this.base}/${infirmierId}/affectations/${affectationId}`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  deleteAffectation(centerId: string, infirmierId: string, affectationId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${infirmierId}/affectations/${affectationId}`,
      {params: InfirmierApiService.centre(centerId)});
  }

  listAbsences(centerId: string, page: number, size: number): Observable<PagedResponse<AbsenceInfirmier>> {
    return this.http.get<PagedResponse<AbsenceInfirmier>>(`${this.base}/absences`,
      {params: InfirmierApiService.centre(centerId).set('page', page).set('size', size)});
  }

  createAbsence(centerId: string, payload: AbsenceInfirmierPayload): Observable<AbsenceInfirmier> {
    return this.http.post<AbsenceInfirmier>(`${this.base}/absences`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  deleteAbsence(centerId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/absences/${id}`, {params: InfirmierApiService.centre(centerId)});
  }

  /** @param date un jour de la semaine voulue (yyyy-MM-dd) ; défaut : aujourd'hui. */
  semaine(centerId: string, date?: string): Observable<SemainePresence> {
    let params = InfirmierApiService.centre(centerId);
    if (date) params = params.set('date', date);
    return this.http.get<SemainePresence>(`${this.base}/presence/semaine`, {params});
  }

  alertes(centerId: string, jours: number): Observable<AlertePresence[]> {
    return this.http.get<AlertePresence[]>(`${this.base}/presence/alertes`,
      {params: InfirmierApiService.centre(centerId).set('jours', jours)});
  }

  remplacants(centerId: string, date: string, salleId: string, creneauId: string):
    Observable<CandidatRemplacement[]> {
    return this.http.get<CandidatRemplacement[]>(`${this.base}/presence/remplacants`, {
      params: InfirmierApiService.centre(centerId).set('date', date).set('salleId', salleId).set('creneauId', creneauId),
    });
  }

  charge(centerId: string, mois: string, page: number, size: number): Observable<ChargePage> {
    return this.http.get<ChargePage>(`${this.base}/presence/charge`,
      {params: InfirmierApiService.centre(centerId).set('mois', mois).set('page', page).set('size', size)});
  }

  affecterRemplacement(centerId: string, payload: RemplacementPayload): Observable<unknown> {
    return this.http.post(`${this.base}/presence/remplacements`, payload,
      {params: InfirmierApiService.centre(centerId)});
  }

  annulerRemplacement(centerId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/presence/remplacements/${id}`,
      {params: InfirmierApiService.centre(centerId)});
  }

  /** Planning de présence imprimable de la semaine (document du centre). */
  imprimer(centerId: string, date: string): Observable<Blob> {
    return this.http.get(`${this.base}/presence/imprimer`,
      {params: InfirmierApiService.centre(centerId).set('date', date), responseType: 'blob'});
  }
}
