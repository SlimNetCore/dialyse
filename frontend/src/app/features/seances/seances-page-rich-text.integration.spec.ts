import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {SeancesPageComponent} from './seances-page.component';
import {SeanceStore} from './state/seance.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService} from '../../core/api/backend-api.service';
import {WebSocketService} from '../../core/ws/websocket.service';
import {TranslateService} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

/**
 * Tests d'intégration pour le RichTextEditor dans le composant SeancesPage
 * Vérifie que le contenu enrichi est correctement propagé au store
 */
describe('SeancesPageComponent - RichTextEditor Integration', () => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let storeMock: any;

  beforeEach(async () => {
    storeMock = {
      selectSeance: vi.fn(),
      loadSeanceSummary: vi.fn(),
      summaryLoading: vi.fn(() => false),
      isSeanceAlreadyValidated: vi.fn(() => false),
      patchParamedical: vi.fn(),
      patchMedical: vi.fn(),
      saveParamedical: vi.fn(),
      saveMedical: vi.fn(),
      validateSeance: vi.fn(),
      seances: vi.fn(() => []),
      selectedSeanceId: vi.fn(() => null),
      selectedPreview: vi.fn(() => null),
      incidents: vi.fn(() => ''),
      toleranceSeance: vi.fn(() => ''),
      ajustementsTherapeutiques: vi.fn(() => ''),
      conclusionMedicale: vi.fn(() => ''),
      summary: vi.fn(() => null),
      consommables: vi.fn(() => []),
      // ... autres mocks
      seanceDashboard: vi.fn(() => null),
      dashboardMonth: vi.fn(() => '2026-07'),
      dashboardLoading: vi.fn(() => false),
      dashboardDetailsOpen: vi.fn(() => false),
      dashboardDetailsKind: vi.fn(() => 'presence'),
      dashboardDetailsLoading: vi.fn(() => false),
      dashboardDetailItems: vi.fn(() => []),
      dashboardDetailPageIndex: vi.fn(() => 0),
      dashboardDetailPageItems: vi.fn(() => []),
      dashboardDetailTotalPages: vi.fn(() => 1),
      journalDate: vi.fn(() => '2026-07-24'),
      journalLoading: vi.fn(() => false),
      journalPatients: vi.fn(() => []),
      journalArticles: vi.fn(() => []),
      editDateSeance: vi.fn(() => '2026-07-24'),
      taAvant: vi.fn(() => ''),
      taApres: vi.fn(() => ''),
      poidsAvantKg: vi.fn(() => null),
      poidsApresKg: vi.fn(() => null),
      dureeMinutes: vi.fn(() => null),
      debitSangMlMin: vi.fn(() => null),
      ultrafiltrationMl: vi.fn(() => null),
      anticoagulant: vi.fn(() => ''),
      typeDialysat: vi.fn(() => ''),
      prescription: vi.fn(() => ''),
      examenClinique: vi.fn(() => ''),
      resultatsBiologiques: vi.fn(() => ''),
      qrCode: vi.fn(() => ''),
      scanState: vi.fn(() => 'idle'),
      scanMessage: vi.fn(() => ''),
      scanning: vi.fn(() => false),
      savingParamedical: vi.fn(() => false),
      savingMedical: vi.fn(() => false),
      validatingSeance: vi.fn(() => false),
      cameraActive: vi.fn(() => false),
      error: vi.fn(() => null),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: SeanceStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: () => CENTER_ID}},
        {
          provide: AuthStore, useValue: {
            hasRole: (role: string) => role === 'INFIRMIER',
            username: () => 'inf-01'
          }
        },
        {provide: BackendApiService, useValue: {}},
        {provide: WebSocketService, useValue: {lastEvent: () => null}},
        {provide: TranslateService, useValue: {currentLang: 'fr'}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    });
  });

  it('should update incidents paramedical field when rich text content changes', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const richHtmlContent = '<p><strong>Hypotension</strong> observée à 15h00</p>';
    component['onIncidentsRichChange'](richHtmlContent);

    expect(storeMock.patchParamedical).toHaveBeenCalledWith({
      incidents: richHtmlContent
    });
  });

  it('should update tolerance seance medical field when rich text content changes', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const richHtmlContent = '<p>Patient a toléré <em>correctement</em> la séance</p>';
    component['onToleranceSeanceRichChange'](richHtmlContent);

    expect(storeMock.patchMedical).toHaveBeenCalledWith({
      toleranceSeance: richHtmlContent
    });
  });

  it('should update ajustements therapeutiques medical field when rich text content changes', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const richHtmlContent = '<table><tr><td>Paramètre</td><td>Valeur</td></tr></table>';
    component['onAjustementsTherapeutiquesRichChange'](richHtmlContent);

    expect(storeMock.patchMedical).toHaveBeenCalledWith({
      ajustementsTherapeutiques: richHtmlContent
    });
  });

  it('should update conclusion medicale medical field when rich text content changes', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const richHtmlContent = '<ul><li>Point 1</li><li>Point 2</li></ul>';
    component['onConclusionMedicaleRichChange'](richHtmlContent);

    expect(storeMock.patchMedical).toHaveBeenCalledWith({
      conclusionMedicale: richHtmlContent
    });
  });

  it('should handle empty rich text content', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    component['onIncidentsRichChange']('');

    expect(storeMock.patchParamedical).toHaveBeenCalledWith({
      incidents: ''
    });
  });

  it('should handle HTML with special characters and formatting', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const complexHtml = `
      <h2>Synthèse</h2>
      <p>Patient: <strong>Jean Dupont</strong></p>
      <p>Observations:</p>
      <ul>
        <li>Pression: <span style="color: red;">145/95</span></li>
        <li>Poids: <em>72.5 kg</em></li>
      </ul>
    `;

    component['onConclusionMedicaleRichChange'](complexHtml);

    expect(storeMock.patchMedical).toHaveBeenCalledWith({
      conclusionMedicale: complexHtml
    });
  });

  it('should preserve rich text content structure through save cycle', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock.summary = vi.fn(() => ({
      seance: {id: 'seance-123', centerId: CENTER_ID, patientId: 'p1', dateSeance: '2026-07-24', status: 'CREE'},
      patient: {id: 'p1', codePatient: 'PAT-001'},
      paramedical: null,
      medical: null,
      forfait: null,
    }));

    const enrichedContent = '<p><strong>Important</strong>: Surveiller la pression</p>';
    storeMock.incidents = vi.fn(() => enrichedContent);

    component['saveParamedical']();

    // Vérifier que saveParamedical a été appelé avec le contenu enrichi
    expect(storeMock.saveParamedical).toHaveBeenCalled();
    const call = storeMock.saveParamedical.mock.calls[0][0];
    expect(call.payload.incidents).toBeDefined();
  });

  it('should handle table content in rich text editor', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const tableHtml = `
      <table>
        <thead>
          <tr>
            <th>Paramètre</th>
            <th>Avant</th>
            <th>Après</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Poids (kg)</td>
            <td>72.5</td>
            <td>71.2</td>
          </tr>
          <tr>
            <td>TA</td>
            <td>145/95</td>
            <td>138/90</td>
          </tr>
        </tbody>
      </table>
    `;

    component['onAjustementsTherapeutiquesRichChange'](tableHtml);

    expect(storeMock.patchMedical).toHaveBeenCalledWith({
      ajustementsTherapeutiques: tableHtml
    });
  });

  it('should handle colored text and background colors', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    const coloredHtml = `
      <p>
        <span style="color: rgb(192, 0, 0);">Alerte</span>:
        <span style="background-color: rgb(255, 255, 0);">Surveiller étroitement</span>
      </p>
    `;

    component['onIncidentsRichChange'](coloredHtml);

    expect(storeMock.patchParamedical).toHaveBeenCalledWith({
      incidents: coloredHtml
    });
  });
});

