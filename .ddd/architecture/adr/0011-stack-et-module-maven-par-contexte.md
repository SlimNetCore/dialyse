---
status: proposed
date: 2026-10-09
decision-makers: [architecte, direction technique]
context-scope: global
research: [.ddd/research/spring-boot-4-web-data-tx.md, .ddd/research/spring-modulith-events-outbox.md]
---
# 0011 — Construire chaque contexte extrait comme un module Maven `backend/contexts/<ctx>` sur la stack existante (Spring Boot 4.0.8, Java 21), packagé sous `com.hemodialyse.backend.contexts.<ctx>`

## Contexte et problème
Le backend réel est un multi-module Maven (`backend/pom.xml` : parent `spring-boot-starter-parent` 4.0.8, `java.version` 21, modules `shared-kernel`, `bc-*`, `platform`). `platform` porte l'unique application Spring Boot (`BackendApplication`, package `com.hemodialyse.backend`) et tous les adapters. ADR 0001 : monolithe modulaire ; ADR 0008 : Strangler. Il faut une place pour le nouveau code qui n'oblige pas à modifier le legacy (gelé).

## Options considérées
1. Changer de version (Boot 4.1.x) en même temps — contre : deux migrations simultanées, non requis par le domaine.
2. Un module Maven par contexte **et par couche** (`gmao-domain`, `gmao-application`…) — pour : pureté imposée par le compilateur ; contre : ×5 modules par contexte (≈ 55), lourd pour des contextes supporting.
3. **Un module Maven par contexte**, couches = packages, pureté imposée par `check-arch.ts` (hook) + ArchUnit (build) — pour : un contexte = une unité extractible (ADR 0001) ; contre : la pureté dépend des tests d'architecture.
4. Packages hors racine (`com.hemodialyse.contexts…`) — contre : imposerait `@EntityScan`/`scanBasePackages` sur la classe principale legacy (`@EntityScan` remplace les packages par défaut => risque de casser le legacy).

## Décision
Option 3, sur la stack actuelle **Boot 4.0.8 / Java 21** (pas de montée de version liée à la migration). Package racine `com.hemodialyse.backend.contexts.<ctx>` : sous le package de `BackendApplication`, donc scanné (composants, entités, dépôts) sans toucher la classe principale. Seules modifications legacy : déclarer le module dans `backend/pom.xml` et la dépendance dans `platform/pom.xml` (câblage Strangler, accord utilisateur requis).

## Conséquences
+ zéro modification de `BackendApplication` ; extraction future = déplacer un module.
− le scan automatique charge le contexte dès que le jar est présent => activation conditionnée par propriété `hemodialyse.contexts.<ctx>.enabled` (bascule réversible).
⚠ noms d'entités JPA uniques dans l'unité de persistance partagée : suffixer les entités d'infrastructure (`EquipementJpa`) et fixer `@Entity(name=…)`.
## Révision
À la fin de l'extraction de 3 contextes, ou si Boot 4.0 sort du support : ADR de montée de version.
