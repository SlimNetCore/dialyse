import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type TypeEquipement =
  | 'GENERATEUR_DIALYSE' | 'STATION_TRAITEMENT_EAU' | 'RO_REVERSE_OSMOSIS'
  | 'ULTRAFILTRE' | 'CHARBON_ACTIF' | 'ADOUCISSEUR' | 'DESINFECTANT_CHIMIQUE'
  | 'FILTRE_PARTICULES' | 'POMPE_EAU' | 'COMPRESSEUR_AIR' | 'ALARME_SURVEILLANCE' | 'AUTRE';

export type StatutEquipement =
  'EN_SERVICE' | 'EN_MAINTENANCE' | 'EN_ATTENTE_PIECE' | 'HORS_SERVICE' | 'DESACTIF' | 'A_REFORMER' | 'REFORME';

export type PrioriteIntervention = 'NORMALE' | 'HAUTE' | 'URGENTE';

export type TypeDocumentIntervention = 'BON_INTERVENTION' | 'FACTURE' | 'PHOTO' | 'AUTRE';

export type TypeIntervention =
  | 'PREVENTIVE' | 'CURATIVE' | 'URGENTE' | 'CONTROLE' | 'INSTALLATION'
  | 'DEINSTALLATION' | 'REMPLACEMENT_PIECE' | 'REVISION_COMPLETE';

export type StatutIntervention = 'PLANIFIEE' | 'EN_COURS' | 'TERMINEE' | 'ANNULEE' | 'EN_ATTENTE_VALIDATION';

export type TypeLigneCout = 'PIECE' | 'MAIN_OEUVRE' | 'INTERVENANT' | 'AUTRE';

export type TypeIntervenant = 'INTERNE' | 'EXTERNE';

export interface Equipement {
  id: string;
  code: string;
  designation: string;
  type: TypeEquipement;
  fabricant: string | null;
  modele: string | null;
  numeroSerie: string | null;
  dateInstallation: string;
  statut: StatutEquipement;
  localisation: string | null;
  observations: string | null;
  dateCreation: string;
  dateModification: string | null;
  salleId: string | null;
  prixAcquisition: number | null;
}

export interface LigneCout {
  id: string;
  type: TypeLigneCout;
  libelle: string;
  quantite: number;
  prixUnitaire: number;
  montant: number;
  articleStockId: string | null;
  /** Ligne générée à la clôture (honoraires / main d'œuvre de l'intervenant), recalculée après rectification. */
  automatique: boolean;
}

export interface Intervention {
  id: string;
  equipementId: string;
  centreId: string;
  type: TypeIntervention;
  statut: StatutIntervention;
  dateDebut: string;
  dateFin: string | null;
  intervenantId: string | null;
  /** État de l'équipement saisi à la création (appliqué au démarrage). */
  etatEquipementAvant: StatutEquipement | null;
  /** État de l'équipement saisi à la clôture. */
  etatEquipementApres: StatutEquipement | null;
  priorite: PrioriteIntervention;
  echeance: string | null;
  /** Panne constatée à la création. */
  symptome: string | null;
  /** Cause trouvée à la clôture. */
  cause: string | null;
  /** Planifiée mais non démarrée à l'heure prévue. */
  enRetard: boolean;
  echeanceDepassee: boolean;
  nbRectifications: number;
  description: string;
  actions: string | null;
  pieceRemplacee: string | null;
  lignesCout: LigneCout[];
  coutTotal: number;
  observations: string | null;
  dateCreation: string;
  dateModification: string | null;
}

export interface Intervenant {
  id: string;
  centreId: string;
  nom: string;
  type: TypeIntervenant;
  telephone: string | null;
  email: string | null;
  tarifHoraireDefaut: number | null;
  actif: boolean;
}

export interface GmaoStats {
  totalEquipements: number;
  equipementsEnService: number;
  equipementsEnMaintenance: number;
  equipementsHorsService: number;
  totalInterventions: number;
  interventionsEnCours: number;
  interventionsTerminees: number;
  plansMaintenanceActifs: number;
  plansMaintenanceEnRetard: number;
  /** Interventions planifiées non démarrées à l'heure, ou à échéance dépassée. */
  interventionsARelancer: number;
}

export interface EquipementFiche {
  equipement: Equipement;
  nbInterventions: number;
  indisponibiliteHeures: number;
  coutMaintenancePeriode: number;
  coutMaintenanceCumule: number;
  /** Prix d'acquisition + maintenance cumulée. */
  coutPossession: number;
  /** Maintenance cumulée / prix d'acquisition (null si prix inconnu). */
  ratioMaintenance: number | null;
  /** Coût de maintenance de la période / heures d'indisponibilité (null si aucune). */
  coutParHeureIndisponibilite: number | null;
  reformeRecommandee: boolean;
  seuilReforme: number;
  derniereIntervention: Intervention | null;
  prochainePlanMaintenance: {
    id: string;
    designation: string;
    prochaineDatePrevue: string | null;
  } | null;
}

