import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthStore} from '../../../core/state/auth.store';
import {StepGeneralitesComponent} from './step-generalites.component';

describe('StepGeneralitesComponent (signal forms)', () => {
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

  it('affiche la date d’évènement pour les statuts demandés avec le bon libellé', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onEtatPatientChange('VACANCIER_LOCAL');
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_FIN_SEJOUR');

    component.onEtatPatientChange('GREFFE');
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_GREFFE');

    component.onEtatPatientChange('GUERRI');
    expect(component.showDateEvenement()).toBe(true);
    expect(component.dateEvenementLabelKey()).toBe('PATIENT_FORM.DATE_GUERISON');
  });

  it('vide la date d’évènement quand le statut ne la requiert plus', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const dateEvenement = new Date(2026, 5, 12);

    component.onEtatPatientChange('GREFFE');
    component.onDateEvenement(dateEvenement);
    expect(component.form.get('dateEvenementEtat')).toEqual(dateEvenement);

    component.onEtatPatientChange('PERMANENT');
    expect(component.showDateEvenement()).toBe(false);
    expect(component.form.get('dateEvenementEtat')).toBeNull();
  });

  it('exige nom, prénom, sexe, date d’admission et date de naissance', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.isValid()).toBe(false);

    component.onText('nom', 'Dupont');
    component.onText('prenom', 'Jean');
    component.onSelect('sexe', {id: 'M', label: 'Masculin'});
    component.onDate('dateAdmission', new Date(2026, 0, 10));
    expect(component.isValid()).toBe(false);

    component.onDate('dateNaissance', new Date(1990, 0, 1));
    expect(component.isValid()).toBe(true);
  });

  it('calcule l’âge à partir de la date de naissance', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const birth = new Date();
    birth.setFullYear(birth.getFullYear() - 30);
    component.onDate('dateNaissance', birth);
    expect(component.calculatedAge()).toBe(30);
  });

  it('patchData renseigne les champs et émet la validité', () => {
    const fixture = TestBed.createComponent(StepGeneralitesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const validSpy = vi.fn();
    component.validChange.subscribe(validSpy);

    component.patchData({
      nom: 'Dupont',
      prenom: 'Jean',
      sexe: 'M',
      dateAdmission: '2026-01-10',
      dateNaissance: '1990-01-01',
      etatPatient: 'PERMANENT',
    });

    expect(component.form.get('nom')).toBe('Dupont');
    expect(component.form.get('sexe')).toBe('M');
    expect(component.isValid()).toBe(true);
    expect(validSpy).toHaveBeenLastCalledWith(true);
  });

  describe('date d’évènement obligatoire pour un état de sortie (RG-PAT-031)', () => {
    const base = {
      nom: 'Dupont', prenom: 'Jean', sexe: 'M', dateAdmission: '2026-01-10', dateNaissance: '1990-01-01',
    };

    it.each(['TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI'])('%s sans date rend le formulaire invalide', (etat) => {
      const fixture = TestBed.createComponent(StepGeneralitesComponent);
      fixture.detectChanges();
      const component = fixture.componentInstance;
      const validSpy = vi.fn();
      component.validChange.subscribe(validSpy);
      component.patchData({...base, etatPatient: 'PERMANENT'});

      component.onEtatPatientChange(etat);

      expect(component.isValid()).toBe(false);
      expect(component.form.firstError('dateEvenementEtat')).toBe('PATIENT_FORM.DATE_EVENEMENT_REQUISE');
      expect(validSpy).toHaveBeenLastCalledWith(false);
    });

    it('devient valide une fois la date saisie', () => {
      const fixture = TestBed.createComponent(StepGeneralitesComponent);
      fixture.detectChanges();
      const component = fixture.componentInstance;
      component.patchData({...base, etatPatient: 'PERMANENT'});
      component.onEtatPatientChange('DECEDE');

      component.onDateEvenement(new Date(2026, 8, 20));

      expect(component.isValid()).toBe(true);
    });

    it('la fin de séjour d’un vacancier reste facultative', () => {
      const fixture = TestBed.createComponent(StepGeneralitesComponent);
      fixture.detectChanges();
      const component = fixture.componentInstance;
      component.patchData({...base, etatPatient: 'PERMANENT'});

      component.onEtatPatientChange('VACANCIER_LOCAL');

      expect(component.isValid()).toBe(true);
    });

    it('revenir à l’état permanent supprime l’obligation', () => {
      const fixture = TestBed.createComponent(StepGeneralitesComponent);
      fixture.detectChanges();
      const component = fixture.componentInstance;
      component.patchData({...base, etatPatient: 'PERMANENT'});
      component.onEtatPatientChange('TRANSFERE');

      component.onEtatPatientChange('PERMANENT');

      expect(component.isValid()).toBe(true);
    });

    it('un patient chargé en état de sortie sans date est signalé invalide', () => {
      const fixture = TestBed.createComponent(StepGeneralitesComponent);
      fixture.detectChanges();
      const component = fixture.componentInstance;

      component.patchData({...base, etatPatient: 'TRANSFERE'});

      expect(component.isValid()).toBe(false);
    });
  });
});
