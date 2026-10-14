---
status: approved
date: 2026-10-09
decision-makers: [administrateur de centre, secrétaire, architecte]
context-scope: planification, patient-couverture
research: []
---
# 0005 — Rendre la Planification propriétaire de la place du patient

## Contexte et problème
La place (salle·créneau·générateur·jours) est portée par `Patient` (`Patient.java:126`) et libérée par un cron 04:00 (RG-PAT-033), tandis que le placement et l'optimisation sont dans le planning. L'ordre implicite 02:30 (absences) → 04:00 (libération) est fragile (H9).

## Options considérées
1. Place dans Patient & couverture — contre : la place est une ressource de planification, modifiée par l'optimisation.
2. Place dans Planification ; Patient & couverture publie `PatientSorti` (avec date de dernier jour) ; Planification libère la place par politique — pour : un seul propriétaire, ordre temporel explicite.

## Décision
Option 2.

## Conséquences
+ invariants de capacité et d'isolement réunis ; plus d'ordre implicite entre crons.
− migration des colonnes de place hors de la table `patients` (ACL pendant la transition).
⚠ vérifier que la détection d'absence n'utilise pas une place déjà libérée (jours prévus lus depuis Planification).

## Révision
Au gate `tactical` de Planification.
