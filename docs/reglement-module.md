# Module Règlements de factures

## Périmètre livré

- Backend hexagonal/DDD : nouveau bounded context `backend/bc-reglement`
- API REST : `GET /api/v1/reglements`, `GET /api/v1/reglements/dashboard`,
  `POST /api/v1/reglements/{factureId}/paiements`
- Frontend Angular : écran `frontend/src/app/features/reglement/reglement-workspace.component.*`
- Filtres : année, mois, caisse, agence, centre payeur
- Pagination : `page`, `size` → réponse `{items,total,page,size}`
- États métier : `NON_REGLEE`, `PARTIELLEMENT_REGLEE`, `REGLEE`
- Solde visuel : reste (rouge), réglée (vert), trop-perçu (orange)

## Règles métier appliquées

- montant réglé = 0 → facture `NON_REGLEE`
- montant réglé < montant facture → facture `PARTIELLEMENT_REGLEE`
- montant réglé >= montant facture → facture `REGLEE`
- si montant réglé > montant facture, le statut reste `REGLEE` mais le solde devient `TROP_PERCU`
- isolation multi-centre obligatoire sur toutes les requêtes via `centerId`

## Tables impactées

- existante : `factures`
- nouvelle : `facture_reglements`

## Tests ajoutés

- Backend unitaire : `backend/bc-reglement/src/test/.../ReglementServiceTest.java`
- Backend intégration : `backend/platform/src/test/.../ReglementRestControllerIntegrationTest.java`
- Frontend Vitest store : `frontend/src/app/features/reglement/state/reglement.store.spec.ts`
- Frontend Vitest composant : `frontend/src/app/features/reglement/reglement-workspace.component.spec.ts`
- Frontend Playwright : `frontend/e2e/reglement.spec.ts`

