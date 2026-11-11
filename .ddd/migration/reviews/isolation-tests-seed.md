---
status: proposed
date: 2026-11-11
sources: backend/platform/src/test/resources/application-test.yml, backend/platform/src/main/resources/application.yml, db/seed.sql
---
# Isolation des tests de backend/platform : seed.sql rejoue sur un H2 partage

## Reproduction
Commande (depuis `backend/`) : `./mvnw -o -pl platform -am test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=12709620220600 -l <journal>`
Resultat : BUILD FAILURE en 12 min 05 s, 3382 tests, 1 echec + 7 erreurs (le jeu exact des classes en echec varie
selon l'ordre effectif ; cette execution : `ControleDesPeremptionsCharacterizationIntegrationTest` = contexte non chargeable,
`InitialSetupIntegrationTest` = 400 au lieu de 200).

## Cause reelle (preuve)
- `application-test.yml` (profil `test`) impose `jdbc:h2:mem:hemodialyse;DB_CLOSE_DELAY=-1` a TOUS les contextes Spring.
  `DB_CLOSE_DELAY=-1` garde la base en vie tant que la JVM vit : tous les contextes en cache (configurations differentes :
  `@Import`, `@MockitoBean`, `@TestPropertySource`...) partagent donc la MEME base.
- `spring.sql.init.mode=always` rejoue `db/seed.sql` a CHAQUE nouveau contexte, sur une base deja mutee par les tests precedents.
- Le seed n'est idempotent que sur une base a l'etat du seed : ses `INSERT ... SELECT` generent leurs ids a partir de
  `ROW_NUMBER()` (`rn`) et ne se gardent que par `NOT EXISTS` sur une cle metier. Si des tests ont ajoute/supprime des
  lignes sources, `rn` se decale et l'id calcule collisionne avec un id existant.
- Erreur observee (journal, ligne ~63122) : `ScriptStatementFailedException ... statement #266 of db/seed.sql` :
  `Violation d'index unique ou cle primaire ... AUTRES_TRAITEMENTS_PRESCRIPTION(ID) VALUES (UUID '97001052-7000-...')`.
- En ordre par defaut, l'ordre des classes se trouve etre favorable (les classes qui mutent passent apres les contextes
  charges) : le defaut est latent, pas absent. Aucun `@DirtiesContext` n'est en cause (un seul usage, `AuthCorsIntegrationTest`,
  qui a deja sa propre base `hemodialyse_cors`).

## Correctif retenu
Une base H2 unique par contexte Spring, via un `ApplicationContextInitializer` enregistre dans
`src/test/resources/META-INF/spring.factories` : si l'URL resolue est l'URL partagee `jdbc:h2:mem:hemodialyse...`, elle est remplacee
par `jdbc:h2:mem:hemodialyse-<UUID>...` (propriete `spring.datasource.url` et `jakarta.persistence.jdbc.url`). Les tests qui fixent
deja leur propre URL ne sont pas touches. Aucun fichier de production modifie, aucun test desactive, aucune assertion changee.
Le seed reste non idempotent par construction : il suppose une base vierge, ce que garantit desormais l'isolation.
