# UI Consistency Skill - Hemodialyse Frontend

## Objectif

Maintenir une UI homogène sur toute l'application Angular en réutilisant les conventions visuelles déjà en place (
tokens, composants Material, layouts, responsive), sans introduire de styles isolés.

## Portée

- Frontend Angular (`frontend/src/app/**`)
- Styles globaux (`frontend/src/styles.scss`)
- Composants standalone (`*.component.ts/.html/.css`)

## Règles obligatoires

### 1) Utiliser les tokens CSS existants

Toujours préférer les variables `--app-*` déjà définies dans `frontend/src/styles.scss`.

Tokens principaux:

- Couleurs: `--app-bg`, `--app-surface`, `--app-surface-solid`, `--app-text`, `--app-muted`, `--app-border`
- Couleur d'action: `--app-primary`, `--app-primary-soft`, `--app-primary-outline`, `--app-primary-hover`
- Effets: `--app-shadow`, `--app-shadow-soft`, `--app-blur`
- Table/hover: `--app-table-header`, `--app-row-hover`, `--app-hover-surface`

### Palette de couleur (référence officielle)

Source: `frontend/src/styles.scss`

Palette light (base):

- `--app-bg`: `#edf3f7`
- `--app-surface`: `#ffffff`
- `--app-surface-soft`: `#f7fbfe`
- `--app-text`: `#0d1d26`
- `--app-muted`: `#4f6573`
- `--app-border`: `rgba(15, 23, 42, 0.13)`

Palette dark (base):

- `--app-bg`: `#041219`
- `--app-surface`: `rgba(10, 28, 36, 0.78)`
- `--app-surface-solid`: `#0d2430`
- `--app-text`: `#eff8fb`
- `--app-muted`: `#8aa8b3`
- `--app-border`: `rgba(151, 196, 206, 0.14)`

Thèmes de marque disponibles:

- `cyan`
    - `--app-primary`: `#61d8df`
    - `--app-primary-hover`: `#8ce8ed`
    - `--app-primary-soft`: `rgba(97, 216, 223, 0.14)`
    - `--app-primary-outline`: `rgba(97, 216, 223, 0.38)`
    - `--app-accent`: `#f0bf7a`
- `emerald`
    - `--app-primary`: `#006a6a`
    - `--app-primary-hover`: `#008080`
    - `--app-primary-soft`: `rgba(0, 106, 106, 0.14)`
    - `--app-primary-outline`: `rgba(0, 106, 106, 0.38)`
    - `--app-accent`: `#f1ce84`
- `indigo`
    - `--app-primary`: `#9da6ff`
    - `--app-primary-hover`: `#bcc3ff`
    - `--app-primary-soft`: `rgba(157, 166, 255, 0.16)`
    - `--app-primary-outline`: `rgba(157, 166, 255, 0.38)`
    - `--app-accent`: `#f6bf92`

Règles d'usage de palette:

- UI neutre (fonds/bordures/textes): utiliser `--app-bg`, `--app-surface`, `--app-text`, `--app-muted`, `--app-border`
- Actions principales (CTA, focus, états actifs): utiliser `--app-primary` et variantes
- Survols/états doux: utiliser `--app-primary-soft` ou `--app-hover-surface`
- Contraste fort (texte sur primaire): garder une couleur texte lisible validée par le thème Material
- Ne jamais hardcoder une couleur de marque hors tokens, même pour un seul composant

Interdit:

- Ajouter des couleurs hex hardcodées sans justification
- Répliquer des valeurs déjà tokenisées

### 2) Respecter le pattern cartes/panels

Pour les blocs principaux (dashboards, sections, formulaires):

- Bordure: `1px solid var(--app-border)`
- Rayon: 18px a 26px selon taille
- Fond: `var(--app-surface)` ou `var(--app-surface-solid)`
- Ombre: `var(--app-shadow)` ou `var(--app-shadow-soft)`

