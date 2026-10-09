import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './backend-api.service';

/** Code d'un journal : les journaux sont paramétrés par chaque centre, ce n'est plus une liste figée. */
export type JournalCode = string;

export type JournalItem = {
  code: JournalCode;
  libelle: string;
  actif: boolean;
};

/** Opérations de gestion qui produisent une écriture ; chaque centre choisit le journal de chacune. */
export const OPERATIONS_COMPTABLES = [
  'VENTE', 'REGLEMENT_BANQUE', 'REGLEMENT_CAISSE', 'STOCK_RECEPTION', 'STOCK_SORTIE', 'STOCK_INVENTAIRE',
] as const;
export type OperationComptable = typeof OPERATIONS_COMPTABLES[number];

/** Résultat d'une comptabilisation du stock : écritures créées ou mises à jour, par nature. */
export type SynchronisationStock = {
  receptions: number;
  joursSorties: number;
  inventaires: number;
  complements: number;
  ignorees: number;
};

export type StatutEcriture = 'BROUILLON' | 'VALIDEE' | 'EXPORTEE';

export type LigneEcritureItem = {
  id: string;
  compteSCF: string;
  libelleLigne: string;
  montantDebit: number;
  montantCredit: number;
};

export type EcritureComptableItem = {
  id: string;
  centerId: string;
  journalCode: JournalCode;
  dateEcriture: string;
  datePiece: string;
  numeroPiece: string;
  libelle: string;
  statut: StatutEcriture;
  totalDebit: number;
  lignes: LigneEcritureItem[];
};

export type MappingComptableItem = {
  centerId: string;
  compteVentes: string;
  compteClientPatient: string;
  compteClientCnas: string;
  compteClientCasnos: string;
  compteClientMutuelle: string;
  compteClientAutre: string;
  compteBanque: string;
  compteCaisse: string;
  compteTVACollectee: string;
  compteStock: string;
  compteConsommation: string;
  compteFacturesNonParvenues: string;
  compteBoniInventaire: string;
  compteMaliInventaire: string;
  journaux: Record<OperationComptable, JournalCode>;
};

export type RegleTVAItem = {
  typePrestation: string;
  tauxApplique: number;
  exonere: boolean;
  dateDebutValidite: string;
  dateFinValidite?: string;
  texteReference?: string;
};

export type ComptabiliteListQuery = {
  centerId: string;
  from: string;
  to: string;
  journalCode?: JournalCode | null;
  statut?: StatutEcriture | null;
  page: number;
  size: number;
};

@Injectable({providedIn: 'root'})
export class ComptabiliteApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/comptabilite`;

  searchEcritures(query: ComptabiliteListQuery): Observable<PagedResponse<EcritureComptableItem>> {
    let params = new HttpParams()
      .set('centerId', query.centerId)
      .set('from', query.from)
      .set('to', query.to)
      .set('page', String(query.page))
      .set('size', String(query.size));
    if (query.journalCode) params = params.set('journalCode', query.journalCode);
    if (query.statut) params = params.set('statut', query.statut);
    return this.http.get<PagedResponse<EcritureComptableItem>>(`${this.base}/ecritures`, {params});
  }

  validerEcriture(id: string, centerId: string): Observable<EcritureComptableItem> {
    return this.http.post<EcritureComptableItem>(`${this.base}/ecritures/${id}/valider`, null, {
      params: new HttpParams().set('centerId', centerId)
    });
  }

  exporterJournal(centerId: string, from: string, to: string,
                  journalCode: JournalCode, format = 'SAGE100'): Observable<Blob> {
    const params = new HttpParams()
      .set('centerId', centerId)
      .set('from', from)
      .set('to', to)
      .set('journalCode', journalCode)
      .set('format', format);
    return this.http.get(`${this.base}/ecritures/export`, {params, responseType: 'blob'});
  }

  cloturerPeriode(centerId: string, annee: number, mois: number, userId: string): Observable<{ closed: boolean }> {
    return this.http.post<{ closed: boolean }>(`${this.base}/periodes/cloturer`, {centerId, annee, mois, userId});
  }

  getMapping(centerId: string): Observable<MappingComptableItem> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<MappingComptableItem>(`${this.base}/mapping`, {params});
  }

  saveMapping(mapping: MappingComptableItem): Observable<MappingComptableItem> {
    return this.http.put<MappingComptableItem>(`${this.base}/mapping`, mapping);
  }

  getJournaux(centerId: string, page: number, size: number): Observable<PagedResponse<JournalItem>> {
    const params = new HttpParams().set('centerId', centerId).set('page', String(page)).set('size', String(size));
    return this.http.get<PagedResponse<JournalItem>>(`${this.base}/journaux`, {params});
  }

  saveJournal(centerId: string, journal: JournalItem): Observable<JournalItem> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.put<JournalItem>(`${this.base}/journaux/${encodeURIComponent(journal.code)}`,
      {libelle: journal.libelle, actif: journal.actif}, {params});
  }

  deleteJournal(centerId: string, code: JournalCode): Observable<void> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<void>(`${this.base}/journaux/${encodeURIComponent(code)}`, {params});
  }

  synchroniserStock(centerId: string, from: string, to: string): Observable<SynchronisationStock> {
    return this.http.post<SynchronisationStock>(`${this.base}/stock/synchroniser`, {centerId, from, to});
  }

  getRegles(centerId: string): Observable<RegleTVAItem[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<RegleTVAItem[]>(`${this.base}/regles-tva`, {params});
  }

  saveRegle(centerId: string, regle: RegleTVAItem): Observable<{ saved: boolean }> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.post<{ saved: boolean }>(`${this.base}/regles-tva`, regle, {params});
  }
}

