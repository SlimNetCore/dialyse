import {Routes} from '@angular/router';

export const stockRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./stock-layout.component').then(m => m.StockLayoutComponent),
    children: [
      {
        // arrivée dans le module = écran de travail (stock valorisé + explication du PMP)
        path: '',
        loadComponent: () => import('./etat-du-stock.component').then(m => m.EtatDuStockComponent),
      },
      {
        path: 'tableau-de-bord',
        loadComponent: () => import('./stock-tableau-de-bord.component').then(m => m.StockTableauDeBordComponent),
      },
      {
        path: 'fournisseurs',
        loadComponent: () => import('./fournisseurs.component').then(m => m.FournisseursComponent),
      },
      {
        path: 'articles',
        loadComponent: () => import('./articles.component').then(m => m.ArticlesComponent),
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
      {
        path: 'sorties-pour-seances',
        loadComponent: () => import('./sorties-pour-seances.component').then(m => m.SortiesPourSeancesComponent),
      },
      {
        path: 'inventaires',
        loadComponent: () => import('./inventaire/inventaires.component').then(m => m.InventairesComponent),
      },
      {
        path: 'inventaires/:id',
        loadComponent: () => import('./inventaire/inventaire-detail.component').then(m => m.InventaireDetailComponent),
      },
    ],
  },
];
