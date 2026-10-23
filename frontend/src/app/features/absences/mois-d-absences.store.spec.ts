import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsencePatientApiService, MoisDAbsences} from '../../core/api/absence-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {MoisDAbsencesStore, moisDAbsencesErrorKey} from './mois-d-absences.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function mois(m: string, statut: 'OUVERT' | 'CLOTURE', extra: Partial<MoisDAbsences> = {}): MoisDAbsences {
  return {
    mois: m,
    statut,
    cloturePar: null,
    clotureLe: null,
    rouvertPar: null,
    rouvertLe: null,
    motifDeReouverture: null, ...extra
  };
}

const refus = (status: number, code: string) => new HttpErrorResponse({status, error: {code}});

describe('MoisDAbsencesStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      moisDeLAnnee: vi.fn().mockReturnValue(of({
        centerId: CENTRE,
        annee: 2026,
        mois: [mois('2026-09', 'CLOTURE', {cloturePar: 'u'})]
      })),
      cloturerMois: vi.fn().mockReturnValue(of({
        centerId: CENTRE, mois: '2026-08', statut: 'CLOTURE', cloturePar: 'u', clotureLe: 't',
        absencesAQualifierRestantes: 3, absencesEnRetard: 2,
      })),
      rouvrirMois: vi.fn().mockReturnValue(of({centerId: CENTRE, mois: '2026-09', statut: 'OUVERT'})),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: AbsencePatientApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge les mois d\'une année et sait si le mois d\'une date est clos', () => {
    const store = TestBed.inject(MoisDAbsencesStore);

    store.charger({annee: 2026});

    expect(api['moisDeLAnnee']).toHaveBeenCalledWith(CENTRE, 2026);
    expect(store.moisDeLaDateEstClos('2026-09-28')).toBe(true);
    expect(store.moisDeLaDateEstClos('2026-10-01')).toBe(false);
    expect(store.moisDeLaDateEstClos('2025-01-01')).toBe(false);
  });

  it('une lecture en échec ne bloque pas l\'écran : aucun mois n\'est tenu pour clos', () => {
    api['moisDeLAnnee'].mockReturnValueOnce(throwError(() => refus(500, 'X')));
    const store = TestBed.inject(MoisDAbsencesStore);

    store.charger({annee: 2026});

    expect(store.loading()).toBe(false);
    expect(store.moisDeLaDateEstClos('2026-09-28')).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('clôture un mois : retient l\'avertissement chiffré et recharge l\'année', () => {
    const store = TestBed.inject(MoisDAbsencesStore);

    store.cloturer({mois: '2026-08'});

    expect(api['cloturerMois']).toHaveBeenCalledWith(CENTRE, '2026-08');
    expect(store.avertissement()).toEqual({mois: '2026-08', aQualifier: 3, enRetard: 2});
    expect(store.successMessage()).toBe('ABSENCES.MOIS.SUCCES_CLOTURE');
    expect(store.actionMois()).toBeNull();
    expect(api['moisDeLAnnee']).toHaveBeenCalledWith(CENTRE, 2026);
  });

  it('garde la clôture affichée même si le rechargement de l\'année échoue', () => {
    api['moisDeLAnnee'].mockReturnValue(throwError(() => refus(500, 'X')));
    const store = TestBed.inject(MoisDAbsencesStore);

    store.cloturer({mois: '2026-08'});

    expect(store.moisDeLaDateEstClos('2026-08-15')).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('traduit le refus d\'un mois non terminé et d\'une double clôture', () => {
    const store = TestBed.inject(MoisDAbsencesStore);
    api['cloturerMois'].mockReturnValueOnce(throwError(() => refus(422, 'MOIS_D_ABSENCES_NON_TERMINE')));

    store.cloturer({mois: '2026-10'});

    expect(store.error()).toBe('ABSENCES.ERR.MOIS_D_ABSENCES_NON_TERMINE');
    expect(store.actionMois()).toBeNull();
    expect(moisDAbsencesErrorKey(refus(409, 'MOIS_D_ABSENCES_DEJA_CLOS'))).toBe('ABSENCES.ERR.MOIS_D_ABSENCES_DEJA_CLOS');
    expect(moisDAbsencesErrorKey(refus(500, 'AUTRE'))).toBe('ABSENCES.MOIS.ERR_GENERIQUE');
  });

  it('rouvre un mois avec son motif et le rend ouvert', () => {
    const store = TestBed.inject(MoisDAbsencesStore);
    store.charger({annee: 2026});
    api['moisDeLAnnee'].mockReturnValue(of({
      centerId: CENTRE, annee: 2026,
      mois: [mois('2026-09', 'OUVERT', {rouvertPar: 'u', motifDeReouverture: 'oubli'})]
    }));

    store.rouvrir({mois: '2026-09', motif: 'oubli'});

    expect(api['rouvrirMois']).toHaveBeenCalledWith(CENTRE, '2026-09', 'oubli');
    expect(store.moisDeLaDateEstClos('2026-09-28')).toBe(false);
    expect(store.successMessage()).toBe('ABSENCES.MOIS.SUCCES_REOUVERTURE');
  });

  it('traduit le refus d\'un motif manquant', () => {
    const store = TestBed.inject(MoisDAbsencesStore);
    api['rouvrirMois'].mockReturnValueOnce(throwError(() => refus(400, 'MOTIF_DE_REOUVERTURE_OBLIGATOIRE')));

    store.rouvrir({mois: '2026-09', motif: ''});

    expect(store.error()).toBe('ABSENCES.ERR.MOTIF_DE_REOUVERTURE_OBLIGATOIRE');
  });
});
