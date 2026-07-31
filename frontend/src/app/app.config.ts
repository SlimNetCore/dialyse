import {
  ApplicationConfig,
  importProvidersFrom,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideHttpClient, withInterceptors, withXhr} from '@angular/common/http';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {provideTranslateHttpLoader, TranslateHttpLoader} from '@ngx-translate/http-loader';
import {MAT_DATE_LOCALE, MatNativeDateModule} from '@angular/material/core';
import {MAT_FORM_FIELD_DEFAULT_OPTIONS, type MatFormFieldDefaultOptions} from '@angular/material/form-field';

import {routes} from './app.routes';
import {authInterceptor} from './core/api/auth.interceptor';
import {AuthStore} from './core/state/auth.store';
import {AppShellStore} from './core/state/app-shell.store';

export const materialFormFieldDefaults: MatFormFieldDefaultOptions = {
  subscriptSizing: 'dynamic',
};

async function initAuthSession(): Promise<void> {
  const auth = inject(AuthStore);
  const store = inject(AppShellStore);

  const onLoginRoute =
    typeof window !== 'undefined' && window.location.pathname.startsWith('/login');
  if (onLoginRoute) {
    return;
  }

  await auth.initFromServer({force: true});
  const centerId = auth.centerId();
  if (centerId) {
    store.switchCenter(centerId);
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    importProvidersFrom(
      MatNativeDateModule,
      TranslateModule.forRoot({
        defaultLanguage: 'fr',
        loader: {
          provide: TranslateLoader,
          useClass: TranslateHttpLoader,
        },
      }),
    ),
    provideTranslateHttpLoader({prefix: './i18n/', suffix: '.json'}),
    {provide: MAT_DATE_LOCALE, useValue: 'fr-FR'},
    {provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: materialFormFieldDefaults},
    provideAppInitializer(initAuthSession),
  ],
};
