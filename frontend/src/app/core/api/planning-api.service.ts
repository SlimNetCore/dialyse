import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type JourSemaine = 'DIMANCHE' | 'LUNDI' | 'MARDI' | 'MERCREDI' | 'JEUDI' | 'VENDREDI' | 'SAMEDI';

export const JOURS_SEMAINE: readonly JourSemaine[] =
  ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];

export type RaisonProposition =
  'JOURS_IMPOSES_LIBRES' | 'JOURS_BIEN_ESPACES' | 'CRENEAU_PREFERE' | 'SALLE_PREFEREE' | 'SALLE_PEU_CHARGEE'
  | 'SALLE_ISOLEMENT';

export type AlertePlacement = 'AUCUNE_SALLE_ISOLEMENT' | 'JOURS_IMPOSES_FERMES';

export interface SalleRef {
  id: string;
  nom: string;
}

export interface CreneauRef {
  id: string;
  libelle: string;
  ordre: number;
}

export interface GenerateurRef {
  id: string;
  code: string;
  salleId: string;
}

/** Fermeture datée (férié, fermeture exceptionnelle) tombant sur un jour d'une proposition. */
export interface FermetureJour {
  jour: JourSemaine;
  date: string;
  motif: string | null;
}

/** Placement proposé : salle, créneau, générateur et jours (score 0-100, 100 = placement idéal). */
export interface PropositionAffectation {
  salle: SalleRef;
  creneau: CreneauRef;
  generateur: GenerateurRef;
  generateursAlternatifs: GenerateurRef[];
  jours: JourSemaine[];
  score: number;
  raisons: RaisonProposition[];
  fermetures: FermetureJour[];
}

export interface CaseGrille {
  salleId: string;
  creneauId: string;
  jour: JourSemaine;
  capacite: number;
  occupes: number;
}

export interface PropositionsAffectation {
  salles: SalleRef[];
  creneaux: CreneauRef[];
  propositions: PropositionAffectation[];
  grille: CaseGrille[];
  alertes: AlertePlacement[];
  patientARisque: boolean;
}

export interface DemandePlacementParams {
  seances: number;
  jours: JourSemaine[];
  creneauId?: string | null;
  salleId?: string | null;
  patientId?: string | null;
  /** null/absent : risque déduit des sérologies du patient. */
  isolement?: boolean | null;
  limite?: number;
}

export interface JourPlanning {
  jour: JourSemaine;
  date: string;
  ouvertHebdomadaire: boolean;
  fermetureMotif: string | null;
}

export interface OccupantPlanning {
  patientId: string;
  nom: string;
  generateurCode: string | null;
  aRisque: boolean;
}

export interface CellulePlanning {
  salleId: string;
  creneauId: string;
  jour: JourSemaine;
  capacite: number;
  occupants: OccupantPlanning[];
}

export type TypeConflit =
  'GENERATEUR_DOUBLE' | 'SALLE_SURCHARGEE' | 'GENERATEUR_INDISPONIBLE' | 'SANS_GENERATEUR'
  | 'ISOLEMENT_NON_RESPECTE' | 'GENERATEUR_MIXTE';

export interface ConflitPlanning {
  type: TypeConflit;
  salleId: string | null;
  creneauId: string | null;
  jour: JourSemaine | null;
  patients: string[];
}

export interface SemainePlanning {
  debut: string;
  fin: string;
  jours: JourPlanning[];
  salles: SalleRef[];
  creneaux: CreneauRef[];
  cellules: CellulePlanning[];
  conflits: ConflitPlanning[];
  patientsAReplanifier: number;
}

export interface PlanningParametres {
  joursOuverts: JourSemaine[];
  sallesIsolement: string[];
}

/** Planning du centre actif : aide au placement, planning hebdomadaire réel et paramétrage. */
@Injectable({providedIn: 'root'})
export class PlanningApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/planning`;

  propositions(centerId: string, demande: DemandePlacementParams): Observable<PropositionsAffectation> {
    let params = new HttpParams().set('centerId', centerId).set('seances', demande.seances);
    if (demande.jours.length > 0) params = params.set('jours', demande.jours.join(','));
    if (demande.creneauId) params = params.set('creneauId', demande.creneauId);
    if (demande.salleId) params = params.set('salleId', demande.salleId);
    if (demande.patientId) params = params.set('patientId', demande.patientId);
    if (demande.isolement !== null && demande.isolement !== undefined) params = params.set('isolement', demande.isolement);
    if (demande.limite) params = params.set('limite', demande.limite);
    return this.http.get<PropositionsAffectation>(`${this.base}/affectations`, {params});
  }

  /** @param date un jour de la semaine voulue (yyyy-MM-dd) ; défaut : aujourd'hui. */
  semaine(centerId: string, date?: string): Observable<SemainePlanning> {
    let params = new HttpParams().set('centerId', centerId);
    if (date) params = params.set('date', date);
    return this.http.get<SemainePlanning>(`${this.base}/semaine`, {params});
  }

  parametres(centerId: string): Observable<PlanningParametres> {
    return this.http.get<PlanningParametres>(`${this.base}/parametres`, {params: new HttpParams().set('centerId', centerId)});
  }

  enregistrerParametres(centerId: string, parametres: PlanningParametres): Observable<PlanningParametres> {
    return this.http.put<PlanningParametres>(`${this.base}/parametres`, parametres,
      {params: new HttpParams().set('centerId', centerId)});
  }
}
