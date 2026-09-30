import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {DossierMedicalApiService, PrescriptionMedicale} from '../../../core/api/dossier-medical-api.service';
import {ecartKg, PoidsSecSeanceComponent} from './poids-sec-seance.component';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

function prescription(poidsSecCibleKg: number | null): PrescriptionMedicale {
  return {poidsSecCibleKg} as PrescriptionMedicale;
}

describe('ecartKg', () => {
  it('retourne la différence arrondie au centième', () => {
    expect(ecartKg(72.4, 69.5)).toBe(2.9);
    expect(ecartKg(69.3, 69.5)).toBe(-0.2);
  });

  it('retourne null si une valeur manque', () => {
    expect(ecartKg(null, 69.5)).toBeNull();
    expect(ecartKg(72, null)).toBeNull();
    expect(ecartKg(undefined, undefined)).toBeNull();
  });
});

describe('PoidsSecSeanceComponent', () => {
  let api: { getActivePrescription: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {getActivePrescription: vi.fn(() => of(prescription(69.5)))};
    TestBed.configureTestingModule({
      imports: [PoidsSecSeanceComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: DossierMedicalApiService, useValue: api},
      ],
    });
  });

  async function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(PoidsSecSeanceComponent);
    for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (root: HTMLElement, testId: string) =>
    root.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? null;

  it('affiche le poids sec de la prescription en vigueur à la date de la séance et calcule les écarts', async () => {
    const root = await render({
      centerId: CENTER_ID, patientId: 'patient-1', seanceDate: '2026-09-30', poidsAvantKg: 72.4, poidsApresKg: 69.8,
    });

    expect(api.getActivePrescription).toHaveBeenCalledWith(CENTER_ID, 'patient-1', '2026-09-30');
    expect(text(root, 'poids-sec-cible')).toBe('69.5 kg');
    expect(text(root, 'poids-sec-surcharge')).toBe('+2.90 kg');
    expect(text(root, 'poids-sec-ecart-fin')).toBe('+0.30 kg');
    expect(root.querySelector('[data-testid="poids-sec-alerte"]')).toBeNull();
  });

  it('signale une surcharge supérieure à 3 kg', async () => {
    const root = await render({
      centerId: CENTER_ID, patientId: 'patient-1', seanceDate: '2026-09-30', poidsAvantKg: 73.1, poidsApresKg: null,
    });

    expect(text(root, 'poids-sec-surcharge')).toBe('+3.60 kg');
    expect(text(root, 'poids-sec-ecart-fin')).toBe('-');
    expect(root.querySelector('[data-testid="poids-sec-alerte"]')).not.toBeNull();
  });

  it('indique que le poids sec n\'est pas prescrit', async () => {
    api.getActivePrescription.mockReturnValue(of(prescription(null)));
    const root = await render({centerId: CENTER_ID, patientId: 'patient-1', seanceDate: '2026-09-30'});

    expect(root.querySelector('[data-testid="poids-sec-absent"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="poids-sec-panel"]')).toBeNull();
  });

  it('gère l\'absence de prescription (204) et les erreurs sans planter', async () => {
    api.getActivePrescription.mockReturnValue(throwError(() => new Error('403')));
    const root = await render({centerId: CENTER_ID, patientId: 'patient-1', seanceDate: '2026-09-30'});

    expect(root.querySelector('[data-testid="poids-sec-absent"]')).not.toBeNull();
  });

  it('ne charge rien sans patient', async () => {
    await render({centerId: CENTER_ID, patientId: null});
    expect(api.getActivePrescription).not.toHaveBeenCalled();
  });
});

