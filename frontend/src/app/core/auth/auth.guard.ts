import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';
import {BackendInitService} from '../startup/backend-init.service';

/** Page de remplacement du mot de passe temporaire (hors coque applicative). */
export const PASSWORD_CHANGE_URL = '/changer-mot-de-passe';

/**
 * Attend le serveur si besoin puis vérifie la session (locale, sinon rechargée depuis `/me`).
 */
async function sessionOuverte(): Promise<boolean> {
  const auth = inject(AuthStore);
  const backendInit = inject(BackendInitService);

  if (backendInit.state() === 'waiting-server') {
    const deadline = Date.now() + 240_000;
    while (backendInit.state() === 'waiting-server' && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
  }

  if (backendInit.state() === 'server-unavailable') {
    return auth.isAuthenticated();
  }

  return auth.isAuthenticated() || await auth.initFromServer({force: true});
}

/**
 * Routes de l'application : session requise ; un mot de passe temporaire doit d'abord être remplacé.
 */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (!(await sessionOuverte())) {
    return router.parseUrl('/login');
  }
  return auth.mustChangePassword() ? router.parseUrl(PASSWORD_CHANGE_URL) : true;
};

/**
 * Page de changement de mot de passe : session requise, accessible seulement tant que le mot de passe est temporaire.
 */
export const passwordChangeGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (!(await sessionOuverte())) {
    return router.parseUrl('/login');
  }
  return auth.mustChangePassword() ? true : router.parseUrl('/');
};
