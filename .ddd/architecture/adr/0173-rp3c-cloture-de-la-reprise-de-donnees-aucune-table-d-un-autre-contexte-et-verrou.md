---
status: accepted (aucun changement de comportement, aucun code de production modifié)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données)
related: ADR 0024, 0097, 0099, 0134, 0169, 0170, 0171, 0172 ; `.ddd/migration/reviews/rp3c-realisation.md` ; `.ddd/migration/reviews/inventaire-platform-restant-v2.md` ligne 11
---

# 0173 - RP3c : clôture de la reprise de données, aucune table d'un autre contexte, verrou permanent

Refactoring sur place (ADR 0024). Dernière sous-slice de RP3 (RP3a : ADR 0171, RP3b : ADR 0172).

## Constat (inventaire par Grep le 2026-11-14)
Le périmètre prévu pour RP3c était DÉJÀ livré par des slices antérieures :
- **Séance historique** : import (`findExisting`/`insert`/`update` de `SEANCES`) et annulation par `SeancesDeLaReprisePubliees` depuis SE7e (ADR 0099) ; caractérisé par `LesSeancesDeLaRepriseDeDonneesCharacterizationIntegrationTest` (SE7e).
- **Soldes d'ouverture** : propriétaire = **Facturation** (tables de factures). `OpeningBalanceJdbcAdapter` n'est plus qu'un traducteur vers `RepriseDesSoldes` (F6, `facturation.publishedlanguage`) ; caractérisé par `soldes_ouverture_controles_et_remplacement`.
- **Annulation** : `MigrationRollbackJdbcAdapter` n'a plus ni `JdbcTemplate` ni SQL (RP1, RP2, RP3b).
- `MigrationRestController`, `MigrationDtos`, `MigrationApplicationService`, `infrastructure/migration/**` : aucun SQL.
Seules requêtes restantes du paquet : celles des quatre tables PROPRES de la reprise (`migration_batch`, `migration_entity_run`, `migration_id_map`, `migration_value_map`), dans trois adapters.

## Décision
Aucun nouveau port : en créer un par « destinataire » dupliquerait `SeancesDeLaReprisePubliees` et `RepriseDesSoldes`. RP3c se réduit à **verrouiller** l'état atteint : test d'architecture `LaRepriseDeDonneesNeTouchePlusLesTablesDesContextesTest` (aucune table autre que `migration_*` nommée par une requête de la reprise ; seuls les trois adapters des tables de la reprise portent un `JdbcTemplate`). Sans liste blanche ni butoir : il n'y a pas d'entrée à ajouter.

## Butoirs 2027-06-30
Aucune entrée de la reprise ne subsistait dans les gardes (soldées par RP2, RP3a, RP3b). Les butoirs 2027-06-30 restants de `TableDesSeancesTest` (`schema.sql`, `seed.sql`, `renadial-*.sql`, `cahier_dialyse.jrxml`) ne concernent PAS la reprise : ce sont les slices S-DDL2/3, SEED2..n et DOC5 de l'inventaire ; ils ne sont ni soldés ni reportés ici (non modifiés).

## Décisions héritées, inchangées
D5 (colonnes de place écrites directement par la reprise, KB-S6a-3) : reproduit, à confirmer métiers/DPO <= 2026-12-15. KB-RP3B-1/2 : reproduits, même échéance. `seamAllowed` = []. Aucun golden modifié ou ajouté, aucun `_nouveau_code`, aucune KB « CORRIGÉ ».
