import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

/** Réponse d'identifiant créé (record Java `{value}` sérialisé en JSON). */
export type IdResponse = {
  value: string;
};

export type Page<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type SocieteOrganisation = {
  id: string;
  code: string;
  raisonSociale: string;
  actif: boolean;
};

export type CentreOrganisation = {
  id: string;
  code: string;
  nom: string;
  actif: boolean;
};

export type SalleOrganisation = {
  id: string;
  code: string;
  libelle: string;
  ouverte: boolean;
};

export type CreneauOrganisation = {
  id: string;
  code: string;
  libelle: string;
};

export type MedecinOrganisation = {
  id: string;
  nom: string;
  prenom: string | null;
  specialite: string | null;
};

export type MachineOrganisation = {
  id: string;
  code: string;
  libelle: string;
  actif: boolean;
};

export type ProtocoleOrganisation = {
  id: string;
  code: string;
  libelle: string;
};

/**
 * Client HTTP du contexte Organisation (`/api/v1/organisation/*`, slices O1-O4).
 * <p>
 * SDK typé en attente de la bascule (slice O5 `retrait-bc-referential`) : les composants
 * continuent d'utiliser `SocieteApiService` / `ReferentialApiService` / `ReferentialAdminApiService`
 * jusqu'au cutover. Aucune régression : ce service n'est branché sur aucun composant.
 */
@Injectable({providedIn: 'root'})
export class OrganisationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/organisation`;

  // --- Sociétés (O1) ---

  creerSociete(code: string, raisonSociale: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/societes`, {code, raisonSociale});
  }

  modifierSociete(id: string, code: string, raisonSociale: string, actif: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/societes/${id}`, {code, raisonSociale, actif});
  }

  listerSocietes(recherche: string, page: number, size: number): Observable<Page<SocieteOrganisation>> {
    return this.http.get<Page<SocieteOrganisation>>(`${this.base}/societes`, {
      params: new HttpParams().set('q', recherche).set('page', page).set('size', size),
    });
  }

  consulterSociete(id: string): Observable<SocieteOrganisation> {
    return this.http.get<SocieteOrganisation>(`${this.base}/societes/${id}`);
  }

  uploadLogo(id: string, file: File): Observable<unknown> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.put(`${this.base}/societes/${id}/logo`, body);
  }

  getLogo(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/societes/${id}/logo`, {responseType: 'blob'});
  }

  deleteLogo(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/societes/${id}/logo`);
  }

  societesAvecLogo(ids: string[]): Observable<string[]> {
    let params = new HttpParams();
    ids.forEach((id) => (params = params.append('ids', id)));
    return this.http.get<string[]>(`${this.base}/societes/logos`, {params});
  }

  // --- Centres (O1) ---

  creerCentre(societeId: string, code: string, nom: string, fuseauHoraire: string,
              capaciteTheorique: number): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/centres`,
      {societeId, code, nom, fuseauHoraire, capaciteTheorique});
  }

  modifierCentre(id: string, nom: string, fuseauHoraire: string, capaciteTheorique: number,
                 actif: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/centres/${id}`,
      {nom, fuseauHoraire, capaciteTheorique, actif});
  }

  transfererCentre(id: string, nouvelleSocieteId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/centres/${id}/transferer`, {nouvelleSocieteId});
  }

  centresActifs(societeId: string): Observable<CentreOrganisation[]> {
    return this.http.get<CentreOrganisation[]>(`${this.base}/centres/actifs`, {
      params: new HttpParams().set('societeId', societeId),
    });
  }

  listerCentres(societeId: string): Observable<CentreOrganisation[]> {
    return this.http.get<CentreOrganisation[]>(`${this.base}/centres`, {
      params: new HttpParams().set('societeId', societeId),
    });
  }

  parametresCentre(id: string): Observable<CentreOrganisation> {
    return this.http.get<CentreOrganisation>(`${this.base}/centres/${id}/parametres`);
  }

  // --- Salles (O2) ---

  listerSalles(centreId: string): Observable<SalleOrganisation[]> {
    return this.http.get<SalleOrganisation[]>(`${this.base}/salles`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  ouvrirSalle(id: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/salles/${id}/ouvrir`, {}, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  fermerSalle(id: string, centreId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/salles/${id}/fermer`, {}, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  // --- Créneaux (O2) ---

  listerCreneaux(centreId: string): Observable<CreneauOrganisation[]> {
    return this.http.get<CreneauOrganisation[]>(`${this.base}/creneaux`, {
      params: new HttpParams().set('centreId', centreId),
    });
  }

  creerCreneau(centreId: string, code: string, libelle: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/creneaux`, {centreId, code, libelle});
  }

  modifierCreneau(id: string, centreId: string, code: string, libelle: string): Observable<void> {
    return this.http.put<void>(`${this.base}/creneaux/${id}`, {centreId, code, libelle});
  }

  // --- Référentiels (O3) : médecins, machines, protocoles ---

  listerMedecins(centreId: string, page: number, size: number): Observable<Page<MedecinOrganisation>> {
    return this.http.get<Page<MedecinOrganisation>>(`${this.base}/referentiels/medecins`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  creerMedecin(centreId: string, nom: string, prenom: string | null,
               specialite: string | null): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/referentiels/medecins`,
      {centreId, nom, prenom, specialite});
  }

  modifierMedecin(id: string, nom: string, prenom: string | null,
                  specialite: string | null): Observable<void> {
    return this.http.put<void>(`${this.base}/referentiels/medecins/${id}`, {nom, prenom, specialite});
  }

  supprimerMedecin(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/referentiels/medecins/${id}`);
  }

  listerMachines(centreId: string, page: number, size: number): Observable<Page<MachineOrganisation>> {
    return this.http.get<Page<MachineOrganisation>>(`${this.base}/referentiels/machines`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  creerMachine(centreId: string, code: string, libelle: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/referentiels/machines`, {centreId, code, libelle});
  }

  modifierMachine(id: string, code: string, libelle: string, actif: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/referentiels/machines/${id}`, {code, libelle, actif});
  }

  supprimerMachine(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/referentiels/machines/${id}`);
  }

  listerProtocoles(centreId: string, page: number, size: number): Observable<Page<ProtocoleOrganisation>> {
    return this.http.get<Page<ProtocoleOrganisation>>(`${this.base}/referentiels/protocoles`, {
      params: new HttpParams().set('centreId', centreId).set('page', page).set('size', size),
    });
  }

  creerProtocole(centreId: string, code: string, libelle: string): Observable<IdResponse> {
    return this.http.post<IdResponse>(`${this.base}/referentiels/protocoles`, {centreId, code, libelle});
  }

  modifierProtocole(id: string, code: string, libelle: string): Observable<void> {
    return this.http.put<void>(`${this.base}/referentiels/protocoles/${id}`, {code, libelle});
  }

  supprimerProtocole(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/referentiels/protocoles/${id}`);
  }
}
