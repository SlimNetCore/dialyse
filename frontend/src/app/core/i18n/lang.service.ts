import {inject, Injectable, signal} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {DateAdapter} from '@angular/material/core';
import {TranslateService} from '@ngx-translate/core';

export type AppLang = 'fr' | 'en' | 'ar' | 'kab';

export interface LangOption {
  code: AppLang;
  label: string;
  dir: 'ltr' | 'rtl';
  flag: string;
}

export const LANGUAGES: LangOption[] = [
  { code: 'fr',  label: 'Français',  dir: 'ltr', flag: '🇫🇷' },
  { code: 'en',  label: 'English',   dir: 'ltr', flag: '🇬🇧' },
  { code: 'ar',  label: 'العربية',   dir: 'rtl', flag: '🇩🇿' },
  { code: 'kab', label: 'Taqbaylit', dir: 'ltr', flag: 'ⵣ'  },
];

const STORAGE_KEY = 'hemodialyse.lang';

@Injectable({ providedIn: 'root' })
export class LangService {
  private readonly translate = inject(TranslateService);
  private readonly doc = inject(DOCUMENT);
  private readonly dateAdapter = inject(DateAdapter<Date>, {optional: true});

  readonly currentLang = signal<AppLang>('fr');
  readonly languages = LANGUAGES;

  constructor() {
    this.translate.addLangs(['fr', 'en', 'ar', 'kab']);
    const saved = (localStorage.getItem(STORAGE_KEY) as AppLang) || 'fr';
    this.setLang(saved);
  }

  setLang(lang: AppLang): void {
    this.translate.use(lang);
    this.currentLang.set(lang);
    localStorage.setItem(STORAGE_KEY, lang);

    const opt = LANGUAGES.find(l => l.code === lang)!;
    const html = this.doc.documentElement;
    html.setAttribute('lang', lang);
    html.setAttribute('dir', opt.dir);

    // Keep Material datepickers in sync with selected app language.
    this.dateAdapter?.setLocale(this.toDateLocale(lang));
  }

  private toDateLocale(lang: AppLang): string {
    if (lang === 'fr') return 'fr-FR';
    if (lang === 'en') return 'en-GB';
    if (lang === 'ar') return 'ar-DZ';
    return 'fr-DZ';
  }
}

