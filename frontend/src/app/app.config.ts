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
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader, TranslateHttpLoader} from '@ngx-translate/http-loader';
import {firstValueFrom} from 'rxjs';
import {MAT_DATE_LOCALE, MatNativeDateModule} from '@angular/material/core';
import {MAT_FORM_FIELD_DEFAULT_OPTIONS, type MatFormFieldDefaultOptions} from '@angular/material/form-field';

import {routes} from './app.routes';
import {authInterceptor} from './core/api/auth.interceptor';
import {BackendInitService} from './core/startup/backend-init.service';

export const materialFormFieldDefaults: MatFormFieldDefaultOptions = {
  subscriptSizing: 'dynamic',
};

async function initTranslations(): Promise<void> {
  const translate = inject(TranslateService);
  translate.setDefaultLang('fr');
  const savedLang = typeof localStorage !== 'undefined' ? (localStorage.getItem('app-lang') ?? 'fr') : 'fr';
  await firstValueFrom(translate.use(savedLang));
}

function initBackendFlow(): void {
  inject(BackendInitService).start();
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
    provideAppInitializer(initTranslations),
    provideAppInitializer(initBackendFlow),
  ],
};
