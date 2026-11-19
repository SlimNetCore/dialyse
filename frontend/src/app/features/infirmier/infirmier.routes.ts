import {Routes} from '@angular/router';

export const infirmierRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./presence-semaine.component').then((m) => m.PresenceSemaineComponent),
  },
  {
    path: 'tableau-de-bord',
    loadComponent: () => import('./tableau-de-bord/infirmiers-tableau-de-bord.component')
      .then((m) => m.InfirmiersTableauDeBordComponent),
  },
  {
    path: 'moi',
    loadComponent: () => import('./mon-planning.component').then((m) => m.MonPlanningComponent),
  },
  {
    path: 'referentiel',
    loadComponent: () => import('./infirmiers.component').then((m) => m.InfirmiersComponent),
  },
  {
    path: 'absences',
    loadComponent: () => import('./absences.component').then((m) => m.AbsencesInfirmiersComponent),
  },
  {
    path: 'charge',
    loadComponent: () => import('./charge.component').then((m) => m.ChargeInfirmiersComponent),
  },
];
