import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  LignePreferencePatient,
  LigneProfilInfirmier,
  ReglagesOptimisation,
} from '../../../core/api/planning-preferences-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {createPagedListState} from '../../../core/state/paged-list-state.util';
import {PositionsStore} from '../../../core/state/referentials.store';
import {PlanningPreferencesComponent} from './planning-preferences.component';
import {PreferencesPlanningStore} from './preferences-planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

describe('PlanningPreferencesComponent', () => {
  let admin: boolean;
  let store: ReturnType<typeof creerStore>;

  function creerStore() {
    const patients: LignePreferencePatient[] = [{
      patientId: 'p1', nom: 'Alpha', jours: ['LUNDI', 'JEUDI'], creneauActuelId: 'c1',
      preference: {patientId: 'p1', creneauPrefereId: 'c2', seancesParSemaine: null, joursAChoisir: false},
    }];
    const infirmiers: LigneProfilInfirmier[] = [{
      infirmierId: 'i1', nom: 'Marie', qualification: 'INFIRMIER',
      profil: {infirmierId: 'i1', tauxActivite: 50, competences: ['PEDIATRIE']},
    }];
    return {
      patients: signal(createPagedListState({rows: patients, total: 1})),
      infirmiers: signal(createPagedListState({rows: infirmiers, total: 1})),
      reglages: signal<ReglagesOptimisation | null>({
        replanificationAuto: true, heuresParVacation: 6, heuresHebdoTempsPlein: 35, reposHebdoMin: 2,
      }),
      saving: signal(false), error: signal<string | null>(null), successMessage: signal<string | null>(null),
      charger: vi.fn(), reinitialiser: vi.fn(), setPatientsPagination: vi.fn().mockResolvedValue(undefined),
      setInfirmiersPagination: vi.fn().mockResolvedValue(undefined),
      enregistrerPreference: vi.fn().mockResolvedValue(true), enregistrerProfil: vi.fn().mockResolvedValue(true),
      enregistrerReglages: vi.fn().mockResolvedValue(true),
    };
  }

  async function render() {
    await TestBed.configureTestingModule({
      imports: [PlanningPreferencesComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: PreferencesPlanningStore, useValue: store},
        {provide: AuthStore, useValue: {hasRole: (r: string) => admin && r === 'ADMIN'}},
        {
          provide: PositionsStore, useValue: {
            items: signal([{id: 'c1', nom: 'Matin', label: 'Matin'}, {id: 'c2', nom: 'Soir', label: 'Soir'}]),
            ensureLoaded: vi.fn(),
          },
        },
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
    const fixture = TestBed.createComponent(PlanningPreferencesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  async function stable(fixture: { detectChanges: () => void; whenStable: () => Promise<unknown> }) {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    admin = true;
    store = creerStore();
  });

  it('charge les données du centre actif et affiche le créneau préféré par son nom', async () => {
    const {root} = await render();

    expect(store.reinitialiser).toHaveBeenCalled();
    expect(store.charger).toHaveBeenCalled();
    const ligne = root.querySelector('[data-testid="prefs-patients"] tbody tr');
    expect(ligne?.textContent).toContain('Alpha');
    expect(ligne?.textContent).toContain('Soir');
    expect(root.querySelectorAll('mat-paginator').length).toBeGreaterThanOrEqual(1);
  });

  it('modifie la préférence d\'un patient : jours à choisir avec leur nombre de séances', async () => {
    const {fixture, root} = await render();
    (root.querySelector('[data-testid="prefs-patient-modifier"]') as HTMLButtonElement).click();
    await stable(fixture);
    const composant = fixture.componentInstance as unknown as {
      preferenceModel: { update: (f: (m: Record<string, unknown>) => Record<string, unknown>) => void }
    };
    composant.preferenceModel.update((m) => ({...m, joursAChoisir: true, seancesParSemaine: 3}));
    await stable(fixture);

    expect(root.querySelector('[data-testid="prefs-seances"]')).not.toBeNull();
    (root.querySelector('[data-testid="prefs-patient-enregistrer"]') as HTMLButtonElement).click();
    await stable(fixture);

    expect(store.enregistrerPreference).toHaveBeenCalledWith('p1',
      {creneauPrefereId: 'c2', joursAChoisir: true, seancesParSemaine: 3});
    expect(root.querySelector('[data-testid="prefs-patient-form"]')).toBeNull();
  });

  it('refuse un nombre de séances hors bornes', async () => {
    const {fixture, root} = await render();
    (root.querySelector('[data-testid="prefs-patient-modifier"]') as HTMLButtonElement).click();
    await stable(fixture);
    const composant = fixture.componentInstance as unknown as {
      preferenceModel: { update: (f: (m: Record<string, unknown>) => Record<string, unknown>) => void }
    };
    composant.preferenceModel.update((m) => ({...m, joursAChoisir: true, seancesParSemaine: 9}));
    await stable(fixture);

    expect((root.querySelector('[data-testid="prefs-patient-enregistrer"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('enregistre le profil d\'un infirmier', async () => {
    const {fixture} = await render();
    const composant = fixture.componentInstance as unknown as {
      modifierInfirmier: (l: LigneProfilInfirmier) => void;
      enregistrerProfil: () => Promise<void>;
      profilModel: { update: (f: (m: Record<string, unknown>) => Record<string, unknown>) => void };
    };
    composant.modifierInfirmier(store.infirmiers().rows[0]);
    composant.profilModel.update((m) => ({...m, tauxActivite: 80, competences: ['PEDIATRIE', 'CATHETER']}));
    await composant.enregistrerProfil();

    expect(store.enregistrerProfil).toHaveBeenCalledWith('i1', {tauxActivite: 80, competences: ['PEDIATRIE', 'CATHETER']});
  });

  it('pré-remplit les réglages et ne laisse que l\'administrateur les enregistrer', async () => {
    const {fixture} = await render();
    const composant = fixture.componentInstance as unknown as {
      reglagesModel: () => ReglagesOptimisation;
      enregistrerReglages: () => Promise<void>;
    };
    expect(composant.reglagesModel()).toMatchObject({heuresParVacation: 6, reposHebdoMin: 2});

    await composant.enregistrerReglages();
    expect(store.enregistrerReglages).toHaveBeenCalledWith(
      {replanificationAuto: true, heuresParVacation: 6, heuresHebdoTempsPlein: 35, reposHebdoMin: 2});
  });

  it('n\'enregistre pas les réglages pour un non-administrateur', async () => {
    admin = false;
    const {fixture} = await render();
    const composant = fixture.componentInstance as unknown as { enregistrerReglages: () => Promise<void> };

    await composant.enregistrerReglages();

    expect(store.enregistrerReglages).not.toHaveBeenCalled();
  });

  it('pagine la liste des patients côté serveur', async () => {
    const {fixture} = await render();
    const composant = fixture.componentInstance as unknown as {
      onPagePatients: (e: { pageIndex: number; pageSize: number; length: number }) => void
    };

    composant.onPagePatients({pageIndex: 1, pageSize: 20, length: 40});

    expect(store.setPatientsPagination).toHaveBeenCalledWith(1, 20);
  });
});
