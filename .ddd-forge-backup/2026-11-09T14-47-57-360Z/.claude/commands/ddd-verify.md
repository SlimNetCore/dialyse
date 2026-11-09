---
description: Phase 8 — vérification globale (fitness d'architecture, revue DDD indépendante, parité, dette legacy restante)
argument-hint: "[slice | contexte | global]"
---

# /ddd-verify $ARGUMENTS

1. Contrôles automatiques (Bash) : `check-arch.ts` ; `state.ts show` ; en legacy `scan.ts . --incremental` puis comparer
   `summary.md` à la version précédente (cycles, tables partagées, hubs, fuite de frameworks, modules sans test) —
   rapporter les deltas.
2. Déléguer à **`ddd-reviewer`** avec le périmètre `$ARGUMENTS` (défaut `global`) : rapport dans `.ddd/reviews/`.
3. Résumer : verdict, blockers/majors, progression Strangler (slices par état, % code legacy restant = LOC legacy
   actuel / LOC initial, couverture de caractérisation), prochains pas.
4. Si des blockers existent : proposer les commandes correctives (`/ddd-slice`, `/ddd-tactical`…) ; ne pas corriger
   toi-même dans cette commande.
5. Si tout est `retired`/`pass` : `phases.verify done` et rédiger la liste de décommissionnement (ACL restantes, flags à
   supprimer, tables à archiver — à valider par l'utilisateur, **aucune suppression automatique**).
