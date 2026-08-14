import {Routes} from '@angular/router';

export const reglementRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./reglement-workspace.component').then((m) => m.ReglementWorkspaceComponent),
  },
  {path: '', redirectTo: '', pathMatch: 'full'},
];

