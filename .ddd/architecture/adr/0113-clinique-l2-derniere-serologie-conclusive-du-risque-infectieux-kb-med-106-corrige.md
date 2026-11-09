---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de CORRIGER KB-MED-106, 2026-11-09 ; validation des médecins attendue au plus tard le 2026-12-15), architecte]
context-scope: dossier-medical (domain, infrastructure/persistence), planification (consommateur par le langage publié, double de test), plateforme (traducteur inchangé, goldens)
related: ADR 0083 (DM4, sérologies), 0101 (DM9, KB-MED-106 reproduit), 0024 et 0075 (refactoring sur place) ; `.ddd/migration/corrections-kb-2026-11-09.md` (lot L2) ; `.ddd/safety-net/known-bugs.md` (KB-MED-106) ; RG-PLN-012, RG-MED-049h ; `.ddd/migration/verifications-production.md` §66.1 et §75
---

# 0113 - Lot L2 (clinique) : dernière sérologie conclusive du risque infectieux, KB-MED-106 CORRIGÉ

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement, à la demande de l'utilisateur. L'ADR 0101 avait REPRODUIT l'anomalie pendant le
refactoring sur place (mêmes tables, mêmes URL, mêmes rôles). Ni façade, ni flag, ni projection, ni migration de schéma ; aucune donnée n'est modifiée.
La **validation des médecins reste à obtenir (au plus tard le 2026-12-15)** avant toute mise en production.

## Contexte
Le risque infectieux d'un patient (RG-PLN-012) décide de la salle d'isolement. Un patient était à risque si sa dernière sérologie d'un marqueur (AgHBs, anti-VHC,
ARN du VHC, VIH) était positive, « dernière » au sens de n'importe quelle sérologie plus récente du même marqueur. Un résultat **DOUTEUX** ou **EN_COURS** plus
récent qu'un positif levait donc le risque : un patient positif dont un contrôle est douteux ou en attente sortait de l'isolement sans aucune preuve de négativité.

## Décision
Règle nommée **dernière sérologie conclusive** : seul un résultat POSITIF ou NEGATIF est conclusif. Pour chaque patient et chaque marqueur, le risque est décidé par la
dernière sérologie conclusive (date de prélèvement, puis horodatage de saisie, inchangés) ; le patient est à risque si elle est positive.

| Cas (un marqueur) | Avant | Après |
|---|---|---|
| POSITIF puis DOUTEUX plus récent | hors risque | **à risque** |
| POSITIF puis EN_COURS plus récent | hors risque | **à risque** |
| POSITIF puis NEGATIF plus récent | hors risque | hors risque |
| NEGATIF puis POSITIF | à risque | à risque |
| DOUTEUX seul, EN_COURS seul | hors risque | hors risque |
| NEGATIF puis DOUTEUX | hors risque | hors risque |
| POSITIF, NEGATIF puis DOUTEUX | hors risque | hors risque |
| NEGATIF, POSITIF puis EN_COURS | hors risque | **à risque** |
| Égalité totale (même jour, même saisie) POSITIF / NEGATIF | à risque (KB-MED-107) | à risque (inchangé) |

Choisie plutôt que de traiter DOUTEUX comme positif (élargirait la population isolée sans preuve). Inchangés : marqueurs surveillés, départage, cloisonnement par la
ligne (KB-MED-108), casse stricte, patient désactivé, centre. KB-SEA-96 n'est pas traité ici.

## Une seule règle sur tous les chemins
| Chemin | Source de la règle | État |
|---|---|---|
| Domaine (forme Java, double mémoire des contrats) | `RisqueInfectieux.patientsARisque`, `estConclusif` | corrigé |
| SQL réel (liste des patients à risque d'un centre) | `PatientsARisqueInfectieuxJdbc` : `AND n.resultat IN ('POSITIF', 'NEGATIF')` dans le `NOT EXISTS` | corrigé |
| Planification : placement, réaffectation après sérologie, propositions de place, lecture de la semaine | `RisquesInfectieuxPublies` (ACL) puis `RisquesInfectieuxFournis`, relais de `RisquesInfectieuxParLeDossierMedical` vers le langage publié du Dossier médical | aucune règle propre : hérite |
| Double de test de la Planification | `RisquesInfectieuxDeTest` (même SQL) | aligné |
| Frontend | aucun calcul du risque côté client | sans objet |
| **Indicateur Direction « porteurs positifs »** | `IndicateursCliniquesJdbc.porteursPositifs` (KB-MED-105) : règle propre (marqueurs AgHBs, anti-VHC, VIH ; dernier résultat toutes valeurs confondues, un douteux plus récent compte non positif) | **NON modifié** (voir Questions ouvertes) |

## Effet sur l'isolement en Planification
Davantage de patients sont à risque : au prochain placement, la réaffectation après une sérologie ou une proposition de place, ils sont dirigés vers la salle d'isolement
(RG-PLN-053 : alerte d'administration si aucune place). Pas de réaffectation rétroactive de masse : seuls les calculs à venir changent. La charge des salles d'isolement
peut augmenter ; la liste des patients concernés est donnée par la détection §75.1 (lecture seule).

## Tests et goldens
- Domaine `RisqueInfectieuxTest` (7 tests) et contrat `RisquesInfectieuxContrat` (mémoire, H2, PostgreSQL 16 : 3 tests chacun) couvrent tous les cas du tableau et l'indépendance des marqueurs.
- Golden `dm9/planification/seule_la_derniere_serologie_de_chaqu/derniere_serologie` devient `derniere_serologie_nouveau_code` : seuls les cas 07 (P6) et 08 (P7) passent de
  `SANS_RISQUE` à `A_RISQUE`, et le cas 01 (ensemble) gagne P6 et P7 ; l'ancien est archivé dans `dm9/_ecarts-legacy/planification/derniere_serologie_avant_l2.legacy.json`.
  Approuvé à la main, jamais `-Dcharacterization.approve=true`. Les goldens DM4 (`planification/derniere_serologie`, `resultats`) et DM8 (`direction/serologies`) sont INCHANGÉS.
- Rejeu PostgreSQL 16 jetable `backend/scripts/dossier-medical-dm9-pg16-rejeu.sh` mis à jour (M1 centre A : P6 et P7 à risque ; détection 75.1) et vert, y compris le contrat PG16.

## Questions ouvertes pour les médecins
1. Confirmer qu'un résultat douteux ou en cours n'est jamais conclusif, pour chacun des quatre marqueurs (un ARN du VHC « en cours » après un positif maintient-il l'isolement ?).
2. Valider la liste des patients de §75.1 (ceux qui reviendront en isolement) AVANT mise en production.
3. Aligner ou non l'indicateur Direction des porteurs positifs (KB-MED-105) sur cette règle : sinon un patient peut être isolé sans être compté porteur positif dans l'indicateur.
4. Une durée de validité d'un positif ou une clôture explicite d'un douteux (contrôle de confirmation) est-elle souhaitée ? Non modélisée.

## Conséquences
- L'ADR 0101 reste historique (comportement reproduit à l'époque) ; ce document le remplace pour KB-MED-106.
- Retour arrière : revert du lot (aucune donnée modifiée) ; le golden `_avant_l2.legacy.json` décrit l'ancien comportement.
- Résiduel : un résultat ou un marqueur en casse inattendue (impossible par l'application, possible par accès SQL direct) ne lève plus le risque comme un « plus récent » le faisait avant ; il n'est pas conclusif.
