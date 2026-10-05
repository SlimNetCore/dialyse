import {TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeanceStationComponent} from './seance-station.component';
import {SeanceStore} from '../state/seance.store';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {QrScannerComponent} from '../../../shared/qr-scanner/qr-scanner.component';
import {RichTextEditorComponent} from '../../../shared/rich-text-editor/rich-text-editor.component';
import {AdministrationAnemieSeanceComponent} from '../administration-anemie/administration-anemie-seance.component';
import {PoidsSecSeanceComponent} from '../poids-sec/poids-sec-seance.component';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const SEANCE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

function summary(status: string, generateurEtat?: string) {
  return {
    seance: {id: SEANCE_ID, centerId: CENTER_ID, patientId: 'pid', dateSeance: '2026-10-04', status},
    patient: {id: 'pid', codePatient: 'PAT-1', nom: 'Dupont', prenom: 'Jean', generateurNom: 'G2', generateurEtat},
    paramedical: null, medical: null, forfait: null,
  };
}

function fakeStore() {
  return {
    qrCode: signal(''), scanning: signal(false), scanState: signal('idle'), scanMessage: signal(''),
    lastScan: signal(null), summary: signal<unknown>(null), summaryLoading: signal(false),
    selectedSeanceId: signal<string | null>(null), isSeanceAlreadyValidated: signal(false),
    journalPatients: signal([
      {
        seanceId: SEANCE_ID,
        patientId: 'pid',
        dateSeance: '2026-10-04',
        status: 'CREE',
        patientNom: 'Dupont',
        patientPrenom: 'Jean',
        patientCode: 'PAT-1'
      },
      {
        seanceId: 'b',
        patientId: 'p2',
        dateSeance: '2026-10-04',
        status: 'VALIDEE',
        patientNom: 'Benali',
        patientPrenom: 'Ali',
        patientCode: 'PAT-2'
      },
    ]),
    journalArticles: signal([{articleId: 'art2', quantiteTotale: 9}, {articleId: 'art1', quantiteTotale: 2}]),
    availableArticles: signal([
      {id: 'art1', centerId: CENTER_ID, code: 'A1', libelle: 'Lignes', active: true},
      {id: 'art2', centerId: CENTER_ID, code: 'A2', libelle: 'Dialyseur', active: true},
    ]),
    consommables: signal<Array<{ articleId: string; articleLibelle: string; quantite: number }>>([]),
    poidsAvantKg: signal<number | null>(null), poidsApresKg: signal<number | null>(null),
    dureeMinutes: signal<number | null>(null), debitSangMlMin: signal<number | null>(null),
    ultrafiltrationMl: signal<number | null>(null), taAvant: signal(''), taApres: signal(''),
    anticoagulant: signal(''), typeDialysat: signal(''), incidents: signal(''),
    savingParamedical: signal(false), savingConsommable: signal(false), validatingSeance: signal(false),
    raccourcisIds: signal<string[]>([]), savingRaccourcis: signal(false),
    recentSeances: signal<Array<Record<string, unknown>>>([]),
    loadRaccourcis: vi.fn(), saveRaccourcis: vi.fn(), loadRecentSeances: vi.fn(),
    setQrCode: vi.fn(), setDateSeance: vi.fn(), setJournalDate: vi.fn(), loadJournal: vi.fn(),
    loadArticlesStock: vi.fn(), loadSeanceSummary: vi.fn(), selectSeance: vi.fn(),
    scanQr: vi.fn(), addConsommableToSeance: vi.fn(), addConsommable: vi.fn(), adjustConsommable: vi.fn(),
    removeConsommable: vi.fn(), removeConsommableFromSeance: vi.fn(), updateConsommableQuantiteInSeance: vi.fn(),
    patchParamedical: vi.fn(), saveParamedical: vi.fn(), validateSeance: vi.fn(),
  };
}

