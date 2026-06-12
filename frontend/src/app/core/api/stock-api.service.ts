import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface Fournisseur {
  id: string;
  centerId: string;
  code?: string;
  raisonSociale: string;
  contact?: string;
  telephone?: string;
  email?: string;
  actif: boolean;
}

export interface Emplacement {
  id: string;
  centerId: string;
  code?: string;
  libelle: string;
  actif: boolean;
}

export interface LigneBonCommande {
  id?: string;
  articleId: string;
  quantite: number;
  prixUnitaire: number;
}

export interface BonCommande {
  id: string;
  centerId: string;
  reference: string;
  fournisseurId?: string;
  statut: 'BROUILLON' | 'VALIDE' | 'RECU' | 'ANNULE';
  lignes: LigneBonCommande[];
  createdBy?: string;
  createdAt?: string;
}

export interface LigneReception {
  id?: string;
  articleId: string;
  quantite: number;
  prixUnitaire: number;
  numeroLot?: string;
  datePeremption?: string;
  emplacementId?: string;
  lotId?: string;
}

export interface BonReception {
  id: string;
  centerId: string;
  reference: string;
  bonCommandeId?: string;
  fournisseurId?: string;
  dateReception?: string;
  statut: 'BROUILLON' | 'VALIDE' | 'RECU' | 'ANNULE';
  lignes: LigneReception[];
  createdAt?: string;
}

export interface SortieItem {
  articleId: string;
  quantite: number;
}

export interface BonSortie {
  id: string;
  centerId: string;
  reference: string;
  seanceId: string;
  patientId?: string;
  poste?: string;
  dateSortie?: string;
  lignes: { id?: string; articleId: string; lotId?: string; quantite: number; pmpApplique?: number }[];
  createdAt?: string;
}

export interface StockValoriseItem {
  articleId: string;
  code: string;
  libelle: string;
  unite: string;
  quantite: number;
  pmpCourant: number;
  valeur: number;
}

export interface AlerteStock {
  type: 'PEREMPTION' | 'RUPTURE' | 'SEUIL';
  articleId: string;
  articleLibelle: string;
  lotId?: string;
  numeroLot?: string;
  datePeremption?: string;
  quantite?: number;
  seuil?: number;
}

export interface TracabiliteItem {
  lotId: string;
  numeroLot: string;
  articleId: string;
  articleLibelle: string;
  seanceId?: string;
  patientId?: string;
  dateSortie?: string;
  quantite: number;
}

export interface PmpExplanationStep {
  date?: string;
  type: 'ENTREE' | 'SORTIE' | 'AJUSTEMENT';
  quantite: number;
  prixUnitaire?: number;
  quantiteAvant: number;
  valeurAvant: number;
  pmpAvant: number;
  quantiteApres: number;
  valeurApres: number;
  pmpApres: number;
  formule: string;
}

export interface PmpExplanation {
  articleId: string;
  code: string;
  libelle: string;
  methode: string;
  etapes: PmpExplanationStep[];
  quantiteFinale: number;
  valeurFinale: number;
  pmpFinal: number;
}

@Injectable({providedIn: 'root'})
export class StockApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/stock`;

  // --- Referentiel ---
  listFournisseurs(centerId: string, q?: string): Observable<Fournisseur[]> {
    let params = new HttpParams().set('centerId', centerId);
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<Fournisseur[]>(`${this.base}/referentiel/fournisseurs`, {params});
  }

  createFournisseur(payload: Partial<Fournisseur> & {
    centerId: string;
    raisonSociale: string
  }): Observable<Fournisseur> {
    return this.http.post<Fournisseur>(`${this.base}/referentiel/fournisseurs`, payload);
  }

  listEmplacements(centerId: string): Observable<Emplacement[]> {
    return this.http.get<Emplacement[]>(`${this.base}/referentiel/emplacements`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createEmplacement(payload: { centerId: string; code?: string; libelle: string }): Observable<Emplacement> {
    return this.http.post<Emplacement>(`${this.base}/referentiel/emplacements`, payload);
  }

  // --- Bons de commande (BL) ---
  listBonsCommande(centerId: string): Observable<BonCommande[]> {
    return this.http.get<BonCommande[]>(`${this.base}/bons-commande`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  getBonCommande(id: string, centerId: string): Observable<BonCommande> {
    return this.http.get<BonCommande>(`${this.base}/bons-commande/${id}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createBonCommande(payload: {
    centerId: string;
    fournisseurId?: string;
    userId?: string;
    lignes: LigneBonCommande[]
  }) {
    return this.http.post<BonCommande>(`${this.base}/bons-commande`, payload);
  }

  validerBonCommande(id: string, centerId: string, userId?: string) {
    return this.http.post<BonCommande>(`${this.base}/bons-commande/${id}/valider`, {centerId, userId});
  }

  // --- Bons de reception (BR) ---
  listBonsReception(centerId: string): Observable<BonReception[]> {
    return this.http.get<BonReception[]>(`${this.base}/bons-reception`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createBonReception(payload: {
    centerId: string;
    bonCommandeId?: string;
    fournisseurId?: string;
    dateReception?: string;
    userId?: string;
    lignes: LigneReception[];
  }) {
    return this.http.post<BonReception>(`${this.base}/bons-reception`, payload);
  }

  fromBonCommande(payload: { centerId: string; bonCommandeId: string; userId?: string }) {
    return this.http.post<BonReception>(`${this.base}/bons-reception/from-bon-commande`, payload);
  }

  updateBonReception(id: string, payload: {
    centerId: string;
    fournisseurId?: string;
    dateReception?: string;
    lignes: LigneReception[];
  }) {
    return this.http.put<BonReception>(`${this.base}/bons-reception/${id}`, payload);
  }

  validerBonReception(id: string, centerId: string, userId?: string) {
    return this.http.post<BonReception>(`${this.base}/bons-reception/${id}/valider`, {centerId, userId});
  }

  // --- Bons de sortie (BS) ---
  listBonsSortie(centerId: string): Observable<BonSortie[]> {
    return this.http.get<BonSortie[]>(`${this.base}/bons-sortie`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  createBonSortie(payload: {
    centerId: string;
    seanceId: string;
    patientId?: string;
    poste?: string;
    dateSortie?: string;
    userId?: string;
    items: SortieItem[];
  }) {
    return this.http.post<BonSortie>(`${this.base}/bons-sortie`, payload);
  }

  // --- Dashboard ---
  stockValorise(centerId: string): Observable<StockValoriseItem[]> {
    return this.http.get<StockValoriseItem[]>(`${this.base}/dashboard/stock-valorise`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  alertes(centerId: string): Observable<AlerteStock[]> {
    return this.http.get<AlerteStock[]>(`${this.base}/dashboard/alertes`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  tracabilite(lotId: string, centerId: string): Observable<TracabiliteItem[]> {
    return this.http.get<TracabiliteItem[]>(`${this.base}/dashboard/tracabilite/${lotId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }

  pmpExplain(articleId: string, centerId: string): Observable<PmpExplanation> {
    return this.http.get<PmpExplanation>(`${this.base}/dashboard/pmp-explain/${articleId}`, {
      params: new HttpParams().set('centerId', centerId),
    });
  }
}




