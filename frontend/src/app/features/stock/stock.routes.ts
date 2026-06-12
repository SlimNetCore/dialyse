import {Routes} from '@angular/router';

export const stockRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./stock-dashboard.component').then(m => m.StockDashboardComponent),
  },
  {
    path: 'fournisseurs',
    loadComponent: () => import('./fournisseurs.component').then(m => m.FournisseursComponent),
  },
  {
    path: 'bons-commande',
    loadComponent: () => import('./bons-commande.component').then(m => m.BonsCommandeComponent),
  },
  {
    path: 'bons-reception',
    loadComponent: () => import('./bons-reception.component').then(m => m.BonsReceptionComponent),
  },
  {
    path: 'bons-sortie',
    loadComponent: () => import('./bons-sortie.component').then(m => m.BonsSortieComponent),
  },
  {path: '', redirectTo: '', pathMatch: 'full'},
];

