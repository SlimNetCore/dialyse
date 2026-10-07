import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsenceSemaine, SeanceRealisee} from '../../core/api/absence-patient-api.service';
import {MonPlanning} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {WebSocketService, WsEvent} from '../../core/ws/websocket.service';
import {aujourdhuiUtc} from './presence.util';
import {MonPlanningComponent} from './mon-planning.component';
import {MonPlanningStore} from './mon-planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function planningFixture(): MonPlanning {
  const aujourdhui = aujourdhuiUtc();
  return {
    infirmier: {nom: 'Amrani', prenom: 'Sara', qualification: 'INFIRMIER'} as MonPlanning['infirmier'],
    debut: aujourdhui, fin: aujourdhui,
    salles: [{id: 's1', nom: 'Salle 1'}, {id: 's2', nom: 'Salle 2'}] as MonPlanning['salles'],
    creneaux: [{id: 'm', libelle: 'Matin'}] as MonPlanning['creneaux'],
    jours: [
      {jour: 'LUNDI', date: aujourdhui, ouvertHebdomadaire: true, fermetureMotif: null},
      {jour: 'MARDI', date: '2099-01-02', ouvertHebdomadaire: true, fermetureMotif: 'Férié'},
    ],
    mesCreneaux: [{date: aujourdhui, jour: 'LUNDI', salleId: 's1', creneauId: 'm', situation: 'PREVU'}],
    mesCases: [{
      date: aujourdhui, jour: 'LUNDI', salleId: 's1', creneauId: 'm', patients: 6, requis: 2, salleIsolement: false,
      collegues: 1,
      occupants: [
        {patientId: 'p1', nom: 'Amine Benali', generateurCode: 'G201', aRisque: false, libereLe: null},
        {patientId: 'p2', nom: 'Houria Bekkouche', generateurCode: null, aRisque: true, libereLe: '2026-10-20'},
      ],
    }],
  };
}

