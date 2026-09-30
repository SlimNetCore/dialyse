import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './admin-api.service';

export type InventaireStatut = 'EN_COURS' | 'CLOTURE' | 'ANNULE';

export interface LigneInventaire {
  id: string;
  articleId: string;
  articleCode: string | null;
  articleLibelle: string | null;
  unite: string | null;
  lotId: string | null;
  numeroLot: string | null;
  datePeremption: string | null;
  quantiteTheorique: number;
  quantiteComptee: number | null;
  ecart: number | null;
  pmp: number;
  valeurEcart: number | null;
  motifEcart: string | null;
  comptePar: string | null;
  compteLe: string | null;
  ajoutee: boolean;
}

export interface Inventaire {
  id: string;
  reference: string;
  dateInventaire: string;
  statut: InventaireStatut;
  commentaire: string | null;
  createdBy: string | null;
  createdAt: string;
  closedBy: string | null;
  closedAt: string | null;
  lignes: number;
  comptees: number;
  ecarts: number;
  ecartsSansMotif: number;
  valeurTheorique: number;
  valeurComptee: number;
  valeurEcarts: number;
  details: LigneInventaire[];
}

export interface InventaireResume {
  id: string;
  reference: string;
  dateInventaire: string;
  statut: InventaireStatut;
  lignes: number;
  comptees: number;
  ecarts: number;
  valeurEcarts: number;
  createdBy: string | null;
  createdAt: string;
  closedBy: string | null;
  closedAt: string | null;
}

/** Situation du centre : mouvements gelés par un inventaire en cours, date de la dernière clôture. */
export interface EtatInventaire {
  mouvementsBloques: boolean;
  inventaireEnCoursId: string | null;
  inventaireEnCoursReference: string | null;
  inventaireEnCoursDate: string | null;
  ouvertPar: string | null;
  derniereCloture: string | null;
}

export interface AjoutLignePayload {
  articleId: string;
  numeroLot: string | null;
  datePeremption: string | null;
  quantite: number;
  motif: string | null;
}

/** Inventaire de stock du centre actif (le centre est toujours transmis ; le backend refuse tout autre centre). */
@Injectable({providedIn: 'root'})
export class InventaireApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/stock/inventaires`;

  list(centerId: string, page: number, size: number): Observable<PagedResponse<InventaireResume>> {
    const params = new HttpParams().set('centerId', centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<InventaireResume>>(this.base, {params, withCredentials: true});
  }

  etat(centerId: string): Observable<EtatInventaire> {
    return this.http.get<EtatInventaire>(`${this.base}/etat`, {params: this.center(centerId), withCredentials: true});
  }

  ouvrir(centerId: string, dateInventaire: string | null, commentaire: string | null): Observable<Inventaire> {
    return this.http.post<Inventaire>(this.base, {centerId, dateInventaire, commentaire}, {withCredentials: true});
  }

  get(centerId: string, id: string): Observable<Inventaire> {
    return this.http.get<Inventaire>(`${this.base}/${id}`, {params: this.center(centerId), withCredentials: true});
  }

  compter(centerId: string, id: string, ligneId: string, quantite: number, motif: string | null): Observable<Inventaire> {
    return this.http.put<Inventaire>(`${this.base}/${id}/lignes/${ligneId}`, {
      centerId,
      quantite,
      motif
    }, {withCredentials: true});
  }

  ajouterLigne(centerId: string, id: string, payload: AjoutLignePayload): Observable<Inventaire> {
    return this.http.post<Inventaire>(`${this.base}/${id}/lignes`, {centerId, ...payload}, {withCredentials: true});
  }

  retirerLigne(centerId: string, id: string, ligneId: string): Observable<Inventaire> {
    return this.http.delete<Inventaire>(`${this.base}/${id}/lignes/${ligneId}`, {
      params: this.center(centerId),
      withCredentials: true
    });
  }

  reporterTheorique(centerId: string, id: string): Observable<Inventaire> {
    return this.http.post<Inventaire>(`${this.base}/${id}/reporter-theorique`, null, {
      params: this.center(centerId),
      withCredentials: true
    });
  }

  cloturer(centerId: string, id: string): Observable<Inventaire> {
    return this.http.post<Inventaire>(`${this.base}/${id}/cloturer`, null, {
      params: this.center(centerId),
      withCredentials: true
    });
  }

  annuler(centerId: string, id: string): Observable<Inventaire> {
    return this.http.post<Inventaire>(`${this.base}/${id}/annuler`, null, {
      params: this.center(centerId),
      withCredentials: true
    });
  }

  /** Feuille de comptage Excel, « à l'aveugle » par défaut (sans quantité théorique). */
  feuilleComptage(centerId: string, id: string, aveugle: boolean): Observable<Blob> {
    const params = this.center(centerId).set('aveugle', aveugle);
    return this.http.get(`${this.base}/${id}/feuille-comptage`, {params, responseType: 'blob', withCredentials: true});
  }

  private center(centerId: string): HttpParams {
    return new HttpParams().set('centerId', centerId);
  }
}