export interface DisponibilitePatient {
  disponible: boolean;
  statut: StatutEquipement;
}

export type PagedResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export interface CreateEquipementPayload {
  code: string;
  designation: string;
  type: TypeEquipement;
  fabricant?: string | null;
  modele?: string | null;
  numeroSerie?: string | null;
  dateInstallation: string;
  localisation?: string | null;
  salleId?: string | null;
  prixAcquisition?: number | null;
}

export interface UpdateEquipementPayload {
  designation: string;
  fabricant?: string | null;
  modele?: string | null;
  numeroSerie?: string | null;
  localisation?: string | null;
  salleId?: string | null;
  prixAcquisition?: number | null;
}

export interface CreateInterventionPayload {
  equipementId: string;
  type: TypeIntervention;
  dateDebut: string;
  description: string;
  intervenantId?: string | null;
  etatEquipementAvant: StatutEquipement;
  symptome?: string | null;
  priorite?: PrioriteIntervention;
  echeance?: string | null;
}

export interface IndicateursIntervention {
  delaiPriseEnChargeMinutes: number;
  dureeInterventionMinutes: number;
  indisponibiliteInterventionMinutes: number;
  indisponibiliteEquipement12MoisMinutes: number;
  /** Part (0-100) de l'indisponibilité des 12 derniers mois due à l'intervention ; null si aucune. */
  partIndisponibilite12MoisPct: number | null;
}

export type TypeEvenementIntervention = 'CREEE' | 'DEMARREE' | 'TERMINEE' | 'ANNULEE' | 'RECTIFIEE' | 'MODIFIEE';

export interface EvenementIntervention {
  type: TypeEvenementIntervention;
  at: string;
  parId: string | null;
  parNom: string | null;
  /** Précision (ex. motif d'une rectification). */
  detail: string | null;
}

export interface DocumentIntervention {
  id: string;
  interventionId: string;
  type: TypeDocumentIntervention;
  nom: string;
  contentType: string;
  taille: number;
  ajouteLe: string;
}

export interface AjouterLigneCoutPayload {
  type: TypeLigneCout;
  libelle: string;
  quantite: number;
  prixUnitaire: number;
  articleStockId?: string | null;
}

export interface IntervenantPayload {
  nom: string;
  type: TypeIntervenant;
  telephone?: string | null;
  email?: string | null;
  tarifHoraireDefaut?: number | null;
}

/**
 * Client HTTP du module GMAO (gestion de maintenance des équipements).
 * Toutes les listes sont paginées côté serveur (AGENTS.md §9) — le centre courant
 * est toujours déduit de la session authentifiée côté backend, jamais passé en paramètre ici.
 */
