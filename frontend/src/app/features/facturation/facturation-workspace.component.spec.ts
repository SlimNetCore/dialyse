import {provideZonelessChangeDetection, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {vi} from 'vitest';
import {NgTableColumn, NgTableComponent} from '@sbourahla/ng-table';
import {FacturationWorkspaceComponent, tonDuStatutPatient} from './facturation-workspace.component';
import {FACTURATION_VALIDEE, FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BackendApiService, FacturationPreviewInvoice} from '../../core/api/backend-api.service';
import {MatSnackBar} from '@angular/material/snack-bar';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';

describe('FacturationWorkspaceComponent', () => {
  const facturationStoreMock = {
    month: vi.fn(() => '2026-08'),
    periodMode: vi.fn(() => 'month'),
    startDate: vi.fn(() => '2026-08-01'),
    endDate: vi.fn(() => '2026-08-31'),
    successMessage: vi.fn((): string | null => null),
    preview: vi.fn((): unknown => null),
    setMonth: vi.fn(),
    validateFacturation: vi.fn(),
    removeSeanceFromPreview: vi.fn(),
    reincludeSeanceInPreview: vi.fn(),
    excludedSeances: vi.fn(() => []),
    updateSeanceForfaitInPreview: vi.fn(),
    setActiveCenterId: vi.fn(),
    loadSettings: vi.fn(),
    loadForfaits: vi.fn(),
    forfaits: vi.fn(() => []),
    previewLoading: vi.fn(() => false),
    seanceActionLoadingId: vi.fn(() => null),
    anneePeriodes: vi.fn(() => 2026),
    anneesChargees: vi.fn((): number[] => [2026]),
    moisVises: vi.fn(() => ['2026-08']),
    moisClosVises: vi.fn(() => [] as string[]),
    moisEstClos: vi.fn((mois: string) => mois === '2026-08'),
    loadPeriodes: vi.fn(),
    cloturerMois: vi.fn(),
    rouvrirMois: vi.fn(),
    setAnneePeriodes: vi.fn(),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111'),
  };

  const authMock = {
    username: vi.fn(() => 'billing.user'),
    hasRole: vi.fn((role: string) => role === 'SECRETAIRE'),
  };

  const translateMock = {
    instant: vi.fn((key: string) => key),
    getCurrentLang: vi.fn(() => 'fr'),
    onLangChange: of(),
    onTranslationChange: of(),
  };

  const apiMock = {
    printFacturationSynthese: vi.fn(() => of(new Blob(['%PDF'], {type: 'application/pdf'}))),
  };

  const snackBarMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: FacturationStore, useValue: facturationStoreMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: TranslateService, useValue: translateMock},
        {provide: BackendApiService, useValue: apiMock},
        {provide: MatSnackBar, useValue: snackBarMock},
      ],
    });
  });

  it('change le mois de calcul sans charger d indicateurs (ils sont sur le tableau de bord)', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onMonthChange']('2026-09');

    expect(facturationStoreMock.setMonth).toHaveBeenCalledWith('2026-09');
    expect('refreshDashboard' in component).toBe(false);
  });

  it('indicateur d\'étapes (remplace le bloc « Cycle de facturation ») : Sélection, puis Aperçu, puis Validation',
    () => {
      const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
      const etape = (): number => component['etape']();

      expect(etape()).toBe(1);

      facturationStoreMock.preview.mockReturnValue({lignes: []});
      const avecApercu = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
      expect(avecApercu['etape']()).toBe(2);

      facturationStoreMock.preview.mockReturnValue(null);
      facturationStoreMock.successMessage.mockReturnValue(FACTURATION_VALIDEE);
      const valide = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
      expect(valide['etape']()).toBe(3);

      facturationStoreMock.successMessage.mockReturnValue(null);
    });

  it('propage centerId et userId lors de la validation', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onValidateFacturation']();

    expect(facturationStoreMock.validateFacturation).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'billing.user',
    });
  });

  it('envoie l exclusion avec le motif et le commentaire saisis, et refuse AUTRE sans commentaire', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onConfirmExclusion']();
    expect(facturationStoreMock.removeSeanceFromPreview).not.toHaveBeenCalled();

    component['onOpenExclusion']('seance-1');
    component['onExclusionMotif']('AUTRE');
    expect(component['exclusionValid']()).toBe(false);
    component['onConfirmExclusion']();
    expect(facturationStoreMock.removeSeanceFromPreview).not.toHaveBeenCalled();

    component['onExclusionCommentaire']('Panne du transport');
    expect(component['exclusionValid']()).toBe(true);
    component['onConfirmExclusion']();

    expect(facturationStoreMock.removeSeanceFromPreview).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'billing.user',
      seanceId: 'seance-1',
      motif: 'AUTRE',
      commentaire: 'Panne du transport',
    });
    expect(component['exclusionDraft']()).toBeNull();
  });

  it('reinclut une seance avec le centre de la session', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onReincludeSeance']('seance-1');

    expect(facturationStoreMock.reincludeSeanceInPreview).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      seanceId: 'seance-1',
    });
  });

  it('la clôture mensuelle a quitté l\'espace de calcul : plus d\'action de clôture ni de réouverture ici', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    for (const action of ['onDemanderCloture', 'onConfirmerCloture', 'onOuvrirReouverture', 'onConfirmerReouverture']) {
      expect(action in component, action).toBe(false);
    }
  });

  it('ignore les messages de succès de la clôture mensuelle (ils appartiennent à l\'écran d\'administration)', () => {
    facturationStoreMock.successMessage.mockReturnValue('FACTURATION.SUCCESS.MOIS_CLOTURE');
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
    expect(component['succesDuCalcul']()).toBeNull();
    expect(component['etape']()).toBe(1);

    facturationStoreMock.successMessage.mockReturnValue('FACTURATION.SUCCESS.SEANCE_EXCLUDED');
    expect(TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent())['succesDuCalcul']())
      .toBe('FACTURATION.SUCCESS.SEANCE_EXCLUDED');
    facturationStoreMock.successMessage.mockReturnValue(null);
  });


  it('charge les mois des années visées par la période (blocage de la validation), pas l\'année de la clôture', () => {
    facturationStoreMock.anneesChargees.mockReturnValue([]);
    facturationStoreMock.anneePeriodes.mockReturnValue(2024);
    TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
    TestBed.tick();

    expect(facturationStoreMock.loadPeriodes).toHaveBeenCalledTimes(1);
    expect(facturationStoreMock.loadPeriodes).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      annee: 2026,
    });
    facturationStoreMock.anneesChargees.mockReturnValue([2026]);
    facturationStoreMock.anneePeriodes.mockReturnValue(2026);
  });

  it('bloque les actions d une séance dont le mois est clos (mois de la date de la séance)', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    expect(component['moisDeSeanceClos']('2026-08-12')).toBe(true);
    expect(component['moisDeSeanceClos']('2026-07-31')).toBe(false);
    expect(component['moisDeSeanceClos'](null)).toBe(false);
  });

  it('imprime la synthese mensuelle avec centerId et periode du mois actif', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onPrintMonthlySummary']();

    expect(apiMock.printFacturationSynthese).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      periodStart: '2026-08-01',
      periodEnd: '2026-08-31',
      format: 'PDF',
    });
  });

  it('reserve la synthese a ADMIN et SECRETAIRE : refuse l impression pour les autres profils', () => {
    authMock.hasRole.mockImplementation((role: string) => role === 'MEDECIN');
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    expect(component['canPrintSynthese']()).toBe(false);
    component['onPrintMonthlySummary']();
    expect(apiMock.printFacturationSynthese).not.toHaveBeenCalled();

    authMock.hasRole.mockImplementation((role: string) => role === 'ADMIN');
    expect(TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent())['canPrintSynthese']()).toBe(true);
    authMock.hasRole.mockImplementation((role: string) => role === 'SECRETAIRE');
  });

  it('affiche le detail du backend quand l impression de la synthese est refusee en 403 (blob d erreur)', async () => {
    const errorBlob = new Blob([JSON.stringify({detail: 'Centre différent de la session'})], {type: 'application/json'});
    apiMock.printFacturationSynthese.mockReturnValueOnce(throwError(() => new HttpErrorResponse({status: 403, error: errorBlob})) as never);
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onPrintMonthlySummary']();
    await vi.waitFor(() => expect(snackBarMock.open).toHaveBeenCalled());

    expect(snackBarMock.open.mock.calls[0][0]).toBe('Centre différent de la session');
  });

  it('colonnes ng-table des résultats : en-têtes traduits, statut patient en pastille, montants triables', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
    const colonnes = component['invoiceColumns']() as NgTableColumn<FacturationPreviewInvoice>[];

    expect(colonnes.map((c) => c.id)).toEqual(
      ['numeroFacture', 'patient', 'patientStatusSnapshot', 'seancesCount', 'totalHt', 'totalTva', 'totalTtc']);
    expect(colonnes[0].header).toBe('FACTURATION.PREVIEW.TABLE.NUMERO');
    expect(colonnes.find((c) => c.id === 'patientStatusSnapshot')?.cell?.type).toBe('badge');
    const ttc = colonnes.find((c) => c.id === 'totalTtc')!;
    expect(ttc.sortable).toBe(true);
    expect(ttc.valueAccessor(invoice(1))).toBe(1190);
    expect(component['pageSize']).toBe(20);
    expect(component['pageSizeOptions']).toEqual([10, 20, 50, 100]);
  });

  it('teinte du statut patient', () => {
    expect(tonDuStatutPatient('PERMANENT')).toBe('success');
    expect(tonDuStatutPatient('TRANSFERE')).toBe('info');
    expect(tonDuStatutPatient('VACANCIER')).toBe('warning');
    expect(tonDuStatutPatient('DECEDE')).toBe('danger');
    expect(tonDuStatutPatient(null)).toBe('neutral');
  });
});

