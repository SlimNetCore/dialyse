import {Routes} from '@angular/router';
import {ShellComponent} from './core/layout/shell.component';
import {authGuard, passwordChangeGuard} from './core/auth/auth.guard';
import {medecinAccueilGuard} from './core/auth/medecin.guard';
import {directionGuard, roleScopeGuard} from './core/auth/role-scope.guard';

export const routes: Routes = [
  {path: 'setup', loadComponent: () => import('./features/auth/setup-page.component').then(m => m.SetupPageComponent)},
  { path: 'login', loadComponent: () => import('./features/auth/login-page.component').then(m => m.LoginPageComponent) },
  {
    path: 'login/proprietaire',
    loadComponent: () => import('./features/auth/owner-login-page.component').then(m => m.OwnerLoginPageComponent),
  },
  {
    path: 'changer-mot-de-passe',
    canActivate: [passwordChangeGuard],
    loadComponent: () => import('./features/auth/change-password-page.component').then(m => m.ChangePasswordPageComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [roleScopeGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/center-dashboard.component').then(m => m.CenterDashboardComponent) },
      { path: 'modeles-document', loadComponent: () => import('./features/reporting/modeles-document.component').then(m => m.ModelesDocumentComponent) },
      { path: 'patients', loadChildren: () => import('./features/patient/patient.routes').then(m => m.patientRoutes) },
      {
        path: 'seances',
        loadChildren: () => import('./features/seances/seances.routes').then((m) => m.seancesRoutes),
      },
      {
        path: 'medecin',
        canActivate: [medecinAccueilGuard],
        // accueil du médecin : exactement le planning des séances (vues semaine et jour)
        loadComponent: () => import('./features/planning/planning-semaine.component').then((m) => m.PlanningSemaineComponent),
      },
      {
        path: 'infirmiers',
        loadChildren: () => import('./features/infirmier/infirmier.routes').then((m) => m.infirmierRoutes),
      },
      {path: 'stock', loadChildren: () => import('./features/stock/stock.routes').then(m => m.stockRoutes)},
      {
        path: 'gmao',
        loadChildren: () => import('./features/gmao/gmao.routes').then(m => m.GMAO_ROUTES)
      },
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
      {
        path: 'direction',
        canActivate: [directionGuard],
        loadComponent: () => import('./features/direction/direction-dashboard.component').then(m => m.DirectionDashboardComponent)
      },
      { path: 'admin', loadChildren: () => import('./features/admin/admin.routes').then(m => m.adminRoutes) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
