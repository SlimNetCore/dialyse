import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';
import {CreneauRef, JourSemaine, SalleRef} from './planning-api.service';

/** Ce que l'optimisation planifie : patients, roulement des infirmiers, couverture des absences, ou patients + roulement. */
export type PerimetreOptimisation = 'PATIENTS' | 'ROULEMENT' | 'COUVERTURE' | 'COMPLET';
export type ObjectifInfirmiers = 'EQUITE' | 'ECONOMIE';
export type StatutOptimisation = 'EN_COURS' | 'TERMINEE' | 'ECHEC';
export type CauseNonPlace = 'AUCUNE_PLACE' | 'ISOLEMENT_IMPOSSIBLE' | 'JOURS_FERMES';

export const PERIMETRES_OPTIMISATION: readonly PerimetreOptimisation[] =
  ['COMPLET', 'PATIENTS', 'ROULEMENT', 'COUVERTURE'];
export const OBJECTIFS_INFIRMIERS: readonly ObjectifInfirmiers[] = ['EQUITE', 'ECONOMIE'];

/** Bornes acceptées par le serveur. */
export const BORNES_OPTIMISATION = {
  semaines: {min: 1, max: 4},
  duree: {min: 2, max: 300, defaut: 20},
  stabilite: {min: 0, max: 10, defaut: 5},
  vacationsJour: {min: 1, max: 3, defaut: 2},
  vacationsSemaine: {min: 1, max: 14, defaut: 6},
} as const;

export interface ParametresOptimisation {
  perimetre: PerimetreOptimisation;
  debutSemaine: string;
  nbSemaines: number;
  dureeMaxSecondes: number;
  stabilite: number;
  objectif: ObjectifInfirmiers;
  maxVacationsParJour: number;
  maxVacationsParSemaine: number;
}

/** Corps du lancement : seul le périmètre est requis, le serveur complète les valeurs par défaut. */
export type LancerOptimisationPayload = Partial<ParametresOptimisation> & Pick<ParametresOptimisation, 'perimetre'>;

/** Place de dialyse d'un patient : salle, créneau et générateur. */
export interface PosteOptimisation {
  salleId: string | null;
  creneauId: string | null;
  generateurId: string | null;
  generateurCode: string | null;
}

export interface DeplacementPatient {
  patientId: string;
  nom: string;
  /** Place actuelle ; nulle pour un patient jusqu'ici non placé. */
  de: PosteOptimisation | null;
  vers: PosteOptimisation;
}

export interface PatientNonPlace {
  patientId: string;
  nom: string;
  cause: CauseNonPlace;
}

export interface VacationPlanifiee {
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
  infirmierId: string;
  nom: string;
  /** L'infirmier était déjà prévu sur cette case. */
  existante: boolean;
}

export interface VacationNonPourvue {
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
  manque: number;
}

/** Consommation de ressources (patients et salles : semaine type ; personnel : tout l'horizon). */
export interface IndicateursOptimisation {
  generateursUtilises: number;
  sallesOuvertes: number;
  vacationsRequises: number;
  placesInfirmierInutilisees: number;
  patientsNonPlaces: number;
  vacationsNonPourvues: number;
  infirmiersMobilises: number;
  ecartCharge: number;
  depassementsHebdo: number;
}

export interface ResumeOptimisation {
  deplacements: number;
  nonPlaces: number;
  vacations: number;
  manques: number;
  avant: IndicateursOptimisation;
  apres: IndicateursOptimisation;
}

export interface ResultatOptimisation {
  salles: SalleRef[];
  creneaux: CreneauRef[];
  deplacements: DeplacementPatient[];
  nonPlaces: PatientNonPlace[];
  vacations: VacationPlanifiee[];
  manques: VacationNonPourvue[];
  avant: IndicateursOptimisation;
  apres: IndicateursOptimisation;
}

/** Exécution d'une optimisation : `resultat` n'est chargé que par la consultation d'une exécution (pas par l'historique). */
export interface RunOptimisation {
  id: string;
  centerId: string;
  statut: StatutOptimisation;
  parametres: ParametresOptimisation;
  creeLe: string;
  termineLe: string | null;
  lancePar: string | null;
  phase: 'PATIENTS' | 'INFIRMIERS' | null;
  score: string | null;
  resume: ResumeOptimisation | null;
  resultat: ResultatOptimisation | null;
  erreur: string | null;
  appliqueLe: string | null;
}

/** Optimisation du planning du centre actif (moteur Timefold) : lancement, suivi, arrêt, historique, application. */
@Injectable({providedIn: 'root'})
export class PlanningOptimisationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/planning/optimisations`;

  private static centre(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }

  lancer(centerId: string, payload: LancerOptimisationPayload): Observable<RunOptimisation> {
    return this.http.post<RunOptimisation>(this.base, payload, {params: PlanningOptimisationApiService.centre(centerId)});
  }

  historique(centerId: string, page: number, size: number): Observable<PagedResponse<RunOptimisation>> {
    const params = PlanningOptimisationApiService.centre(centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<RunOptimisation>>(this.base, {params});
  }

  consulter(centerId: string, id: string): Observable<RunOptimisation> {
    return this.http.get<RunOptimisation>(`${this.base}/${id}`, {params: PlanningOptimisationApiService.centre(centerId)});
  }

  arreter(centerId: string, id: string): Observable<RunOptimisation> {
    return this.http.post<RunOptimisation>(`${this.base}/${id}/arret`, null,
      {params: PlanningOptimisationApiService.centre(centerId)});
  }

  appliquer(centerId: string, id: string): Observable<RunOptimisation> {
    return this.http.post<RunOptimisation>(`${this.base}/${id}/application`, null,
      {params: PlanningOptimisationApiService.centre(centerId)});
  }
}
