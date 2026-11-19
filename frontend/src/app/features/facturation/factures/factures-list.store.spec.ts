import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {Subject} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import type {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {BackendApiService, DetailDeFacture, PageDeFactures} from '../../../core/api/backend-api.service';
import {
  bornesDeMontant,
  FacturesListStore,
  filtresAChoixUnique,
  lireMontant,
  messageDErreur,
  moisDe,
  periodeValide,
  versCriteres,
} from './factures-list.store';

const CENTRE = 'c1';

const page = (items: number): PageDeFactures => ({
  items: Array.from({length: items}, (_, i) => ({factureId: `f${i}`}) as never),
  total: items, page: 0, size: 20, du: '2026-11-01', au: '2026-11-30',
  totaux: {nombreDeFactures: items, montantHt: 0, montantTva: 0, montantTtc: 0, montantRegle: 0, reste: 0},
});
const detail = (id: string): DetailDeFacture => ({
  factureId: id, numeroFacture: id, tauxTva: 9, lignes: [], seances: [],
  totaux: {montantHt: 0, montantTva: 0, montantTtc: 0, montantRegle: 0, reste: 0},
});
const requete = (partiel: Partial<NgTableRemoteQuery>): NgTableRemoteQuery =>
  ({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 0, size: 20}, ...partiel}) as NgTableRemoteQuery;

describe('versCriteres / bornes / période', () => {
  it('traduit les ids de colonnes de la table en critères du contrat', () => {
    expect(versCriteres(requete({
      sort: {columnId: 'montantTtc', direction: 'desc'} as never,
      filters: {numeroFacture: ' FAC ', patient: 'ben', caisse: 'k1', etat: 'REGLEE', montantTtc: '10..5'},
      page: {index: 3, size: 50},
    }), 20)).toEqual({
      page: 3, size: 50, sort: {champ: 'montantTtc', direction: 'desc'},
      numero: 'FAC', patient: 'ben', caisseId: 'k1', etat: 'REGLEE', montantMin: 5, montantMax: 10,
    });
  });

  it('ignore un tri non triable côté serveur, un état inconnu, borne la taille à 100 et garde la taille courante si 0', () => {
    const c = versCriteres(requete({sort: {columnId: 'periode', direction: 'asc'} as never, filters: {etat: 'X'},
      page: {index: -1, size: 0}}), 50);
    expect(c).toMatchObject({page: 0, size: 50, sort: null, etat: null, montantMin: null, montantMax: null});
    expect(versCriteres(requete({page: {index: 0, size: 500}}), 20).size).toBe(100);
  });

  it('lit une plage de montant à une borne, décimale à virgule ; une saisie illisible est signalée', () => {
    expect(bornesDeMontant('..1500,5')).toEqual({montantMin: null, montantMax: 1500.5, invalide: false});
    expect(bornesDeMontant('abc')).toEqual({montantMin: null, montantMax: null, invalide: true});
    expect(bornesDeMontant('x..20')).toEqual({montantMin: null, montantMax: 20, invalide: true});
  });

  it('lit les montants avec séparateurs de milliers (espaces, insécables, points ou virgules)', () => {
    expect(lireMontant('10 000')).toBe(10000);
    expect(lireMontant('1 000,50')).toBe(1000.5);
    expect(lireMontant('1 000,50')).toBe(1000.5);
    expect(lireMontant('1.000,50')).toBe(1000.5);
    expect(lireMontant('1,000.50')).toBe(1000.5);
    expect(lireMontant('1.000.000')).toBe(1000000);
    expect(lireMontant('  ')).toBeNull();
    expect(lireMontant('12a')).toBeUndefined();
    expect(bornesDeMontant('1 000..10 000,5')).toEqual({montantMin: 1000, montantMax: 10000.5, invalide: false});
  });

  it('mois courant / précédent et validité de la période (366 jours au plus, bornes incluses)', () => {
    expect(moisDe(new Date(2026, 0, 15), -1)).toEqual({du: '2025-12-01', au: '2025-12-31'});
    expect(moisDe(new Date(2026, 1, 3))).toEqual({du: '2026-02-01', au: '2026-02-28'});
    expect(periodeValide({du: '2026-11-30', au: '2026-11-01'})).toBe(false);
    expect(periodeValide({du: '', au: '2026-11-01'})).toBe(false);
    expect(periodeValide({du: '2024-01-01', au: '2024-12-31'})).toBe(true);
    expect(periodeValide({du: '2025-01-01', au: '2026-01-01'})).toBe(true);
    expect(periodeValide({du: '2025-01-01', au: '2026-01-02'})).toBe(false);
  });

  it('caisse et état : la liste multiple de la table devient un choix unique (dernière valeur ajoutée)', () => {
    expect(filtresAChoixUnique({caisse: 'k1,k2', etat: 'REGLEE,NON_REGLEE', patient: 'a,b'},
      {caisse: 'k1', etat: 'NON_REGLEE'}))
      .toEqual({caisse: 'k2', etat: 'REGLEE', patient: 'a,b'});
    expect(filtresAChoixUnique({caisse: 'k1,k2'}, null)).toEqual({caisse: 'k2'});
    expect(versCriteres(requete({filters: {caisse: 'k1,k2', etat: 'REGLEE,NON_REGLEE'}}), 20))
      .toMatchObject({caisseId: null, etat: null});
  });

  it('traduit les refus du serveur : 403, 400 avec code, 400 sans code, 404, autre', () => {
    expect(messageDErreur(new HttpErrorResponse({status: 403}))).toBe('FACTURATION.FACTURES.ERREURS.ACCES_REFUSE');
    expect(messageDErreur(new HttpErrorResponse({status: 400, error: {code: 'PERIODE_INVALIDE'}})))
      .toBe('FACTURATION.FACTURES.ERREURS.PERIODE_INVALIDE');
    expect(messageDErreur(new HttpErrorResponse({status: 400, error: {code: 'AUTRE'}})))
      .toBe('FACTURATION.FACTURES.ERREURS.REQUETE_INVALIDE');
    expect(messageDErreur(new HttpErrorResponse({status: 404}))).toBe('FACTURATION.FACTURES.ERREURS.INTROUVABLE');
    expect(messageDErreur(new HttpErrorResponse({status: 500}))).toBe('FACTURATION.FACTURES.ERREURS.GENERALE');
  });
});

