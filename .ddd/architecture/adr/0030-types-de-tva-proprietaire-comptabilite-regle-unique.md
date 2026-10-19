---
status: accepted
date: 2026-10-19
deciders: utilisateur (validation du plan Facturation, question Q1 du modèle tactique)
---

# ADR 0030 — Types de TVA : la Comptabilité reste propriétaire de `tva_types` ; une seule règle de validation

## Contexte

La table `tva_types` est écrite par deux écrans aux contrôles différents :

- `POST /api/v1/comptabilite/regles-tva` (contexte `comptabilite`, slice C2) : ajout seulement ; refuse une fin
  antérieure au début et un chevauchement avec une règle active du même centre et du même type (KB-CPT-16) ; aucune
  borne haute de taux (KB-CPT-17) ;
- `/api/v1/tva-types` (legacy Facturation) : création, modification, désactivation ; taux 0 à 100 % ; fin strictement
  postérieure au début ; chevauchement non contrôlé (à caractériser).

Le modèle tactique Facturation (Q1) proposait la Facturation comme propriétaire.

## Décision

1. **La Comptabilité reste seule propriétaire de `tva_types`** (option B). La slice F1 fait de `/api/v1/tva-types` une
   **façade** (mêmes URL et JSON) qui délègue au contexte `comptabilite` ; la Facturation, les Absences et la
   simulation lisent le taux par le langage publié de la Comptabilité (`ParametrageFiscal`, étendu si besoin).
2. **Une seule règle de validation, union des contrôles**, appliquée à toute écriture (création, modification) par l'un
   ou l'autre écran : taux **de 0 à 100 %** ; date de début obligatoire ; date de fin facultative, **égale ou
   postérieure** au début ; **pas de chevauchement** avec une règle active du même centre et du même type de prestation
   (bornes incluses, fin absente = ouverte, règles désactivées ignorées). Les règles déjà en base ne sont pas
   revalidées.
3. Le modèle de la Comptabilité s'enrichit des commandes que `/tva-types` expose déjà (modification, désactivation) ;
   ce sont des capacités existantes du legacy, pas de nouvelles fonctionnalités.

## Conséquences

- Écarts au legacy (ADR 0019), à verrouiller par des goldens `_nouveau_code` à la livraison de F1 :
  `/regles-tva` refusera un taux > 100 (aujourd'hui accepté, KB-CPT-17) ; `/tva-types` acceptera une fin égale au début
  et refusera un chevauchement (aujourd'hui l'inverse, à confirmer par le filet).
- **Règles de gestion modifiées** : RG-FAC-001 à 003 et RG-CPT-006 seront mises à jour dans le référentiel et la
  documentation fonctionnelle régénérée **à la livraison de F1** (pas avant : le comportement n'a pas encore changé).
- Le modèle tactique Facturation est révisé : `TypeTVA` sort des agrégats de la Facturation (lecture seule).

