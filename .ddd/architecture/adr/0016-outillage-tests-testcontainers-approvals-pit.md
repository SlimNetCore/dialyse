---
status: proposed
date: 2026-10-09
decision-makers: [architecte, développeurs]
context-scope: global
research: [.ddd/research/testcontainers-postgresql.md, .ddd/research/approval-golden-master-java.md, .ddd/research/mutation-testing-pit.md, .ddd/research/spring-boot-4-web-data-tx.md]
---
# 0016 — Outiller les tests : Testcontainers PostgreSQL 16, ApprovalTests (caractérisation/parité), PIT sur le domaine

## Contexte et problème
Stack de test actuelle : JUnit 5 + Mockito, starters de test Boot 4 (`*-test`), H2 en runtime. Le filet de caractérisation doit tourner sur le vrai SGBD de production (PostgreSQL 16, `docker-compose.prod.yml`) et la qualité du filet doit être mesurée.

## Options considérées
1. H2 pour les tests d'adapters — contre : dialecte ≠ prod, schémas, contraintes d'unicité (R7).
2. **Testcontainers 2.x `postgres:16-alpine` + `@ServiceConnection`**, conteneur déclaré en `@Bean` (cache de contexte) — contre : Docker requis en CI/poste.
3. Golden master : **ApprovalTests.Java** (`JsonApprovals`, scrubbers dates/UUID) vs JSON maison — ApprovalTests retenu (fichiers `*.approved.json` relisibles).
4. Mutation : **PIT + pitest-junit5-plugin** ciblé sur `..contexts.<ctx>.domain..` et sur les classes legacy caractérisées.

## Décision
Options 2, 3 (ApprovalTests 31.0.0), 4 (pitest-maven 1.30.0 / plugin 1.2.3). Versions = dernières vues sur Maven Central le 2026-10-09 (source web, non vérifiée par context7) ; à figer après `mvn dependency:tree`.

## Conséquences
+ parité caractérisation ↔ nouveau code sur le même SGBD.
− temps de build (conteneurs) : tag JUnit `integration` exécuté en `verify`.
⚠ artefact Testcontainers PostgreSQL (`postgresql` vs `testcontainers-postgresql`) et cohabitation Jackson 2 (ApprovalTests) / Jackson 3 (Boot 4) à vérifier au premier build.
## Révision
Après la slice GMAO n°1 (temps de build, score de mutation obtenu).
