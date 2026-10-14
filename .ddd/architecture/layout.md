---
status: proposed
date: 2026-10-09
sources: [.ddd/config.json, backend/pom.xml, backend/platform/pom.xml, .ddd/strategic/bounded-contexts.md, .ddd/tactical/gmao.md, ADR 0001/0008/0010/0011-0016, .ddd/research/INDEX.md]
---
# Layout & stack — nouveaux contextes (`backend/contexts`)

## 1. Stack retenue
| Sujet | Choix | Version | Preuve (note datée) | ADR |
|---|---|---|---|---|
| Langage / build | Java 21, Maven multi-modules (parent `backend-parent`) | constaté `backend/pom.xml` | — (existant) | 0011 |
| Framework | Spring Boot (webmvc, data-jpa, security, validation) | **4.0.8** (constaté, pas de montée) | spring-boot-4-web-data-tx.md (2026-10-09) | 0011 |
| Persistance | PostgreSQL 16, JPA/Hibernate (infra seulement), schéma par contexte, Flyway CLI chaîne unique | constaté (`docker-compose.prod.yml`, `flyway.yml`) | spring-boot-4-web-data-tx.md | 0014 |
| Événements / outbox | Spring Modulith Event Publication Registry JDBC + `@ApplicationModuleListener` | BOM 2.0.x (↔ Boot 4.0) | spring-modulith-events-outbox.md (2026-10-09, complété) | 0013 |
| Tests | JUnit 5, AssertJ, Mockito (adapters seulement), starters `*-test` Boot 4 | gérés par Boot | spring-boot-4-web-data-tx.md | 0016 |
| Conteneurs de test | Testcontainers 2.x `postgres:16-alpine`, `@ServiceConnection` | 2.0.5 | testcontainers-postgresql.md | 0016 |
| Architecture | `check-arch.ts` + ArchUnit `archunit-junit5` | 1.5.1 | architecture-tests-archunit-modulith.md | 0015 |
| Caractérisation / parité | ApprovalTests.Java (`JsonApprovals`) | 31.0.0 | approval-golden-master-java.md | 0016 |
| Mutation | PIT + pitest-junit5-plugin | 1.30.0 / 1.2.3 | mutation-testing-pit.md | 0016 |

Toutes les notes : source web, context7 indisponible (clé invalide, re-testé le 2026-10-09) => versions à figer après `mvn dependency:tree`.

## 2. Arborescence d'un contexte (ex. GMAO)
```
backend/contexts/
  gmao/                                      # module Maven `contexts-gmao` (artefact unique du contexte)
    pom.xml
    src/main/java/com/hemodialyse/backend/contexts/gmao/
      domain/            equipement/ intervention/ intervenant/ planmaintenance/ piecejointe/ (agrégats, VO, événements, Repository), service/ (UniciteDuCodeEquipement, CalculDIndisponibilite, AnalyseDeDecision)
      application/       use cases (1 classe = 1 intention), ports sortants applicatifs (Horloge, UniteDeTravail, PublicateurDEvenements…), politiques P1/P2, read-model ports
      infrastructure/    persistence/ (entités *Jpa, mappers, adapters Repository, read models SQL) · evenements/ (publication Modulith, journal des messages traités) · stockage/ · acl/legacyplanification/ (seul code autorisé à importer le legacy)
      interfaces/        http/ (contrôleurs REST, DTO, détection type fichier) · consumers/ (listeners P1/P2, @ApplicationModuleListener)
      publishedlanguage/ v1/ (événements d'intégration gmao.v1 : records Java, sans dépendance)
      configuration/     composition root du contexte : @Configuration conditionnée par `hemodialyse.contexts.gmao.enabled`, @Bean des use cases
    src/main/resources/  (vide ; migrations dans la chaîne Flyway existante, ADR 0014)
    src/test/java/com/hemodialyse/backend/contexts/gmao/   miroir des couches + architecture/ (ArchUnit)
  shared-kernel/         # module `contexts-shared-kernel` (ADR 0010 : CentreId, SocieteId, Mois) — créé à la 1re slice qui en a besoin
```
Adaptations Java du modèle : `interface` est un mot réservé => `interfaces` ; `published-language` => `publishedlanguage`.

## 3. Composition root
- Application unique : `platform` / `BackendApplication` (legacy, inchangé). Le package `com.hemodialyse.backend.contexts..` étant sous sa racine, composants, entités JPA et dépôts sont découverts sans modifier la classe principale (ADR 0011).
- Chaque contexte expose `configuration/<Ctx>Configuration` (beans des use cases Java purs, adapters), toujours active (refactoring sur place, ADR 0024 : pas de drapeau ni de bascule ; retour arrière = redéploiement).
- Câblage legacy nécessaire (accord utilisateur, règle legacy-safety 2b) : `<module>contexts/gmao</module>` dans `backend/pom.xml` ; dépendance `contexts-gmao` dans `backend/platform/pom.xml` ; migrations `V<n>__gmao_*.sql`.

## 4. Partagé
- Shared kernel cible : `CentreId`, `SocieteId`, `Mois` uniquement (ADR 0010). `Money`, `PagedResult`, `CenterId` legacy **non** importés : `Montant` est un VO GMAO ; pagination = infrastructure.
- Aucune dépendance Maven vers `bc-*`, `platform` ou `shared-kernel` legacy ; le legacy n'est atteint que par `infrastructure/acl/**`.

## 5. Règles d'architecture actives
`.ddd/arch-rules.json` (check-arch, hook PostToolUse) ; reprises en ArchUnit dans `src/test/.../architecture/` à la slice 1 (ADR 0015) :
domain → {domain, shared-kernel} · application → + {publishedlanguage propre} · infrastructure/interfaces → + {application} · acl → + {legacy} · configuration → tout sauf legacy · inter-contextes : `publishedlanguage` ou shared-kernel uniquement · frameworks interdits : domaine (Spring, jakarta, javax, Hibernate, Jackson, Lombok, SLF4J, Timefold), application (Spring, JPA, Servlet, Jackson, Lombok).

## Questions ouvertes
1. Modulith : registre d'événements utilisable sans `@Modulithic` / détection de modules ? (pilote slice 1, ADR 0013)
2. `ddl-auto: update` (`application.yml:12`) : passer en `validate` pour éviter la création des tables `gmao` hors Flyway ? Accord requis.
3. API Spring de l'adapter `UniteDeTravail` et des listeners transactionnels : note de recherche à produire avant la slice 1.
4. Artefact Testcontainers PostgreSQL et versions ArchUnit/ApprovalTests/PIT : figer après résolution Maven.
5. (Clos) Routes : mêmes URLs `/api/v1/gmao/**`, refactoring sur place sans routage (ADR 0024).
