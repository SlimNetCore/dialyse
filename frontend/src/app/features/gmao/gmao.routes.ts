import {Routes} from '@angular/router';
import {GmaoDashboardComponent} from './components/dashboard/dashboard.component';
import {GmaoListEquipementComponent} from './components/list-equipement/list-equipement.component';
import {GmaoFormEquipementComponent} from './components/form-equipement/form-equipement.component';
import {GmaoFicheEquipementComponent} from './components/fiche-equipement/fiche-equipement.component';
import {GmaoListInterventionComponent} from './components/list-intervention/list-intervention.component';
import {GmaoFormInterventionComponent} from './components/form-intervention/form-intervention.component';
import {GmaoFicheInterventionComponent} from './components/fiche-intervention/fiche-intervention.component';
import {GmaoIntervenantsComponent} from './components/intervenants/intervenants.component';

/**
 * Routes du module GMAO (lazy-loaded)
 */
export const GMAO_ROUTES: Routes = [
  {
    path: '',
    children: [
      {
        path: 'dashboard',
        component: GmaoDashboardComponent
      },
      {
        path: 'equipements',
        component: GmaoListEquipementComponent
      },
      {
        path: 'equipements/new',
        component: GmaoFormEquipementComponent
      },
      {
        path: 'equipements/:id/edit',
        component: GmaoFormEquipementComponent
      },
      {
        path: 'equipements/:id',
        component: GmaoFicheEquipementComponent
      },
      {
        path: 'interventions',
        component: GmaoListInterventionComponent
      },
      {
        path: 'interventions/new',
        component: GmaoFormInterventionComponent
      },
      {
        path: 'interventions/:id',
        component: GmaoFicheInterventionComponent
      },
      {
        path: 'interventions/:id/edit',
        component: GmaoFormInterventionComponent
      },
      {
        path: 'intervenants',
        component: GmaoIntervenantsComponent
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  }
];
