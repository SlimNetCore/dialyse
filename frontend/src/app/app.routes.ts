import {Routes} from '@angular/router';
import {ShellComponent} from './core/layout/shell.component';
import {authGuard} from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login-page.component').then(m => m.LoginPageComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/center-dashboard.component').then(m => m.CenterDashboardComponent) },
      { path: 'modeles-document', loadComponent: () => import('./features/reporting/modeles-document.component').then(m => m.ModelesDocumentComponent) },
      { path: 'patients', loadChildren: () => import('./features/patient/patient.routes').then(m => m.patientRoutes) },
      {
        path: 'seances',
        loadChildren: () => import('./features/seances/seances.routes').then((m) => m.seancesRoutes),
      },
      {path: 'stock', loadChildren: () => import('./features/stock/stock.routes').then(m => m.stockRoutes)},
      {
        path: 'facturation',
        loadChildren: () => import('./features/facturation/facturation.routes').then(m => m.facturationRoutes)
      },
      {
        path: 'reglement',
        loadChildren: () => import('./features/reglement/reglement.routes').then(m => m.reglementRoutes)
      },
      {
        path: 'comptabilite',
        loadChildren: () => import('./features/comptabilite/comptabilite.routes').then(m => m.comptabiliteRoutes)
      },
      { path: 'admin', loadChildren: () => import('./features/admin/admin.routes').then(m => m.adminRoutes) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
