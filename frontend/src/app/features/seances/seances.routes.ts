import {Routes} from '@angular/router';

export const seancesRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./station/seance-station.component').then((m) => m.SeanceStationComponent),
  },
  {
    path: 'historique',
    loadComponent: () =>
      import('./historique/seances-historique.component').then((m) => m.SeancesHistoriqueComponent),
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
