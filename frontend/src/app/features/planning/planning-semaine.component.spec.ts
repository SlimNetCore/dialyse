import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {CasePresence, SemainePresence} from '../../core/api/infirmier-api.service';
import {JOURS_SEMAINE, SemainePlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService, WsEvent} from '../../core/ws/websocket.service';
import {PlanningSemaineComponent} from './planning-semaine.component';
import {PlanningStore} from './planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const SALLE = 's1';
const CRENEAU = 'c1';

const semaine: SemainePlanning = {
  debut: '2026-09-27', fin: '2026-10-03',
  jours: JOURS_SEMAINE.map((jour, i) => ({
    jour,
    date: `2026-09-${27 + i}`,
    ouvertHebdomadaire: true,
    fermetureMotif: null
  })),
  salles: [{id: SALLE, nom: 'Salle A'}], creneaux: [{id: CRENEAU, libelle: 'Matin', ordre: 1}],
  cellules: [{
    salleId: SALLE, creneauId: CRENEAU, jour: 'LUNDI', capacite: 4,
    occupants: [{patientId: 'p1', nom: 'Benali', generateurCode: 'A-G1', aRisque: false}],
  }, {
    salleId: SALLE, creneauId: CRENEAU, jour: 'MARDI', capacite: 1,
    occupants: [{patientId: 'p2', nom: 'Kaci', generateurCode: 'ISO-G1', aRisque: true}],
  }],
  conflits: [], patientsAReplanifier: 0,
};

function casePresence(surcharge: Partial<CasePresence>): CasePresence {
  return {
    salleId: SALLE,
    creneauId: CRENEAU,
    jour: 'LUNDI',
    date: '2026-09-28',
    patients: 1,
    requis: 1,
    salleIsolement: false,
    statut: 'COUVERT',
    manque: 0,
    surplus: 0,
    presents: [],
    absents: [], ...surcharge,
  };
}

function presence(cases: CasePresence[]): SemainePresence {
  return {
    debut: '2026-09-27', fin: '2026-10-03', jours: [], salles: [], creneaux: [], cases, conflits: [],
    patientsParInfirmier: 4, casesSousEffectif: 0,
  };
}

