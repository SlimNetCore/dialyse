---
description: Revue rapide d'une slice — un gate unique de tests (aucun relecteur ne lance de test), puis 3 relecteurs en parallèle sur le diff, fusion et verdict
argument-hint: "<slice> [--regate] [--fix]"
---

# /ddd-review $ARGUMENTS

`TOOLS = node --disable-warning=ExperimentalWarning tools/ddd-scan`. Objectif : < 10 minutes, dont l'essentiel est le
gate (exécuté **une fois**, jamais par un relecteur).

1. `TOOLS/state.ts log start review:<s>`. Base du diff : `TOOLS/state.ts get bases.<s>` (sinon
   `git merge-base HEAD <branche de base>`).
2. **Gate** : si `.ddd/reviews/gate-<s>.json` n'existe pas, est plus ancien que le dernier commit, ou `--regate` :
   `TOOLS/gate.ts <s>`. C'est la **seule** exécution de tests/lint/ArchUnit.
3. Calculer les commandes de diff (restreintes aux chemins de la slice, `ownership.json`) :
   `git diff <base> -- <domain> <application> <infrastructure> <interface> <tests> <frontend>` ; passer aux relecteurs
   **la commande**, pas le diff (ils le lisent eux-mêmes). `git diff --stat` pour ton propre contrôle.
4. **En parallèle, dans un seul message** : `review-domain`, `review-hexagonal`, `review-tests` — prompt = slice, base
   du diff, chemins des fichiers de contexte (`contract.md`, glossaire, `gate-<s>.json`). Interdire explicitement
   tests/build.
5. `TOOLS/review-merge.ts <s>` → `.ddd/reviews/<s>/REPORT.md` + verdict ; afficher le résumé (blockers/majors
   uniquement).
6. `--fix` (ou appelé par `/ddd-ship`) : routage des constats vers les propriétaires de fichiers, comme à l'étape 8 de
   `/ddd-ship`.
7. `TOOLS/state.ts log end review:<s>`.

Revue **globale** (jalon, multi-slices) : `/ddd-verify`, pas cette commande.
