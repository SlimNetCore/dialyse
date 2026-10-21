# Module Règlements de factures

> Refonte sur place (slice F4, 2026-10-21, ADR 0033) : les règlements sont dans le contexte `backend/contexts/facturation`
> (le module `bc-reglement` est retiré). Détail : `.ddd/migration/slices/facturation-f4-reglements.md`. Règles :
> `docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md` (RG-REG-001 à 007).

## Périmètre livré

- Backend hexagonal/DDD : contexte `backend/contexts/facturation` (domaine `reglement`, application `reglement`, adaptateurs JDBC,
  `ReglementControleur`)
- API REST : `GET /api/v1/reglements`, `GET /api/v1/reglements/dashboard`, `GET /api/v1/reglements/export`,
  `POST /api/v1/reglements/{factureId}/paiements`, `GET /api/v1/reglements/{factureId}/paiements`
- Frontend Angular : écran `frontend/src/app/features/reglement/reglement-workspace.component.*`
- Filtres : année, mois, caisse, agence, centre payeur
- Pagination : `page`, `size` → réponse `{items,total,page,size}`
- États métier : `NON_REGLEE`, `PARTIELLEMENT_REGLEE`, `REGLEE`
- Solde visuel : reste (rouge), réglée (vert), trop-perçu (orange)

## Règles métier appliquées

- somme signée des règlements = 0 → facture `NON_REGLEE`
- somme < montant facture → facture `PARTIELLEMENT_REGLEE`
- somme >= montant facture → facture `REGLEE`
- si la somme dépasse le montant facture, le statut reste `REGLEE` mais le solde devient `TROP_PERCU` (accepté)
- un règlement négatif est un remboursement : refusé (422) au-delà du total déjà réglé ; écriture comptable de sens inverse
- mode de règlement facultatif (`BANQUE`, `CHEQUE`, `CAISSE`, `ESPECES`) et référence de paiement ; `CAISSE`/`ESPECES` au
  journal de caisse, sans mode : banque (à valider par le métier)
- clé d'idempotence facultative (`cleIdempotence`) : un renvoi ne crée pas de doublon
- auteur = utilisateur de la session ; isolation multi-centre : 403 si le `centerId` n'est pas celui de la session

## Tables impactées

- existante : `factures`
- `facture_reglements` (colonnes ajoutées par la migration V41 : `mode_reglement`, `reference_paiement`, `cle_idempotence`)

## Tests

- Backend unitaire et cas d'usage : `backend/contexts/facturation/src/test/.../domain/reglement/*`, `.../application/reglement/*`
- Contrats (double en mémoire, H2, PostgreSQL 16) : `.../contrat/Reglements*`
- Caractérisation et parité : `backend/platform/src/test/.../characterization/facturation/reglements/`
- Backend intégration : `backend/platform/src/test/.../ReglementRestControllerIntegrationTest.java`
- Frontend Vitest store : `frontend/src/app/features/reglement/state/reglement.store.spec.ts`
- Frontend Vitest composant : `frontend/src/app/features/reglement/reglement-workspace.component.spec.ts`
- Frontend Playwright : `frontend/e2e/reglement.spec.ts`
