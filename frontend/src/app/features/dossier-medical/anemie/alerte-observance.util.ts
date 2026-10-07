import {AlerteObservance} from '../../../core/api/dossier-medical-api.service';

/**
 * Une alerte porte sa prescription (fréquence) : on peut l'expliquer par le détail. Les alertes antérieures à cette
 * information (sans fréquence) gardent leur texte d'origine.
 */
export function alerteDetaillee(alerte: Pick<AlerteObservance, 'frequenceValeur' | 'frequenceUnite'>): boolean {
  return !!alerte.frequenceValeur && !!alerte.frequenceUnite;
}

/** Attendu et administré sont des quantités de dose (UI, mg) et non des nombres d'administrations. */
export function alerteEnDose(alerte: Pick<AlerteObservance, 'uniteDose'>): boolean {
  return !!alerte.uniteDose;
}

/** Ce qui manque : attendu moins administré, jamais négatif, dans la même unité que l'alerte. */
export function manqueAlerte(alerte: Pick<AlerteObservance, 'dosesAttendues' | 'dosesAdministrees'>): number {
  return Math.max(0, alerte.dosesAttendues - alerte.dosesAdministrees);
}
