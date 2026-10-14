---
status: approved
date: 2026-10-09
decision-makers: [pharmacien, comptable, architecte]
context-scope: stock
research: []
---
# 0006 — Intégrer le catalogue d'articles au contexte Stock et l'exposer en OHS

## Contexte et problème
`bc-stock → bc-article` : 17 arêtes à sens unique ; `bc-seance → bc-article` : 3 ; la comptabilité utilise un compte d'article (RG-CPT-011). Article n'a pas de cycle de vie propre observé.

## Options considérées
1. Contexte « Catalogue » séparé — pour : réutilisation ; contre : aucun invariant propre, un seul acteur (pharmacien).
2. Article dans Stock, exposé en OHS (lecture) à Séance et Comptabilité — pour : un propriétaire, PMP et lots au même endroit.

## Décision
Option 2.

## Conséquences
+ Séance consomme le catalogue en conformiste (lecture) ; Comptabilité reçoit les mouvements valorisés via ACL.
− si les médicaments (Dossier médical : ordonnances) ont besoin d'un référentiel distinct, ce sera un autre modèle (homonyme « médicament »), pas ce catalogue.

## Révision
Si un second propriétaire du référentiel article apparaît (ex. pharmacie centrale société).
