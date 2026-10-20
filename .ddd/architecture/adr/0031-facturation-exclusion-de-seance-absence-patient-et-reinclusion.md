---
status: accepted
date: 2026-10-20
deciders: utilisateur (décisions F2 du 2026-10-19, révision de Q3 point 3)
---

# ADR 0031 — Facturation : l'exclusion d'une séance déclare une absence de patient et se réinclut

## Contexte

Depuis la simulation, l'administrateur peut exclure une séance de la facturation. Le legacy la passait à `ABSENT` sans
motif
et rangeait l'auteur dans les colonnes du changement de forfait (`forfait_override_updated_*`, KB-FAC-13) ; aucune
réinclusion n'existait. L'utilisateur a décidé le 2026-10-19 (révision de Q3, point 3) : l'exclusion **reste** un
passage
au statut `ABSENT`, mais avec un **motif d'absence obligatoire**, enregistré comme **absence de patient** du module
Absences (table `absence_patient`, motifs `MotifAbsence`), et elle devient **réversible** tant que la séance n'est pas
facturée.

## Décision

1. **Stockage de l'exclusion** : deux colonnes nullables ajoutées à `seances` par la migration additive
   `V39__facturation_exclusion_de_seance.sql` : `statut_avant_exclusion` (le statut d'avant, `VALIDEE` ou `SIGNEE`) et
   `exclusion_absence_id` (l'absence déclarée par l'exclusion). Option écartée : porter la mémoire par l'absence
   (`absence_patient`), table d'un autre module sans colonne pour cela et qui peut être qualifiée, annulée ou rattrapée
   indépendamment. Option écartée : table dédiée (une jointure de plus pour une relation 1-1 avec la séance).
2. **Exclusion** (`ExclureUneSeance`), dans une seule transaction : la séance validée ou signée non facturée passe à
   `ABSENT` (statut d'avant mémorisé), puis l'absence est déclarée par `AbsencePatientService.declarer` (ACL
   `platform/infrastructure/acl/facturation/AbsencesDesPatientsLegacy`), au nom de l' **utilisateur de la session** ;
   son
   identité est mémorisée sur la séance ; la simulation est recalculée. Les règles du module Absences s'appliquent
   telles quelles : délai de déclaration de 60 jours, pas de date future, période du patient non encore facturée, une
   absence par patient et par jour, commentaire obligatoire pour `AUTRE`. Un refus (422 et son code) annule toute
   l'exclusion. L'auteur et la date sont tracés par l'absence, plus par `forfait_override_updated_*`.
3. **Réinclusion** (`ReinclureUneSeance`), route `POST /api/v1/facturation/preview/seances/{id}/include` : tant que la
   séance n'est pas facturée et porte la mémoire d'une exclusion de la Facturation, elle retrouve son statut d'avant,
   la mémoire est effacée et l'absence déclarée par l'exclusion est **supprimée** (`DELETE` de cette seule ligne par son
   identité) : le module Absences ne sait qu'annuler une absence, état définitif, et la contrainte « une absence par
   patient
   et par jour » interdirait ensuite d'exclure de nouveau la séance. Une séance `ABSENT` sans mémoire (absence réelle,
   exclusion antérieure à la slice) ne se réinclut pas (422).
4. **Contrat JSON** : `POST …/exclude` accepte en plus `motif` (obligatoire, un des motifs du module Absences :
   `HOSPITALISATION`, `MALADIE`, `VOYAGE`, `TRANSPORT`, `FAMILIAL`, `REFUS_PATIENT`, `DECES`, `AUTRE`, `NON_JUSTIFIEE`)
   et
   `commentaire` (obligatoire avec `AUTRE`) ; manquant ou inconnu : 400. `…/include` prend les mêmes champs de période
   et
   de regroupement que `…/exclude`, sans motif. Les réponses gardent le JSON de la simulation.
5. **Autorisation** (KB-FAC-14, 15, 16) : les quatre routes sont réservées à `ADMIN` ; un centre demandé différent de
   celui de la session répond 403 ; le taux de TVA est celui en vigueur à la fin de la période facturée.

## Conséquences

- La séance `ABSENT` n'est plus facturable ni « réalisée » (RG-ABS-012) ; l'écran d'absences la voit comme une absence
  déclarée avec son motif, qualifiée d'emblée.
- Une séance plus ancienne que 60 jours ne peut plus être exclue (règle des Absences) : à valider avec les experts ; le
  contournement serait une déclaration hors délai par l'ACL, non retenu.
- Le retrait de l'absence est une suppression de ligne (jamais de `DROP`) limitée à l'absence que l'exclusion a créée.
- Si la séance repasse par l'écran de séance entre l'exclusion et la réinclusion, la mémoire peut être périmée : la
  réinclusion exige `ABSENT` + mémoire, et rétablit le statut mémorisé.
- ACL temporaire : retirée avec la reprise du module Absences en contexte.
