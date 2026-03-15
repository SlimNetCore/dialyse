import { Routes } from '@angular/router';

export const patientRoutes: Routes = [
  { path: '', loadComponent: () => import('./patient-dashboard.component').then(m => m.PatientDashboardComponent) },
  { path: 'new', loadComponent: () => import('./wizard/patient-wizard.component').then(m => m.PatientWizardComponent) },
  { path: 'pec-admin', loadComponent: () => import('./pec-admin/pec-admin.component').then(m => m.PecAdminComponent) }
];

