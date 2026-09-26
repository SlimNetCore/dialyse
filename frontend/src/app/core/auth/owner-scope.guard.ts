import {inject} from '@angular/core';
import {CanActivateChildFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/** Routes réservées au propriétaire de l'application (SUPERADMIN). */
const OWNER_AREAS = ['/admin/societes', '/admin/licenses'];

export const isOwnerArea = (url: string): boolean => {
  const path = url.split('?')[0];
  return OWNER_AREAS.some((area) => path === area || path.startsWith(area + '/'));
};

/**
 * Cantonne le propriétaire (SUPERADMIN) aux sociétés et aux licences : toute autre page le renvoie vers la liste des
 * sociétés. Le serveur applique la même règle sur l'API (SuperAdminScopeFilter) ; ce garde évite seulement
 * d'afficher des écrans qui échoueraient. Les autres rôles ne sont pas concernés ici (les routes du propriétaire
 * ont leur propre `superadminGuard`).
 */
export const ownerScopeGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (!auth.hasRole('SUPERADMIN') || isOwnerArea(state.url)) {
    return true;
  }
  return inject(Router).parseUrl('/admin/societes');
};
