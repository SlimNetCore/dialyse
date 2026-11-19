import {Routes} from '@angular/router';

export const reglementRoutes: Routes = [
  {
    // arrivée dans le module = saisie des règlements (filtres, liste, brouillons, paiement par lot, historique, exports)
    path: '',
    loadComponent: () => import('./reglement-workspace.component').then((m) => m.ReglementWorkspaceComponent),
  },
  {
    // mêmes droits que l'écran de saisie d'où les indicateurs sont extraits (aucun guard propre au module)
    path: 'tableau-de-bord',
    loadComponent: () => import('./tableau-de-bord/reglement-tableau-de-bord.component')
      .then((m) => m.ReglementTableauDeBordComponent),
  },
];
