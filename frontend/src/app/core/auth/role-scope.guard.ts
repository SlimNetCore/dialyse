import {inject} from '@angular/core';
import {CanActivateChildFn, CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/** Routes réservées au propriétaire de l'application (SUPERADMIN). */
const OWNER_AREAS = ['/admin/societes', '/admin/licenses', '/admin/audit', '/admin/performance'];
/** Zone unique de la direction d'une société. */
const DIRECTION_AREA = '/direction';
/** Accueil de l'infirmier : son planning du jour. */
export const NURSE_HOME = '/infirmiers/moi';
/** Accueil du médecin : le planning du jour des infirmiers et des patients. */
export const DOCTOR_HOME = '/medecin';
/** Rôles qui, cumulés à MEDECIN, donnent accès au reste de l'application. */
const DOCTOR_EXCLUDING_ROLES = ['ADMIN', 'SECRETAIRE', 'INFIRMIER', 'SUPERADMIN', 'DIRECTION'];
/** Sous-routes de `/patients` fermées au médecin seul (création, prises en charge, attestations). */
const PATIENT_ROUTES_CLOSED_TO_DOCTOR = ['new', 'pec-admin', 'pec-list', 'attestations-list'];
/** Rôles de centre qui, cumulés à INFIRMIER, donnent accès au reste de l'application. */
const OTHER_CENTRE_ROLES = ['ADMIN', 'MEDECIN', 'SECRETAIRE', 'SUPERADMIN', 'DIRECTION'];

const isUnder = (url: string, area: string): boolean => {
  const path = url.split('?')[0];
  return path === area || path.startsWith(area + '/');
};

export const isOwnerArea = (url: string): boolean => OWNER_AREAS.some((area) => isUnder(url, area));

export const isDirectionArea = (url: string): boolean => isUnder(url, DIRECTION_AREA);

const has = (roles: readonly string[], role: string): boolean => roles.includes(role) || roles.includes(`ROLE_${role}`);

/**
 * Infirmier « seul » : rôle INFIRMIER sans aucun autre rôle donnant accès au reste de l'application. Il ne dispose que
 * de son planning du jour et des séances.
 */
export const isNurseOnly = (roles: readonly string[]): boolean =>
  has(roles, 'INFIRMIER') && !OTHER_CENTRE_ROLES.some((role) => has(roles, role));

/** Suivi des absences de patients : ouvert à l'infirmier seul et au médecin seul. */
const ABSENCES_PATIENTS = '/seances/absences-patients';

/** Historique des séances (statistiques et tableau) : consultation ouverte à l'infirmier seul. */
const SEANCES_HISTORIQUE = '/seances/historique';

/** Écrans de l'infirmier seul : son planning (page d'accueil), le poste infirmier, l'historique des séances et les absences. */
export const isNurseArea = (url: string): boolean => {
  const path = url.split('?')[0];
  return isUnder(path, NURSE_HOME) || path === '/seances' || path === SEANCES_HISTORIQUE || path === ABSENCES_PATIENTS;
};

/**
 * Médecin « seul » : rôle MEDECIN sans aucun autre rôle donnant accès au reste de l'application. Il consulte les
 * patients (fiche en lecture seule), leur dossier médical, leur cahier de dialyse et leurs statistiques, et voit sur
 * son tableau de bord le planning du jour des infirmiers et des patients.
 */
export const isDoctorOnly = (roles: readonly string[]): boolean =>
  has(roles, 'MEDECIN') && !DOCTOR_EXCLUDING_ROLES.some((role) => has(roles, role));

/**
 * Même test que {@link isDoctorOnly} à partir de la fonction `hasRole` du magasin d'authentification : pratique dans
 * les composants (consultation seule de la fiche patient, boutons de création masqués).
 */
export const isDoctorOnlyFor = (hasRole: (role: string) => boolean): boolean =>
  hasRole('MEDECIN') && !DOCTOR_EXCLUDING_ROLES.some((role) => hasRole(role));

/**
 * Écrans du médecin seul : son accueil (le planning des séances, vues semaine et jour), la liste des patients et, par
 * patient, fiche, cahier, dossier et statistiques.
 */
export const isDoctorArea = (url: string): boolean => {
  const path = url.split('?')[0];
  if (path === DOCTOR_HOME || path === '/patients' || path === ABSENCES_PATIENTS) {
    return true;
  }
  const match = /^\/patients\/([^/]+)(?:\/(?:cahier|stats|dossier-medical)(?:\/.*)?)?$/.exec(path);
  return !!match && !PATIENT_ROUTES_CLOSED_TO_DOCTOR.includes(match[1]);
};

/** Page d'accueil de chaque profil après connexion. */
export const homeRouteFor = (roles: readonly string[]): string => {
  if (has(roles, 'SUPERADMIN')) return '/admin/societes';
  if (has(roles, 'DIRECTION')) return DIRECTION_AREA;
  if (isNurseOnly(roles)) return NURSE_HOME;
  if (isDoctorOnly(roles)) return DOCTOR_HOME;
  return '/dashboard';
};

/**
 * Cantonne les profils sans centre à leur zone : le propriétaire (SUPERADMIN) aux sociétés et aux licences, la
 * direction au tableau de bord de sa société, l'infirmier seul à son planning et aux séances. Le serveur applique les
 * mêmes règles sur l'API (RoleScopeFilter) ; ce garde évite seulement d'afficher des écrans qui échoueraient. Les
 * autres rôles ne sont pas concernés.
 */
export const roleScopeGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (auth.hasRole('SUPERADMIN') && !isOwnerArea(state.url)) {
    return inject(Router).parseUrl('/admin/societes');
  }
  if (auth.hasRole('DIRECTION') && !isDirectionArea(state.url)) {
    return inject(Router).parseUrl(DIRECTION_AREA);
  }
  if (isNurseOnly(auth.roles()) && !isNurseArea(state.url)) {
    return inject(Router).parseUrl(NURSE_HOME);
  }
  if (isDoctorOnly(auth.roles()) && !isDoctorArea(state.url)) {
    return inject(Router).parseUrl(DOCTOR_HOME);
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
