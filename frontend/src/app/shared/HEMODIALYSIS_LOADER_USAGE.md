# Hemodialysis Loader — Guide d'Utilisation Partout dans l'Application

## Vue d'ensemble

Le composant `HemodialysisLoaderComponent` doit être utilisé systématiquement pour tous les loaders/spinners de l'application, dans une version compact.

## Trois tailles disponibles

### 1. Small (par défaut pour les overlays internes)

```html

<app-hemodialysis-loader
  [label]="'COMMON.LOADING_DATA'"
  [mode]="'overlay'"
  [sizePreset]="'small'"
/>
```

- **Taille**: 80px de diamètre
- **Vitesse d'animation**: 2.5s par tour
- **Utilisation**: Overlays dans les tableaux, listes, dialogs
- **Code**: `sizePreset="small"`

### 2. Medium (par défaut, page entière)

```html

<app-hemodialysis-loader
  [label]="'COMMON.LOADING_DATA'"
  [mode]="'overlay'"
  [sizePreset]="'medium'"
/>
```

- **Taille**: 120px de diamètre
- **Vitesse d'animation**: 3s par tour
- **Utilisation**: Loaders au démarrage de l'app
- **Code**: `sizePreset="medium"` (par défaut)

### 3. Large (rarement utilisé)

```html

<app-hemodialysis-loader
  [label]="'COMMON.LOADING_DATA'"
  [mode]="'overlay'"
  [sizePreset]="'large'"
/>
```

- **Taille**: 160px de diamètre
- **Vitesse d'animation**: 3.5s par tour
- **Utilisation**: Overlays critiques ou branding

## Propriétés principales

| Propriété                   | Type                           | Défaut                  | Description                               |
|-----------------------------|--------------------------------|-------------------------|-------------------------------------------|
| `label`                     | string                         | `'COMMON.LOADING_DATA'` | Clé i18n du libellé affiché               |
| `mode`                      | 'inline' \| 'overlay'          | `'inline'`              | Mode d'affichage (absolu fixe vs. inline) |
| `sizePreset`                | 'small' \| 'medium' \| 'large' | `'medium'`              | Préset de taille                          |
| `showServerUnavailableIcon` | boolean                        | `false`                 | Affiche le rein pleurant (erreur)         |
| `showReconnectingKidney`    | boolean                        | `false`                 | Affiche le compteur de reconnexion        |
| `showElapsedSeconds`        | boolean                        | `false`                 | Affiche le compteur de secondes écoulées  |

## Formatage du compteur

- Moins de 60s : `"5s"`, `"12s"`, `"59s"`
- 60s et plus : `"01:00"`, `"01:30"`, `"02:15"`

## Intégration dans les composants

### Étape 1 : Importer le composant

```typescript
import {HemodialysisLoaderComponent} from '../../../shared/hemodialysis-loader.component';

@Component({
  // ...
  imports: [
    // ... autres imports
    HemodialysisLoaderComponent,
  ],
})
export class MyComponent {
  // ...
}
```

### Étape 2 : Ajouter le loader dans le template

```html
<!-- Pour un overlay de chargement dans une liste/table -->
@if (loading()) {
<app-hemodialysis-loader
  label="COMMON.LOADING_DATA"
  mode="overlay"
  sizePreset="small"
/>
}

<!-- Pour un overlay pendant une opération -->
@if (savingData()) {
<app-hemodialysis-loader
  label="COMMON.SAVING"
  mode="overlay"
  sizePreset="small"
  [showElapsedSeconds]="true"
/>
}
```

### Étape 3 : Ajouter une traduction pour le label

```json
{
  "COMMON": {
    "SAVING": "Enregistrement en cours",
    "DELETING": "Suppression en cours",
    "EXPORTING": "Export en cours"
  }
}
```

## Cas d'usage courants

### Dashboard - Chargement stats

```html
@if (dashboardLoading()) {
<app-hemodialysis-loader
  label="COMMON.LOADING_DATA"
  mode="overlay"
  sizePreset="small"
/>
}
```

### Liste - Overlay de table

```html

<div class="table-container">
  @if (tableLoading()) {
  <app-hemodialysis-loader
    label="COMMON.LOADING_DATA"
    mode="overlay"
    sizePreset="small"
  />
  }
  <!-- table content -->
</div>
```

### Export/Import long

```html
@if (exporting()) {
<app-hemodialysis-loader
  label="COMMON.EXPORTING"
  mode="overlay"
  sizePreset="small"
  [showElapsedSeconds]="true"
/>
}
```

## Notes de responsive

Le loader compact (`small`) s'adapte automatiquement sur les petits écrans (mobile/tablette) grâce au CSS responsive du composant. Aucune configuration supplémentaire n'est nécessaire.

## Migration des loaders existants

Pour migrer un loader/spinner existant :

1. **Avant** (spinner Material generic)
   ```html
   <mat-spinner></mat-spinner>
   ```

2. **Après** (loader hémodialyse)
   ```html
   <app-hemodialysis-loader
     label="COMMON.LOADING_DATA"
     mode="overlay"
     sizePreset="small"
   />
   ```

## Limitation intentionnelle

Le loader est uniquement disponible en **overlay** pour les chargements critiques (API, validation, export). Pour les indicateurs de progression déterministes (e.g., `<mat-progress-bar>`), continuer à utiliser les composants Material standards.