describe('PlanningSemaineComponent', () => {
  let presenceSignal: ReturnType<typeof signal<SemainePresence | null>>;
  let chargerSemaine: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;
  let dernierEvenement: ReturnType<typeof signal<WsEvent | null>>;
  let roles: string[];

  async function render() {
    await TestBed.configureTestingModule({
      imports: [PlanningSemaineComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: MatDialog, useValue: {open: dialogOpen}},
        {provide: WebSocketService, useValue: {lastEvent: dernierEvenement}},
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}},
        {
          provide: PlanningStore, useValue: {
            semaine: signal(semaine), presence: presenceSignal, absences: signal([]), seancesRealisees: signal([]),
            loading: signal(false), error: signal<string | null>(null), date: signal<string | null>('2026-09-27'),
            chargerSemaine, changerSemaine: vi.fn(),
          },
        },
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
    const fixture = TestBed.createComponent(PlanningSemaineComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  beforeEach(() => {
    chargerSemaine = vi.fn();
    dialogOpen = vi.fn();
    dernierEvenement = signal<WsEvent | null>(null);
    roles = ['ADMIN'];
    presenceSignal = signal<SemainePresence | null>(presence([casePresence({
      presents: [
        {infirmierId: 'i1', nom: 'Sara', habiliteIsolement: false, remplacant: false, remplacementId: null},
        {infirmierId: 'i2', nom: 'Lila', habiliteIsolement: false, remplacant: true, remplacementId: 'r1'},
      ],
      absents: [{infirmierId: 'i3', nom: 'Nadia', type: 'MALADIE'}],
    })]));
  });
  afterEach(() => TestBed.resetTestingModule());

  it('affiche dans chaque case les patients avec leur générateur et les infirmiers du créneau', async () => {
    const {root} = await render();

    expect(chargerSemaine).toHaveBeenCalledWith(null);
    expect(root.querySelector('[data-testid="week-patient"]')?.textContent).toContain('A-G1');
    const infirmiers = Array.from(root.querySelectorAll('[data-testid="week-infirmier"]')).map((e) => e.textContent);
    expect(infirmiers).toHaveLength(2);
    expect(infirmiers[0]).toContain('Sara');
    expect(infirmiers[1]).toContain('Lila');
    expect(root.querySelector('[data-testid="week-infirmier"].sub')?.textContent).toContain('Lila');
    expect(root.querySelector('[data-testid="week-infirmier-absent"]')?.textContent).toContain('Nadia');
  });

  it('indique le nombre d\'infirmiers affectés (et non le nombre requis), absents exclus', async () => {
    const {root} = await render();

    const affectes = root.querySelector('[data-testid="week-affectes"]');
    expect(affectes?.textContent).toContain('PLANNING.SEMAINE.INFIRMIERS_AFFECTES');
    expect(root.textContent).not.toContain('INFIRMIERS_REQUIS');
  });

  it('n\'indique aucun nombre d\'affectés quand personne n\'est affecté à la case', async () => {
    presenceSignal.set(presence([casePresence({statut: 'SOUS_EFFECTIF', requis: 2, manque: 2, presents: []})]));
    const {root} = await render();

    expect(root.querySelector('[data-testid="week-affectes"]')).toBeNull();
    expect(root.querySelector('[data-testid="week-manque"]')).not.toBeNull();
  });

  it('marque d\'une icône de risque infectieux les seuls patients à risque, comme le planning proposé', async () => {
    const {root} = await render();

    const patients = Array.from(root.querySelectorAll('[data-testid="week-patient"]'));
    const avecIcone = patients.filter((p) => p.querySelector('[data-testid="week-risque"]'));
    expect(patients).toHaveLength(2);
    expect(avecIcone).toHaveLength(1);
    expect(avecIcone[0].textContent).toContain('Kaci');
    expect(avecIcone[0].querySelector('[data-testid="week-risque"]')?.textContent).toContain('coronavirus');
  });

  describe('mise à jour en temps réel', () => {
    const evenement = (type: string, payload: Record<string, string> = {}, centerId = CENTRE): WsEvent =>
      ({type, centerId, payload, timestamp: '2026-10-07T08:00:00Z'});

    async function apres(evt: WsEvent) {
      const rendu = await render();
      chargerSemaine.mockClear();
      dernierEvenement.set(evt);
      rendu.fixture.detectChanges();
      await rendu.fixture.whenStable();
      return chargerSemaine;
    }

    it('recharge la semaine affichée quand une séance est validée (scan du QR code) dans le centre', async () => {
      const recharge = await apres(evenement('SEANCE_VALIDATED', {targetRoles: 'INFIRMIER,SECRETAIRE'}));

      expect(recharge).toHaveBeenCalledTimes(1);
      expect(recharge).toHaveBeenCalledWith('2026-09-27');
    });

    it('recharge aussi pour une création ou suppression de séance, un déplacement et une absence de patient', async () => {
      for (const evt of [evenement('SEANCE_CREATED'), evenement('SEANCE_SUPPRIMEE'), evenement('SEANCES_DEPLACEES'),
        evenement('PATIENT_UPDATED'), evenement('SAISIE_INFIRMIER', {saisie: 'ABSENCE'}),
        evenement('INFIRMIER_ABSENCE_DECLAREE')]) {
        TestBed.resetTestingModule();
        dernierEvenement = signal<WsEvent | null>(null);
        expect(await apres(evt), evt.type).toHaveBeenCalledTimes(1);
      }
    });

    it('ignore les évènements d\'un autre centre ou sans effet sur le planning', async () => {
      for (const evt of [evenement('SEANCE_VALIDATED', {}, 'autre-centre'), evenement('STOCK_MOVEMENT_CHANGED'),
        evenement('SAISIE_INFIRMIER', {saisie: 'PARAMEDICAL'}), evenement('OPTIMISATION_PROPOSITION')]) {
        TestBed.resetTestingModule();
        dernierEvenement = signal<WsEvent | null>(null);
        expect(await apres(evt), evt.type).not.toHaveBeenCalled();
      }
    });

    it('met aussi à jour le planning du médecin, qui le consulte en lecture seule', async () => {
      roles = ['MEDECIN'];

      const recharge = await apres(evenement('SEANCE_VALIDATED'));

      expect(recharge).toHaveBeenCalledWith('2026-09-27');
    });
  });

  describe('lecture seule du médecin', () => {
    it('ne propose aucun bouton de déclaration d\'absence au médecin', async () => {
      roles = ['MEDECIN'];
      const {root} = await render();

      expect(root.querySelector('[data-testid="week-patient"]')).not.toBeNull();
      expect(root.querySelector('[data-testid="week-declare"]')).toBeNull();
    });

    it('propose la déclaration d\'absence à l\'administrateur, au secrétariat et à l\'infirmier', async () => {
      for (const role of ['ADMIN', 'SECRETAIRE', 'INFIRMIER']) {
        TestBed.resetTestingModule();
        roles = [role];
        const {root} = await render();
        expect(root.querySelector('[data-testid="week-declare"]'), role).not.toBeNull();
      }
    });

    it('ouvre le détail du patient sans formulaire d\'absence pour le médecin', async () => {
      roles = ['MEDECIN'];
      const {root} = await render();

      (root.querySelector('[data-testid="week-patient-open"]') as HTMLButtonElement).click();

      expect(dialogOpen).toHaveBeenCalledTimes(1);
      expect(dialogOpen.mock.calls[0][1].data.peutDeclarer).toBe(false);
    });

    it('ouvre le détail du patient avec le formulaire d\'absence pour l\'administrateur', async () => {
      const {root} = await render();

      (root.querySelector('[data-testid="week-patient-open"]') as HTMLButtonElement).click();

      expect(dialogOpen.mock.calls[0][1].data.peutDeclarer).toBe(true);
    });
  });

  it('signale le manque d\'infirmiers d\'une case en sous-effectif', async () => {
    presenceSignal.set(presence([casePresence({statut: 'SOUS_EFFECTIF', requis: 2, manque: 1, presents: []})]));
    const {root} = await render();

    expect(root.querySelector('[data-testid="week-manque"]')?.textContent).toContain('PLANNING.SEMAINE.INFIRMIERS_MANQUE');
  });

  it('signale les infirmiers en trop d\'une case sans en faire un manque', async () => {
    presenceSignal.set(presence([casePresence({
      requis: 1, surplus: 2,
      presents: [
        {infirmierId: 'i1', nom: 'Sara', habiliteIsolement: false, remplacant: false, remplacementId: null},
        {infirmierId: 'i2', nom: 'Lila', habiliteIsolement: false, remplacant: false, remplacementId: null},
        {infirmierId: 'i4', nom: 'Rym', habiliteIsolement: false, remplacant: false, remplacementId: null},
      ],
    })]));
    const {root} = await render();

    expect(root.querySelector('[data-testid="week-surplus"]')?.textContent).toContain('PLANNING.SEMAINE.INFIRMIERS_SURPLUS');
    expect(root.querySelector('[data-testid="week-manque"]')).toBeNull();
  });

  it('garde le planning des patients quand la présence des infirmiers est indisponible', async () => {
    presenceSignal.set(null);
    const {root} = await render();

    expect(root.querySelector('[data-testid="week-patient"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="week-infirmiers"]')).toBeNull();
  });

  it('explique dans la légende comment lire les infirmiers', async () => {
    const {root} = await render();

    expect(root.querySelector('[data-testid="week-legend"]')?.textContent).toContain('PLANNING.SEMAINE.LEGENDE.INFIRMIER_ABSENT');
  });

  it('montre aussi les infirmiers dans la vue d\'un jour', async () => {
    const {fixture, root} = await render();

    (root.querySelector('[data-testid="week-view-jour"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    // le premier jour ouvert (dimanche) est affiché par défaut : on choisit lundi, qui a des patients et des infirmiers
    const jours = root.querySelectorAll('.day-tabs mat-button-toggle');
    (jours[1].querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(root.querySelector('[data-testid="week-day-view"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="week-day-card"] [data-testid="week-infirmier"]')?.textContent).toContain('Sara');
  });
});
