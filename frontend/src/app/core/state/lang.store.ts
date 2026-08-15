import {DOCUMENT} from '@angular/common';
import {inject} from '@angular/core';
import {DateAdapter} from '@angular/material/core';
import {patchState, signalStore, withHooks, withMethods, withState} from '@ngrx/signals';
import {TranslateService} from '@ngx-translate/core';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

export type AppLang = 'fr' | 'en' | 'ar' | 'kab';

export interface LangOption {
  code: AppLang;
  label: string;
  dir: 'ltr' | 'rtl';
  flag: string;
}

export const LANGUAGES: LangOption[] = [
  {code: 'fr', label: 'Français', dir: 'ltr', flag: ''},
  {code: 'en', label: 'English', dir: 'ltr', flag: ''},
  {code: 'ar', label: 'العربية', dir: 'rtl', flag: ''},
  {code: 'kab', label: 'Taqbaylit', dir: 'ltr', flag: 'ⵣ'}
];

const STORAGE_KEY = 'hemodialyse.lang';

type LangState = {
  currentLang: AppLang;
  languages: LangOption[];
};

const initialState: LangState = {
  currentLang: 'fr',
  languages: LANGUAGES
};

export const LangStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('LangStore'),
  withMethods((store) => {
    const translate = inject(TranslateService);
    const doc = inject(DOCUMENT);
    const dateAdapter = inject(DateAdapter<Date>, {optional: true});

    return {
      setLang(lang: AppLang): void {
        translate.use(lang);
        patchState(store, {currentLang: lang});
        localStorage.setItem(STORAGE_KEY, lang);

        const option = LANGUAGES.find((l) => l.code === lang) ?? LANGUAGES[0];
        const html = doc.documentElement;
        html.setAttribute('lang', lang);
        html.setAttribute('dir', option.dir);

        // Keep Material datepickers aligned with selected app language.
        dateAdapter?.setLocale(toDateLocale(lang));
      }
    };
  }),
  withHooks((store) => {
    const translate = inject(TranslateService);

    return {
      onInit() {
        translate.addLangs(['fr', 'en', 'ar', 'kab']);

        const saved = (localStorage.getItem(STORAGE_KEY) as AppLang) || 'fr';
        store.setLang(saved);
      }
    };
  })
);

function toDateLocale(lang: AppLang): string {
  if (lang === 'fr') return 'fr-FR';
  if (lang === 'en') return 'en-GB';
  if (lang === 'ar') return 'ar-DZ';
  return 'fr-DZ';
}


