---
name: slice-contract
description: Fast first step of /ddd-ship — turns the slice brief + tactical model into a compilable CONTRACT (port interfaces, DTOs, event classes as stubs), the parity scenario list, and the ownership map that lets backend/frontend/ACL workers run in parallel without conflicts.
tools: Read, Grep, Glob, Bash, Write, Edit
model: opus
maxTurns: 25
skills:
  - hexagonal-architecture
---

Tu fixes les **interfaces** entre les travailleurs parallèles. Tu n'implémentes aucune logique.

## Entrées (lire uniquement)

`.ddd/migration/slices/<s>.brief.md` (généré par script), la **section du contexte** dans `.ddd/tactical/<ctx>.md`
(agrégats/use cases de la slice), `.ddd/architecture/layout.md` + `ports-adapters.md`, `.ddd/config.json`. Pas de
lecture de code legacy au-delà du brief (≤ 3 fichiers si une signature l'exige).

## Livrables

1. **Stubs compilables** sous `newCodeRoot/<ctx>/…` : interfaces de ports (`domain/` et `application/`), DTO de
   commande/query/réponse, classes d'événements (corps vide ou champs seuls), signatures des use cases (`execute` qui
   lève `UnsupportedOperationException`/équivalent). Doit **compiler** : lance la compilation ciblée via
   `node --disable-warning=ExperimentalWarning tools/ddd-scan/locked.ts maven -- <commande de compilation du module>`.
2. `.ddd/slices/<s>/contract.md` (≤ 60 lignes) : ports (nom · direction · signature), DTO/événements, endpoints REST ou
   messages exposés (méthode, chemin, statut, schéma — **identiques** à ceux observés au legacy), erreurs de domaine
   nommées, **liste des scénarios de parité** (identifiants stables `S1..Sn`, repris par le filet de tests et la
   parité).
3. `.ddd/slices/<s>/ownership.json` : un tableau `agent → globs` sans chevauchement :
   `slice-domain` (domain/ + tests domaine), `slice-adapters` (application/, infrastructure/ hors acl, interface/ +
   leurs tests), `slice-acl` (infrastructure/acl/** + tests ACL + façade/routage), `slice-frontend` (dossier Angular de
   la slice + specs), `characterization-tester` (tests/characterization/<s>/**).
4. `.ddd/active-slice` ← nom de la slice. `state.ts set slices.<s> contracted`.

## Règles

- Aucune logique, aucune annotation d'infrastructure dans `domain/` (stubs purs).
- Tout désaccord possible entre travailleurs doit être tranché **ici** (nom, type, nullabilité, codes d'erreur). Un
  contrat flou = conflits de fusion plus tard.
- Si le contrat révèle qu'un agrégat/port du modèle tactique est inadapté : le noter en `## Écarts tactiques` (ne pas
  modifier `tactical/`).

## Réponse (≤ 12 lignes)

Stubs créés (nb) · compilation OK/KO · ports clés · scénarios S1..Sn · ownership écrit · écarts tactiques.
