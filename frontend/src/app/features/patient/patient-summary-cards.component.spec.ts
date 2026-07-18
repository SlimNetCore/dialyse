import {TestBed} from '@angular/core/testing';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PatientSummaryCardsComponent} from './patient-summary-cards.component';

type PatientSummary = {
  totalPatients: number;
  sexDistribution: Array<{ code: string; label: string; count: number }>;
  ageDistribution: Array<{ code: string; label: string; count: number }>;
  ktDistribution: Array<{ code: string; label: string; count: number }>;
};

describe('PatientSummaryCardsComponent', () => {
  const summary: PatientSummary = {
    totalPatients: 4,
    sexDistribution: [
      {code: 'M', label: 'Masculin', count: 2},
      {code: 'F', label: 'Féminin', count: 1},
      {code: 'AUTRE', label: 'Autre / inconnu', count: 1},
    ],
    ageDistribution: [
      {code: '0_17', label: '0-17 ans', count: 1},
      {code: '18_29', label: '18-29 ans', count: 1},
      {code: '30_44', label: '30-44 ans', count: 1},
      {code: '45_59', label: '45-59 ans', count: 1},
      {code: '60_PLUS', label: '60 ans et +', count: 0},
      {code: 'INCONNU', label: 'Non renseigné', count: 0},
    ],
    ktDistribution: [
      {code: 'OUI', label: 'Sous KT', count: 3},
      {code: 'NON', label: 'Sans KT', count: 1},
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PatientSummaryCardsComponent, TranslateModule.forRoot()],
    }).compileComponents();

    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      PATIENT_LIST: {
        SUMMARY_EYEBROW: 'Synthèse du centre',
        SUMMARY_TITLE: 'Répartition des patients',
        SUMMARY_EMPTY: 'Aucune donnée patient à afficher',
        SUMMARY_TOTAL: '{{count}} patient(s) au total',
        SUMMARY_SEX_TITLE: 'Répartition par sexe',
        SUMMARY_AGE_TITLE: 'Répartition par âge',
        SUMMARY_KT_TITLE: 'Patients sous KT',
        SUMMARY_MONTH: 'Mois des statistiques',
        BTN_PRINT_REPORT: 'Imprimer le rapport',
      },
    }, true);
    translate.setDefaultLang('fr');
    translate.use('fr');
  });

  it('affiche les répartitions et le bouton d impression', () => {
    const fixture = TestBed.createComponent(PatientSummaryCardsComponent);
    fixture.componentInstance.summaryInput = summary;
    fixture.componentInstance.selectedMonthInput = '2026-06';
    fixture.componentInstance.loadingInput = false;
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Répartition des patients');
    expect(text).toContain('Masculin');
    expect(text).toContain('Patients sous KT');
    expect(text).toContain('Imprimer le rapport');
    expect(text).toContain('Mois des statistiques');
  });

  it('emet une demande d impression', () => {
    const fixture = TestBed.createComponent(PatientSummaryCardsComponent);
    fixture.componentInstance.summaryInput = summary;
    fixture.componentInstance.selectedMonthInput = '2026-06';
    fixture.detectChanges();

    const spy = vi.fn();
    fixture.componentInstance.printReport.subscribe(spy);

    const button = fixture.nativeElement.querySelector('[data-testid="print-patient-report"]') as HTMLButtonElement | null;
    if (button) {
      button.click();
    } else {
      const allButtons = fixture.nativeElement.querySelectorAll('button');
      allButtons[0]?.click();
    }

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('2026-06');
  });

  it('emet un changement de mois valide', () => {
    const fixture = TestBed.createComponent(PatientSummaryCardsComponent);
    fixture.componentInstance.selectedMonthInput = '2026-06';
    fixture.detectChanges();

    const spy = vi.fn();
    fixture.componentInstance.monthChanged.subscribe(spy);

    const input = fixture.nativeElement.querySelector('[data-testid="patient-summary-month"]') as HTMLInputElement;
    input.value = '2026-07';
    input.dispatchEvent(new Event('change'));

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('2026-07');
  });
});





