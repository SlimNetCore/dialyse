---
description: Phase 7b — implémente une slice (TDD domaine → application → adapters → ACL/façade en legacy) et prouve la parité
argument-hint: "<slice | use-case>"
---

# /ddd-slice $ARGUMENTS

Pré-requis :

- legacy : `state.ts get slices.$ARGUMENTS` = `characterized` (sinon `/ddd-safety-net $ARGUMENTS`) ;
- greenfield : le use case existe dans `.ddd/tactical/<ctx>.md` ; `phases.architecture = done`.

1. Déléguer à **`slice-implementer`** avec le nom de la slice. En greenfield, la 1ʳᵉ slice est un *walking skeleton* :
   traverse toutes les couches (interface → application → domaine → persistance) sur un cas simple du cœur de domaine,
   avec CI/tests branchés.
2. Contrôles de ta part (Bash, sans relire le code) : tests du contexte verts ; `check-arch.ts` OK ; legacy : suite de
   caractérisation exécutée **contre le nouveau code** = parité 100 % ou écarts documentés/acceptés.
3. Lancer **`ddd-reviewer`** sur le périmètre de la slice (read-only) ; traiter les `blocker`/`major` en renvoyant à
   `slice-implementer`.
4. Legacy : rappeler le plan de bascule de `.ddd/migration/slices/$ARGUMENTS.md` (shadow → canary → 100 % →
   observation → retrait) ; **l'activation du flag en production est faite par l'équipe, jamais par Claude**. Mettre à
   jour `slices.$ARGUMENTS` (`built` → `routed` → `cutover` → `retired`) à mesure que l'utilisateur confirme chaque
   étape.
5. Après chaque slice : demander si le modèle stratégique/tactique doit être révisé (ce que le code a révélé) ; si oui
   ADR + mise à jour glossaire/artefacts (`status: revised`).
6. Proposer la slice suivante (`roadmap.md`) ou `/ddd-verify`.
