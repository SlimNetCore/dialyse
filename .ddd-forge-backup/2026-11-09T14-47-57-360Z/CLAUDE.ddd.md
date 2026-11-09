# DDD Forge — pipeline DDD (stratégique → tactique) pour projet neuf ou refactoring legacy

Ce dépôt est un **kit** à copier dans un projet cible (`node scripts/install.mjs <chemin-du-projet>`).
Il orchestre : Event Storming → design stratégique → design tactique → architecture hexagonale → (legacy) tests de
caractérisation + Strangler Fig → implémentation par *slices* → vérification.

## Carte du pipeline

```
/ddd-init ─► /ddd-scan* ─► /ddd-discover ─► /ddd-strategic ─GATE─► /ddd-tactical <ctx> ─GATE─► /ddd-architecture
                                                                                                   │
        (legacy)  /ddd-strangle-plan ─GATE─► pour chaque slice : /ddd-safety-net ─► /ddd-slice ─► cutover ─► retire
        (neuf)    slices = use cases du modèle tactique ─► /ddd-slice ─────────────────────────────────────┘
                                                                                                   ▼
                                                                                              /ddd-verify
```

`*` legacy uniquement. `/ddd-run` enchaîne tout en reprenant là où `/ddd-status` indique. Les **gates** exigent une
validation humaine (`/ddd-gate`).

## Où est quoi

- `.claude/commands/` — points d'entrée `/ddd-*` (orchestration, tourne dans le fil principal).
- `.claude/agents/` — 10 sous-agents spécialisés (contexte isolé, rendent un résumé ≤ 20 lignes).
- `.claude/skills/` — connaissance métier (DDD, hexagonal, strangler, characterization, context7…), chargée à la
  demande.
- `.claude/rules/` — règles permanentes (token economy, langage omniprésent, couche domaine, sécurité legacy…).
- `tools/ddd-scan/` — scripts TypeScript **sans LLM** : `scan.ts`, `query.ts`, `check-arch.ts`, `state.ts`,
  `guard-legacy.ts`.
- `.ddd/` — espace de travail du projet cible : config, state, artefacts de chaque phase (voir `docs/artifacts.md`).
- `docs/` — documentation complète.

## Principes non négociables

1. **Script d'abord, LLM ensuite** : ne jamais lire le code legacy brut quand `.ddd/scan/summary.md` ou `query.ts`
   répond (cf. `rules/01-token-economy.md`).
2. **Artefacts sur disque, résumés en conversation** : chaque agent écrit dans `.ddd/…` et ne renvoie qu'un résumé.
3. **Pas de modification de legacy sans filet** : tests de caractérisation d'abord (hook `guard-legacy.ts` l'impose).
4. **Dépendances vers l'intérieur** : `check-arch.ts` tourne après chaque édition (hook PostToolUse).
5. **Documentation à jour via context7** : avant toute décision technique, `/ddd-research` (ou l'agent
   `docs-researcher`) ; ne pas se fier à la mémoire pour les API de frameworks.
6. **Validation humaine aux gates** : les experts métier valident le langage et les frontières, pas Claude.

## Commandes utiles

```bash
node --disable-warning=ExperimentalWarning tools/ddd-scan/state.ts show     # où en est-on ?
node --disable-warning=ExperimentalWarning tools/ddd-scan/query.ts module orders
node --disable-warning=ExperimentalWarning tools/ddd-scan/check-arch.ts      # fitness d'architecture
```

Prérequis : Node ≥ 22.18 (TypeScript natif, zéro dépendance), git (pour churn/co-change), variable `CONTEXT7_API_KEY`
(gratuite sur context7.com/dashboard).
