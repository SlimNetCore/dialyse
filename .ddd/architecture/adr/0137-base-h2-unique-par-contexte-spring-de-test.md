---
status: accepted
date: 2026-11-11
decision-makers: [architecte]
context-scope: platform (tests)
related: ADR 0024, 0133 ; `.ddd/migration/reviews/isolation-tests-seed.md`
---

# 0137 - Une base H2 unique par contexte Spring de test

Refactoring sur place (ADR 0024) : aucun code de production, aucune assertion de golden modifies.

## Contexte
Le profil `test` fixe `jdbc:h2:mem:hemodialyse;DB_CLOSE_DELAY=-1` : tous les contextes Spring en cache partagent une base et
`db/seed.sql` est rejoue sur une base deja modifiee. Ses ids derives de `ROW_NUMBER()` collisionnent selon l'ordre d'execution
(echec en ordre aleatoire, ex. seed 12709620220600 ; hypothese ADR 0133 confirmee).

## Decision
Un `ApplicationContextInitializer` de test (`BaseH2UniqueParContexte`, enregistre dans `src/test/resources/META-INF/spring.factories`)
remplace l'URL partagee par `jdbc:h2:mem:hemodialyse-<UUID>` pour chaque contexte. Les URL explicites des tests sont conservees.

## Consequences
Les tests sont independants de l'ordre ; le seed s'execute sur une base vierge (jamais rejoue). Alternative ecartee : rendre le
seed idempotent (production touchee, 29 `INSERT ... SELECT` a reecrire, ne traite pas la pollution entre classes).
