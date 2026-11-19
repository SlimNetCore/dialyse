import {Routes} from '@angular/router';
import {centreAdminGuard} from '../../core/auth/admin.guard';

export const facturationRoutes: Routes = [
  {
    // arrivée dans le module = écran de travail (calcul, aperçu, validation, clôture des périodes)
    path: '',
    canActivate: [centreAdminGuard],
    loadComponent: () => import('./facturation-workspace.component').then((m) => m.FacturationWorkspaceComponent),
  },
  {
    // mêmes droits que l'écran de calcul d'où les indicateurs sont extraits
    path: 'tableau-de-bord',
    canActivate: [centreAdminGuard],
    loadComponent: () => import('./tableau-de-bord/facturation-tableau-de-bord.component')
      .then((m) => m.FacturationTableauDeBordComponent),
  },
];
