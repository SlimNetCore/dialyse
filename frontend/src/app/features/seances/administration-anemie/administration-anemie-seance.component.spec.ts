import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AdministrationAnemieSeanceComponent} from './administration-anemie-seance.component';
import {DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';
import {StockApiService} from '../../../core/api/stock-api.service';
import {AuthStore} from '../../../core/state/auth.store';

const CENTRE = 'c1';

const prescription = {
  id: 'p1', patientId: 'pat', centerId: CENTRE, epoArticleId: 'a-epo', epoArticleCode: 'EPO-4000',
  epoArticleLibelle: 'Époétine 4000 UI', epoDoseUi: 8000, epoVoie: 'IV', epoFrequenceValeur: 3,
  epoFrequenceUnite: 'SEMAINE', ferArticleId: null,
};

describe('AdministrationAnemieSeanceComponent — conversion dose → stock', () => {
  let stockApi: Record<string, ReturnType<typeof vi.fn>>;
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    stockApi = {
      getArticle: vi.fn().mockReturnValue(of({
        id: 'a-epo', unite: 'seringue', dosageParUnite: 4000, uniteDosage: 'UI',
      })),
    };
    api = {
      getActivePrescription: vi.fn().mockReturnValue(of(prescription)),
      getObservanceAnemie: vi.fn().mockReturnValue(of(null)),
      listAdministrationsAnemie: vi.fn().mockReturnValue(of({items: [], total: 0, page: 0, size: 100})),
      createAdministrationAnemie: vi.fn().mockReturnValue(of({})),
    };
    await TestBed.configureTestingModule({
      imports: [AdministrationAnemieSeanceComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: DossierMedicalApiService, useValue: api},
        {provide: StockApiService, useValue: stockApi},
        {provide: AuthStore, useValue: {username: () => 'inf-01'}},
      ],
    }).compileComponents();
  });

  async function openEpoForm() {
    const fixture = TestBed.createComponent(AdministrationAnemieSeanceComponent);
    fixture.componentRef.setInput('patientId', 'pat');
    fixture.componentRef.setInput('seanceId', 's1');
    fixture.componentRef.setInput('seanceDate', '2026-10-05');
    fixture.componentRef.setInput('centerId', CENTRE);
    fixture.componentRef.setInput('canEdit', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.prescription-card button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root};
  }

  it('affiche la quantité de stock déduite de la dose prescrite', async () => {
    const {root} = await openEpoForm();

    expect(stockApi['getArticle']).toHaveBeenCalledWith(CENTRE, 'a-epo');
    expect(root.querySelector('[data-testid="admin-conversion"]')!.textContent).toContain('SEANCES.CONVERSION_OK');
  });

  it('n\'envoie plus de quantité saisie : le serveur la déduit de la fiche article', async () => {
    const {fixture, root} = await openEpoForm();

    (fixture.componentInstance as unknown as { save(): void }).save();

    expect(api['createAdministrationAnemie']).toHaveBeenCalledWith('pat', expect.objectContaining({
      articleId: 'a-epo', dose: 8000, uniteDose: 'UI', quantiteArticle: null,
    }));
    expect(root.querySelector('input[type="number"][step="0.001"]')).toBeNull();
  });

  it('bloque l\'enregistrement quand l\'unité de la dose est incompatible avec la fiche article', async () => {
    stockApi['getArticle'].mockReturnValue(of({
      id: 'a-epo', unite: 'seringue', dosageParUnite: 100, uniteDosage: 'mg',
    }));
    const {fixture, root} = await openEpoForm();

    (fixture.componentInstance as unknown as { save(): void }).save();

    expect(api['createAdministrationAnemie']).not.toHaveBeenCalled();
    expect(root.querySelector('[data-testid="admin-conversion"]')!.textContent)
      .toContain('SEANCES.CONVERSION_ERR.UNITE_INCOMPATIBLE');
  });
});
