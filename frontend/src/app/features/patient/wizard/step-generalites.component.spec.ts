import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {AuthStore} from '../../../core/state/auth.store';
import {StepGeneralitesComponent} from './step-generalites.component';

describe('StepGeneralitesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepGeneralitesComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        {
          provide: AuthStore,
          useValue: {
            hasRole: () => false,
          },
        },
      ],
    }).compileComponents();

    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      PATIENT_FORM: {
        DATE_EVENEMENT_ETAT: "Date de l'événement",
        DATE_SORTIE: 'Date de sortie',
        DATE_GREFFE: 'Date de la greffe',
        DATE_GUERISON: 'Date de guérison',
        DATE_DECES: 'Date du décès',
        DATE_TRANSFERT: 'Date du transfert',
      },
    }, true);
    translate.setDefaultLang('fr');
    translate.use('fr');
  });

  it('affiche la date d évènement pour les statuts demandés avec le bon libellé', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance;

    component.form.patchValue({etatPatient: 'VACANCIER_LOCAL'});
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_SORTIE');

    component.form.patchValue({etatPatient: 'GREFFE'});
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_GREFFE');

    component.form.patchValue({etatPatient: 'GUERRI'});
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_GUERISON');
  });

  it('vide la date d évènement quand le statut ne la requiert plus', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const dateEvenement = new Date(2026, 5, 12);

    component.form.patchValue({
      etatPatient: 'GREFFE',
      dateEvenementEtat: dateEvenement,
    });
    expect(component.form.get('dateEvenementEtat')?.value).toEqual(dateEvenement);

    component.form.patchValue({etatPatient: 'PERMANENT'});

    expect(component.showDateEvenement()).toBe(false);
    expect(component.form.get('dateEvenementEtat')?.value).toBeNull();
  });
});


