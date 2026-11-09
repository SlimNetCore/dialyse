---
description: Phase 0 — initialise le pipeline DDD (mode legacy ou greenfield), config et état
argument-hint: "[legacy|greenfield]"
---

# /ddd-init $ARGUMENTS

Objectif : créer `.ddd/` avec `config.json` et `state.json`. Notation :
`TOOLS = node --disable-warning=ExperimentalWarning tools/ddd-scan`.

1. **Vérifier l'environnement** (Bash) : `node -v` (≥ 22.18 requis), `git rev-parse --is-inside-work-tree` (sinon
   prévenir : pas de churn/co-changement), présence de `tools/ddd-scan/scan.ts` et de `.mcp.json`. Vérifier la variable
   `CONTEXT7_API_KEY` (`echo` de sa présence uniquement, jamais sa valeur) ; si absente, indiquer comment l'obtenir
   (context7.com/dashboard) — le pipeline fonctionnera en repli WebFetch.
2. **Mode** : `$ARGUMENTS` sinon détecter (dépôt contenant du code source existant ⇒ proposer `legacy`, dépôt vide ⇒
   `greenfield`) et confirmer via `AskUserQuestion`.
3. **Questions** (une seule salve `AskUserQuestion`, ≤ 4 questions) : langage cible du nouveau code ; framework/test
   framework (ou « à décider en /ddd-architecture ») ; langue des documents (`fr` par défaut) ; en legacy : dossiers
   legacy (`legacyPaths`, globs) et dossier du nouveau code (`newCodeRoot`, défaut `src/contexts` greenfield,
   `contexts/` ou `src/contexts` legacy hors dossiers legacy).
4. **Écrire** `.ddd/config.json` :

```json
{ "mode": "legacy|greenfield", "language": "", "framework": "", "testFramework": "",
  "docsLanguage": "fr", "legacyPaths": [], "newCodeRoot": "src/contexts",
  "scan": { "depth": 1, "since": "2 years ago", "exclude": [] } }
```

5. `TOOLS/state.ts init <mode>` ; créer les dossiers
   `.ddd/{discovery,strategic/glossaries,tactical,architecture/adr,migration/slices,safety-net,reviews,research}`.
6. Si `.gitignore` existe, y ajouter les lignes de scratch du kit (cf. `.gitignore` du kit) sans doublon.
7. Terminer par `TOOLS/state.ts show` et proposer la suite : `/ddd-scan` (legacy) ou `/ddd-discover` (greenfield), et
   optionnellement `/ddd-research` pour amorcer la documentation à jour.

Ne rien écrire hors `.ddd/` et `.gitignore`.
