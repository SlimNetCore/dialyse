import { Routes } from '@angular/router';
import { ShellComponent } from './core/layout/shell.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login-page.component').then(m => m.LoginPageComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/center-dashboard.component').then(m => m.CenterDashboardComponent) },
      { path: 'reporting', loadComponent: () => import('./features/reporting/reporting-page.component').then(m => m.ReportingPageComponent) },
      { path: 'patients', loadChildren: () => import('./features/patient/patient.routes').then(m => m.patientRoutes) },
      { path: 'seances', loadComponent: () => import('./features/seances/seances-placeholder.component').then(m => m.SeancesPlaceholderComponent) },
      { path: 'facturation', loadComponent: () => import('./features/facturation/facturation-placeholder.component').then(m => m.FacturationPlaceholderComponent) },
      { path: 'reglement', loadComponent: () => import('./features/reglement/reglement-placeholder.component').then(m => m.ReglementPlaceholderComponent) },
      { path: 'admin', loadChildren: () => import('./features/admin/admin.routes').then(m => m.adminRoutes) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
