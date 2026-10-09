import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {ComptabiliteApiService, JournalItem, MappingComptableItem} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ComptabiliteParametrageStore, parametrageErrorKey} from './comptabilite-parametrage.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';

const journaux: JournalItem[] = [
  {code: 'VE', libelle: 'Ventes', actif: true},
  {code: 'BQ', libelle: 'Banque', actif: true},
  {code: 'OD', libelle: 'Opérations diverses', actif: false},
];

const mapping: MappingComptableItem = {
  centerId: CENTRE, compteVentes: '706', compteClientPatient: '411100', compteClientDefaut: '411500',
  compteBanque: '512', compteCaisse: '530', compteTVACollectee: '44571', compteStock: '322', compteConsommation: '602',
  compteFacturesNonParvenues: '408', compteBoniInventaire: '757', compteMaliInventaire: '657',
  journaux: {
    VENTE: 'VE', REGLEMENT_BANQUE: 'BQ', REGLEMENT_CAISSE: 'CA', STOCK_RECEPTION: 'AC', STOCK_SORTIE: 'ST',
    STOCK_INVENTAIRE: 'ST',
  },
};

function erreurMetier(code: string): HttpErrorResponse {
  return new HttpErrorResponse({status: 422, error: {code}});
}

describe('ComptabiliteParametrageStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      getMapping: vi.fn().mockReturnValue(of(mapping)),
      getJournaux: vi.fn().mockImplementation((_centre: string, page: number, size: number) =>
        of({items: journaux, total: journaux.length, page, size})),
      saveJournal: vi.fn().mockReturnValue(of(journaux[2])),
      deleteJournal: vi.fn().mockReturnValue(of(undefined)),
      saveMapping: vi.fn().mockImplementation((m: MappingComptableItem) => of(m)),
      synchroniserStock: vi.fn().mockReturnValue(
        of({receptions: 1, joursSorties: 2, inventaires: 0, complements: 0, ignorees: 1})),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: ComptabiliteApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge le paramétrage et les journaux du centre actif, page par page', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);

    store.load();

    expect(api['getMapping']).toHaveBeenCalledWith(CENTRE);
    expect(api['getJournaux']).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(api['getJournaux']).toHaveBeenCalledWith(CENTRE, 0, 100);
    expect(store.mapping()).toEqual(mapping);
    expect(store.rows()).toHaveLength(3);
    expect(store.total()).toBe(3);
    expect(store.journauxActifs().map((j) => j.code)).toEqual(['VE', 'BQ']);
    expect(store.loading()).toBe(false);
  });

  it('ne lit jamais le paramétrage d\'un autre centre que le centre actif', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);

    store.load();
    store.saveJournal({code: 'OD', libelle: 'Opérations diverses', actif: true});

    expect(api['getMapping']).toHaveBeenCalledWith(AUTRE_CENTRE);
    expect(api['saveJournal']).toHaveBeenCalledWith(AUTRE_CENTRE, expect.objectContaining({code: 'OD'}));
    expect(api['getMapping']).not.toHaveBeenCalledWith(CENTRE);
  });

  it('change de page de journaux et recharge la page demandée', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);

    store.setJournauxPagination({pageIndex: 2, pageSize: 20});

    expect(store.pageIndex()).toBe(2);
    expect(store.pageSize()).toBe(20);
    expect(api['getJournaux']).toHaveBeenCalledWith(CENTRE, 2, 20);
  });

  it('enregistre puis supprime un journal, recharge la liste et signale le succès', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);

    store.saveJournal({code: 'OD', libelle: 'Opérations diverses', actif: true});
    expect(api['saveJournal']).toHaveBeenCalledWith(CENTRE, {code: 'OD', libelle: 'Opérations diverses', actif: true});
    expect(store.successMessage()).toBe('COMPTABILITE.PARAMETRAGE.JOURNAL_ENREGISTRE');
    expect(store.rows()).toHaveLength(3);

    store.deleteJournal('OD');
    expect(api['deleteJournal']).toHaveBeenCalledWith(CENTRE, 'OD');
    expect(store.successMessage()).toBe('COMPTABILITE.PARAMETRAGE.JOURNAL_SUPPRIME');
    expect(store.saving()).toBe(false);
  });

  it('traduit le refus du serveur quand le journal est utilisé par une opération', () => {
    api['deleteJournal'].mockReturnValue(throwError(() => erreurMetier('JOURNAL_UTILISE')));
    const store = TestBed.inject(ComptabiliteParametrageStore);

    store.deleteJournal('VE');

    expect(store.error()).toBe('COMPTABILITE.PARAMETRAGE.ERR.JOURNAL_UTILISE');
    expect(store.successMessage()).toBeNull();
    expect(store.saving()).toBe(false);
  });

  it('enregistre les comptes et les journaux pour le centre actif', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);
    const {centerId: _centre, ...saisie} = {...mapping, compteStock: '32'};

    store.saveMapping(saisie);

    expect(api['saveMapping']).toHaveBeenCalledWith({...saisie, centerId: CENTRE});
    expect(store.mapping()?.compteStock).toBe('32');
    expect(store.successMessage()).toBe('COMPTABILITE.PARAMETRAGE.COMPTES_ENREGISTRES');
  });

  it('comptabilise le stock sur la période et expose le résultat', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);

    store.synchroniserStock({from: '2026-09-01', to: '2026-09-30'});

    expect(api['synchroniserStock']).toHaveBeenCalledWith(CENTRE, '2026-09-01', '2026-09-30');
    expect(store.synchronisation()).toEqual({
      receptions: 1,
      joursSorties: 2,
      inventaires: 0,
      complements: 0,
      ignorees: 1
    });
  });

  it('signale l\'échec de la comptabilisation du stock sans garder un ancien résultat', () => {
    const store = TestBed.inject(ComptabiliteParametrageStore);
    store.synchroniserStock({from: '2026-09-01', to: '2026-09-30'});
    api['synchroniserStock'].mockReturnValue(throwError(() => new HttpErrorResponse({status: 500})));

    store.synchroniserStock({from: '2026-09-01', to: '2026-09-30'});

    expect(store.synchronisation()).toBeNull();
    expect(store.error()).toBe('COMPTABILITE.PARAMETRAGE.ERR.SYNCHRONISATION');
  });

  it('ne traduit que les codes d\'erreur connus', () => {
    expect(parametrageErrorKey(erreurMetier('JOURNAL_INCONNU'))).toBe('COMPTABILITE.PARAMETRAGE.ERR.JOURNAL_INCONNU');
    expect(parametrageErrorKey(erreurMetier('AUTRE'))).toBe('COMPTABILITE.PARAMETRAGE.ERR.SAVE');
    expect(parametrageErrorKey(new Error('réseau'))).toBe('COMPTABILITE.PARAMETRAGE.ERR.SAVE');
  });
});
