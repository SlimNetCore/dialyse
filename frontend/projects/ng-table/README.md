# ng-table

Table Angular Material configurable, standalone, basée sur les signals — colonnes réordonnables, tri, filtres (menu ou inline), sélection de lignes, ligne détail, menu contextuel, vues sauvegardées, et deux modes de données (**local** ou **remote**).

**Aucune dépendance i18n ni métier** : tous les textes sont en dur (overridables via `[labels]`), et le style s'appuie sur des variables CSS avec valeurs de repli — installez et utilisez sans configuration.

## Installation

```bash
npm install ng-table
```

Peer dependencies : `@angular/core`, `@angular/common`, `@angular/material`, `rxjs` (versions compatibles avec votre projet).

## Démarrage rapide

```ts
import {Component, signal} from '@angular/core';
import {NgTableColumn, NgTableComponent} from 'ng-table';

interface User {
  id: string;
  name: string;
  email: string;
  active: boolean;
}

@Component({
  standalone: true,
  imports: [NgTableComponent],
  template: `<ng-table [columns]="columns" [rows]="rows()" />`,
})
export class UserListComponent {
  readonly rows = signal<User[]>([
    {id: '1', name: 'Alice', email: 'alice@example.com', active: true},
    {id: '2', name: 'Bob', email: 'bob@example.com', active: false},
  ]);

  readonly columns: NgTableColumn<User>[] = [
    {id: 'name', header: 'Nom', valueAccessor: (u) => u.name, sortable: true, filter: {type: 'text'}},
    {id: 'email', header: 'Email', valueAccessor: (u) => u.email, sortable: true},
    {id: 'active', header: 'Actif', valueAccessor: (u) => u.active, filter: {type: 'boolean'}},
  ];
}
```

C'est tout — tri, filtrage et rendu fonctionnent immédiatement, avec le style par défaut du composant.

## Fonctionnalités

- **Colonnes** : visibilité (menu intégré), ordre (drag-and-drop natif HTML5), largeur (redimensionnable + auto-fit au double-clic), templates de cellule custom
- **Tri** — sur n'importe quelle colonne marquée `sortable`
- **Filtres** par colonne — texte, nombre, date/plage de dates, énuméré (select), booléen, ou un composant de filtre 100% custom ; en menu ou inline dans l'en-tête ; options chargées à la demande (`optionsLoader`) avec debounce automatique sur les champs texte
- **Sélection de lignes** (case à cocher), interne ou pilotée par le parent
- **Ligne détail** (master/detail), 3 modes (non contrôlé, par prédicat, par clé)
- **Menu contextuel** (clic droit) fourni par le parent
- **Copie rapide** d'une cellule en un clic
- **Vues sauvegardées** : l'utilisateur enregistre/active/supprime des configurations nommées (colonnes, ordre, tri, filtres, pagination) — persistées en `localStorage` par défaut, ou déléguées entièrement au parent (API, fichier...)
- **Deux modes de données** :
  - `local` (défaut) : tri/filtre/pagination appliqués côté client, zéro requête après le chargement initial
  - `remote` : le composant affiche `rows()` tel quel et notifie chaque changement de tri/filtre via un événement combiné unique, prêt à devenir une requête serveur
- **Responsive** : bascule automatique en vue mobile condensée sous 760px

## Documentation complète

Voir [`DOCUMENTATION.md`](./DOCUMENTATION.md) — référence API exhaustive (tous les inputs/outputs), exemples pour chaque mode, personnalisation des textes (`NgTableLabels`) et du style (variables CSS avec exemples clair/sombre).

## Licence

MIT
