---
status: approved
date: 2026-10-09
decision-makers: [direction, secrétaire, infirmier référent, architecte]
context-scope: absences-patients
research: []
---
# 0003 — Faire des absences de patients un contexte autonome

## Contexte et problème
Les absences de patients vivent dans `bc-seance/domain/absence` et leur service applicatif dans `platform`. Elles ont un cycle propre (`A_QUALIFIER → JUSTIFIEE/NON_JUSTIFIEE → RATTRAPEE/ANNULEE`), une détection nocturne, une valorisation financière et une clôture mensuelle demandée (Q5). « Absence » est homonyme (patient ≠ infirmier ≠ `ABSENT`, H1).

## Options considérées
1. Module interne du contexte Séance — pour : proximité de la règle « séance réalisée » ; contre : mélange acte de soin et valorisation financière, clôture mensuelle dans le cœur Séance.
2. Contexte autonome alimenté par Planification (jours prévus), Séance (`SéanceValidée`), Patient & couverture (forfait) et Facturation (TVA, période facturée) — pour : invariants et clôture propres, vocabulaire clair ; contre : 4 dépendances amont.
3. Dans Facturation — contre : l'absence n'est pas une facture, qualification par infirmiers.

## Décision
Option 2, parce que la valorisation et la clôture mensuelle sont des responsabilités distinctes de l'acte de soin, et que l'homonymie impose une frontière.

## Conséquences
+ clôture mensuelle des absences implémentable sans toucher au cœur Séance.
− détection dépendante de projections locales (jours prévus, séances réalisées).
⚠ détection à tort si un événement `SéanceValidée` arrive en retard : la détection tourne sur jours échus et reste requalifiable.

## Révision
Si les experts classent les absences en « supporting » sans règle propre, réévaluer la fusion avec Séance.
