---
description: Orchestrateur — enchaîne les phases du pipeline DDD depuis l'état courant jusqu'au prochain gate ou à la fin
argument-hint: "[--until <phase>] [--yes-defaults]"
---

# /ddd-run $ARGUMENTS

1. `node --disable-warning=ExperimentalWarning tools/ddd-scan/state.ts show` → déterminer la prochaine phase. Si aucun
   état : exécuter la logique de `/ddd-init`.
2. Exécuter les phases **dans l'ordre**, chacune selon le fichier de commande correspondant
   (`.claude/commands/ddd-<phase>.md`) — lire le fichier au moment d'exécuter la phase (pas avant), appliquer ses étapes
   telles quelles :
   `init → scan* → discover → strategic [GATE] → tactical [GATE] → architecture → plan* [GATE] → slices (boucle) → verify`
   (`*` legacy).
3. **Arrêt obligatoire** à chaque gate non approuvé : présenter le résumé et attendre la décision de l'utilisateur ;
   reprendre avec `/ddd-run` après `/ddd-gate`.
4. Boucle de slices : pour la prochaine slice du `roadmap.md` (ou use case en greenfield) → `safety-net`* → `slice` →
   vérification ; demander avant d'enchaîner sur la slice suivante (livraison incrémentale, pas de lot automatique).
5. `--until <phase>` : s'arrêter après cette phase. `--yes-defaults` : ne pas poser les questions de config optionnelles
   (garder les défauts), **jamais** applicable aux gates ni aux seams legacy.
6. À chaque fin de phase : une ligne de statut ; en cas d'échec d'un contrôle (tests, check-arch) : s'arrêter,
   expliquer, proposer la correction — ne pas contourner.
7. Économie : ne pas dupliquer dans la conversation ce que les agents ont écrit sur disque ; renvoyer les chemins.
