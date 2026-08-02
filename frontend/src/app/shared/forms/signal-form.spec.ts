import {describe, expect, it} from 'vitest';
import {requiredValidator, SignalForm} from './signal-form';

interface SampleModel {
  nom: string;
  prenom: string;
  age: number | null;
  actif: boolean;
}

function createForm(): SignalForm<SampleModel> {
  return new SignalForm<SampleModel>(
    {nom: '', prenom: '', age: null, actif: false},
    {
      nom: [requiredValidator()],
      prenom: [requiredValidator()],
    },
  );
}

describe('SignalForm', () => {
  it('expose les valeurs initiales via le signal value()', () => {
    const form = createForm();
    expect(form.value()).toEqual({nom: '', prenom: '', age: null, actif: false});
    expect(form.get('actif')).toBe(false);
  });

  it('met à jour un champ avec set() de façon immuable', () => {
    const form = createForm();
    const before = form.value();
    form.set('nom', 'Dupont');
    expect(form.get('nom')).toBe('Dupont');
    expect(form.value()).not.toBe(before);
    expect(before.nom).toBe('');
  });

  it('fusionne plusieurs champs avec patch()', () => {
    const form = createForm();
    form.patch({nom: 'Dupont', prenom: 'Jean'});
    expect(form.value()).toEqual({nom: 'Dupont', prenom: 'Jean', age: null, actif: false});
  });

  it('calcule la validité à partir des validateurs requis', () => {
    const form = createForm();
    expect(form.valid()).toBe(false);
    expect(form.hasError('nom')).toBe(true);

    form.patch({nom: 'Dupont', prenom: 'Jean'});
    expect(form.valid()).toBe(true);
    expect(form.hasError('nom')).toBe(false);
    expect(form.errorsOf('nom')).toEqual([]);
  });

  it('n’affiche l’erreur qu’après visite du champ (showError)', () => {
    const form = createForm();
    expect(form.showError('nom')).toBe(false);
    form.markTouched('nom');
    expect(form.showError('nom')).toBe(true);

    form.set('nom', 'Dupont');
    expect(form.showError('nom')).toBe(false);
  });

  it('markAllTouched marque tous les champs comme visités', () => {
    const form = createForm();
    form.markAllTouched();
    expect(form.isTouched('nom')).toBe(true);
    expect(form.isTouched('prenom')).toBe(true);
    expect(form.isTouched('age')).toBe(true);
  });

  it('ajoute et retire des validateurs dynamiquement', () => {
    const form = createForm();
    form.patch({nom: 'Dupont', prenom: 'Jean'});
    expect(form.valid()).toBe(true);

    form.setValidators('age', [requiredValidator()]);
    expect(form.valid()).toBe(false);
    expect(form.hasError('age')).toBe(true);

    form.set('age', 42);
    expect(form.valid()).toBe(true);

    form.set('age', null);
    expect(form.valid()).toBe(false);

    form.clearValidators('age');
    expect(form.valid()).toBe(true);
  });

  it('gère l’état disabled sans impacter la validité métier', () => {
    const form = createForm();
    expect(form.disabled()).toBe(false);
    form.disable();
    expect(form.disabled()).toBe(true);
    // La validité reste calculée sur les valeurs, indépendamment du mode lecture seule.
    form.patch({nom: 'Dupont', prenom: 'Jean'});
    expect(form.valid()).toBe(true);
    form.enable();
    expect(form.disabled()).toBe(false);
  });

  it('reset restaure les valeurs initiales et l’état touched', () => {
    const form = createForm();
    form.patch({nom: 'Dupont', prenom: 'Jean'});
    form.markAllTouched();

    form.reset();
    expect(form.value()).toEqual({nom: '', prenom: '', age: null, actif: false});
    expect(form.isTouched('nom')).toBe(false);

    form.reset({nom: 'Init'});
    expect(form.get('nom')).toBe('Init');
  });

  it('requiredValidator considère les chaînes vides et tableaux vides comme invalides', () => {
    const validate = requiredValidator('X');
    expect(validate(null)).toBe('X');
    expect(validate(undefined)).toBe('X');
    expect(validate('')).toBe('X');
    expect(validate('   ')).toBe('X');
    expect(validate([])).toBe('X');
    expect(validate('ok')).toBeNull();
    expect(validate(0)).toBeNull();
    expect(validate(false)).toBeNull();
  });
});

