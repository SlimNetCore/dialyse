import {inject, Injectable} from '@angular/core';
import {AppMode, AppTheme, ThemeStore} from '../state/theme.store';

@Injectable({providedIn: 'root'})
export class ThemeService {
  private readonly store = inject(ThemeStore);
  readonly themes = this.store.themes;
  readonly modes = this.store.modes;
  readonly currentTheme = this.store.currentTheme;
  readonly currentMode = this.store.currentMode;

  setTheme(theme: AppTheme): void {
    this.store.setTheme(theme);
  }

  setMode(mode: AppMode): void {
    this.store.setMode(mode);
  }
}


