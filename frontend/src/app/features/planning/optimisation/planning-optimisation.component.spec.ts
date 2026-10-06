import {computed, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  IndicateursOptimisation,
  ResultatOptimisation,
  RunOptimisation,
} from '../../../core/api/planning-optimisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {PlanningOptimisationComponent} from './planning-optimisation.component';
import {OptimisationStore} from './optimisation.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const AVANT: IndicateursOptimisation = {
  generateursUtilises: 6, sallesOuvertes: 12, vacationsRequises: 12, placesInfirmierInutilisees: 12, patientsNonPlaces: 1,
  vacationsNonPourvues: 12, infirmiersMobilises: 0, ecartCharge: 0, depassementsHebdo: 0,
};
const APRES: IndicateursOptimisation = {
  ...AVANT, sallesOuvertes: 6, vacationsRequises: 6, placesInfirmierInutilisees: 0, patientsNonPlaces: 0,
  vacationsNonPourvues: 0, infirmiersMobilises: 3,
};

function resultat(): ResultatOptimisation {
  return {
    salles: [{id: 's1', nom: 'Salle A'}, {id: 's2', nom: 'Salle B'}],
    creneaux: [{id: 'c1', libelle: 'Matin', ordre: 1}],
    deplacements: Array.from({length: 12}, (_, i) => ({
      patientId: 'p' + i, nom: 'Patient ' + i,
      de: i === 0 ? null : {salleId: 's2', creneauId: 'c1', generateurId: 'g' + i, generateurCode: 'B-G' + i},
      vers: {salleId: 's1', creneauId: 'c1', generateurId: 'h' + i, generateurCode: 'A-G' + i},
    })),
    nonPlaces: [{patientId: 'px', nom: 'Sans place', cause: 'AUCUNE_PLACE'}],
    vacations: [
      {date: '2026-09-28', jour: 'LUNDI', salleId: 's1', creneauId: 'c1', infirmierId: 'i1', nom: 'Marie', existante: false},
      {date: '2026-09-28', jour: 'LUNDI', salleId: 's1', creneauId: 'c1', infirmierId: 'i2', nom: 'Paul', existante: true},
    ],
    manques: [],
    avant: AVANT,
    apres: APRES,
  };
}

function run(overrides: Partial<RunOptimisation> = {}): RunOptimisation {
  return {
    id: 'r1', centerId: CENTRE, statut: 'TERMINEE',
    parametres: {
      perimetre: 'COMPLET', debutSemaine: '2026-09-27', nbSemaines: 1, dureeMaxSecondes: 20, stabilite: 5,
      objectif: 'EQUITE', maxVacationsParJour: 2, maxVacationsParSemaine: 6,
    },
    creeLe: '2026-10-01T08:00:00Z', termineLe: '2026-10-01T08:00:30Z', lancePar: 'u', phase: null, score: '0hard/0medium/-5soft',
    resume: null, resultat: resultat(), erreur: null, appliqueLe: null, ...overrides,
  };
}

