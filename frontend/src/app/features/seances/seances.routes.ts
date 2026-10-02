import {Routes} from '@angular/router';

export const seancesRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./seances-page.component').then((m) => m.SeancesPageComponent),
  },
  {
    path: 'planning',
    loadComponent: () =>
      import('../planning/planning-semaine.component').then((m) => m.PlanningSemaineComponent),
  },
  {
    path: 'salles',
    loadComponent: () =>
      import('../planning/salles-generateurs.component').then((m) => m.SallesGenerateursComponent),
  },
  {
    path: 'absences-patients',
    loadComponent: () =>
      import('../absences/absences-patients.component').then((m) => m.AbsencesPatientsComponent),
  },
];
