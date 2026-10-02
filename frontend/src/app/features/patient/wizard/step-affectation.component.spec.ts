import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {StepAffectationComponent} from './step-affectation.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {
  CategoriesTransportStore,
  MedecinsStore,
  PositionsStore,
  SallesStore,
  TransporteursStore,
} from '../../../core/state/referentials.store';
import {signal} from '@angular/core';

function referentialMock() {
  return {items: signal([]), ensureLoaded: vi.fn()};
}

describe('StepAffectationComponent (signal forms)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepAffectationComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: SallesStore, useValue: referentialMock()},
        {provide: MedecinsStore, useValue: referentialMock()},
        {provide: PositionsStore, useValue: referentialMock()},
        {provide: TransporteursStore, useValue: referentialMock()},
        {provide: CategoriesTransportStore, useValue: referentialMock()},
      ],
    }).compileComponents();
  });

  it('reste toujours valide (étape facultative)', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isValid()).toBe(true);
  });

  it('émet les données et la validité lors de la sélection du médecin traitant', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const dataSpy = vi.fn();
    const validSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);
    component.validChange.subscribe(validSpy);

    component.onSelect('medecinTraitantId', {id: 'med-1', label: 'Dr Test'});

    expect(component.form.get('medecinTraitantId')).toBe('med-1');
    expect(dataSpy).toHaveBeenCalledWith(expect.objectContaining({medecinTraitantId: 'med-1'}));
    expect(validSpy).toHaveBeenCalledWith(true);
  });

  it('ne règle salle, créneau, générateur et jours que via une proposition du planificateur', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const dataSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);

    expect(component.aUnPlacement()).toBe(false);
    component.appliquerProposition({
      salle: {id: 'salle-1', nom: 'Salle 1'}, creneau: {id: 'pos-1', libelle: 'Matin', ordre: 1},
      generateur: {id: 'gen-1', code: 'G01', salleId: 'salle-1'}, generateursAlternatifs: [],
      jours: ['LUNDI', 'JEUDI'], score: 90, raisons: [], fermetures: [],
    });

    expect(component.form.get('salleId')).toBe('salle-1');
    expect(component.form.get('positionId')).toBe('pos-1');
    expect(component.form.get('generateurId')).toBe('gen-1');
    expect(component.form.get('jourLundi')).toBe(true);
    expect(component.form.get('jourJeudi')).toBe(true);
    expect(component.form.get('jourMardi')).toBe(false);
    expect(component.aUnPlacement()).toBe(true);
    expect(dataSpy).toHaveBeenCalledWith(expect.objectContaining({salleId: 'salle-1', jourLundi: true}));
  });

  it('retire le placement de la fiche', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.patchData({
      salle_id: 'salle-9',
      position_id: 'pos-2',
      generateur_id: 'gen-3',
      jours_dialyse: {lundi: true}
    });
    expect(component.aUnPlacement()).toBe(true);

    component.effacerPlacement();

    expect(component.aUnPlacement()).toBe(false);
    expect(component.form.get('salleId')).toBeNull();
    expect(component.form.get('generateurId')).toBeNull();
    expect(component.form.get('jourLundi')).toBe(false);
  });

  it('patchData normalise les identifiants (camel/snake/nested) et les jours', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const validSpy = vi.fn();
    component.validChange.subscribe(validSpy);

    component.patchData({
      salle_id: 'salle-9',
      medecinTraitant: {id: 'med-3'},
      position: {value: 'pos-2'},
      jours_dialyse: {lundi: true, mercredi: true},
    });

    expect(component.form.get('salleId')).toBe('salle-9');
    expect(component.form.get('medecinTraitantId')).toBe('med-3');
    expect(component.form.get('positionId')).toBe('pos-2');
    expect(component.form.get('jourLundi')).toBe(true);
    expect(component.form.get('jourMercredi')).toBe(true);
    expect(component.form.get('jourMardi')).toBe(false);
    expect(validSpy).toHaveBeenCalledWith(true);
  });

  it('ignore les changements en mode lecture seule', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.componentRef.setInput('readonly', true);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onSelect('medecinTraitantId', {id: 'med-1', label: 'Dr Test'});
    component.appliquerProposition({
      salle: {id: 'salle-1', nom: 'Salle 1'}, creneau: {id: 'pos-1', libelle: 'Matin', ordre: 1},
      generateur: {id: 'gen-1', code: 'G01', salleId: 'salle-1'}, generateursAlternatifs: [],
      jours: ['LUNDI'], score: 90, raisons: [], fermetures: [],
    });

    expect(component.form.get('medecinTraitantId')).toBeNull();
    expect(component.form.get('salleId')).toBeNull();
    expect(component.form.get('jourLundi')).toBe(false);
  });
});

