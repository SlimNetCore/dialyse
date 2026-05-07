import {DOCUMENT} from '@angular/common';
import {inject} from '@angular/core';
import {patchState, signalStore, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

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

const THEMES: ThemeOption[] = [
  {code: 'cyan', i18nKey: 'THEME.CYAN'},
  {code: 'emerald', i18nKey: 'THEME.EMERALD'},
  {code: 'indigo', i18nKey: 'THEME.INDIGO'}
];

const MODES: ThemeModeOption[] = [
  {code: 'light', i18nKey: 'THEME.LIGHT'},
  {code: 'dark', i18nKey: 'THEME.DARK'}
];

type ThemeState = {
  themes: ThemeOption[];
  modes: ThemeModeOption[];
  currentTheme: AppTheme;
  currentMode: AppMode;
};

const initialState: ThemeState = {
  themes: THEMES,
  modes: MODES,
  currentTheme: 'emerald',
  currentMode: 'light'
};

export const ThemeStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ThemeStore'),
  withMethods((store) => {
    const doc = inject(DOCUMENT);

    const applyTheme = (theme: AppTheme, mode: AppMode): void => {
      patchState(store, {currentTheme: theme, currentMode: mode});
      localStorage.setItem(THEME_STORAGE_KEY, theme);
      localStorage.setItem(MODE_STORAGE_KEY, mode);
      doc.documentElement.setAttribute('data-theme', theme);
      doc.documentElement.setAttribute('data-mode', mode);
    };

    return {
      setTheme(theme: AppTheme): void {
        const exists = THEMES.some((t) => t.code === theme);
        const safeTheme = exists ? theme : 'emerald';
        applyTheme(safeTheme, store.currentMode());
      },

      setMode(mode: AppMode): void {
        const exists = MODES.some((m) => m.code === mode);
        const safeMode = exists ? mode : 'light';
        applyTheme(store.currentTheme(), safeMode);
      }
    };
  }),
  withHooks((store) => ({
    onInit() {
      const savedTheme = localStorage.getItem(THEME_STORAGE_KEY) as AppTheme | null;
      const savedMode = localStorage.getItem(MODE_STORAGE_KEY) as AppMode | null;
      store.setTheme(savedTheme ?? 'emerald');
      store.setMode(savedMode ?? 'light');
    }
  }))
);


