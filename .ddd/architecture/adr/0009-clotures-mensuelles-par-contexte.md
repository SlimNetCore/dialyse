---
status: approved
date: 2026-10-09
decision-makers: [direction, administrateur, comptable, pharmacien]
context-scope: absences-patients, facturation, stock, comptabilite
research: []
---
# 0009 — Chaque contexte possède sa clôture mensuelle, coordonnée par événements

## Contexte et problème
Les experts attendent quatre clôtures mensuelles : comptable (existe), facturation (à créer), absences (à créer), stock/inventaire (existe sous forme d'inventaire) — réponse Q5. « Clôture » est polysémique (H3).

## Options considérées
1. Une clôture globale unique (« fin de mois ») dans un contexte transverse — contre : modèle partagé, couple 4 contextes.
2. Une clôture par contexte, chacune publiant un événement (`MoisDAbsencesClôturé`, `PériodeDeFacturationClôturée`, `InventaireClôturé`, `PériodeComptableClôturée`) ; les dépendances d'ordre sont des préconditions locales sur ces événements — pour : chaque propriétaire garde son invariant.

## Décision
Option 2. Ordre par défaut proposé : absences → facturation → stock → comptable (à valider).

## Conséquences
+ vocabulaire explicite : « clôture des absences », « clôture de facturation », « clôture d'inventaire », « clôture de période comptable ».
− le pilotage direction doit afficher l'état des 4 clôtures par centre.
⚠ une clôture comptable bloquant les écritures de ventes tardives : la facturation d'un mois clos comptablement doit être refusée ou reportée (règle à confirmer).

## Révision
Au gate `tactical` de Facturation et Absences.
