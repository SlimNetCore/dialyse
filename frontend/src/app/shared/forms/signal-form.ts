import {computed, Signal, signal, WritableSignal} from '@angular/core';

/**
 * Validateur de champ « signal form ».
 * Retourne un message d'erreur (clé i18n ou texte) si invalide, sinon `null`.
 */
export type SignalFormValidator<V> = (value: V) => string | null;

/**
 * Validateur « requis » réutilisable.
 * Considère invalides : null, undefined, chaîne vide/espaces, tableau vide.
 */
export function requiredValidator(message = 'PATIENT_FORM.REQUIRED'): SignalFormValidator<unknown> {
  return (value: unknown): string | null => {
    if (value === null || value === undefined) return message;
    if (typeof value === 'string' && value.trim() === '') return message;
    if (Array.isArray(value) && value.length === 0) return message;
    return null;
  };
}

/**
 * Formulaire piloté par signaux (remplace `FormGroup`/`ReactiveFormsModule`).
 *
 * - `value()` expose l'état courant sous forme de signal (réactif en mode zoneless).
 * - `valid()` / `errors()` sont des signaux dérivés recalculés automatiquement.
 * - `touched` est suivi par champ pour piloter l'affichage des messages d'erreur.
 * - `disabled` reflète le mode lecture seule sans influencer la validité métier.
 *
 * L'émission des évènements (`dataChange`, `validChange`) reste explicite dans les
 * composants afin de reproduire fidèlement la sémantique des formulaires réactifs.
 */
export class SignalForm<T extends Record<string, any>> {
  readonly value: Signal<T>;
  readonly disabled: Signal<boolean>;
  readonly errors: Signal<ReadonlyMap<keyof T, string[]>>;
  readonly valid: Signal<boolean>;
  readonly invalid: Signal<boolean>;

  private readonly initial: T;
  private readonly _value: WritableSignal<T>;
  private readonly _touched = signal<ReadonlySet<string>>(new Set<string>());
  private readonly _disabled = signal(false);
  private readonly _validators: WritableSignal<Map<keyof T, SignalFormValidator<any>[]>>;

  constructor(
    initial: T,
    validators?: Partial<Record<keyof T, SignalFormValidator<any>[]>>,
  ) {
    this.initial = {...initial};
    this._value = signal<T>({...initial});
    this.value = this._value.asReadonly();
    this.disabled = this._disabled.asReadonly();

    const map = new Map<keyof T, SignalFormValidator<any>[]>();
    if (validators) {
      for (const key of Object.keys(validators) as (keyof T)[]) {
        map.set(key, validators[key] ?? []);
      }
    }
    this._validators = signal(map);

    this.errors = computed(() => {
      const value = this._value();
      const validatorMap = this._validators();
      const result = new Map<keyof T, string[]>();
      validatorMap.forEach((validatorList, key) => {
        const messages: string[] = [];
        for (const validator of validatorList) {
          const message = validator(value[key]);
          if (message) messages.push(message);
        }
        if (messages.length > 0) result.set(key, messages);
      });
      return result;
    });
    this.valid = computed(() => this.errors().size === 0);
    this.invalid = computed(() => !this.valid());
  }

  /** Valeur courante d'un champ. */
  get<K extends keyof T>(key: K): T[K] {
    return this._value()[key];
  }

  /** Met à jour un champ (immutable). */
  set<K extends keyof T>(key: K, value: T[K]): void {
    this._value.update((current) => ({...current, [key]: value}));
  }

  /** Fusionne un ensemble partiel de champs. */
  patch(partial: Partial<T>): void {
    this._value.update((current) => ({...current, ...partial}));
  }

  /** Réinitialise le formulaire (valeurs initiales + éventuel override). */
  reset(next?: Partial<T>): void {
    this._value.set({...this.initial, ...(next ?? {})});
    this._touched.set(new Set<string>());
  }

  /** Remplace les validateurs d'un champ (validation métier dynamique). */
  setValidators<K extends keyof T>(key: K, validators: SignalFormValidator<T[K]>[]): void {
    this._validators.update((map) => {
      const next = new Map(map);
      next.set(key, validators);
      return next;
    });
  }

  /** Supprime les validateurs d'un champ. */
  clearValidators<K extends keyof T>(key: K): void {
    this._validators.update((map) => {
      if (!map.has(key)) return map;
      const next = new Map(map);
      next.delete(key);
      return next;
    });
  }

  /** Messages d'erreur d'un champ (vide si valide). */
  errorsOf<K extends keyof T>(key: K): string[] {
    return this.errors().get(key) ?? [];
  }

  /** Indique si un champ porte au moins une erreur. */
  hasError<K extends keyof T>(key: K): boolean {
    return this.errorsOf(key).length > 0;
  }

  /** Premier message d'erreur d'un champ, ou `null`. */
  firstError<K extends keyof T>(key: K): string | null {
    return this.errorsOf(key)[0] ?? null;
  }

  /** Indique si un champ a été visité (blur). */
  isTouched<K extends keyof T>(key: K): boolean {
    return this._touched().has(String(key));
  }

  /** Marque un champ comme visité. */
  markTouched<K extends keyof T>(key: K): void {
    this._touched.update((set) => {
      if (set.has(String(key))) return set;
      const next = new Set(set);
      next.add(String(key));
      return next;
    });
  }

  /** Marque tous les champs comme visités (soumission). */
  markAllTouched(): void {
    this._touched.set(new Set(Object.keys(this._value())));
  }

  /** Réinitialise l'état « touched ». */
  markUntouched(): void {
    this._touched.set(new Set<string>());
  }

  /** Erreur affichable : invalide ET visité. */
  showError<K extends keyof T>(key: K): boolean {
    return this.hasError(key) && this.isTouched(key);
  }

  /** Passe le formulaire en lecture seule (n'affecte pas la validité métier). */
  disable(): void {
    this._disabled.set(true);
  }

  /** Réactive l'édition. */
  enable(): void {
    this._disabled.set(false);
  }

  /** Applique l'état lecture seule. */
  setDisabled(disabled: boolean): void {
    this._disabled.set(disabled);
  }
}

