# Environnements : développement local (H2) et production (PostgreSQL)

Le projet distingue les deux par **un profil Spring** (`prod`) et par des variables d'environnement. Sans profil, tout
est réglé pour le développement local : `./mvnw spring-boot:run` démarre sans rien configurer.

| Sujet               | Local (aucun profil)                                    | Production (`SPRING_PROFILES_ACTIVE=prod`)                                                                                       |
|---------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Base                | H2 en mémoire (`MODE=PostgreSQL`)                       | PostgreSQL 16 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`)                                                              |
| Schéma              | Hibernate `ddl-auto: update` + `db/schema.sql`          | **Flyway seul** (`db/migration`) ; Hibernate en `validate` ; `schema.sql` non exécuté                                            |
| Données             | `db/seed.sql` (démo), `STOCK_DEMO_DATA=true`            | aucune donnée de démo (`spring.sql.init.mode: never`, `STOCK_DEMO_DATA=false`)                                                   |
| Console H2, Swagger | ouverts sans authentification (`app.dev-tools.enabled`) | fermés (`app.dev-tools.enabled: false`, console H2 et springdoc désactivés)                                                      |
| Comptes de démo     | `SeedPasswordInitializer` (`@Profile("!prod")`)         | absent : aucun mot de passe public n'est jamais posé                                                                             |
| Secret JWT, base    | valeur de développement tolérée                         | `ProductionSafetyGuard` **refuse le démarrage** si `JWT_SECRET` < 32 caractères ou égal à la valeur de dev, ou si la base est H2 |
| Frontend            | `environment.ts` : `/api/v1` + proxy Angular            | `Dockerfile` : « même origine » `/api/v1`, Nginx proxifie `/api`, `/ws`, `/actuator/health`                                      |

## Où c'est défini

- `backend/platform/src/main/resources/application.yml` : valeurs par défaut (développement).
- `backend/platform/src/main/resources/application-prod.yml` : surcharges de production.
- `docker-compose.prod.yml` : pose `SPRING_PROFILES_ACTIVE=prod` ; réglage PostgreSQL (`shared_buffers`,
  `work_mem`, `random_page_cost`, `pg_stat_statements`), paramétrable dans `.env` (`PG_*`, voir `env.prod.example`).
- `.github/workflows/flyway.yml` : démarre le backend **avec le profil `prod`** sur un PostgreSQL 16 vierge migré par
  Flyway. Il échoue si le schéma Flyway ne suffit pas aux entités JPA (Hibernate `validate`), si la configuration de
  production est refusée, ou si la console H2 / Swagger répondent sans authentification.

## Règles pour les développeurs

1. **Tout changement de schéma = une nouvelle migration `V<n+1>__description.sql`** dans `db/migration` (jamais
   modifier une migration publiée : la CI l'interdit), **plus** son équivalent H2 dans les `@Entity` /
   `db/schema.sql` pour le développement local.
2. Une table lue uniquement en JDBC n'est pas contrôlée par Hibernate `validate` : sa migration doit être relue avec
   soin, `schema.sql` ne la créera plus en production.
3. Repli d'urgence si un démarrage de production échoue sur une validation de schéma : `HIBERNATE_DDL_AUTO=none`
   dans le `.env` de la VM, redémarrer, puis ajouter la migration manquante. Ne pas rester dans ce mode.
4. Ne jamais ajouter de mot de passe « de commodité » dans un composant actif en production ; réserver ce genre de
   composant à `@Profile("!prod")` ou à une propriété désactivée par défaut.
