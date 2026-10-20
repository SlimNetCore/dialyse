import {Routes} from '@angular/router';
import {centreAdminGuard} from '../../core/auth/admin.guard';

export const facturationRoutes: Routes = [
  {
    path: '',
    canActivate: [centreAdminGuard],
    loadComponent: () => import('./facturation-workspace.component').then((m) => m.FacturationWorkspaceComponent),
  },
  {path: '', redirectTo: '', pathMatch: 'full'},
];

