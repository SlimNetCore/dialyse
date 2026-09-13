import {Routes} from '@angular/router';

export const dossierMedicalRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./dossier-medical-shell.component').then(m => m.DossierMedicalShellComponent),
    children: [
      {path: '', redirectTo: 'synthese', pathMatch: 'full'},
      {
        path: 'synthese',
        loadComponent: () => import('./synthese/dossier-synthese.component').then(m => m.DossierSyntheseComponent),
      },
      {
        path: 'antecedents',
        loadComponent: () => import('./antecedents/antecedents.component').then(m => m.AntecedentsComponent),
      },
      {
        path: 'serologies',
        loadComponent: () => import('./serologies/serologies.component').then(m => m.SerologiesComponent),
      },
      {
        path: 'examens',
        loadComponent: () => import('./examens/examens.component').then(m => m.ExamensComponent),
      },
      {
        path: 'abords',
        loadComponent: () => import('./abords/abords-vasculaires.component').then(m => m.AbordsVasculairesComponent),
      },
      {
        path: 'prescriptions',
        loadComponent: () => import('./prescriptions/prescriptions-list.component').then(m => m.PrescriptionsListComponent),
      },
      {
        path: 'biologie',
        loadComponent: () => import('./biologie/bilans-list.component').then(m => m.BilansListComponent),
      },
    ],
  },
];
