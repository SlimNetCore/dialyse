import {DOCUMENT} from '@angular/common';
import {inject, Injectable, signal} from '@angular/core';

export type AppTheme = 'cyan' | 'emerald' | 'indigo';
export type AppMode = 'light' | 'dark';

export interface ThemeOption {
  code: AppTheme;
  i18nKey: string;
}

export interface ThemeModeOption {
  code: AppMode;
  i18nKey: string;
}

const THEME_STORAGE_KEY = 'hemodialyse.theme';
const MODE_STORAGE_KEY = 'hemodialyse.mode';

@Injectable({providedIn: 'root'})
export class ThemeService {
  readonly themes: ThemeOption[] = [
    {code: 'cyan', i18nKey: 'THEME.CYAN'},
    {code: 'emerald', i18nKey: 'THEME.EMERALD'},
    {code: 'indigo', i18nKey: 'THEME.INDIGO'}
  ];
  readonly modes: ThemeModeOption[] = [
    {code: 'light', i18nKey: 'THEME.LIGHT'},
    {code: 'dark', i18nKey: 'THEME.DARK'}
  ];
  readonly currentTheme = signal<AppTheme>('cyan');
  readonly currentMode = signal<AppMode>('light');
  private readonly doc = inject(DOCUMENT);

  constructor() {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY) as AppTheme | null;
    const savedMode = localStorage.getItem(MODE_STORAGE_KEY) as AppMode | null;
    this.applyTheme(savedTheme ?? 'cyan', savedMode ?? 'light');
  }

  setTheme(theme: AppTheme): void {
    const exists = this.themes.some(t => t.code === theme);
    const safeTheme = exists ? theme : 'cyan';
    this.applyTheme(safeTheme, this.currentMode());
  }

  setMode(mode: AppMode): void {
    const exists = this.modes.some(m => m.code === mode);
    const safeMode = exists ? mode : 'light';
    this.applyTheme(this.currentTheme(), safeMode);
  }

  private applyTheme(theme: AppTheme, mode: AppMode): void {
    this.currentTheme.set(theme);
    this.currentMode.set(mode);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
    localStorage.setItem(MODE_STORAGE_KEY, mode);
    this.doc.documentElement.setAttribute('data-theme', theme);
    this.doc.documentElement.setAttribute('data-mode', mode);
  }
}


