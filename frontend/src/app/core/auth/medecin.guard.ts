import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthStore} from '../state/auth.store';

/**
 * Restreint le dossier médical au corps médical : le médecin (lecture + écriture) et
 * l'administrateur (lecture seule, cf. `DossierMedicalAccessService.canEdit`).
 * <p>
 * Reflète exactement les rôles acceptés par les contrôleurs REST du dossier médical
 * (`hasAnyRole('MEDECIN','ADMIN')`) — ne pas élargir ici sans élargir aussi le backend,
 * sous peine de laisser passer la route côté client pour recevoir des 403 en cascade.
 */
/** Tableau de bord du médecin : réservé aux utilisateurs ayant le rôle MEDECIN (les autres reviennent à l'accueil). */
export const medecinAccueilGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.hasRole('MEDECIN') ? true : inject(Router).parseUrl('/dashboard');
};

export const medecinGuard: CanActivateFn = (route) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.hasRole('MEDECIN') || auth.hasRole('ADMIN')) {
    return true;
  }

  const patientId = route.parent?.paramMap.get('id') ?? route.paramMap.get('id');
  return router.parseUrl(patientId ? `/patients/${patientId}` : '/patients');
};
