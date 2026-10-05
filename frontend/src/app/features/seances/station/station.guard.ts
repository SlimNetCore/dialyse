import {inject} from '@angular/core';
import {CanMatchFn} from '@angular/router';
import {AuthStore} from '../../../core/state/auth.store';

/** Seul le médecin « seul » garde l'écran de consultation ; tous les autres rôles travaillent au poste infirmier. */
const CLINICAL_STATION_ROLES = ['ADMIN', 'INFIRMIER', 'SECRETAIRE'];

export const seanceStationMatch: CanMatchFn = () => {
  const auth = inject(AuthStore);
  const doctorOnly = auth.hasRole('MEDECIN') && !CLINICAL_STATION_ROLES.some((role) => auth.hasRole(role));
  return !doctorOnly;
};