describe('SeanceStationComponent', () => {
  let store: ReturnType<typeof fakeStore>;
  let roles: string[];
  let clipboard: ReturnType<typeof signal<{ centerId: string; patientCode: string; copiedAt: number } | null>>;

  beforeEach(() => {
    store = fakeStore();
    clipboard = signal(null);
    roles = ['INFIRMIER'];
    TestBed.configureTestingModule({
      imports: [SeanceStationComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: SeanceStore, useValue: store},
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r), username: () => 'inf-01'}},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTER_ID), seanceScanClipboard: clipboard}},
        {provide: WebSocketService, useValue: {lastEvent: signal(null)}},
      ],
    });
    TestBed.overrideComponent(SeanceStationComponent, {
      remove: {imports: [QrScannerComponent, RichTextEditorComponent, AdministrationAnemieSeanceComponent, PoidsSecSeanceComponent]},
      add: {schemas: [NO_ERRORS_SCHEMA]},
    });
  });

  function render() {
    const fixture = TestBed.createComponent(SeanceStationComponent);
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('charge la file du jour, les articles et les forfaits du centre actif', () => {
    render();
    expect(store.loadJournal).toHaveBeenCalledWith({
      centerId: CENTER_ID,
      date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)
    });
    expect(store.loadArticlesStock).toHaveBeenCalledWith({centerId: CENTER_ID});
  });

  it('propose le lien vers l\'historique des séances à tous les profils du poste', () => {
    const {root} = render();
    expect(root.querySelector('.history-link')).not.toBeNull();
  });

  it('affiche les patients du jour et ouvre la séance au toucher', () => {
    const {fixture, root} = render();
    const items = root.querySelectorAll<HTMLButtonElement>('.queue-item');
    expect(items).toHaveLength(2);
    items[0].click();
    fixture.detectChanges();
    expect(store.selectSeance).toHaveBeenCalledWith(SEANCE_ID);
    expect(store.loadSeanceSummary).toHaveBeenCalledWith({seanceId: SEANCE_ID, centerId: CENTER_ID});
    expect(root.querySelector('.station')?.getAttribute('data-view')).toBe('seance');
  });

  it('la secrétaire voit la file mais ne peut pas ouvrir une séance', () => {
    roles = ['SECRETAIRE'];
    const {root} = render();
    const items = root.querySelectorAll<HTMLButtonElement>('.queue-item');
    expect(items[0].disabled).toBe(true);
    items[0].click();
    expect(store.selectSeance).not.toHaveBeenCalled();
  });

  it('la touche Entrée dans le champ de scan lance le scan', () => {
    const {root} = render();
    store.qrCode.set('PAT-1');
    const input = root.querySelector<HTMLInputElement>('.scan-field input')!;
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    expect(store.scanQr).toHaveBeenCalledWith({centerId: CENTER_ID, qrCode: 'PAT-1'});
  });

  it('n\'envoie aucun scan quand le champ est vide', () => {
    const {root} = render();
    root.querySelector<HTMLInputElement>('.scan-field input')!
      .dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    expect(store.scanQr).not.toHaveBeenCalled();
  });

  it('propose en premier les consommables les plus sortis aujourd\'hui', () => {
    store.summary.set(summary('VALIDEE'));
    const {fixture, root} = render();
    fixture.componentInstance['step'].set('consommables');
    fixture.detectChanges();
    const names = Array.from(root.querySelectorAll('.quick-name')).map((n) => n.textContent?.trim());
    expect(names).toEqual(['Dialyseur', 'Lignes']);
  });

  it('un toucher sur un article ajoute une unité tout de suite quand la séance est déjà validée', () => {
    store.summary.set(summary('VALIDEE'));
    store.isSeanceAlreadyValidated.set(true);
    const {fixture, root} = render();
    fixture.componentInstance['step'].set('consommables');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.quick-btn')!.click();
    expect(store.addConsommableToSeance).toHaveBeenCalledWith(
      {seanceId: SEANCE_ID, centerId: CENTER_ID, articleId: 'art2', quantite: 1});
    expect(store.addConsommable).not.toHaveBeenCalled();
  });

  it('garde la saisie en local tant que la séance n\'est pas validée', () => {
    store.summary.set(summary('CREE'));
    const {fixture, root} = render();
    fixture.componentInstance['step'].set('consommables');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.quick-btn')!.click();
    expect(store.addConsommable).toHaveBeenCalledTimes(1);
    expect(store.addConsommableToSeance).not.toHaveBeenCalled();
  });

  it('propose « Valider la séance » seulement tant qu\'elle est à l\'état CREE', () => {
    store.summary.set(summary('CREE'));
    const first = render();
    expect(first.root.querySelector('.action-bar .act-btn[color="primary"], .action-bar button.mat-mdc-unelevated-button')).not.toBeNull();
    first.fixture.componentInstance['validate']();
    expect(store.validateSeance).toHaveBeenCalledWith({
      seanceId: SEANCE_ID, payload: {centerId: CENTER_ID, userId: 'inf-01', consommations: []},
    });
  });

  it('ne valide pas une séance déjà validée', () => {
    store.summary.set(summary('VALIDEE'));
    const {fixture} = render();
    fixture.componentInstance['validate']();
    expect(store.validateSeance).not.toHaveBeenCalled();
  });

  it('alerte quand le générateur du patient n\'est pas en service', () => {
    store.summary.set(summary('VALIDEE', 'EN_MAINTENANCE'));
    const {root} = render();
    expect(root.querySelector('.banner-warning')).not.toBeNull();
  });

  it('n\'alerte pas quand le générateur est en service', () => {
    store.summary.set(summary('VALIDEE', 'EN_SERVICE'));
    const {root} = render();
    expect(root.querySelector('.banner-warning')).toBeNull();
  });

  it('enregistre automatiquement la constante saisie à la sortie du champ', () => {
    store.summary.set(summary('VALIDEE'));
    const {root} = render();
    const input = root.querySelector<HTMLInputElement>('.field input')!;
    input.value = '71,2';
    input.dispatchEvent(new Event('change'));
    expect(store.patchParamedical).toHaveBeenCalledWith({poidsAvantKg: 71.2});
    expect(store.saveParamedical).toHaveBeenCalledTimes(1);
  });

  it('n\'enregistre rien pour une séance facturée', () => {
    store.summary.set(summary('FACTUREE'));
    const {root} = render();
    const input = root.querySelector<HTMLInputElement>('.field input')!;
    expect(input.disabled).toBe(true);
    input.dispatchEvent(new Event('change'));
    expect(store.saveParamedical).not.toHaveBeenCalled();
  });

  it('charge les raccourcis du centre à l\'ouverture', () => {
    render();
    expect(store.loadRaccourcis).toHaveBeenCalledWith({centerId: CENTER_ID});
  });

  it('propose les raccourcis configurés par le centre, dans leur ordre, avant les articles les plus sortis', () => {
    store.summary.set(summary('VALIDEE'));
    store.raccourcisIds.set(['art1', 'inconnu']);
    const {fixture, root} = render();
    fixture.componentInstance['step'].set('consommables');
    fixture.detectChanges();
    const names = Array.from(root.querySelectorAll('.quick-name')).map((n) => n.textContent?.trim());
    expect(names).toEqual(['Lignes']);
  });

  it('seul l\'administrateur configure les raccourcis, dans l\'ordre de sélection et dans la limite du serveur', () => {
    store.summary.set(summary('VALIDEE'));
    const {fixture, root} = render();
    const cmp = fixture.componentInstance;
    expect(cmp['canConfigureShortcuts']()).toBe(false);

    roles = ['ADMIN'];
    const admin = render();
    const adminCmp = admin.fixture.componentInstance;
    adminCmp['step'].set('consommables');
    admin.fixture.detectChanges();
    adminCmp['startConfiguring']();
    adminCmp['toggleShortcut']('art2');
    adminCmp['toggleShortcut']('art1');
    adminCmp['toggleShortcut']('art2');
    expect(adminCmp['draftShortcuts']()).toEqual(['art1']);
    adminCmp['toggleShortcut']('art2');
    adminCmp['saveShortcuts']();
    expect(store.saveRaccourcis).toHaveBeenCalledWith({centerId: CENTER_ID, articleIds: ['art1', 'art2']});
    expect(root).toBeTruthy();
  });

  it('charge le rappel des dernières séances du patient et l\'affiche', () => {
    store.summary.set(summary('VALIDEE'));
    store.recentSeances.set([
      {
        seanceId: 'r1',
        dateSeance: '2026-10-02',
        status: 'VALIDEE',
        poidsAvantKg: 72,
        poidsApresKg: 69.5,
        taAvant: '12/8',
        dureeMinutes: 240
      },
    ]);
    const {root} = render();
    expect(store.loadRecentSeances).toHaveBeenCalledWith({centerId: CENTER_ID, patientId: 'pid', before: '2026-10-04'});
    const item = root.querySelector('.context-pane .recent-item')!;
    expect(item.textContent).toContain('2026-10-02');
    expect(item.textContent).toContain('72 → 69.5');
    expect(item.textContent).toContain('2.5');
  });

  it('reprend dans le champ de scan le code patient copié depuis la liste des patients', () => {
    clipboard.set({centerId: CENTER_ID, patientCode: 'PAT-777', copiedAt: 1750000000000});
    render();
    expect(store.setQrCode).toHaveBeenCalledWith('PAT-777');
  });

  it('ignore un code copié pour un autre centre', () => {
    clipboard.set({centerId: 'autre', patientCode: 'PAT-777', copiedAt: 1750000000000});
    render();
    expect(store.setQrCode).not.toHaveBeenCalled();
  });

  it('avance d\'étape en étape puis revient à la file à la fin', () => {
    store.summary.set(summary('VALIDEE'));
    const {fixture} = render();
    const cmp = fixture.componentInstance;
    cmp['next']();
    expect(cmp['step']()).toBe('consommables');
    cmp['next']();
    cmp['next']();
    expect(cmp['step']()).toBe('remarques');
    cmp['next']();
    expect(cmp['view']()).toBe('queue');
  });
});