describe('MonPlanningComponent — grille de mes salles et créneaux', () => {
  let planning: ReturnType<typeof signal<MonPlanning | null>>;
  let absencesPatients: ReturnType<typeof signal<AbsenceSemaine[]>>;
  let seancesRealisees: ReturnType<typeof signal<SeanceRealisee[]>>;
  let dernierEvenement: ReturnType<typeof signal<WsEvent | null>>;
  let chargerPlanning: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    planning = signal<MonPlanning | null>(planningFixture());
    absencesPatients = signal<AbsenceSemaine[]>([]);
    seancesRealisees = signal<SeanceRealisee[]>([]);
    dernierEvenement = signal<WsEvent | null>(null);
    chargerPlanning = vi.fn();
    const store = {
      planning, absencesPatients, seancesRealisees, nonLie: signal(false), loadingPlanning: signal(false),
      saving: signal(false), error: signal(null),
      successMessage: signal(null), rows: signal([]), total: signal(0), pageIndex: signal(0), pageSize: signal(10),
      loading: signal(false), chargerPlanning, loadAbsences: vi.fn(), changerSemaine: vi.fn(),
      setPagination: vi.fn(), declarer: vi.fn(), annuler: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [MonPlanningComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), provideRouter([]), {provide: MonPlanningStore, useValue: store},
        {provide: WebSocketService, useValue: {lastEvent: dernierEvenement}}],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  async function render() {
    const fixture = TestBed.createComponent(MonPlanningComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('affiche une grille semaine avec seulement mes salles et créneaux', async () => {
    const {root} = await render();

    const rows = root.querySelectorAll('[data-testid="moi-table"] tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Salle 1');
    expect(rows[0].textContent).toContain('Matin');
    expect(root.textContent).not.toContain('Salle 2');
  });

  it('indique ma situation, la charge de la salle et mes collègues dans la case du jour', async () => {
    const {root} = await render();

    const cells = Array.from(root.querySelectorAll('[data-testid="moi-cell"]'));
    const mine = cells.find((c) => c.querySelector('[data-testid="moi-cell-situation"]'))!;
    expect(mine.classList.contains('prevu')).toBe(true);
    expect(mine.textContent).toContain('INFIRMIER.MOI.SITUATION.PREVU');
    expect(mine.textContent).toContain('INFIRMIER.MOI.GRILLE.PATIENTS');
    expect(mine.textContent).toContain('INFIRMIER.MOI.GRILLE.COLLEGUES');
  });

  it('détaille les patients de ma case : nom, générateur, risque infectieux et libération de la place', async () => {
    const {root} = await render();

    const patients = Array.from(root.querySelectorAll('[data-testid="moi-patient"]'));
    expect(patients).toHaveLength(2);
    expect(patients[0].textContent).toContain('Amine Benali');
    expect(patients[0].textContent).toContain('G201');
    expect(patients[0].classList.contains('risk')).toBe(false);
    expect(patients[1].textContent).toContain('Houria Bekkouche');
    expect(patients[1].classList.contains('risk')).toBe(true);
    expect(patients[1].querySelectorAll('mat-icon')).toHaveLength(2);
  });

  it('barre le patient absent et signale l\'absence du jour même, selon son statut', async () => {
    absencesPatients.set([{
      absenceId: 'a1',
      patientId: 'p1',
      dateSeance: aujourdhuiUtc(),
      statut: 'JUSTIFIEE',
      motif: 'MALADIE'
    }]);
    const {root} = await render();

    const patients = Array.from(root.querySelectorAll('[data-testid="moi-patient"]'));
    expect(patients[0].classList.contains('absent')).toBe(true);
    expect(patients[0].classList.contains('justifiee')).toBe(true);
    expect(patients[0].querySelector('[data-testid="moi-absent"]')).not.toBeNull();
    expect(patients[1].classList.contains('absent')).toBe(false);
    expect(patients[1].querySelector('[data-testid="moi-absent"]')).toBeNull();
  });

  it('marque d\'un soleil le patient dont la séance est validée, sans le barrer', async () => {
    seancesRealisees.set([{patientId: 'p2', dateSeance: aujourdhuiUtc()}]);
    const {root} = await render();

    const patients = Array.from(root.querySelectorAll('[data-testid="moi-patient"]'));
    expect(patients[1].classList.contains('done')).toBe(true);
    expect(patients[1].classList.contains('absent')).toBe(false);
    expect(patients[1].querySelector('[data-testid="moi-validee"]')).not.toBeNull();
  });

  it('ne barre pas un patient absent un autre jour', async () => {
    absencesPatients.set([{
      absenceId: 'a1',
      patientId: 'p1',
      dateSeance: '2099-01-02',
      statut: 'A_QUALIFIER',
      motif: null
    }]);
    const {root} = await render();

    expect(root.querySelector('[data-testid="moi-patient"].absent')).toBeNull();
  });

  describe('mise à jour en temps réel', () => {
    const evenement = (type: string, payload: Record<string, string> = {}, centerId = CENTRE): WsEvent =>
      ({type, centerId, payload, timestamp: '2026-10-07T08:00:00Z'});

    async function apres(evt: WsEvent) {
      const {fixture} = await render();
      chargerPlanning.mockClear();
      dernierEvenement.set(evt);
      fixture.detectChanges();
      await fixture.whenStable();
      return chargerPlanning;
    }

    it('recharge la semaine affichée quand une absence de patient est déclarée (même le jour J)', async () => {
      const recharge = await apres(evenement('SAISIE_INFIRMIER', {saisie: 'ABSENCE'}));

      expect(recharge).toHaveBeenCalledTimes(1);
      expect(recharge).toHaveBeenCalledWith(aujourdhuiUtc());
    });

    it('recharge aussi quand une séance est validée par le scan du QR code', async () => {
      expect(await apres(evenement('SEANCE_VALIDATED'))).toHaveBeenCalledTimes(1);
    });

    it('ignore les évènements d\'un autre centre ou sans effet sur le planning', async () => {
      expect(await apres(evenement('SEANCE_VALIDATED', {}, 'autre-centre'))).not.toHaveBeenCalled();
    });

    it('ignore un évènement qui ne change pas le planning', async () => {
      expect(await apres(evenement('STOCK_MOVEMENT_CHANGED'))).not.toHaveBeenCalled();
    });
  });

  it('indique quand aucun patient n\'est placé dans ma case', async () => {
    const p = planningFixture();
    p.mesCases[0].occupants = [];
    planning.set(p);
    const {root} = await render();

    expect(root.querySelector('[data-testid="moi-patients"]')).toBeNull();
    expect(root.querySelector('[data-testid="moi-aucun-patient"]')).not.toBeNull();
  });

  it('s\'ouvre sur la vue jour sur un écran de mobile, sans la grille semaine à faire défiler', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({matches: query.includes('767px')}));
    try {
      const {root} = await render();

      expect(root.querySelector('[data-testid="moi-table"]')).toBeNull();
      expect(root.querySelectorAll('[data-testid="moi-day-card"]')).toHaveLength(1);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('s\'ouvre sur la grille semaine sur un grand écran', async () => {
    vi.stubGlobal('matchMedia', () => ({matches: false}));
    try {
      const {root} = await render();

      expect(root.querySelector('[data-testid="moi-table"]')).not.toBeNull();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('affiche aussi les patients dans la vue jour', async () => {
    const {fixture, root} = await render();

    root.querySelector<HTMLElement>('[data-testid="moi-view-jour"] button')!.click();
    fixture.detectChanges();

    const card = root.querySelector('[data-testid="moi-day-card"]')!;
    expect(card.querySelectorAll('[data-testid="moi-patient"]')).toHaveLength(2);
  });

  it('grise un jour de fermeture du centre et n\'y affiche aucun créneau', async () => {
    const {root} = await render();

    const closed = Array.from(root.querySelectorAll('[data-testid="moi-cell"]')).filter((c) =>
      c.classList.contains('closed'));
    expect(closed.length).toBeGreaterThan(0);
    expect(closed.every((c) => !c.querySelector('[data-testid="moi-cell-situation"]'))).toBe(true);
  });

  it('bascule en vue jour : une carte par salle/créneau où je travaille ce jour-là', async () => {
    const {fixture, root} = await render();

    root.querySelector<HTMLElement>('[data-testid="moi-view-jour"] button')!.click();
    fixture.detectChanges();

    expect(root.querySelector('[data-testid="moi-table"]')).toBeNull();
    const cards = root.querySelectorAll('[data-testid="moi-day-card"]');
    expect(cards).toHaveLength(1);
    expect(cards[0].textContent).toContain('Salle 1 · Matin');
  });

  it('propose un message quand je n\'ai aucun créneau cette semaine', async () => {
    planning.set({...planningFixture(), mesCreneaux: [], mesCases: []});
    const {root} = await render();

    expect(root.querySelector('[data-testid="moi-aucun-creneau"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="moi-table"]')).toBeNull();
  });
});
