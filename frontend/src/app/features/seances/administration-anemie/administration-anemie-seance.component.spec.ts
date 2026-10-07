import {provideZonelessChangeDetection, signal, WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AdministrationAnemieSeanceComponent} from './administration-anemie-seance.component';
import {DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';
import {StockApiService} from '../../../core/api/stock-api.service';
import {AuthStore} from '../../../core/state/auth.store';
import {WebSocketService, WsEvent} from '../../../core/ws/websocket.service';

const CENTRE = 'c1';

const prescription = {
  id: 'p1', patientId: 'pat', centerId: CENTRE, epoArticleId: 'a-epo', epoArticleCode: 'EPO-4000',
  epoArticleLibelle: 'Époétine 4000 UI', epoDoseUi: 8000, epoVoie: 'IV', epoFrequenceValeur: 3,
  epoFrequenceUnite: 'SEMAINE', ferArticleId: null,
};

describe('AdministrationAnemieSeanceComponent — conversion dose → stock', () => {
  let stockApi: Record<string, ReturnType<typeof vi.fn>>;
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let lastEvent: WritableSignal<WsEvent | null>;

  beforeEach(async () => {
    lastEvent = signal<WsEvent | null>(null);
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
        {provide: WebSocketService, useValue: {lastEvent}},
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

  describe('prescription relevée de 4000 à 8000 UI après une première administration de 4000 UI', () => {
    const observance = {
      epo: {
        periodeDebut: '2026-10-05', periodeFin: '2026-10-11', dosesAttendues: 8000, dosesAdministrees: 4000,
        dosesRestantes: 1, joursRestants: 4, uniteDose: 'UI', dosePrescrite: 8000,
        doseAttendue: 8000, doseAdministree: 4000, doseRestante: 4000,
      },
      fer: null,
    };
    const dejaAdministre = {
      items: [{id: 'a1', seanceId: 's1', typeTraitement: 'EPO', administree: true, dose: 4000}],
      total: 1, page: 0, size: 100,
    };

    async function ouvrir() {
      api['getObservanceAnemie'].mockReturnValue(of(observance));
      api['listAdministrationsAnemie'].mockReturnValue(of(dejaAdministre));
      const fixture = TestBed.createComponent(AdministrationAnemieSeanceComponent);
      fixture.componentRef.setInput('patientId', 'pat');
      fixture.componentRef.setInput('seanceId', 's1');
      fixture.componentRef.setInput('seanceDate', '2026-10-05');
      fixture.componentRef.setInput('centerId', CENTRE);
      fixture.componentRef.setInput('canEdit', true);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      return {fixture, root: fixture.nativeElement as HTMLElement};
    }

    it('affiche le reste en quantité et non « 0 dose restante »', async () => {
      const {root} = await ouvrir();

      const texte = root.querySelector('[data-testid="observance-epo"]')!.textContent!;
      expect(texte).toContain('SEANCES.RESTE_A_ADMINISTRER_DOSE');
      expect(texte).not.toContain('SEANCES.RESTE_A_ADMINISTRER ');
    });

    it('autorise le complément alors que l\'EPO est déjà administrée à cette séance', async () => {
      const {fixture} = await ouvrir();
      const component = fixture.componentInstance as unknown as {
        peutAdministrer(t: 'EPO' | 'FER_INJECTABLE'): boolean;
      };

      expect(component.peutAdministrer('EPO')).toBe(true);
    });

    it('propose exactement le reste (4000 UI) comme dose du complément', async () => {
      const {fixture} = await ouvrir();
      const component = fixture.componentInstance as unknown as {
        openCreateForm(t: 'EPO' | 'FER_INJECTABLE'): void;
        form: { value(): { dose: number | null } };
      };

      component.openCreateForm('EPO');

      expect(component.form.value().dose).toBe(4000);
    });

    describe('temps réel : le médecin relève la prescription pendant que la page est ouverte', () => {
      const evenement = (centerId: string, patientId: string): WsEvent =>
        ({type: 'PRESCRIPTION_CHANGED', centerId, payload: {patientId}, timestamp: '2026-10-07T10:00:00Z'}) as WsEvent;

      async function ouvrirPuisRelever(evt: WsEvent) {
        const {fixture} = await ouvrir();
        const appelsAvant = api['getActivePrescription'].mock.calls.length;
        api['getActivePrescription'].mockReturnValue(of({...prescription, epoDoseUi: 12000}));
        lastEvent.set(evt);
        fixture.detectChanges();
        await fixture.whenStable();
        return {fixture, appelsAvant};
      }

      it('relit prescription, administrations et reste sans rechargement', async () => {
        const {fixture, appelsAvant} = await ouvrirPuisRelever(evenement(CENTRE, 'pat'));

        expect(api['getActivePrescription'].mock.calls.length).toBe(appelsAvant + 1);
        expect((fixture.componentInstance as unknown as {
          prescription(): { epoDoseUi: number }
        }).prescription().epoDoseUi)
          .toBe(12000);
      });

      it('ignore la prescription d\'un autre patient ou d\'un autre centre', async () => {
        const autrePatient = await ouvrirPuisRelever(evenement(CENTRE, 'autre'));
        expect(api['getActivePrescription'].mock.calls.length).toBe(autrePatient.appelsAvant);

        const autreCentre = await ouvrirPuisRelever(evenement('c2', 'pat'));
        expect(api['getActivePrescription'].mock.calls.length).toBe(autreCentre.appelsAvant);
      });
    });

    it('ne propose plus d\'administration quand la quantité prescrite est atteinte', async () => {
      api['getObservanceAnemie'].mockReturnValue(of({
        epo: {...observance.epo, doseAdministree: 8000, doseRestante: 0, dosesRestantes: 0},
        fer: null,
      }));
      api['listAdministrationsAnemie'].mockReturnValue(of(dejaAdministre));
      const fixture = TestBed.createComponent(AdministrationAnemieSeanceComponent);
      fixture.componentRef.setInput('patientId', 'pat');
      fixture.componentRef.setInput('seanceId', 's1');
      fixture.componentRef.setInput('seanceDate', '2026-10-05');
      fixture.componentRef.setInput('centerId', CENTRE);
      fixture.componentRef.setInput('canEdit', true);
      fixture.detectChanges();
      await fixture.whenStable();

      const component = fixture.componentInstance as unknown as {
        peutAdministrer(t: 'EPO' | 'FER_INJECTABLE'): boolean;
      };
      expect(component.peutAdministrer('EPO')).toBe(false);
    });
  });
});
