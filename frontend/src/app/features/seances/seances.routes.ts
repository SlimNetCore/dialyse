import {Routes} from '@angular/router';
import {seanceStationMatch} from './station/station.guard';

export const seancesRoutes: Routes = [
  {
    path: '',
    canMatch: [seanceStationMatch],
    loadComponent: () =>
      import('./station/seance-station.component').then((m) => m.SeanceStationComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./seances-page.component').then((m) => m.SeancesPageComponent),
  },
  {
    path: 'historique',
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