describe('FacturesListStore', () => {
  let listes: Subject<PageDeFactures>[];
  let details: Map<string, Subject<DetailDeFacture>[]>;
  let api: { listFactures: ReturnType<typeof vi.fn>; getFactureDetail: ReturnType<typeof vi.fn> };
  let store: FacturesListStore;

  beforeEach(() => {
    listes = [];
    details = new Map();
    api = {
      listFactures: vi.fn(() => {
        const s = new Subject<PageDeFactures>();
        listes.push(s);
        return s;
      }),
      getFactureDetail: vi.fn((_c: string, id: string) => {
        const s = new Subject<DetailDeFacture>();
        details.set(id, [...(details.get(id) ?? []), s]);
        return s;
      }),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), FacturesListStore, {provide: BackendApiService, useValue: api}],
    });
    store = TestBed.inject(FacturesListStore);
  });

  it('charge la page 0 du mois courant au choix du centre, puis envoie tri, filtres et page de la table', () => {
    store.changerDeCentre(CENTRE);
    expect(api.listFactures).toHaveBeenCalledWith(CENTRE, expect.objectContaining({
      page: 0, size: 20, ...moisDe(new Date())}));
    listes[0].next(page(2));
    expect(store.lignes()).toHaveLength(2);
    expect(store.totaux()?.nombreDeFactures).toBe(2);
    expect(store.chargement()).toBe(false);

    store.appliquer(requete({sort: {columnId: 'patient', direction: 'asc'} as never, filters: {etat: 'NON_REGLEE'},
      page: {index: 2, size: 10}}));
    expect(api.listFactures).toHaveBeenLastCalledWith(CENTRE, expect.objectContaining({
      page: 2, size: 10, sort: {champ: 'patient', direction: 'asc'}, etat: 'NON_REGLEE'}));
  });

  it('deux caisses choisies : seule la dernière est envoyée et les filtres de la table sont ramenés à un choix', () => {
    store.changerDeCentre(CENTRE);
    store.appliquer(requete({filters: {caisse: 'k1'}}));
    store.appliquer(requete({filters: {caisse: 'k1,k2', montantTtc: 'abc..'}}));
    expect(api.listFactures).toHaveBeenLastCalledWith(CENTRE, expect.objectContaining({caisseId: 'k2', montantMin: null}));
    expect(store.filtres()).toEqual({caisse: 'k2', montantTtc: 'abc..'});
    expect(store.montantInvalide()).toBe(true);
  });

  it('une période de plus de 366 jours n\'est pas envoyée', () => {
    store.changerDeCentre(CENTRE);
    store.changerDePeriode({du: '2025-01-01', au: '2026-06-30'});
    expect(api.listFactures).toHaveBeenCalledTimes(1);
  });

  it('ne relance pas une requête identique et annule la précédente quand une nouvelle part', () => {
    store.changerDeCentre(CENTRE);
    store.appliquer(requete({}));
    expect(api.listFactures).toHaveBeenCalledTimes(1);

    store.appliquer(requete({page: {index: 1, size: 20}}));
    expect(listes[0].observed).toBe(false);
    expect(listes[1].observed).toBe(true);
  });

  it('un changement de période repart de la page 0, replie le détail et vide le cache ; une période invalide est ignorée',
    () => {
      store.changerDeCentre(CENTRE);
      store.appliquer(requete({page: {index: 4, size: 20}}));
      store.deplier('f1');
      details.get('f1')![0].next(detail('f1'));

      store.changerDePeriode({du: '2026-10-01', au: '2026-10-31'});
      expect(api.listFactures).toHaveBeenLastCalledWith(CENTRE, expect.objectContaining({
        page: 0, du: '2026-10-01', au: '2026-10-31'}));
      expect(store.factureDepliee()).toBeNull();
      expect(store.details().size).toBe(0);

      const appels = api.listFactures.mock.calls.length;
      store.changerDePeriode({du: '2026-10-31', au: '2026-10-01'});
      expect(api.listFactures).toHaveBeenCalledTimes(appels);
    });

  it('affiche le refus du serveur (403) puis recharge à la demande', () => {
    store.changerDeCentre(CENTRE);
    listes[0].error(new HttpErrorResponse({status: 403}));
    expect(store.erreur()).toBe('FACTURATION.FACTURES.ERREURS.ACCES_REFUSE');
    expect(store.page()).toBeNull();

    store.recharger();
    expect(api.listFactures).toHaveBeenCalledTimes(2);
    expect(store.erreur()).toBeNull();
  });

  it('lit le détail au premier dépli seulement, puis le sert depuis le cache ; une facture dépliée à la fois', () => {
    store.changerDeCentre(CENTRE);
    store.basculer('f1');
    expect(store.clesDepliees()).toEqual(['f1']);
    details.get('f1')![0].next(detail('f1'));
    expect(store.details().get('f1')).toEqual({etat: 'pret', detail: detail('f1')});

    store.basculer('f1');
    expect(store.clesDepliees()).toEqual([]);
    store.basculer('f2');
    expect(store.clesDepliees()).toEqual(['f2']);
    store.basculer('f1');
    expect(store.clesDepliees()).toEqual(['f1']);
    expect(api.getFactureDetail).toHaveBeenCalledTimes(2);
  });

  it('déplier une autre facture annule la lecture en cours, qui sera relue au prochain dépli', () => {
    store.changerDeCentre(CENTRE);
    store.deplier('f1');
    store.deplier('f2');
    expect(details.get('f1')![0].observed).toBe(false);
    expect(store.details().has('f1')).toBe(false);

    store.deplier('f1');
    expect(details.get('f1')).toHaveLength(2);
  });

  it('une erreur de détail (404) est montrée dans la ligne et peut être réessayée', () => {
    store.changerDeCentre(CENTRE);
    store.deplier('f1');
    details.get('f1')![0].error(new HttpErrorResponse({status: 404}));
    expect(store.details().get('f1')).toEqual({etat: 'erreur', message: 'FACTURATION.FACTURES.ERREURS.INTROUVABLE'});

    store.reessayerDetail('f1');
    details.get('f1')![1].next(detail('f1'));
    expect(store.details().get('f1')?.etat).toBe('pret');
  });

  it('sans centre, rien n\'est demandé', () => {
    store.changerDeCentre(null);
    store.deplier('f1');
    expect(api.listFactures).not.toHaveBeenCalled();
    expect(api.getFactureDetail).not.toHaveBeenCalled();
  });
});
