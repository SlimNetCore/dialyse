---
status: proposed
date: 2026-11-16
sources: décisions utilisateur du 2026-11-16 ; revue globale `.ddd/reviews/2026-11-16-global-final-decommissionnement-platform.md` §6 ; ADR 0207
---
# CLOT4 : réalisation (refactoring sur place, ADR 0024)

| Décision | Résultat |
|---|---|
| 1 SUPERADMIN | `MigrationRestController` : `@PreAuthorize("hasRole('ADMIN')")` (SUPERADMIN retiré) ; `CenterAccessGuard` : branche « accès transverse » et constante supprimées (seuls appelants : reprise et référentiels admin, deux routes que `RoleScopeFilter` refuse au SUPERADMIN avant le contrôleur). Aucune réponse HTTP modifiée. |
| 2 Archivage | `V60__archivage_des_tables_mortes.sql` : RENAME de 4 tables en `archive_<nom>` (`ALTER TABLE IF EXISTS`, PG16 et H2). Aucun usage vivant trouvé (Grep main) ; H2 des contextes déjà sans ces tables (ADR 0184) : aucun schéma H2 à adapter. `verifications-production.md` § 89 (avant/après, sauvegarde obligatoire). |
| 3 ACL | ADR 0207 : retrait des 10 ACL à l'événement « fin de la reprise ET retrait de la façade ADR 0161 » ; `context-map.md` mis à jour. |

## Non fait
- `infra/dataset/*.sql` (export de production généré) et `backend/README.md` (historique) non édités.
- `TableDuStockTest` garde `stock_parametres` dans sa liste (inoffensif).

## Vérification
Voir rapport final de l'exécution (passes ciblées puis build complet).
