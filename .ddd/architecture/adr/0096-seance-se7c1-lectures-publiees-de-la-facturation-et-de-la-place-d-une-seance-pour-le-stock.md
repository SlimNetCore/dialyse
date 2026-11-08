---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance, stock
related: ADR 0024 (refactoring sur place), 0045, 0046, 0062, 0088 à 0095 ; `.ddd/migration/slices/seance-se7c-lectures-facturation-stock.md`, `.ddd/safety-net/seance-se7-lectures-publiees.md`, KB-SEA-68 à 70
---

# 0096 - Séance, SE7c1 : lectures publiées (facturation et place d'une séance) pour le Stock

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Lecture seule : aucun montant, aucune écriture, aucune table nouvelle.

## Contexte
Le Stock lit la table `seances` en SQL direct (2 classes) : `StatutDesSeancesLegacy` (la séance est-elle facturée ?) et `PerimetreDesSeancesLegacy` (salle et demi-journée d'une séance, pour choisir la sortie pour séances du jour). Aucune ne passe par `seance.v1`. Étape 0 : le statut était verrouillé par `SeanceFactureeImmuableCharacterizationIntegrationTest` (6 cas) ; la place ne l'était par AUCUN test SQL (seulement par des doubles de `GestionDesConsommationsTest`). Filet ajouté avant toute migration : `characterization/seance/se7c/PerimetreDesSeancesDuStockCharacterizationIntegrationTest` (9 tests, vert sur le legacy) ; bugs consignés : KB-SEA-68 (demi-journée devinée par sous-chaîne), KB-SEA-69 (périmètre perdu en silence), KB-SEA-70 (statut insensible à la casse côté Stock seulement).

## Décisions
1. **REPRODUIRE exactement** : mêmes requêtes, même résultat pour une séance inconnue, d'un autre centre, un statut inconnu, une salle ou un créneau nul, d'un autre centre ou disparu. Aucune correction (aucun 500 n'existe sur ces lectures).
2. **Deux ports publiés** dans `contexts/seance/publishedlanguage` : `FacturationDesSeancesPubliee.estFacturee(centre, séance)` et `PlacesDesSeancesPubliees.de(centre, séance)` (vide si inconnue ou d'un autre centre ; code de la salle, code et libellé du créneau, `null` s'ils sont absents ou d'un autre centre). La Séance publie les FAITS ; l'interprétation (demi-journée, périmètre de sortie) reste au Stock (langage du Stock : `DemiJournee`, `PerimetreDeSortie`).
3. **Adapter** : `LecturesPublieesDesSeancesJdbc` dans `contexts/seance/infrastructure/persistence` (table `seances`, propre au contexte ; `salle` et `position_creneau` lues en SQL direct comme aujourd'hui, ligne de la liste blanche de `TableDeLOrganisationTest`, jalon O4).
4. **ACL du consommateur dans la plateforme, pas dans le module Stock** : le module Seance dépend déjà du module Stock (langage publié `ConsommationsDeSeance`) ; Stock ne peut donc pas dépendre du module Seance (cycle Maven). Les traducteurs `StatutDesSeancesParLaSeance` et `PerimetreDesSeancesParLaSeance` vivent dans `platform/infrastructure/acl/stock/`, implémentent les ports du Stock (`domain`) en appelant les ports publiés de la Séance, et sont câblés par la plateforme (composition root). Le Stock ne contient plus aucun SQL sur `seances`. Alternative écartée : un module Maven de langage publié séparé (coût disproportionné pour deux interfaces ; à reconsidérer si un troisième consommateur arrive).
5. **Verrous** : `TableDesSeancesTest` (désactivé depuis SE1) est réécrit en liste blanche NOMMÉE et DATÉE des lecteurs de `seances` hors Séance ; les deux classes du Stock en sortent, les 5 de la Facturation et les autres lecteurs y restent avec leur butoir. Règle ArchUnit : le module Stock ne dépend d'aucune classe de `contexts.seance`.
6. **Tests** : contrat commun (`LecturesPublieesDesSeancesContrat`, 8 cas) joué par un double en mémoire, l'adapter JDBC sur H2 et sur PostgreSQL 16 jetable (`seance-se7c1-pg16-rejeu.sh`, port 55543, mot de passe en variable d'environnement) ; traducteurs de la plateforme testés table-driven ; parité par les goldens Stock S6b / S4 inchangés et le filet de caractérisation SE7c.
7. **Aucun événement d'intégration** (aucun consommateur asynchrone).

## RISQUES À FAIRE CONFIRMER (reproduits, non corrigés)
| KB | Risque | Condition de mise en production |
|---|---|---|
| KB-SEA-68 | La demi-journée du consommable d'une séance dépend d'un libellé de créneau libre ; un libellé sans mot connu retombe sur la sortie de la salle ou de la journée | Décision métier : porter la demi-journée dans le référentiel des créneaux (Organisation) |
| KB-SEA-69 | Salle ou créneau absent / d'un autre centre : périmètre perdu en silence, pas d'erreur | Décision métier : refuser ou signaler ; contrôle SELECT seule en §61 |
| KB-SEA-70 | `facturee` en minuscules est « facturée » pour le Stock seulement | Décision : normaliser le statut (contrainte) ou aligner les lecteurs |

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre pour chacun)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| R1 | `LecturesPublieesDesSeancesJdbc` lit `salle` et `position_creneau` en SQL direct : à remplacer par `organisation.v1` (jalon O4) | 2026-12-15 | 2027-03-31 |
| R2 | ACL du Stock logée dans la plateforme (cycle Maven Seance <-> Stock) : à replacer dans `contexts/stock` si un module de langage publié partagé est décidé | 2026-12-15 | 2027-06-30 |
| R3 | Décisions KB-SEA-68, 69, 70 : arbitrage métiers | 2026-12-15 | avant toute mise en production |
| R4 | Aucune garde ArchUnit contre un nouvel appelant de `seances` côté Facturation avant c2 : la liste blanche datée de `TableDesSeancesTest` en tient lieu | 2026-12-15 | 2027-01-31 (fin de c2) |

## Lots
LOT B (livré 2026-11-08) : traducteurs `StatutDesSeancesParLaSeance` et `PerimetreDesSeancesParLaSeance` (plateforme), suppression des 2 classes SQL du Stock, `TableDesSeancesTest` réécrit (18 lecteurs, aucun du Stock), règle ArchUnit `le_stock_ne_depend_pas_de_la_seance`, parité par S6b / S4 et le filet SE7c. LOT C (livré 2026-11-08) : RG-SEA-060, annexe 16, glossaire (proposed), §61 (SELECT seule, rejouée sur PostgreSQL 16), fiche de slice, audit de couverture c2 (`.ddd/safety-net/seance-se7c2-ecritures-facturation.md`), état `built` de c1 (`seance-se7c1-lectures-stock`) ; c2 `planned`. Résidus R1 à R4 ci-dessus : voir la fiche de slice pour le bilan complet (R4 devient « 18 lecteurs en liste blanche, butoirs 2027-01-31 à 2027-06-30 »).

LOT A (livré 2026-11-08) : cet ADR, filet complémentaire (étape 0), ports publiés, adapter, contrat H2 / double / PostgreSQL 16, KB-SEA-68 à 70. AUCUN trafic changé (les ACL du Stock lisent encore la table). LOT B : traducteurs de la plateforme, câblage, suppression des 2 classes SQL du Stock, verrous. LOT C : RG, annexe 16, glossaire, §61, bilan, fiche, état `built` de c1.
