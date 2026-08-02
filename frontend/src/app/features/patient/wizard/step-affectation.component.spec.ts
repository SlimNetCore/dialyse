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

  it('émet les données et la validité lors de la sélection d’une salle', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const dataSpy = vi.fn();
    const validSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);
    component.validChange.subscribe(validSpy);

    component.onSelect('salleId', {id: 'salle-1', label: 'Salle 1'});

    expect(component.form.get('salleId')).toBe('salle-1');
    expect(dataSpy).toHaveBeenCalledWith(expect.objectContaining({salleId: 'salle-1'}));
    expect(validSpy).toHaveBeenCalledWith(true);
  });

  it('bascule un jour de dialyse via la checkbox', () => {
    const fixture = TestBed.createComponent(StepAffectationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onDay('jourLundi', true);
    expect(component.form.get('jourLundi')).toBe(true);

    component.onDay('jourLundi', false);
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

    component.onSelect('salleId', {id: 'salle-1', label: 'Salle 1'});
    component.onDay('jourLundi', true);

    expect(component.form.get('salleId')).toBeNull();
    expect(component.form.get('jourLundi')).toBe(false);
  });
});

