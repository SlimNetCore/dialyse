---
status: proposed
date: 2026-10-09
decision-makers: [architecte, développeurs]
context-scope: global
research: [.ddd/research/spring-boot-4-web-data-tx.md]
---
# 0012 — Garder domaine et application sans Spring, JPA, Jackson ni Lombok ; mapping explicite en infrastructure

## Contexte et problème
Legacy : `bc-gmao` est déjà sans Spring, mais les entités JPA (`platform/.../persistence/entity/gmao`) et la transaction multi-agrégats (`InterventionEquipementStatutService.java:49-58`) vivent dans des services Spring. La règle `rules/12-application-layer.md` donne la frontière transactionnelle à l'application.

## Options considérées
1. Annoter les agrégats JPA — contre : le schéma dicte le modèle ; violait R8 (journal non chargé) et l'immutabilité des VO.
2. Application avec `@Transactional` Spring — pour : simple ; contre : couple les use cases à Spring et rend les tests application dépendants du conteneur.
3. **Domaine et application en Java pur** ; transaction exprimée par un port applicatif `UniteDeTravail` (implémenté en infrastructure par la gestion transactionnelle Spring) ; use cases instanciés par `@Bean` dans `configuration/`.

## Décision
Option 3. Interdits (cf. `.ddd/arch-rules.json`) : domaine = `org.springframework`, `jakarta`, `javax`, `org.hibernate`, Jackson, Lombok, SLF4J, Timefold ; application = Spring, JPA, Servlet, Jackson, Lombok. Records Java pour VO et événements. Entités `*Jpa` + mappers en `infrastructure/persistence`.

## Conséquences
+ tests de domaine/application sans conteneur ; fakes in-memory des ports.
− code de mapping agrégat ↔ entité à écrire et tester (tests de contrat).
⚠ l'API Spring utilisée par l'adapter `UniteDeTravail` doit faire l'objet d'une note de recherche avant la slice 1.
## Révision
Toute exception (ex. annotation imposée par un framework) = nouvel ADR + règle ciblée.
