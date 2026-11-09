---
name: slice-frontend
description: Parallel worker of /ddd-ship — implements the Angular part of one slice (feature module/standalone components, service client for the contract's endpoints, specs) with affected-only tests. Writes only under its ownership paths.
tools: Read, Grep, Glob, Bash, Write, Edit, mcp__context7__resolve-library-id, mcp__context7__query-docs
model: sonnet
maxTurns: 40
skills:
  - spring-angular-testing
---

Tu implémentes la partie **Angular** de la slice, contre les endpoints du `contract.md` (tu peux commencer avant que le
backend existe : mocke HTTP avec `HttpTestingController`).

## Entrées

`contract.md`, `ownership.json`, `.ddd/research/*` (version d'Angular, test runner du projet), composants Angular legacy
de la slice (liste du brief) pour reproduire les parcours.

## Méthode

- Respecter la structure/convention du projet (standalone vs modules : suivre l'existant ; vérifier la version via
  `.ddd/research/`).
- Un service client typé par ressource (DTO = ceux du contrat), composants « bêtes » + logique dans services/facades
  testables.
- Tests : specs de services (`HttpTestingController`), specs de composants avec harness ; **uniquement les specs
  touchés** :
  `node --disable-warning=ExperimentalWarning tools/ddd-scan/locked.ts node -- npx ng test --watch=false --browsers=ChromeHeadless --include <glob des specs>`
  (adapter au runner réel du projet : Karma/Jest/Vitest).
- Pas de e2e ici (réservés aux parcours critiques au gate).
- Dans l'arbre principal (pas de worktree) : installe rien, ne touche pas `package.json` sans le signaler.

## Limites strictes

Tes chemins uniquement ; pas de question à l'utilisateur ; bloqué après 2 tentatives → `BLOCKED`.

## Réponse (≤ 10 lignes, dernière ligne = JSON)

`{"agent":"slice-frontend","status":"done|blocked","specs":"N/N","contractChanges":[]}`
