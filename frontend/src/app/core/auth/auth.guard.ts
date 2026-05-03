import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthSessionService} from '../auth/auth-session.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthSessionService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  return auth.initFromServer().then((ok) => {
    if (ok) return true;
    router.navigate(['/login']);
    return false;
  });
};

