import {ActivatedRoute} from '@angular/router';

/**
 * Résout `:id` (le patient) en remontant la chaîne de routes.
 * <p>
 * Le module est monté à `/patients/:id/dossier-medical`, mais chaque écran (synthèse,
 * abords, prescriptions, biologie) vit sur un segment propre (`.../synthese`, `.../abords`...).
 * Avec la stratégie d'héritage de paramètres par défaut d'Angular (`emptyOnly`), seul le
 * segment vide de la coquille hérite de `:id` — pas ses enfants à chemin non vide. On
 * remonte donc explicitement plutôt que de changer la stratégie globale du routeur (qui
 * affecterait tout le reste de l'application).
 */
export function resolvePatientIdFromRoute(route: ActivatedRoute): string {
  let current: ActivatedRoute | null = route;
  while (current) {
    const id = current.snapshot.paramMap.get('id');
    if (id) return id;
    current = current.parent;
  }
  return '';
}
