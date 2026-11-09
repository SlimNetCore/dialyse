---
description: Phase 7a (legacy) — filet de non-régression par tests de caractérisation pour une slice, avant toute modification
argument-hint: "<slice>"
---

# /ddd-safety-net $ARGUMENTS

Pré-requis : mode `legacy`, `gates.plan = approved`, fichier `.ddd/migration/slices/$ARGUMENTS.md` existant. Sinon STOP
avec la commande manquante.

1. Si `docs-researcher` n'a pas encore traité « approval/snapshot testing + mutation testing pour <stack> », le lancer
   (cache d'abord).
2. Déléguer à **`characterization-tester`** avec le nom de la slice. L'agent peut demander l'accord de l'utilisateur
   pour des seams (`AskUserQuestion`) — relayer fidèlement.
3. Contrôles de ta part (Bash) : exécuter la suite `tests/characterization/$ARGUMENTS/` avec la commande de test de la
   stack ; elle doit être **verte, 3 fois de suite**. Lire `.ddd/safety-net/covered.json` et vérifier que seuls les
   fichiers de la slice y figurent.
4. Présenter : scénarios, point d'observation, couverture/mutation de la zone, **ce qui n'est pas verrouillé**, bugs
   connus, seams.
5. État : `slices.$ARGUMENTS = characterized` (fait par l'agent, vérifier avec `state.ts get slices.$ARGUMENTS`).
   Recommander `/ddd-slice $ARGUMENTS`.

Sans ce filet, aucune édition du legacy n'est autorisée (hook `guard-legacy`).
