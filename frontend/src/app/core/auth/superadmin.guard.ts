import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/** Restricts a route to the SUPERADMIN role (the vendor's own account) — the licensing module. */
export const superadminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.hasRole('SUPERADMIN')) {
    return true;
  }

  return router.parseUrl('/');
};
