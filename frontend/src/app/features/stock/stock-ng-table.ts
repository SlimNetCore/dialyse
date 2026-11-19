import {inject, Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslateService} from '@ngx-translate/core';
import {NgTableTone} from '@sbourahla/ng-table';
import {merge} from 'rxjs';
import {map} from 'rxjs/operators';

/** Tailles de page proposées par les listes du stock. */
export const STOCK_PAGE_SIZES: readonly number[] = [10, 20, 50, 100];
/** Taille de page par défaut d'une liste du stock tenue en mémoire (pagination locale). */
export const STOCK_DEFAULT_PAGE_SIZE = 20;

/** Pastille de la date de péremption d'un lot : expiré en rouge, proche en orange. */
export const TONS_PEREMPTION: Readonly<Record<string, NgTableTone>> = {EXPIRE: 'danger', PROCHE: 'warning', OK: 'neutral'};

/**
 * Signal qui change à chaque changement de langue (ou de traductions chargées) : à lire dans un `computed` qui produit
 * des en-têtes ng-table déjà traduits, pour les recalculer. À appeler dans un contexte d'injection.
 */
export function langueCourante(): Signal<number> {
  const translate = inject(TranslateService);
  return toSignal(
    merge(translate.onLangChange, translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
}
