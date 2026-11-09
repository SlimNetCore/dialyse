---
description: Livre UNE slice vite — contrat, puis filet + domaine + adapters + ACL + Angular en parallèle (agents bornés), gate unique, revue en 3 relecteurs, corrections ciblées
argument-hint: "<slice> [--no-review] [--seq]"
---

# /ddd-ship $ARGUMENTS

Remplace `/ddd-safety-net` + `/ddd-slice` + `/ddd-verify <slice>` pour le chemin normal. `--seq` = ancien chemin
séquentiel (`slice-implementer`), à réserver aux slices triviales.
`TOOLS = node --disable-warning=ExperimentalWarning tools/ddd-scan`. `<s>` = nom de la slice. **Chronomètre chaque
étape** : `TOOLS/state.ts log start|end ship:<s>:<étape>` (étapes : `brief`, `contract`, `fanout`, `merge`, `gate`,
`review`, `fix`) — `TOOLS/state.ts timings <s>` montre où part le temps.

## Pré-requis (sinon STOP et indiquer la commande)

`gates.plan = approved` (legacy) ou `phases.architecture = done` (greenfield) ; `.ddd/migration/slices/<s>.md` existe ;
`git status --porcelain` vide (sinon demander de committer/stasher) ; `.ddd/config.json` contient `commands.gate`.

## 1. Brief (script, 0 token) — `brief`

Legacy : `TOOLS/query.ts slice-brief --files <fichiers des entrypoints de la slice> --name <s>` → écrire dans
`.ddd/migration/slices/<s>.brief.md`. Si `VERDICT: TROP GROSSE` : **STOP**, proposer le découpage (clôtures par seed) et
renvoyer vers `/ddd-strangle-plan` ; ne jamais lancer une slice hors budget sans accord explicite.

## 2. Branche et base

`git switch -c ddd/<s>` ; `TOOLS/state.ts set bases.<s> $(git rev-parse HEAD)` (commit local uniquement, **aucun
push**).

## 3. Contrat — `contract` (séquentiel, court)

Agent **`slice-contract`** → stubs compilables + `contract.md` + `ownership.json`. Vérifier que la compilation ciblée
passe, puis `git add -A && git commit -m "ddd(<s>): contract"` (le contrat doit être dans le commit pour que les
worktrees le voient). Écrire `.ddd/gate-vars.json` (`backendModules`, `frontendSpecs`) d'après `ownership.json`/brief.

## 4. Fan-out — `fanout` (UN seul message, tous les agents en parallèle)

| Agent                       | Condition  | Où                             |
|-----------------------------|------------|--------------------------------|
| `characterization-tester`   | legacy     | `isolation: "worktree"`        |
| `slice-domain`              | toujours   | `isolation: "worktree"`        |
| `slice-adapters`            | toujours   | `isolation: "worktree"`        |
| `slice-acl` (passe `build`) | legacy     | `isolation: "worktree"`        |
| `slice-frontend`            | `ui: true` | arbre principal (node_modules) |

Prompt de chaque agent : nom de la slice, chemins `contract.md` / `ownership.json`, **uniquement** ce dont il a besoin
(rien du reste de la conversation). Max 4-5 agents simultanés. Les builds lourds passent par `locked.ts` (créneaux
`locks` de `config.json`) : ne pas lancer toi-même de Maven/`ng` pendant le fan-out.
Fin du fan-out : lire **seulement la dernière ligne JSON** de chaque agent. `blocked` → relancer UNE fois avec la
raison ; `NEEDS_APPROVAL` (seams) → regrouper toutes les demandes en **une seule** `AskUserQuestion`, puis
`seamAllowed` + reprise ciblée du testeur. `contractChanges` non vide → arbitrer toi-même (modifier `contract.md` +
stubs, relancer seulement les agents concernés).

## 5. Fusion — `merge`

Pour chaque worktree modifié : `git merge --no-ff <branche>` dans `ddd/<s>`. Les chemins étant disjoints, les conflits
ne devraient concerner que `.ddd/state.json` : garder la version de la branche principale (`git checkout --ours`). Autre
conflit : le résoudre selon `ownership.json`. Puis `TOOLS/state.ts set slices.<s> characterized` (legacy). Passe
`wire` : si `facade-wiring.md` existe, relancer `slice-acl` en mode `wire` (arbre principal, filet maintenant enregistré
dans `covered.json`).

## 6. Gate unique — `gate`

`TOOLS/gate.ts <s>` : compile ciblée + tests ciblés + ArchUnit + lint + **parité** (suite de caractérisation contre le
nouveau code) ; backend et frontend en parallèle, verrouillés. Échecs → étape 8 (corrections), pas de relance globale.

## 7. Revue — `review` (sauf `--no-review`)

Exécuter la logique de `/ddd-review <s>` (3 relecteurs en parallèle sur le diff, fusion, verdict). Si `risk: high` et
`commands.mutation` défini : lancer PIT **en arrière-plan** (`run_in_background`, via `locked.ts`) ; ses mutants
survivants alimentent un constat, ils ne bloquent pas.

## 8. Corrections ciblées — `fix` (max 2 tours)

Pour chaque constat `blocker`/`major` et chaque échec de gate : router vers le **propriétaire du fichier**
(`ownership.json`) et lancer ces agents **en parallèle** (arbre principal, un seul propriétaire par fichier), avec la
liste exacte de leurs constats (fichier:ligne + correction), rien d'autre. Puis
`gate.ts <s> --only <commandes en échec>` et seulement les relecteurs concernés. Après 2 tours : s'arrêter et présenter
le reste à l'utilisateur.

## 9. Clôture

`TOOLS/state.ts set slices.<s> built` ; `TOOLS/state.ts timings <s>` (afficher les 3 étapes les plus longues) ; résumé ≤
15 lignes : commit(s) sur `ddd/<s>`, parité X/Y, verdict de revue, `known-bugs`, **flag OFF**, plan de bascule (rappel :
activation en prod par l'équipe, jamais par Claude). Supprimer `.ddd/active-slice`. Proposer la slice suivante.

## Règles

- Pas de lecture de code par toi : tu orchestres sur des JSON et des chemins.
- Toute question à l'utilisateur est **groupée** et posée à l'étape 4 (seams) ou 8 (reste) — pas au fil de l'eau.
- Aucune activation de flag, aucun push, aucune suppression de legacy.
