---
name: ddd-pipeline
description: Master map of the DDD pipeline (phases, gates, inputs/outputs, state machine, greenfield vs legacy). Use when starting, resuming, or deciding what to do next in a DDD project, or when a command needs to know which artifacts a phase consumes/produces.
---

# Pipeline DDD — carte de référence

| #  | Phase                       | Commande              | Entrées                 | Sorties (`.ddd/…`)                                                                              | Agent                                                | Gate          |
|----|-----------------------------|-----------------------|-------------------------|-------------------------------------------------------------------------------------------------|------------------------------------------------------|---------------|
| 0  | Init                        | `/ddd-init`           | repo                    | `config.json`, `state.json`                                                                     | —                                                    | —             |
| 1  | Scan *(legacy)*             | `/ddd-scan`           | code                    | `scan/summary.md`, `graph.json`, `files.json`                                                   | script TS, puis `legacy-analyst`                     | —             |
| 2  | Discovery                   | `/ddd-discover`       | scan **ou** brief       | `discovery/{domain-vision,event-storming,glossary}.md`, `legacy-analysis.md`                    | `legacy-analyst`, `domain-explorer`                  | —             |
| 3  | Stratégique                 | `/ddd-strategic`      | discovery               | `strategic/{subdomains,bounded-contexts,context-map}.md`, `glossaries/<ctx>.md`                 | `strategic-designer`                                 | **strategic** |
| 4  | Tactique                    | `/ddd-tactical <ctx>` | strategic               | `tactical/<ctx>.md`                                                                             | `tactical-designer` (1 par contexte, parallélisable) | **tactical**  |
| 5  | Architecture                | `/ddd-architecture`   | tactical                | `architecture/{layout,ports-adapters,test-strategy}.md`, `arch-rules.json`, `adr/*` + squelette | `hexagonal-architect`, `docs-researcher`             | —             |
| 6  | Plan Strangler *(legacy)*   | `/ddd-strangle-plan`  | scan + strategic + arch | `migration/roadmap.md`, `migration/slices/<s>.md`                                               | `strangler-planner`                                  | **plan**      |
| 7a | Filet *(legacy, par slice)* | `/ddd-safety-net <s>` | slice                   | `tests/characterization/<s>/`, `safety-net/{seams,covered.json,known-bugs}`                     | `characterization-tester`                            | —             |
| 7b | Build (par slice)           | `/ddd-slice <s>`      | tactical + slice        | code dans `newCodeRoot`, `migration/slices/<s>.md`                                              | `slice-implementer`                                  | —             |
| 8  | Vérif                       | `/ddd-verify`         | tout                    | `reviews/*.md`                                                                                  | `ddd-reviewer`                                       | —             |

**Greenfield** : pas de phases 1, 6, 7a ; les slices sont les use cases du modèle tactique (ordre : cœur de domaine
d'abord, walking skeleton de bout en bout dès la première slice).

**Legacy** : boucle par slice  
`characterize (7a) → build parity (7b) → route % → shadow/compare → cutover → observe → retire legacy`.

## Machine d'état

`state.json` : `phases.<k>` ∈ `pending|in-progress|done`, `gates.<k>` ∈ `pending|proposed|approved|changes-requested`,
`slices.<s>` ∈ `planned|characterized|built|routed|cutover|retired`, `contexts.<c>` ∈ `modeled|approved`.
Lecture : `state.ts show`. Écriture : `state.ts set`. Reprise automatique : `/ddd-run`.

## Principes de conception du pipeline

- **Déterministe avant génératif** (scan/query/check-arch/state = 0 token) ; le LLM n'intervient que pour le sens
  (langage, frontières, invariants).
- **Itératif** : le modèle stratégique est révisé après chaque slice (ce que le code révèle). Une révision = ADR + mise
  à jour du glossaire + `status: revised` dans l'artefact.
- **Le métier décide** : les gates sont pour les experts du domaine ; Claude propose, ne tranche pas les frontières.
- **Context7** fournit la doc à jour des frameworks/outils (skill `context7-research`).
