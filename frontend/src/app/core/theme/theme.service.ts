import {DOCUMENT} from '@angular/common';
import {inject, Injectable, signal} from '@angular/core';

export type AppTheme = 'cyan' | 'emerald' | 'indigo';

export interface ThemeOption {
  code: AppTheme;
  i18nKey: string;
}

const STORAGE_KEY = 'hemodialyse.theme';

@Injectable({providedIn: 'root'})
export class ThemeService {
  readonly themes: ThemeOption[] = [
    {code: 'cyan', i18nKey: 'THEME.CYAN'},
    {code: 'emerald', i18nKey: 'THEME.EMERALD'},
    {code: 'indigo', i18nKey: 'THEME.INDIGO'}
  ];
  readonly currentTheme = signal<AppTheme>('cyan');
  private readonly doc = inject(DOCUMENT);

  constructor() {
    const saved = localStorage.getItem(STORAGE_KEY) as AppTheme | null;
    this.setTheme(saved ?? 'cyan');
  }

  setTheme(theme: AppTheme): void {
    const exists = this.themes.some(t => t.code === theme);
    const safeTheme = exists ? theme : 'cyan';
    this.currentTheme.set(safeTheme);
    localStorage.setItem(STORAGE_KEY, safeTheme);
    this.doc.documentElement.setAttribute('data-theme', safeTheme);
  }
}


