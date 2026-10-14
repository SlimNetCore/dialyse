---
status: approved
date: 2026-10-09
decision-makers: [experts métier (direction, médecin référent, administrateur de centre), architecte]
context-scope: global
research: []
---
# 0001 — Découper le backend en onze contextes métier dans un monolithe modulaire

## Contexte et problème
Le legacy compte 14 modules `bc-*` dont les frontières ne suivent pas le métier (`bc-seance` contient 4 sous-domaines, `bc-patient/assure/insurance/pec` sont des micro-modules) et un module `platform` (698 fichiers) qui porte adapters, services applicatifs et 15 crons pour tous. La base (124 tables) est partagée de fait. Voir `.ddd/discovery/legacy-analysis.md`, `.ddd/strategic/bounded-contexts.md`.

## Options considérées
1. Conserver les 14 modules `bc-*` — pour : zéro coût ; contre : frontières erronées (H1, H5, H6).
2. Onze contextes métier + génériques dans un **monolithe modulaire** (`backend/contexts/<ctx>`, un schéma par contexte à terme) — pour : frontières alignées sur les sous-domaines, déploiement unique, transactions locales ; contre : discipline de modules à outiller (`check-arch.ts`).
3. Un microservice par contexte — pour : isolation forte ; contre : monolithe distribué, coût opérationnel sans besoin d'échelle identifié.

## Décision
Option 2, parce que les frontières linguistiques et d'invariants sont nettes mais qu'aucun besoin de déploiement indépendant n'est établi.

## Conséquences
+ frontières testables, extraction Strangler contexte par contexte.
− 11 modules + génériques à créer ; événements in-process via outbox.
⚠ risque de fuite de modèle entre modules : contrôlé par `check-arch.ts` et par l'interdiction d'accès aux tables d'un autre contexte.

## Révision
Si un contexte requiert un rythme de déploiement ou une charge distincts (ex. Planification/solveur), réévaluer son extraction en service.
