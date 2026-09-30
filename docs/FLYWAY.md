# Flyway — gestion des migrations de schéma

## Où

- Scripts : `backend/platform/src/main/resources/db/migration/`
- `V1__baseline_schema.sql` = schéma de production au 2026-09-30 (pg_dump). **Ne jamais le modifier.**
- Anciennes migrations (jamais exécutées) : `backend/db/migration-legacy/`.

## Ajouter une migration

1. Créer `V<n+1>__description_en_snake_case.sql` (SQL PostgreSQL 16, pas de syntaxe H2).
2. Mettre à jour l'entité JPA correspondante dans le même commit.
3. Ne jamais modifier/supprimer une migration déjà poussée : en créer une nouvelle.
4. Désactiver le reformatage automatique de l'IDE sur les `.sql` (il casse les scripts).

## Exécution

| Contexte        | Mécanisme                                                                                                                                                                                         |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Production (VM) | service `flyway` de `docker-compose.prod.yml`, exécuté par le workflow `deploy-vm.yml` **avant** la reconstruction du backend ; le backend dépend de `flyway` (`service_completed_successfully`). |
| Dev local       | `docker compose run --rm flyway migrate` (ou `info`, `validate`)                                                                                                                                  |
| CI              | workflow réutilisable `.github/workflows/flyway.yml`, appelé par `ci.yml` et `deploy-vm.yml`                                                                                                      |

Base existante sans table `flyway_schema_history` → baseline automatique en V1 (`FLYWAY_BASELINE_ON_MIGRATE=true`), puis
application de V2+. Base vide → V1 puis V2+.
`clean` est désactivé en production.

## Contrôles CI (`flyway.yml`)

1. **Immutabilité** : échec si une migration existante est modifiée ou supprimée (comparaison avec la branche de base /
   le push précédent).
2. **Migrate + validate** sur PostgreSQL 16 vierge (nommage validé, idempotence).
3. **Cohérence JPA** : le backend démarre avec `spring.jpa.hibernate.ddl-auto=validate` sur le schéma migré → échec si
   une entité a changé sans migration.

Le déploiement (`deploy-vm.yml`) et les jobs `docker`/`e2e` (`ci.yml`) dépendent du job Flyway.

> Le backend reste en `ddl-auto: update` en production pour l'instant ; une fois quelques
> migrations V2+ en place, passer à `validate` (variable `SPRING_JPA_HIBERNATE_DDL_AUTO`).

