import {computed, inject, Injectable} from '@angular/core';
import {AuthStore} from '../../core/state/auth.store';

/**
 * Point unique de vérité pour le mode lecture seule du dossier médical.
 * <p>
 * Seul le rôle MEDECIN écrit ; l'ADMIN (et tout autre rôle laissé passer par `medecinGuard`)
 * consulte. Chaque composant du module lit `canEdit()` plutôt que de recalculer la règle —
 * si la règle change un jour (ex. ouvrir l'écriture à un autre rôle), un seul fichier bouge.
 */
@Injectable({providedIn: 'root'})
export class DossierMedicalAccessService {
  readonly isReadonly = computed(() => !this.canEdit());
  private readonly auth = inject(AuthStore);
  readonly canEdit = computed(() => this.auth.hasRole('MEDECIN'));
}
