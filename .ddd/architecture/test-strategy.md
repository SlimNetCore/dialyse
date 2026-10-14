---
status: proposed
date: 2026-10-09
sources: [.claude/rules/30-tests.md, .ddd/tactical/gmao.md §9, ADR 0015/0016, .ddd/research/{testcontainers-postgresql,approval-golden-master-java,mutation-testing-pit,architecture-tests-archunit-modulith}.md]
---
# Stratégie de tests

| Niveau | Cible | Outils (stack) | Emplacement | Exécution |
|---|---|---|---|---|
| Unitaire domaine | agrégats, VO, services, politiques (matrice `tactical/gmao.md` §9) | JUnit 5 + AssertJ, aucun double | `contexts/<ctx>/src/test/.../domain` | `mvn test` |
| Application | use cases, P1/P2, quota R23, autorisation | JUnit 5 + **fakes in-memory** des ports (pas de Mockito) | `.../application` (+ `testfixtures/` fakes) | `mvn test` |
| Contrat d'adapter | Repositories, `JournalDesMessagesTraites`, stockage | suite abstraite `*Contrat` exécutée contre le fake **et** l'adapter JPA | `.../infrastructure` | fake : `test` ; JPA : tag `integration` |
| Intégration adapter | JPA schéma `gmao`, read models, registre Modulith, listeners | `@DataJpaTest`/`@SpringBootTest` + Testcontainers `postgres:16-alpine`, conteneur en `@Bean` + `@ServiceConnection` | `.../infrastructure`, `.../interfaces` | `mvn verify` (tag `integration`) |
| ACL | traduction gmao.v1 ↔ legacy Planification/Notifications | tests table-driven (`@ParameterizedTest`) | `.../infrastructure/acl` | `mvn test` |
| Interface HTTP | contrôleurs, sécurité centre/habilitations, upload | `@AutoConfigureMockMvc`, `spring-boot-starter-security-test` | `.../interfaces/http` | `mvn verify` |
| Caractérisation | comportement legacy GMAO observable (HTTP + DB) | ApprovalTests `JsonApprovals` + scrubbers (dates, UUID, ordre), Testcontainers PG16, seams Horloge/UUID | `backend/platform/src/test/java/com/hemodialyse/backend/characterization/<slice>/` | vert sur legacy **avant** tout code neuf |
| Suite de la slice | refactoring sur place (ADR 0024) : la suite de caractérisation est la référence et tourne contre le nouveau code ; pas de suite de parité séparée, pas de drapeau | mêmes fichiers `*.approved.json` (écarts décidés : goldens `*_nouveau_code`) | idem | avant livraison |
| Architecture | règles de couches / contextes | `check-arch.ts` (hook + CI) ; ArchUnit `archunit-junit5` | `.../architecture/ArchitectureTest` | à chaque édition / `mvn test` |
| Mutation | qualité du filet et du domaine | PIT + `pitest-junit5-plugin`, `targetClasses=..contexts.<ctx>.domain.*` et classes legacy caractérisées | plugin dans `contexts/<ctx>/pom.xml` | à la demande / avant bascule |
| E2E | parcours GMAO critiques (peu) | Playwright (frontend existant) — lancés par l'utilisateur | `frontend/e2e` | manuel |

## Règles
- Noms `should_<comportement>_when_<condition>` en langage omniprésent ; Given/When/Then.
- Déterminisme : `Horloge` et `GenerateurIdentifiants` injectés ; aucune `Instant.now()`/`UUID.randomUUID()` en domaine/application.
- Seuils proposés (à fixer au plan) : score de mutation domaine ≥ 80 % ; filet de caractérisation ≥ 70 % des mutants tués sur les classes legacy touchées.
- `*.received.*` ignorés par git ; `*.approved.*` versionnés et relus (1 scénario = 1 fichier).
- Tests d'intégration taggés `integration` ; Docker requis en CI (déjà disponible : services PostgreSQL dans `flyway.yml`).

## Questions ouvertes
1. Seams Horloge/UUID dans le legacy GMAO (`Intervention.java` utilise `now()`) : à demander à l'utilisateur (`seamAllowed`) ou scrubber les valeurs ?
2. Les tests de caractérisation dans `platform/src/test` restent-ils hors `legacyPaths` (oui : seul `src/main/java` est gelé) — à confirmer.