const invoice = (n: number): FacturationPreviewInvoice => ({
  previewKey: `k${n}`,
  numeroFacture: `FAC-${String(n).padStart(2, '0')}`,
  patientId: `p${n}`,
  patientCode: `P${n}`,
  patientFullName: `Patient ${n}`,
  patientStatusSnapshot: 'PERMANENT',
  totalHt: 1000,
  totalTva: 190,
  totalTtc: 1190,
  lines: [],
  seances: [{
    seanceId: `s${n}`, seanceDate: '2026-07-02', seanceStatus: 'TERMINEE', forfaitId: 'f1', forfaitLabel: 'F1',
    forfaitPrixHt: 1000,
  }],
});

describe('FacturationWorkspaceComponent — tableau des résultats (rendu)', () => {
  const invoices = Array.from({length: 25}, (_, i) => invoice(i + 1));
  const store = {
    periodMode: signal('month'), month: signal('2026-07'), startDate: signal(''), endDate: signal(''),
    canCalculate: signal(true), previewLoading: signal(false), validating: signal(false),
    moisClosVises: signal<string[]>([]), moisVises: signal(['2026-07']), anneesChargees: signal([2026]),
    error: signal(null), errorParams: signal(null), successMessage: signal(null),
    excludedSeances: signal([]), seanceActionLoadingId: signal(null), forfaits: signal([]),
    preview: signal({
      centerId: 'c1', generatedAt: '', periodStart: '2026-07-01', periodEnd: '2026-07-31',
      totalFactures: 25, totalHt: 25000, totalTva: 4750, totalTtc: 29750, invoices,
    }),
    moisEstClos: () => false,
    setActiveCenterId: vi.fn(), loadSettings: vi.fn(), loadForfaits: vi.fn(), loadPeriodes: vi.fn(),
  };
  let fixture: ComponentFixture<FacturationWorkspaceComponent>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [FacturationWorkspaceComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideRouter([]),
        {provide: FacturationStore, useValue: store},
        {provide: AppShellStore, useValue: {currentCenterId: signal('c1')}},
        {provide: AuthStore, useValue: {username: signal('u'), hasRole: () => false}},
        {provide: BackendApiService, useValue: {}},
      ],
    });
    fixture = TestBed.createComponent(FacturationWorkspaceComponent);
    await fixture.whenStable();
  });

  const table = (): NgTableComponent<FacturationPreviewInvoice> =>
    fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance;
  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;

  it('ng-table en mode local paginé par 20 : la première page seulement, plus de configurable-list', () => {
    expect(host().querySelector('app-configurable-list')).toBeNull();
    expect(table().dataMode()).toBe('local');
    expect(table().paginator()).toBe(true);
    expect(table().pageSize()).toBe(20);
    const texte = host().querySelector('[data-testid="facturation-resultats"]')?.textContent ?? '';
    expect(texte).toContain('FAC-01');
    expect(texte).toContain('FAC-20');
    expect(texte).not.toContain('FAC-21');
  });

  it('le détail déplié garde le changement de forfait et l\'exclusion de séance', async () => {
    table().toggleDetail(invoices[0]);
    await fixture.whenStable();

    expect(host().querySelector('.seances-table mat-select')).not.toBeNull();
    const exclure = Array.from(host().querySelectorAll<HTMLButtonElement>('.seances-table .actions-cell button'));
    expect(exclure).toHaveLength(1);
    exclure[0].click();
    await fixture.whenStable();

    expect(fixture.componentInstance['exclusionDraft']()).toEqual({seanceId: 's1', motif: null, commentaire: ''});
    expect(host().querySelector('.exclusion-row .exclusion-form')).not.toBeNull();
    expect(host().querySelector('.detail-toolbar')).not.toBeNull();
  });
});
