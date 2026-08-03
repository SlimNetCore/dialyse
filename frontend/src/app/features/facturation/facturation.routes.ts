import {Routes} from '@angular/router';

export const facturationRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./facturation-workspace.component').then((m) => m.FacturationWorkspaceComponent),
  },
  {path: '', redirectTo: '', pathMatch: 'full'},
];

