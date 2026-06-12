# E2E Step 4 (stats/export)

Ces tests couvrent le flux: liste patients -> stats patient -> export PDF.

## Prerequis

- Frontend lance sur `http://127.0.0.1:4200` (ou definir `E2E_BASE_URL`)
- Backend disponible et login fonctionnel
- Variables d'environnement:
  - `E2E_USERNAME`
  - `E2E_PASSWORD`
  - `E2E_CENTER_ID` (optionnel, defaut `11111111-1111-1111-1111-111111111111`)

## Lancer

```bash
npm run e2e
```

Le test stubbe les endpoints stats/export pour fiabiliser le scenario UI, tout en conservant un vrai login et la navigation applicative.

