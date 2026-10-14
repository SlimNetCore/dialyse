---
status: approved
date: 2026-10-09
decision-makers: [néphrologue référent, architecte]
context-scope: dossier-medical
research: []
---
# 0007 — Garder un seul contexte « Dossier médical », parcours greffe inclus

## Contexte et problème
`bc-medical` (98 fichiers) contient ≥ 10 sous-ensembles cohésifs (ordonnance, examen, biologie, sérologie, observance, greffe…) mais Ce 1 et aucune dépendance vers les autres BC. Les données de santé exigent un cloisonnement d'accès.

## Options considérées
1. Un contexte, modules internes par agrégat — pour : même acteur (médecin), mêmes données sensibles, peu d'échanges externes.
2. Scinder Greffe / Biologie / Prescriptions — pour : parcours greffe possiblement différenciant ; contre : pas de signal linguistique ni d'équipe distincte aujourd'hui.

## Décision
Option 1, en gardant le bilan pré-greffe comme module isolé et extractible.

## Conséquences
+ un seul périmètre de sécurité « données de santé ».
− agrégats nombreux dans un contexte ; H8 (ordonnance vs prescription) et H12 (transitions de greffe) à trancher au tactique.

## Révision
Si le parcours greffe est classé core ou confié à une équipe distincte.
