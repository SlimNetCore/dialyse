import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {PatientTableauDeBordComponent} from './patient-tableau-de-bord.component';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

const SUMMARY = {
  totalPatients: 3,
  sexDistribution: [{code: 'M', label: 'Masculin', count: 3}],
  ageDistribution: [{code: '60_PLUS', label: '60 ans et +', count: 3}],
  ktDistribution: [{code: 'OUI', label: 'Sous KT', count: 1}],
};
const STATS = {patientCount: 3, pecCree: 4, pecValidee: 9, pecExpiring: 2, attestationTotal: 7, attestationExpiring: 1};

describe('PatientTableauDeBordComponent', () => {
  let roles: string[];
  let api: {
    getPatientSummary: ReturnType<typeof vi.fn>;
    getDashboardStats: ReturnType<typeof vi.fn>;
    getPatientSummaryDetails: ReturnType<typeof vi.fn>;
    printDocument: ReturnType<typeof vi.fn>;
  };
  const lastEvent = signal<{ type: string } | null>(null);

  beforeEach(() => {
    roles = ['ADMIN'];
    lastEvent.set(null);
    api = {
      getPatientSummary: vi.fn(() => of(SUMMARY)),
      getDashboardStats: vi.fn(() => of(STATS)),
      getPatientSummaryDetails: vi.fn(() => of({})),
      printDocument: vi.fn(() => of(new Blob())),
    };
    TestBed.configureTestingModule({
      imports: [PatientTableauDeBordComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: BackendApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'c1'}},
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r), centerId: () => 'c1'}},
        {provide: WebSocketService, useValue: {lastEvent}},
      ],
    });
  });

  async function rendu() {
    const fixture = TestBed.createComponent(PatientTableauDeBordComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  function lien(root: HTMLElement, testid: string): string {
    return root.querySelector<HTMLAnchorElement>(`[data-testid="${testid}"]`)!.getAttribute('href')!;
  }

  it('affiche la synthèse mensuelle du mois courant du centre', async () => {
    const {root} = await rendu();

    expect(api.getPatientSummary).toHaveBeenCalledWith('c1', expect.stringMatching(/^\d{4}-\d{2}$/));
    expect(root.querySelector('app-patient-summary-cards')).not.toBeNull();
  });

  it('les PEC créées et validées ouvrent la liste des PEC filtrée sur ce statut', async () => {
    const {root} = await rendu();

    expect(api.getDashboardStats).toHaveBeenCalledWith('c1', 30);
    expect(lien(root, 'kpi-pec-creees')).toBe('/patients/pec-list?statut=CREE');
    expect(lien(root, 'kpi-pec-validees')).toBe('/patients/pec-list?statut=VALIDEE');
    expect(root.querySelector('[data-testid="kpi-pec-creees"] strong')!.textContent).toContain('4');
  });

  it('les PEC expirantes et les attestations ouvrent la liste sans filtre (aucun filtre d\'échéance dans la liste)',
    async () => {
      const {root} = await rendu();

      expect(lien(root, 'kpi-pec-expirantes')).toBe('/patients/pec-list?statut=');
      expect(lien(root, 'kpi-attestations')).toBe('/patients/attestations-list');
      expect(lien(root, 'kpi-attestations-expirantes')).toBe('/patients/attestations-list');
    });

  it('masque au médecin seul les prises en charge et attestations, sans les charger', async () => {
    roles = ['MEDECIN'];
    const {root} = await rendu();

    expect(root.querySelector('[data-testid="patient-tdb-pec"]')).toBeNull();
    expect(api.getDashboardStats).not.toHaveBeenCalled();
    expect(root.querySelector('app-patient-summary-cards')).not.toBeNull();
  });

  it('recharge les indicateurs sur un évènement temps réel PEC', async () => {
    const {fixture} = await rendu();
    api.getDashboardStats.mockClear();

    lastEvent.set({type: 'PEC_VALIDATED'});
    fixture.detectChanges();
    await fixture.whenStable();

    expect(api.getDashboardStats).toHaveBeenCalledTimes(1);
  });

  it('imprime le rapport mensuel des patients', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const createObjectURL = vi.fn(() => 'blob:x');
    (URL as any).createObjectURL = createObjectURL;
    const {fixture} = await rendu();

    fixture.componentInstance.printReport();

    expect(api.printDocument).toHaveBeenCalledWith('c1', 'LISTE_PATIENTS', {});
    expect(open).toHaveBeenCalled();
    open.mockRestore();
  });
});
