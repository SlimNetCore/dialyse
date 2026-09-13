import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/** Restricts a route to center ADMIN or the vendor's SUPERADMIN account. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.hasRole('ADMIN') || auth.hasRole('SUPERADMIN')) {
    return true;
  }

  return router.parseUrl('/');
};
