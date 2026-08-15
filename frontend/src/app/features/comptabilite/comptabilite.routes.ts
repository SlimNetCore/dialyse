import {Routes} from '@angular/router';

export const comptabiliteRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./comptabilite-dashboard.component').then((m) => m.ComptabiliteDashboardComponent),
  },
  {path: '', redirectTo: '', pathMatch: 'full'},
];

