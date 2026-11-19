import {createEnvironmentInjector, EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {FacturationClotureComponent} from './facturation-cloture.component';
import {FacturationStore} from '../state/facturation.store';
import {AppShellStore} from '../../../core/state/app-shell.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('FacturationClotureComponent (Paramétrage › Clôtures mensuelles)', () => {
  const storeMock = {
    successMessage: vi.fn((): string | null => null),
    anneePeriodes: vi.fn(() => 2026),
    anneesChargees: vi.fn((): number[] => []),
    loadPeriodes: vi.fn(),
    cloturerMois: vi.fn(),
    rouvrirMois: vi.fn(),
    setAnneePeriodes: vi.fn(),
    clearError: vi.fn(),
    clearSuccess: vi.fn(),
  };
  const appShellMock = {currentCenterId: vi.fn((): string | null => CENTER_ID)};

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        {provide: FacturationStore, useValue: storeMock},
        {provide: AppShellStore, useValue: appShellMock},
      ],
    });
  });

  const creer = (): FacturationClotureComponent =>
    TestBed.runInInjectionContext(() => new FacturationClotureComponent());

  it('charge les mois de l\'année affichée pour le centre de la session, une seule fois par année', () => {
    creer();
    TestBed.tick();
    expect(storeMock.loadPeriodes).toHaveBeenCalledWith({centerId: CENTER_ID, annee: 2026});

    storeMock.loadPeriodes.mockClear();
    storeMock.anneesChargees.mockReturnValue([2026]);
    creer();
    TestBed.tick();
    expect(storeMock.loadPeriodes).not.toHaveBeenCalled();
    storeMock.anneesChargees.mockReturnValue([]);
  });

  it('clôture un mois seulement après confirmation', () => {
    const component = creer();

    component['onConfirmerCloture']();
    expect(storeMock.cloturerMois).not.toHaveBeenCalled();

    component['onDemanderCloture']('2026-07');
    expect(storeMock.cloturerMois).not.toHaveBeenCalled();
    component['onConfirmerCloture']();

    expect(storeMock.cloturerMois).toHaveBeenCalledWith({centerId: CENTER_ID, mois: '2026-07'});
    expect(component['clotureAConfirmer']()).toBeNull();
  });

  it('rouvre un mois avec un motif obligatoire, nettoyé et borné à 500 caractères', () => {
    const component = creer();

    component['onOuvrirReouverture']('2026-08');
    expect(component['reouvertureValide']()).toBe(false);
    component['onMotifReouverture']('   ');
    expect(component['reouvertureValide']()).toBe(false);
    component['onConfirmerReouverture']();
    expect(storeMock.rouvrirMois).not.toHaveBeenCalled();

    component['onMotifReouverture']('x'.repeat(501));
    expect(component['reouvertureValide']()).toBe(false);

    component['onMotifReouverture']('  séances oubliées  ');
    expect(component['reouvertureValide']()).toBe(true);
    component['onConfirmerReouverture']();

    expect(storeMock.rouvrirMois).toHaveBeenCalledWith({centerId: CENTER_ID, mois: '2026-08', motif: 'séances oubliées'});
    expect(component['reouvertureDraft']()).toBeNull();
  });

  it('change d\'année en abandonnant une confirmation en cours', () => {
    const component = creer();
    component['onDemanderCloture']('2026-07');

    component['onAnneePeriodes'](2025);

    expect(storeMock.setAnneePeriodes).toHaveBeenCalledWith(2025);
    expect(component['clotureAConfirmer']()).toBeNull();
  });

  it('n\'affiche que les messages de clôture et de réouverture, pas ceux de l\'espace de calcul', () => {
    storeMock.successMessage.mockReturnValue('FACTURATION.SUCCESS.MOIS_CLOTURE');
    expect(creer()['succesDeCloture']()).toBe('FACTURATION.SUCCESS.MOIS_CLOTURE');

    storeMock.successMessage.mockReturnValue('FACTURATION.SUCCESS.SEANCE_EXCLUDED');
    expect(creer()['succesDeCloture']()).toBeNull();
    storeMock.successMessage.mockReturnValue(null);
  });

  it('n\'hérite pas des messages de l\'espace de facturation et ne lui laisse pas les siens', () => {
    const portee = createEnvironmentInjector([], TestBed.inject(EnvironmentInjector));
    runInInjectionContext(portee, () => new FacturationClotureComponent());
    expect(storeMock.clearError).toHaveBeenCalledTimes(1);
    expect(storeMock.clearSuccess).toHaveBeenCalledTimes(1);

    storeMock.successMessage.mockReturnValue('FACTURATION.SUCCESS.MOIS_ROUVERT');
    portee.destroy();
    expect(storeMock.clearError).toHaveBeenCalledTimes(2);
    expect(storeMock.clearSuccess).toHaveBeenCalledTimes(2);
    storeMock.successMessage.mockReturnValue(null);
  });
});
