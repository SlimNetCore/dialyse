import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';
import {BackendInitService} from '../startup/backend-init.service';

export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const backendInit = inject(BackendInitService);

  if (backendInit.state() === 'waiting-server') {
    const deadline = Date.now() + 240_000;
    while (backendInit.state() === 'waiting-server' && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
  }

  if (backendInit.state() === 'server-unavailable') {
    return auth.isAuthenticated() ? true : router.parseUrl('/login');
  }

  if (auth.isAuthenticated()) {
    return true;
  }

  const hasServerSession = await auth.initFromServer({force: true});
  if (hasServerSession) {
    return true;
  }

  return router.parseUrl('/login');
};

