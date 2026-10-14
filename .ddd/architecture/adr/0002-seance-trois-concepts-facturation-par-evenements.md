---
status: approved
date: 2026-10-09
decision-makers: [médecin référent, secrétaire de facturation, administrateur, architecte]
context-scope: seance, facturation, stock
research: []
---
# 0002 — Scinder « Séance » en trois concepts et alimenter la facturation par événements

## Contexte et problème
« Séance » désigne l'acte de soin, la ligne facturable et le motif de sortie de stock (décision utilisateur Q2, hotspot H6). La facturation lit la table `seances` par SQL direct (`SeanceFacturationJdbcAdapter`) et l'exclusion de facturation est stockée comme statut de séance `ABSENT` (H1, décision Q1 : à renommer).

## Options considérées
1. Un modèle Séance partagé par les trois contextes — contre : interdit (modèle unique), couple tous les cycles de vie.
2. Séance (acte) propriétaire de l'acte ; Facturation construit sa **séance facturable** par projection des événements `SéanceValidée/Signée/ForfaitDeSéanceSurchargé` ; Stock ne garde qu'une référence `seanceId` comme motif de sortie ; l'exclusion devient « Exclusion de facturation » dans Facturation — pour : chaque contexte a son invariant ; contre : projection à maintenir, réciprocité `FacturationValidée` → verrou de séance.
3. Facturation interroge Séance par API synchrone — contre : couplage temporel à la validation de facturation.

## Décision
Option 2. Le statut `ABSENT` disparaît du modèle Séance cible ; le verrou « facturée » est posé par Séance sur réception de `FacturationValidée` ; l'ACL legacy traduit `ABSENT` ↔ « exclue de facturation » pendant la transition.

## Conséquences
+ fin du SQL direct ; vocabulaire non ambigu ; RG-FAC-020 (sans signature) reste locale à Facturation.
− cohérence à terme entre validation infirmière et éligibilité (RG-FAC-030 recontrôle à la validation).
⚠ réciprocité Séance ⇄ Facturation : surveiller toute nouvelle règle croisée (signal de Partnership).

## Révision
Au gate `tactical` de Séance et de Facturation.
