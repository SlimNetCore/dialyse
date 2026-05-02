import {
  APP_INITIALIZER,
  ApplicationConfig,
  importProvidersFrom,
  provideBrowserGlobalErrorListeners
} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {provideTranslateHttpLoader, TranslateHttpLoader} from '@ngx-translate/http-loader';

import {routes} from './app.routes';
import {authInterceptor} from './core/api/auth.interceptor';
import {AuthSessionService} from './core/auth/auth-session.service';
import {AppShellStore} from './core/state/app-shell.store';

function initAuthSession(auth: AuthSessionService, store: {
  switchCenter: (centerId: string) => void
}): () => Promise<void> {
  return async () => {
    await auth.initFromServer();
    const centerId = auth.centerId();
    if (centerId) {
      store.switchCenter(centerId);
    }
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptors([authInterceptor])),
    importProvidersFrom(
      TranslateModule.forRoot({
        defaultLanguage: 'fr',
        loader: {
          provide: TranslateLoader,
          useClass: TranslateHttpLoader
        }
      })
    ),
    provideTranslateHttpLoader({prefix: './i18n/', suffix: '.json'}),
    {
      provide: APP_INITIALIZER,
      useFactory: initAuthSession,
      deps: [AuthSessionService, AppShellStore],
      multi: true
    }
  ]
};
