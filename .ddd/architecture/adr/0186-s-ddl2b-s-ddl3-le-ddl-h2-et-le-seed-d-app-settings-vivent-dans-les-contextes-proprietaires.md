---
status: accepted (livrée le 2026-11-14 ; tranches restantes décrites dans s-ddl3-realisation.md)
date: 2026-11-14
decision-makers: [architecte]
context-scope: comptabilite, stock, documents, notifications, absences-patients, planification, platform (configuration, gardes)
related: ADR 0024, 0137, 0185 ; `inventaire-platform-restant-v2.md` ligne 17
---

# 0186 - S-DDL2b / S-DDL3 : le DDL H2 (et le seed d'`app_settings`) vivent dans le contexte propriétaire

## Constat (mécanisme)
- `application.yml` : `spring.sql.init.mode=always`, `schema-locations` et `data-locations` (`${SEED_DATA_LOCATIONS:classpath:db/seed.sql}`) ; `defer-datasource-initialization: true` (Hibernate, `ddl-auto: update`, passe AVANT les scripts) ; `application-prod.yml` : `never` (Flyway V1-V59, inchangé). Le profil `demo-renadial` REMPLACE `data-locations` par `seed.sql` + `renadial-seed.sql`.
- Les tests d'intégration de la plateforme (`@SpringBootTest`) lisent cette configuration ; ADR 0137 (`BaseH2UniqueParContexte`) ne donne qu'une URL H2 distincte par contexte Spring de test, jamais un schéma différent. Les tests des contextes (`contexts/*/src/test`) construisent leur propre DDL en ligne (`CREATE TABLE` dans le test) : aucun contexte n'avait de script H2 de production.
- Les jars des contextes sont sur le classpath de la plateforme : un script `classpath:db/h2/<contexte>-schema.sql` d'un contexte est résolu comme `classpath:db/schema.sql`.

## Décision
1. Chaque contexte propriétaire porte `src/main/resources/db/h2/<contexte>-schema.sql` (et `-seed.sql`) ; noms uniques (pas de collision de chemin entre jars). Liste explicite et ordonnée dans `spring.sql.init.schema-locations` (pas de joker : ordre déterministe) ; seed d'`app_settings` ajouté à `data-locations` par défaut ET dans `application-demo-renadial.yml` (même ordre qu'avant : c'étaient les dernières instructions de `seed.sql`).
2. Ordre : `schema.sql` (reste) puis les scripts. Aucune clé étrangère ne franchit un contexte ; chaque tranche emporte avec elle ses `ALTER`/index/`UPDATE` (stock : `ALTER stock_movements` avec sa création). Un `ALTER` qui touche une table d'un autre propriétaire (facturation sur `seances`, planification sur `salle`) interdit de déplacer cette table tant qu'il n'a pas son propre propriétaire.
3. Preuve de strict identité : `SchemaH2DesScriptsParContexteTest` rejoue la liste sur une H2 vierge et compare INFORMATION_SCHEMA (colonnes avec rang, type, largeur, précision, nullité, défaut, identité ; contraintes ; vérifications ; index) à `src/test/resources/db/schema-h2-reference.txt`, relevé sur l'ANCIEN script unique avant la scission. Les noms engendrés par H2 (`CONSTRAINT_xx`, suffixe `_INDEX_hex`) sont normalisés. Régénération volontaire : `-Dschema.reference.ecrire=true`.
4. Hors périmètre : Flyway, `seed.sql` (hors `app_settings`), `renadial-*.sql`.

## Conséquences
`SEED_DATA_LOCATIONS` remplace toujours la liste complète (comme avant) : qui l'utilise doit y ajouter `classpath:db/h2/comptabilite-seed.sql`. `backend/scripts/seance-se7e-pg16-rejeu.sh` rejoue `seed.sql` seul sur PostgreSQL : il ne pose plus les 4 compteurs `app_settings` (hors production, à ajouter au script si ces compteurs y sont requis).
