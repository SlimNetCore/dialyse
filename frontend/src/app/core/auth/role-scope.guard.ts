import {inject} from '@angular/core';
import {CanActivateChildFn, CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/** Routes réservées au propriétaire de l'application (SUPERADMIN). */
const OWNER_AREAS = ['/admin/societes', '/admin/licenses'];
/** Zone unique de la direction d'une société. */
const DIRECTION_AREA = '/direction';

const isUnder = (url: string, area: string): boolean => {
  const path = url.split('?')[0];
  return path === area || path.startsWith(area + '/');
};

export const isOwnerArea = (url: string): boolean => OWNER_AREAS.some((area) => isUnder(url, area));

export const isDirectionArea = (url: string): boolean => isUnder(url, DIRECTION_AREA);

const has = (roles: readonly string[], role: string): boolean => roles.includes(role) || roles.includes(`ROLE_${role}`);

/** Page d'accueil de chaque profil après connexion. */
export const homeRouteFor = (roles: readonly string[]): string => {
  if (has(roles, 'SUPERADMIN')) return '/admin/societes';
  if (has(roles, 'DIRECTION')) return DIRECTION_AREA;
  return '/dashboard';
};

/**
 * Cantonne les profils sans centre à leur zone : le propriétaire (SUPERADMIN) aux sociétés et aux licences, la
 * direction au tableau de bord de sa société. Le serveur applique les mêmes règles sur l'API (RoleScopeFilter) ; ce
 * garde évite seulement d'afficher des écrans qui échoueraient. Les autres rôles ne sont pas concernés.
 */
export const roleScopeGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (auth.hasRole('SUPERADMIN') && !isOwnerArea(state.url)) {
    return inject(Router).parseUrl('/admin/societes');
  }
  if (auth.hasRole('DIRECTION') && !isDirectionArea(state.url)) {
    return inject(Router).parseUrl(DIRECTION_AREA);
  }
  return true;
};

/** Route de la direction : réservée au rôle DIRECTION (les autres profils reviennent à leur accueil). */
export const directionGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  if (auth.hasRole('DIRECTION')) {
    return true;
  }
  return inject(Router).parseUrl(homeRouteFor(auth.roles()));
};
