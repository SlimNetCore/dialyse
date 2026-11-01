import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {IdResponse, Page} from './organisation-api.service';

export type AntecedentContexte = {
  id: string;
  patientId: string;
};

export type AllergieContexte = {
  id: string;
  patientId: string;
};

export type ObservationBiologiqueContexte = {
  id: string;
  patientId: string;
};

export type SerologieContexte = {
  id: string;
  patientId: string;
};

export type DemandeExamenContexte = {
  id: string;
  patientId: string;
  statut: string;
};

export type BilanPreGreffeContexte = {
  id: string;
  statut: string;
};

/**
 * Client HTTP du contexte Dossier-médical (`/api/v1/dossier-medical/*`, slices DM1-DM8).
 * <p>
 * SDK typé en attente de la bascule : les composants continuent d'utiliser
 * `DossierMedicalApiService` (endpoints `/api/v1/patients/*`) jusqu'au cutover.
 * Aucune régression : ce service n'est branché sur aucun composant.
 */
@Injectable({providedIn: 'root'})
export class DossierMedicalContexteApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/dossier-medical`;

  // --- Antécédents (DM5) ---

  listerAntecedents(centreId: string, patientId: string, page: number,
                    size: number): Observable<Page<AntecedentContexte>> {
    return this.http.get<Page<AntecedentContexte>>(`${this.base}/antecedents`, {
      params: this.pageParams(centreId, patientId, page, size),
    });
  }

  consulterAntecedent(antecedentId: string, centreId: string): Observable<AntecedentContexte> {
    return this.http.get<AntecedentContexte>(`${this.base}/antecedents/${antecedentId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  enregistrerAntecedent(patientId: string, centreId: string, type: string,
                        diagnosticSystem: string | null, diagnosticCode: string | null,
                        diagnosticDisplay: string | null, libelleLibre: string | null,
                        dateDebut: string | null, dateFin: string | null, severite: string | null,
                        note: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/antecedents`, {
      patientId, centreId, type, diagnosticSystem, diagnosticCode, diagnosticDisplay,
      libelleLibre, dateDebut, dateFin, severite, note,
    });
  }

  modifierAntecedent(antecedentId: string, diagnosticSystem: string | null,
                     diagnosticCode: string | null, diagnosticDisplay: string | null,
                     libelleLibre: string | null, dateDebut: string | null, dateFin: string | null,
                     statutClinique: string | null, severite: string | null,
                     note: string | null): Observable<AntecedentContexte> {
    return this.http.put<AntecedentContexte>(`${this.base}/antecedents/${antecedentId}`, {
      diagnosticSystem, diagnosticCode, diagnosticDisplay, libelleLibre, dateDebut, dateFin,
      statutClinique, severite, note,
    });
  }

  supprimerAntecedent(antecedentId: string, centreId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/antecedents/${antecedentId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  // --- Allergies (DM5) ---

  listerAllergies(centreId: string, patientId: string, page: number,
                  size: number): Observable<Page<AllergieContexte>> {
    return this.http.get<Page<AllergieContexte>>(`${this.base}/allergies`, {
      params: this.pageParams(centreId, patientId, page, size),
    });
  }

  consulterAllergie(allergieId: string, centreId: string): Observable<AllergieContexte> {
    return this.http.get<AllergieContexte>(`${this.base}/allergies/${allergieId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  enregistrerAllergie(patientId: string, centreId: string, allergene: string | null, type: string | null,
                      reaction: string | null, dateDebut: string | null, dateFin: string | null,
                      severite: string | null, note: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/allergies`, {
      patientId, centreId, allergene, type, reaction, dateDebut, dateFin, severite, note,
    });
  }

  modifierAllergie(allergieId: string, reaction: string | null, dateDebut: string | null,
                   dateFin: string | null, statutClinique: string | null, severite: string | null,
                   note: string | null): Observable<AllergieContexte> {
    return this.http.put<AllergieContexte>(`${this.base}/allergies/${allergieId}`, {
      reaction, dateDebut, dateFin, statutClinique, severite, note,
    });
  }

  supprimerAllergie(allergieId: string, centreId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/allergies/${allergieId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  // --- Sérologies (DM4) ---

  listerSerologies(centreId: string, patientId: string, page: number,
                   size: number): Observable<Page<SerologieContexte>> {
    return this.http.get<Page<SerologieContexte>>(`${this.base}/serologies`, {
      params: this.pageParams(centreId, patientId, page, size),
    });
  }

  consulterSerologie(serologieId: string, centreId: string): Observable<SerologieContexte> {
    return this.http.get<SerologieContexte>(`${this.base}/serologies/${serologieId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  enregistrerSerologie(patientId: string, centreId: string, prescripteurId: string | null,
                       datePrelevement: string | null, typeSerologie: string, resultat: string,
                       valeur: string | null, unite: string | null, dateResultat: string | null,
                       laboratoire: string | null, commentaire: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/serologies`, {
      patientId, centreId, prescripteurId, datePrelevement, typeSerologie, resultat,
      valeur, unite, dateResultat, laboratoire, commentaire,
    });
  }

  // --- Demandes d'examen (DM3) ---

  listerDemandesExamen(centreId: string, patientId: string, page: number,
                       size: number): Observable<Page<DemandeExamenContexte>> {
    return this.http.get<Page<DemandeExamenContexte>>(`${this.base}/demandes-examen`, {
      params: this.pageParams(centreId, patientId, page, size),
    });
  }

  consulterDemandeExamen(demandeId: string, centreId: string): Observable<DemandeExamenContexte> {
    return this.http.get<DemandeExamenContexte>(`${this.base}/demandes-examen/${demandeId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  demanderExamen(patientId: string, centreId: string, prescripteurId: string | null,
                 dateDemande: string | null, categorie: string, urgent: boolean,
                 motif: string | null,
                 lignes: {
                   id?: string; codeSystem?: string | null; code?: string | null;
                   codeDisplay?: string | null; libelle?: string | null;
                   commentaire?: string | null
                 }[]): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/demandes-examen`, {
      patientId, centreId, prescripteurId, dateDemande, categorie, urgent, motif, lignes,
    });
  }

  marquerPreleve(demandeId: string, centreId: string): Observable<void> {
    return this.http.put<void>(`${this.base}/demandes-examen/${demandeId}/preleve`, {centreId});
  }

  marquerResultatDisponible(demandeId: string, centreId: string): Observable<void> {
    return this.http.put<void>(`${this.base}/demandes-examen/${demandeId}/resultat-disponible`,
      {centreId});
  }

  validerDemandeExamen(demandeId: string, centreId: string,
                       conclusion: string | null): Observable<void> {
    return this.http.put<void>(`${this.base}/demandes-examen/${demandeId}/valider`,
      {centreId, conclusion});
  }

  annulerDemandeExamen(demandeId: string, centreId: string): Observable<void> {
    return this.http.put<void>(`${this.base}/demandes-examen/${demandeId}/annuler`, {centreId});
  }

  // --- Observations biologiques (DM6) ---

  listerObservations(centreId: string, patientId: string, page: number,
                     size: number): Observable<Page<ObservationBiologiqueContexte>> {
    return this.http.get<Page<ObservationBiologiqueContexte>>(
      `${this.base}/observations-biologiques`, {
        params: this.pageParams(centreId, patientId, page, size),
      });
  }

  consulterObservation(observationId: string, centreId: string,
  ): Observable<ObservationBiologiqueContexte> {
    return this.http.get<ObservationBiologiqueContexte>(
      `${this.base}/observations-biologiques/${observationId}`, {
        params: new HttpParams().set('centreId', centreId),
      });
  }

  enregistrerObservation(patientId: string, centreId: string, code: string, libelle: string | null,
                         valeur: string, unite: string | null, seuilBas: number | null,
                         seuilHaut: number | null, dateObservation: string | null,
                         prescripteurId: string | null, laboratoire: string | null,
                         commentaire: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/observations-biologiques`, {
      patientId, centreId, code, libelle, valeur, unite, seuilBas, seuilHaut,
      dateObservation, prescripteurId, laboratoire, commentaire,
    });
  }

  // --- Bilans pré-greffe (DM7) ---

  listerBilans(centreId: string, patientId: string, page: number,
               size: number): Observable<Page<BilanPreGreffeContexte>> {
    return this.http.get<Page<BilanPreGreffeContexte>>(`${this.base}/bilans-pregreffe`, {
      params: this.pageParams(centreId, patientId, page, size),
    });
  }

  consulterBilan(bilanId: string, centreId: string): Observable<BilanPreGreffeContexte> {
    return this.http.get<BilanPreGreffeContexte>(`${this.base}/bilans-pregreffe/${bilanId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  demarrerBilan(patientId: string, centreId: string,
                etapes: { code: string; libelle: string | null }[], dateDemarrage: string | null,
                commentaire: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/bilans-pregreffe`,
      {patientId, centreId, etapes, dateDemarrage, commentaire});
  }

  validerEtapeBilan(bilanId: string, codeEtape: string, centreId: string,
                    validePar: string | null): Observable<BilanPreGreffeContexte> {
    return this.http.post<BilanPreGreffeContexte>(
      `${this.base}/bilans-pregreffe/${bilanId}/etapes/${codeEtape}/validation`,
      {centreId, validePar});
  }

  completerBilan(bilanId: string, centreId: string): Observable<BilanPreGreffeContexte> {
    return this.http.post<BilanPreGreffeContexte>(
      `${this.base}/bilans-pregreffe/${bilanId}/completion`, {centreId});
  }

  annulerBilan(bilanId: string, centreId: string): Observable<BilanPreGreffeContexte> {
    return this.http.post<BilanPreGreffeContexte>(
      `${this.base}/bilans-pregreffe/${bilanId}/annulation`, {centreId});
  }

  private pageParams(centreId: string, patientId: string, page: number, size: number): HttpParams {
    return new HttpParams().set('centreId', centreId).set('patientId', patientId)
      .set('page', page).set('size', size);
  }
}