@Injectable({providedIn: 'root'})
export class GmaoApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/gmao`;

  // ---- Équipements ----

  listEquipements(page: number, size: number, statut?: string | null): Observable<PagedResponse<Equipement>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statut) params = params.set('statut', statut);
    return this.http.get<PagedResponse<Equipement>>(`${this.base}/equipements`, {params});
  }

  getEquipement(id: string): Observable<Equipement> {
    return this.http.get<Equipement>(`${this.base}/equipements/${id}`);
  }

  createEquipement(payload: CreateEquipementPayload): Observable<Equipement> {
    return this.http.post<Equipement>(`${this.base}/equipements`, payload);
  }

  updateEquipement(id: string, payload: UpdateEquipementPayload): Observable<Equipement> {
    return this.http.put<Equipement>(`${this.base}/equipements/${id}`, payload);
  }

  markEquipementOutOfService(id: string, raison: string): Observable<Equipement> {
    return this.http.post<Equipement>(`${this.base}/equipements/${id}/hors-service`, {raison});
  }

  reactivateEquipement(id: string): Observable<Equipement> {
    return this.http.post<Equipement>(`${this.base}/equipements/${id}/reactiver`, {});
  }

  reformerEquipement(id: string, motif: string): Observable<Equipement> {
    return this.http.post<Equipement>(`${this.base}/equipements/${id}/reformer`, {motif});
  }

  addEquipementObservation(id: string, observation: string): Observable<Equipement> {
    return this.http.post<Equipement>(`${this.base}/equipements/${id}/observations`, {observation});
  }

  getEquipementFiche(id: string): Observable<EquipementFiche> {
    return this.http.get<EquipementFiche>(`${this.base}/equipements/${id}/fiche`);
  }

  /**
   * Vérification de disponibilité avant d'affecter un patient à ce générateur — avertissement non
   * bloquant (sécurité patient, module GMAO v2).
   */
  checkDisponibilitePatient(id: string): Observable<DisponibilitePatient> {
    return this.http.get<DisponibilitePatient>(`${this.base}/equipements/${id}/disponibilite-patient`);
  }

  // ---- Interventions ----

  listInterventions(
    page: number,
    size: number,
    options?: { statut?: string | null; equipementId?: string | null },
  ): Observable<PagedResponse<Intervention>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.equipementId) params = params.set('equipementId', options.equipementId);
    return this.http.get<PagedResponse<Intervention>>(`${this.base}/interventions`, {params});
  }

  getIntervention(id: string): Observable<Intervention> {
    return this.http.get<Intervention>(`${this.base}/interventions/${id}`);
  }

  createIntervention(payload: CreateInterventionPayload): Observable<Intervention> {
    return this.http.post<Intervention>(`${this.base}/interventions`, payload);
  }

  startIntervention(id: string): Observable<Intervention> {
    return this.http.post<Intervention>(`${this.base}/interventions/${id}/demarrer`, {});
  }

  finishIntervention(
    id: string, actions: string, etatEquipementApres: StatutEquipement, dateFin: string, cause?: string | null,
  ): Observable<Intervention> {
    return this.http.post<Intervention>(
      `${this.base}/interventions/${id}/terminer`, {actions, etatEquipementApres, dateFin, cause});
  }

  /** Bon d'intervention (PDF) ; {@code tz} = fuseau du navigateur pour l'affichage des dates stockées en UTC. */
  getBonIntervention(id: string, tz: string): Observable<Blob> {
    const params = new HttpParams().set('tz', tz);
    return this.http.get(`${this.base}/interventions/${id}/bon`, {params, responseType: 'blob'});
  }

  /** Rectifie (rouvre) une intervention terminée — droit particulier, motif obligatoire. */
  rectifierIntervention(id: string, motif: string, dateDebut?: string | null): Observable<Intervention> {
    return this.http.post<Intervention>(`${this.base}/interventions/${id}/rectifier`, {motif, dateDebut});
  }

  supprimerLigneCout(id: string, ligneId: string): Observable<Intervention> {
    return this.http.delete<Intervention>(`${this.base}/interventions/${id}/lignes-cout/${ligneId}`);
  }

  getIndicateursIntervention(id: string): Observable<IndicateursIntervention> {
    return this.http.get<IndicateursIntervention>(`${this.base}/interventions/${id}/indicateurs`);
  }

  getChronologieIntervention(id: string): Observable<EvenementIntervention[]> {
    return this.http.get<EvenementIntervention[]>(`${this.base}/interventions/${id}/chronologie`);
  }

  listDocumentsIntervention(id: string, page: number, size: number): Observable<PagedResponse<DocumentIntervention>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<DocumentIntervention>>(`${this.base}/interventions/${id}/documents`, {params});
  }

  uploadDocumentIntervention(id: string, file: File, type: TypeDocumentIntervention): Observable<DocumentIntervention> {
    const body = new FormData();
    body.append('file', file);
    body.append('type', type);
    return this.http.post<DocumentIntervention>(`${this.base}/interventions/${id}/documents`, body);
  }

  downloadDocumentIntervention(id: string, documentId: string): Observable<Blob> {
    return this.http.get(`${this.base}/interventions/${id}/documents/${documentId}`, {responseType: 'blob'});
  }

  deleteDocumentIntervention(id: string, documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/interventions/${id}/documents/${documentId}`);
  }

  cancelIntervention(id: string, raison: string): Observable<Intervention> {
    return this.http.post<Intervention>(`${this.base}/interventions/${id}/annuler`, {raison});
  }

  ajouterLigneCout(id: string, payload: AjouterLigneCoutPayload): Observable<Intervention> {
    return this.http.post<Intervention>(`${this.base}/interventions/${id}/lignes-cout`, payload);
  }

  // ---- Intervenants (techniciens internes / prestataires externes) ----

  listIntervenants(page: number, size: number): Observable<PagedResponse<Intervenant>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<Intervenant>>(`${this.base}/intervenants`, {params});
  }

  createIntervenant(payload: IntervenantPayload): Observable<Intervenant> {
    return this.http.post<Intervenant>(`${this.base}/intervenants`, payload);
  }

  updateIntervenant(id: string, payload: IntervenantPayload): Observable<Intervenant> {
    return this.http.put<Intervenant>(`${this.base}/intervenants/${id}`, payload);
  }

  deactivateIntervenant(id: string): Observable<Intervenant> {
    return this.http.post<Intervenant>(`${this.base}/intervenants/${id}/desactiver`, {});
  }

  // ---- Statistiques (dashboard) ----

  getStats(): Observable<GmaoStats> {
    return this.http.get<GmaoStats>(`${this.base}/stats`);
  }
}
