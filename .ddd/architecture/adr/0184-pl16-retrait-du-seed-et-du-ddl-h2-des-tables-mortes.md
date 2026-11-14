---
status: accepted (livrée le 2026-11-14)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: platform (schéma H2 de démonstration et de test), gmao, stock
related: ADR 0024, 0047, 0135 ; `inventaire-platform-restant-v2.md` ligne 22
---

# 0184 - PL16 : retrait du seed et du DDL H2 des tables mortes ; aucun DROP Flyway

Décision de l'utilisateur : retirer SEULEMENT le seed et le DDL H2. Aucune migration Flyway touchée ni ajoutée (pas de V60), aucun `DROP` en production.

## Vérification par Grep (backend, contexts, frontend, docs, infra)
| Table | Code vivant | H2 (`schema.sql`) | Seed | Décision |
|---|---|---|---|---|
| `user_center_assignment` | aucun (entité retirée en PL1, ADR 0135) | DDL présent | 7 `MERGE` dans `seed.sql` (aucune autre lecture) | DDL + 7 MERGE retirés |
| `gmao_alertes_maintenance` | aucun | aucun DDL | aucun | rien à retirer en H2 (existe seulement dans Flyway V5) |
| `gmao_historique_maintenance` | aucun | aucun DDL | aucun | idem |
| `stock_parametres` | aucun (route et port retirés, ADR 0047) | DDL présent | aucun | DDL retiré |
Aucune table réellement utilisée : pas d'arrêt.

## Laissé en place, à dessein
Flyway `V1`, `V5`, `V45` (les trois tables y restent) ; `infra/dataset/*.sql` (export de production, `01_dataset_renadial.sql` insère encore dans `user_center_assignment`, `schema_prod.sql` en porte le DDL : fichiers générés, non édités à la main) ; `backend/platform/.../db/demo/renadial-*.sql` ne les citent pas. La liste `TABLES_DU_STOCK` de `TableDuStockTest` garde `stock_parametres` (la table existe encore en production). `backend/README.md` cite `user_center_assignment` à titre historique.

## À décider plus tard (accord écrit requis)
`DROP` de `user_center_assignment`, `gmao_alertes_maintenance`, `gmao_historique_maintenance`, `stock_parametres` en production (V60) : NON fait.
