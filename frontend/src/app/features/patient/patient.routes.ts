import {Routes} from '@angular/router';

export const patientRoutes: Routes = [
  { path: '', loadComponent: () => import('./patient-dashboard.component').then(m => m.PatientDashboardComponent) },
  { path: 'new', loadComponent: () => import('./wizard/patient-wizard.component').then(m => m.PatientWizardComponent) },
  {
    path: ':id/cahier',
    loadComponent: () => import('./cahier-dialyse/cahier-dialyse.component').then(m => m.CahierDialyseComponent)
  },
  { path: 'pec-admin', loadComponent: () => import('./pec-admin/pec-admin.component').then(m => m.PecAdminComponent) },
  { path: 'pec-list', loadComponent: () => import('./pec-admin/pec-list.component').then(m => m.PecListComponent) },
  { path: 'attestations-list', loadComponent: () => import('./pec-admin/attestation-list.component').then(m => m.AttestationListComponent) },
  {path: ':id/stats', loadComponent: () => import('./patient-stats.component').then(m => m.PatientStatsComponent)},
  { path: ':id', loadComponent: () => import('./wizard/patient-wizard.component').then(m => m.PatientWizardComponent) }
];