Pattern recommandé:

- Container page: classe utilitaire type `app-page`
- Section hero: style type `app-hero-card`
- Bloc fonctionnel: style type `app-panel`

### 3) Espacements cohérents

Utiliser une grille d'espacements stable:

- Gaps principaux: `24px`, `16px`, `12px`, `8px`
- Padding panel desktop: `24px` (hero jusqu'a `28px`)
- Padding mobile: `14px` a `20px`

Eviter:

- Mélange arbitraire de marges/paddings non alignés
- Valeurs isolées non réutilisables

### 4) Formulaires Material harmonisés

Les formulaires suivent les overrides globaux de `styles.scss`.

- Hauteur standard `mat-form-field`: 40px
- Focus color: `var(--app-primary)`
- Inputs compacts centrés si même pattern métier
- Textareas alignées à gauche

Ne pas:

- Casser les variables `--mdc-*` globales localement sans nécessité
- Introduire des tailles/formats contradictoires

### 5) Boutons et interactions

Conserver le style Material unifié:

- Rayon boutons: 16px
- Poids typo: 700
- Hover léger avec translation/ombre
- Boutons primaires via thème (`var(--app-primary)`)

Ne pas:

- Créer des styles de boutons custom qui contournent le thème
- Multiplier les variantes non documentées

### 6) Responsive standard du projet

Breakpoints à privilégier:

- `1200px`, `980px`, `900px`, `760px`, `640px`, `600px`

Règle clé:

- `900px` est le pivot principal mobile/tablette
- En mobile: layouts en colonne, tables en conteneur scroll horizontal, actions full-width si nécessaire

### 7) Tables/listes

Pattern récurrent:

- Header: `var(--app-table-header)`
- Hover ligne: `var(--app-row-hover)`
- Bordures légères `var(--app-border)`
- Mobile: `overflow-x: auto` + `min-width` explicite

### 8) i18n UI obligatoire

Toute nouvelle chaîne visible UI passe par `ngx-translate` (`fr`, `en` au minimum).

### 9) Accessibilité minimale

- Conserver le contraste texte/fond via tokens
- Focus visible sur éléments interactifs
- Labels explicites sur boutons/icônes

## Anti-patterns à éviter

- Hardcode massif de couleurs (ex: `#xxxxxx`) à la place des tokens
- Breakpoints ad hoc (ex: 856px) sans justification
- `z-index` excessifs non gouvernés
- Usage excessif de `::ng-deep` pour contourner Material
- Classes locales redondantes alors qu'un pattern global existe

## Checklist PR UI (obligatoire)

- [ ] Aucun nouveau hardcode couleur inutile
- [ ] Tokens `--app-*` réutilisés
- [ ] Espacements alignés sur 8/12/16/24
- [ ] Responsive testé desktop + <=900px
- [ ] Libellés i18n ajoutés
- [ ] Aucun style en contradiction avec `styles.scss`
- [ ] Build frontend OK

## Références internes à suivre

- `frontend/src/styles.scss` (tokens, Material overrides, layouts, breakpoints)
- `frontend/src/app/core/layout/shell.component.css` (navigation + responsive global)
- `frontend/src/app/features/dashboard/center-dashboard.component.css` (cards de stats)
- `frontend/src/app/features/patient/patient-summary-cards.component.css` (grille synthèse)
- `frontend/src/app/features/patient/wizard/*.component.css` (formulaires métier complexes)

## Snippets recommandés

### Carte standard

```css
.card-like {
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-soft);
}
```

### Ligne responsive formulaire

```css
.form-row {
  display: flex;
  gap: 12px;
}

@media (max-width: 900px) {
  .form-row {
    flex-wrap: wrap;
    gap: 10px;
  }
}
```

### Table scroll mobile

```css
.table-wrap {
  overflow-x: auto;
}

@media (max-width: 900px) {
  .data-table {
    min-width: 720px;
  }
}
```


