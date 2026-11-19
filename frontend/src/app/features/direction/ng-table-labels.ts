import {computed, inject, Injectable, Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslateService} from '@ngx-translate/core';
import {NgTableLabels, ngTableLabelsFor} from '@sbourahla/ng-table';
import {merge} from 'rxjs';

/**
 * ng-table remplace ses propres jetons `{column}` ; ngx-translate interpole `{{column}}` : on lui demande d'y remettre
 * le jeton littéral, que ng-table remplira.
 */
const NG_TABLE_TOKENS = {
  field: '{field}', column: '{column}', count: '{count}', total: '{total}', from: '{from}', to: '{to}',
  priority: '{priority}', value: '{value}', max: '{max}', index: '{index}', position: '{position}',
};

/** Clé de traduction d'un texte de ng-table : `sortAsc` → `NG_TABLE.SORT_ASC`. */
export const ngTableLabelKey = (name: string): string =>
  `NG_TABLE.${name.replace(/[A-Z]/g, (letter) => `_${letter}`).toUpperCase()}`;

/**
 * Textes propres à ng-table (tri, tableau vide, pagination, export, annonces aux lecteurs d'écran) dans la langue de
 * l'application, recalculés à chaque changement de langue : le jeu livré par ng-table pour la langue courante
 * (français, anglais, arabe ; le français pour une langue qu'il ne livre pas, comme le kabyle), puis toute clé
 * `NG_TABLE.*` des fichiers de traduction, qui l'emporte.
 *
 * Service à lier par `[labels]` plutôt qu'un `provideNgTableLabels()` dans la configuration de l'application :
 * celui-ci ferait entrer ng-table dans le paquet initial, alors qu'il n'est chargé qu'avec les pages qui l'affichent.
 */
@Injectable({providedIn: 'root'})
export class NgTableTranslatedLabels {
  private readonly translate = inject(TranslateService);
  private readonly changes = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange), {initialValue: null});

  readonly labels: Signal<Partial<NgTableLabels>> = computed(() => {
    this.changes();
    const shipped = ngTableLabelsFor(this.translate.getCurrentLang() ?? 'fr');
    const labels: Partial<NgTableLabels> = {...shipped};
    for (const name of Object.keys(shipped) as (keyof NgTableLabels)[]) {
      const key = ngTableLabelKey(name);
      const text: unknown = this.translate.instant(key, NG_TABLE_TOKENS);
      if (typeof text === 'string' && text !== key) labels[name] = text;
    }
    return labels;
  });
}
