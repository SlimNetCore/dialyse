import {Routes} from '@angular/router';

export const comptabiliteRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./comptabilite-journal.component').then((m) => m.ComptabiliteJournalComponent),
  },
  {
    // le paramétrage comptable vit dans Administration › Paramétrage : l'ancienne URL y redirige
    path: 'parametrage',
    redirectTo: '/admin/parametrage/comptabilite',
  },
  {
    // saisie d'une pièce à partir d'un modèle : administrateur et secrétariat (le serveur applique la règle)
    path: 'pieces',
    loadComponent: () => import('./saisie-piece.component').then((m) => m.SaisiePieceComponent),
  },
];
