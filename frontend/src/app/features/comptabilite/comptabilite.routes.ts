import {Routes} from '@angular/router';
import {adminGuard} from '../../core/auth/admin.guard';

export const comptabiliteRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./comptabilite-dashboard.component').then((m) => m.ComptabiliteDashboardComponent),
  },
  {
    path: 'parametrage',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./comptabilite-parametrage.component').then((m) => m.ComptabiliteParametrageComponent),
  },
];
