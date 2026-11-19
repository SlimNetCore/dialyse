import {Routes} from '@angular/router';
import {DirectionLayoutComponent} from './direction-layout.component';

/**
 * Tableau de bord de la direction, en trois pages sous une barre de filtres commune (période et centre) :
 * vue d'ensemble (/direction), analyses (/direction/analyses), rapports (/direction/rapports).
 */
export const directionRoutes: Routes = [
  {
    path: '',
    component: DirectionLayoutComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./direction-overview.component').then(m => m.DirectionOverviewComponent)
      },
      {
        path: 'analyses',
        loadComponent: () => import('./direction-analyses.component').then(m => m.DirectionAnalysesComponent)
      },
      {
        path: 'rapports',
        loadComponent: () => import('./direction-reports.component').then(m => m.DirectionReportsComponent)
      },
      {path: '**', redirectTo: ''},
    ],
  },
];
