import {Routes} from '@angular/router';

export const seancesRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./seances-page.component').then((m) => m.SeancesPageComponent),
  },
  {
    path: 'planning',
    loadComponent: () =>
      import('../planning/planning-semaine.component').then((m) => m.PlanningSemaineComponent),
  },
];
