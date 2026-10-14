---
status: proposed
date: 2026-10-09
decision-makers: [architecte]
context-scope: global
research: [.ddd/research/architecture-tests-archunit-modulith.md, .ddd/research/spring-modulith-events-outbox.md]
---
# 0015 — Vérifier l'architecture par `check-arch.ts` (hook) et ArchUnit (build Maven), Spring Modulith `verify()` différé

## Contexte et problème
La pureté des couches repose sur des packages (ADR 0011). `ApplicationModules.of(BackendApplication).verify()` détecte les modules par sous-packages directs (`domain`, `application`, `infrastructure` du legacy) : résultat sans sens tant que le legacy n'est pas modularisé.

## Options considérées
1. Modulith `verify()` + `@Modulithic(additionalPackages)` — contre : modifie la classe principale legacy ; détection par jar non documentée.
2. **`check-arch.ts`** (règles `.ddd/arch-rules.json`, à chaque édition) **+ ArchUnit `archunit-junit5`** (test par contexte, dans `mvn verify`).
3. Seulement `check-arch.ts` — contre : hors build Maven/CI Java.

## Décision
Option 2. ArchUnit reproduit les règles : domaine sans dépendance hors JDK/shared-kernel ; application sans Spring/JPA ; seuls `infrastructure.acl` dépend de `com.hemodialyse.backend.(domain|application|infrastructure)..` ; accès inter-contextes uniquement via `publishedlanguage` ; pas de cycles entre contextes. Version 1.5.1 (dernière connue, compat Java 21 à confirmer au premier build).

## Conséquences
+ double filet (éditeur + CI). − règles à maintenir en double (source : `arch-rules.json`).
⚠ couches Java renommées : `interfaces` (mot réservé `interface`), `publishedlanguage`.
## Révision
Quand ≥ 3 contextes sont extraits : réévaluer Modulith `verify()` avec `explicitly-annotated`.
