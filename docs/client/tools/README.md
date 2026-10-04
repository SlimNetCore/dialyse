# Génération des documents clients

Les documents de `docs/client` (documentation fonctionnelle et plaquette commerciale, en HTML, PDF et DOCX) sont
**générés** :

- `build-documentation-fonctionnelle.cjs` → `documentation-fonctionnelle.{html,pdf,docx}` à partir du référentiel
  `docs/reference/regles-de-gestion` ;
- `build-maquette.cjs` → `maquette-commerciale.{html,pdf,docx}` à partir des captures `docs/client/img` et des chiffres
  du référentiel.

Ne jamais corriger une sortie à la main : corriger le référentiel (ou le script) puis régénérer.

## Régénérer

```bash
cd docs/client/tools
npm install          # marked, docx, playwright-core
npx playwright install chromium   # une seule fois (rendu PDF)
node build-documentation-fonctionnelle.cjs
node build-maquette.cjs
```

Variables facultatives : `DOC_VERSION` (défaut `1.0`), `DOC_DATE` (défaut : date du jour).

## Captures d'écran (`../img`)

Les captures sont **réelles** : prises sur une instance de démonstration isolée (base H2 en mémoire, profil
`demo-renadial`, port ≠ 8090/4200),
jamais sur une base de production. Pour les renouveler : démarrer le backend (`SPRING_PROFILES_ACTIVE=demo-renadial`) et
le frontend sur un port
dédié, y amorcer des données de démonstration (licences, direction, équipe, interventions, absences), puis photographier
chaque écran avec
Playwright (1920 × 1200, français). Le Sommaire des écrans attendus est la liste `SCREENS` de
`build-documentation-fonctionnelle.cjs` et `shots`
de `build-maquette.cjs` ; une capture absente est simplement omise.
