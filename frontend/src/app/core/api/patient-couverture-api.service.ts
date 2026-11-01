import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {IdResponse, Page} from './organisation-api.service';

export type PatientCouverture = {
  id: string;
  centreId: string;
  nom: string;
  prenom: string | null;
};

export type AssureCouverture = {
  numero: string;
  nom: string;
  prenom: string | null;
};

export type PecCouverture = {
  id: string;
  statut: string;
};

export type AttestationDroit = {
  id: string;
  dateDebut: string;
  dateFin: string;
};

/**
 * Client HTTP du contexte Patient-couverture (`/api/v1/patient-couverture/*`, slices PC1-PC5).
 * <p>
 * SDK typé en attente de la bascule : les composants continuent d'utiliser les services
 * legacy (`BackendApiService`, etc.) jusqu'au cutover. Aucune régression : ce service
 * n'est branché sur aucun composant.
 */
@Injectable({providedIn: 'root'})
export class PatientCouvertureApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/patient-couverture`;

  // --- Patients (PC1) ---

  admettre(centreId: string, nom: string, prenom: string | null, sexe: string | null,
           dateNaissance: string | null, numeroAssurance: string | null,
           dateAdmission: string | null, auteurId: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/patients`, {
      centreId, nom, prenom, sexe, dateNaissance, numeroAssurance, dateAdmission, auteurId,
    });
  }

  consulterPatient(id: string, centreId: string): Observable<PatientCouverture> {
    return this.http.get<PatientCouverture>(`${this.base}/patients/${id}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  listerPatients(centreId: string, page: number, size: number): Observable<Page<PatientCouverture>> {
    return this.http.get<Page<PatientCouverture>>(`${this.base}/patients`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  sortiePatient(id: string, centreId: string, dateSortie: string | null,
                auteurId: string | null): Observable<void> {
    return this.http.post<void>(`${this.base}/patients/${id}/sortie`,
      {centreId, dateSortie, auteurId});
  }

  // --- Assurés (PC2) ---

  affecterAssurePrincipal(patientId: string, centreId: string, numeroAssurance: string, nom: string,
                          prenom: string | null, sexe: string | null, dateNaissance: string | null,
                          telPersonnel: string | null, telMobile: string | null,
                          telBureau: string | null, adresse: string | null,
                          groupeSanguin: string | null, typeAssure: string | null,
                          auteurId: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/assures/affecter-principal`, {
      patientId, centreId, numeroAssurance, nom, prenom, sexe, dateNaissance,
      telPersonnel, telMobile, telBureau, adresse, groupeSanguin, typeAssure, auteurId,
    });
  }

  consulterAssure(numero: string, centreId: string): Observable<AssureCouverture> {
    return this.http.get<AssureCouverture>(`${this.base}/assures/${numero}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  consulterAssurePrincipal(patientId: string, centreId: string): Observable<AssureCouverture> {
    return this.http.get<AssureCouverture>(`${this.base}/assures/principal`, {
      params: new HttpParams().set('patientId', patientId).set('centreId', centreId),
    });
  }

  modifierAssure(numero: string, centreId: string, nom: string, prenom: string | null,
                 sexe: string | null, dateNaissance: string | null, telPersonnel: string | null,
                 telMobile: string | null, telBureau: string | null, adresse: string | null,
                 groupeSanguin: string | null, typeAssure: string | null): Observable<void> {
    return this.http.put<void>(`${this.base}/assures/${numero}`, {
      nom, prenom, sexe, dateNaissance, telPersonnel, telMobile, telBureau,
      adresse, groupeSanguin, typeAssure,
    }, {params: new HttpParams().set('centreId', centreId)});
  }

  // --- PEC (PC3) ---

  creerPec(patientId: string, centreId: string, dateDebutDemande: string | null,
           dateFinDemande: string | null, forfaitDemandeId: string | null,
           auteurId: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/pec`, {
      patientId, centreId, dateDebutDemande, dateFinDemande, forfaitDemandeId, auteurId,
    });
  }

  listerPec(centreId: string, page: number, size: number): Observable<Page<PecCouverture>> {
    return this.http.get<Page<PecCouverture>>(`${this.base}/pec`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  listerPecParPatient(patientId: string, centreId: string): Observable<PecCouverture[]> {
    return this.http.get<PecCouverture[]>(`${this.base}/pec/patient/${patientId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  consulterPec(pecId: string, centreId: string): Observable<PecCouverture> {
    return this.http.get<PecCouverture>(`${this.base}/pec/${pecId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  validerPec(pecId: string, centreId: string, dateDebutEffectif: string | null,
             dateFinEffectif: string | null, forfaitEffectifId: string | null): Observable<void> {
    return this.http.post<void>(`${this.base}/pec/${pecId}/validate`, {
      centreId, dateDebutEffectif, dateFinEffectif, forfaitEffectifId,
    });
  }

  cloturerPec(pecId: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/pec/${pecId}/close`, {centreId});
  }

  supprimerPec(pecId: string, centreId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/pec/${pecId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  seanceAutorisee(pecId: string, centreId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.base}/pec/${pecId}/session-allowed`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  // --- Attestations (PC4) ---

  enregistrerAttestation(patientId: string, centreId: string, dateDebut: string | null,
                         dateFin: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/attestations`,
      {patientId, centreId, dateDebut, dateFin});
  }

  listerAttestations(centreId: string, page: number, size: number): Observable<Page<AttestationDroit>> {
    return this.http.get<Page<AttestationDroit>>(`${this.base}/attestations`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  listerAttestationsParPatient(patientId: string,
                               centreId: string): Observable<AttestationDroit[]> {
    return this.http.get<AttestationDroit[]>(`${this.base}/attestations/patient/${patientId}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  consulterAttestation(id: string, centreId: string): Observable<AttestationDroit> {
    return this.http.get<AttestationDroit>(`${this.base}/attestations/${id}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  supprimerAttestation(id: string, centreId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/attestations/${id}`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }
}
