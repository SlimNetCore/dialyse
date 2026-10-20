import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';
import {homeRouteFor} from './role-scope.guard';

/** Écran réservé à l'ADMIN du centre (comme l'API : un SUPERADMIN n'a pas de centre). */
export const centreAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  if (auth.hasRole('ADMIN')) {
    return true;
  }
  return inject(Router).parseUrl(homeRouteFor(auth.roles()));
};

/** Restricts a route to center ADMIN or the vendor's SUPERADMIN account. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.hasRole('ADMIN') || auth.hasRole('SUPERADMIN')) {
    return true;
  }

  return router.parseUrl('/');
};