describe('PlanningOptimisationComponent', () => {
  let courant: ReturnType<typeof signal<RunOptimisation | null>>;
  let store: Record<string, unknown> & Record<'lancer' | 'arreter' | 'appliquer' | 'ouvrir' | 'setPagination'
    | 'chargerHistorique' | 'reinitialiser', ReturnType<typeof vi.fn>>;
  let admin: boolean;
  let dialogOpen: ReturnType<typeof vi.fn>;

  async function render() {
    await TestBed.configureTestingModule({
      imports: [PlanningOptimisationComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: OptimisationStore, useValue: store},
        {provide: AuthStore, useValue: {hasRole: (r: string) => admin && r === 'ADMIN'}},
        {provide: MatDialog, useValue: {open: dialogOpen}},
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
    const fixture = TestBed.createComponent(PlanningOptimisationComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  beforeEach(() => {
    admin = true;
    courant = signal<RunOptimisation | null>(run());
    dialogOpen = vi.fn().mockReturnValue({afterClosed: () => of(true)});
    store = {
      courant,
      historique: signal<RunOptimisation[]>([run({resultat: null, appliqueLe: '2026-10-01T09:00:00Z'})]),
      total: signal(1), pageIndex: signal(0), pageSize: signal(10), loading: signal(false), launching: signal(false),
      applying: signal(false), error: signal<string | null>(null), successMessage: signal<string | null>(null),
      enCours: computed(() => courant()?.statut === 'EN_COURS'),
      appliquable: computed(() => courant()?.statut === 'TERMINEE' && !!courant()?.resultat && !courant()?.appliqueLe),
      lancer: vi.fn().mockResolvedValue(true), arreter: vi.fn().mockResolvedValue(undefined),
      appliquer: vi.fn().mockResolvedValue(true), ouvrir: vi.fn().mockResolvedValue(undefined),
      setPagination: vi.fn(), chargerHistorique: vi.fn(), reinitialiser: vi.fn(),
    } as typeof store;
  });

  it('charge l\'historique du centre actif au démarrage', async () => {
    await render();

    expect(store.reinitialiser).toHaveBeenCalled();
    expect(store.chargerHistorique).toHaveBeenCalledWith({page: 0, size: 10});
  });

  it('compare les indicateurs avant et après avec la tendance de chacun', async () => {
    const {root} = await render();

    const lignes = Array.from(root.querySelectorAll('[data-testid="opt-indicateurs"] tbody tr'));
    expect(lignes).toHaveLength(9);
    const vacations = lignes.find((l) => l.textContent?.includes('vacationsRequises'))!;
    expect(vacations.textContent).toContain('12');
    expect(vacations.textContent).toContain('-6');
    expect(vacations.querySelector('.opt-ecart.mieux')).not.toBeNull();
    const mobilises = lignes.find((l) => l.textContent?.includes('infirmiersMobilises'))!;
    expect(mobilises.querySelector('.opt-ecart.neutre')).not.toBeNull();
  });

  it('pagine la liste des déplacements et nomme les salles et créneaux', async () => {
    const {root} = await render();

    const lignes = root.querySelectorAll('[data-testid="opt-deplacements"] tbody tr');
    expect(lignes).toHaveLength(10); // 12 déplacements, pages de 10
    expect(lignes[1].textContent).toContain('Salle B · Matin · B-G1');
    expect(lignes[1].textContent).toContain('Salle A · Matin · A-G1');
    expect(lignes[0].textContent).toContain('—'); // patient jusqu'ici non placé
    expect(root.querySelectorAll('mat-paginator').length).toBeGreaterThanOrEqual(2);
  });

  it('lance l\'optimisation avec les paramètres saisis', async () => {
    const {fixture, root} = await render();
    const composant = fixture.componentInstance as unknown as {
      formModel: { update: (f: (m: Record<string, unknown>) => Record<string, unknown>) => void }
    };
    composant.formModel.update((m) => ({...m, perimetre: 'ROULEMENT', debut: '2026-09-30', duree: 5, stabilite: 8}));
    fixture.detectChanges();
    await fixture.whenStable();

    (root.querySelector('[data-testid="opt-lancer"]') as HTMLButtonElement).click();

    expect(store.lancer).toHaveBeenCalledWith({
      perimetre: 'ROULEMENT', debutSemaine: '2026-09-30', nbSemaines: 1, dureeMaxSecondes: 5, stabilite: 8,
      objectif: 'EQUITE', maxVacationsParJour: 2, maxVacationsParSemaine: 6,
    });
  });

  it('ne propose le nombre de semaines que pour la couverture et n\'envoie 1 semaine sinon', async () => {
    const {fixture, root} = await render();
    const composant = fixture.componentInstance as unknown as {
      formModel: { update: (f: (m: Record<string, unknown>) => Record<string, unknown>) => void }
    };
    expect(root.querySelector('[data-testid="opt-semaines"]')).toBeNull();

    composant.formModel.update((m) => ({...m, perimetre: 'COUVERTURE', nbSemaines: 3}));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="opt-semaines"]')).not.toBeNull();
    (root.querySelector('[data-testid="opt-lancer"]') as HTMLButtonElement).click();
    expect(store.lancer).toHaveBeenLastCalledWith(expect.objectContaining({perimetre: 'COUVERTURE', nbSemaines: 3}));

    composant.formModel.update((m) => ({...m, perimetre: 'PATIENTS', nbSemaines: 3}));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="opt-semaines"]')).toBeNull();
    (root.querySelector('[data-testid="opt-lancer"]') as HTMLButtonElement).click();
    expect(store.lancer).toHaveBeenLastCalledWith(expect.objectContaining({perimetre: 'PATIENTS', nbSemaines: 1}));
  });

  it('interdit de relancer pendant un calcul et permet de l\'arrêter', async () => {
    courant.set(run({statut: 'EN_COURS', resultat: null, phase: 'PATIENTS', score: '0hard/0medium/-100soft'}));
    const {root} = await render();

    expect((root.querySelector('[data-testid="opt-lancer"]') as HTMLButtonElement).disabled).toBe(true);
    expect(root.querySelector('[data-testid="opt-score"]')?.textContent).toContain('-100soft');
    (root.querySelector('[data-testid="opt-arreter"]') as HTMLButtonElement).click();
    expect(store.arreter).toHaveBeenCalled();
  });

  it('demande confirmation puis applique la proposition (administrateur)', async () => {
    const {root} = await render();

    (root.querySelector('[data-testid="opt-appliquer"]') as HTMLButtonElement).click();

    expect(dialogOpen).toHaveBeenCalled();
    expect(store.appliquer).toHaveBeenCalled();
  });

  it('n\'applique pas quand la confirmation est refusée', async () => {
    dialogOpen.mockReturnValue({afterClosed: () => of(false)});
    const {root} = await render();

    (root.querySelector('[data-testid="opt-appliquer"]') as HTMLButtonElement).click();

    expect(store.appliquer).not.toHaveBeenCalled();
  });

  it('réserve l\'application à l\'administrateur', async () => {
    admin = false;
    const {root} = await render();

    expect(root.querySelector('[data-testid="opt-appliquer"]')).toBeNull();
    expect(root.textContent).toContain('PLANNING.OPTIM.ADMIN_SEUL');
  });

  it('désactive l\'application d\'une proposition déjà appliquée', async () => {
    courant.set(run({appliqueLe: '2026-10-01T09:00:00Z'}));
    const {root} = await render();

    expect((root.querySelector('[data-testid="opt-appliquer"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('rouvre une exécution depuis l\'historique', async () => {
    const {root} = await render();

    (root.querySelector('[data-testid="opt-historique"] [data-testid="opt-ouvrir"]') as HTMLButtonElement).click();

    expect(store.ouvrir).toHaveBeenCalledWith('r1');
  });

  it('affiche l\'erreur d\'un calcul en échec', async () => {
    courant.set(run({statut: 'ECHEC', resultat: null, erreur: 'boom'}));
    const {root} = await render();

    expect(root.querySelector('[data-testid="opt-echec"]')?.textContent).toContain('boom');
    expect(root.querySelector('[data-testid="opt-resultat"]')).toBeNull();
  });

  it('affiche les messages d\'erreur et de succès du store', async () => {
    (store['error'] as ReturnType<typeof signal<string | null>>).set('PLANNING.OPTIM.ERR.OPTIMISATION_PERIMEE');
    (store['successMessage'] as ReturnType<typeof signal<string | null>>).set('PLANNING.OPTIM.OK.APPLIQUEE');
    const {root} = await render();

    expect(root.querySelector('[data-testid="optimisation-error"]')?.textContent)
      .toContain('PLANNING.OPTIM.ERR.OPTIMISATION_PERIMEE');
    expect(root.querySelector('[data-testid="optimisation-ok"]')?.textContent).toContain('PLANNING.OPTIM.OK.APPLIQUEE');
  });
});
