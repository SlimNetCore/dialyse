---
slice: pilotage-direction-pd6-composition-des-indicateurs
status: built
date: 2026-11-09
order: 6
context: pilotage-direction
sources: [ADR 0106, ADR 0105 (PD5-R5, revue M1), .ddd/safety-net/pilotage-direction.md (Audit PD5), .ddd/safety-net/known-bugs.md (KB-DIR-01, KB-DIR-07), .ddd/verify/verify-pilotage-direction-lot-d-pd5.md]
---

# PD6 — Composition des indicateurs de la direction

Refactoring SUR PLACE (ADR 0024/0075, ADR 0106) : mêmes URL, rôles, codes ; aucune façade ni flag. Décisions en vigueur, à confirmer métiers / DPO au plus tard le 2026-12-15 (KB-DIR-01, KB-DIR-07). **Option a retenue** après preuve d'absence de cycle de modules (ADR 0106).

## Cas d'usage (dans le module)
- `ConsulterLesIndicateursDeLaDirection` : indicateurs cliniques (Dossier médical, DM9) et du stock (Stock) par centre et en total, alertes ; période validée avant toute lecture ; stock évalué à la fin de période (KB-DIR-07) ; `valeurStock` = quantité x PMP, arrondie par centre, totaux sur les valeurs non arrondies (RG-DIR-110).
- Langage publié `IndicateursDeLaDirection` (route `/api/v1/direction/indicators`, instantanés figés, temps réel) ; `IndicateursDeLaDirectionFournis` conservé comme forme lue par le rapport.

## Fait
| Couche | Fichiers (module `contexts/pilotage-direction`) |
|---|---|
| Domaine | `IndicateursDeLaSociete` (composition), `IndicateursDuCentre`, `IndicateursCliniquesDuCentre`, `MarqueurClinique`, `CliniquesDesCentres`, `StockDuCentre` |
| Application | `ConsulterLesIndicateursDeLaDirection`, ports `IndicateursCliniquesDesCentres`, `StockDesCentres` |
| Infrastructure (ACL) | `CliniquesParLeDossierMedical`, `StockParLeStock`, `IndicateursDuRapportParLesIndicateursPublies` ; `ConstatsDuTableauDeBordParLeTableauDeBord` et `ContenuFigeParLeTableauDeBord` lisent `IndicateursDeLaDirection` |
| Interface / publié | `IndicateursPublies`, `IndicateursDeLaDirection` ; configuration : 4 beans ; `pom.xml` : dépendance directe `contexts-stock` |
| Retiré (plateforme) | `DirectionIndicatorsQueryService`, `IndicateursAFigerParLaPlateforme`, `IndicateursDuRapportParLaPlateforme`, `IndicateursDuTempsReelParLaPlateforme` ; ports `IndicateursAFigerFournis`, `IndicateursDuTempsReelFournis` |
| Édité (plateforme) | `DirectionDashboardRestController` (type lu : `IndicateursDeLaDirection`), javadoc de `DirectionGmaoQueryService` |

## Filet et parité
- Ajouté AVANT la migration, vert sur le legacy : `pd6/LaCompositionDesIndicateursDeLaDirectionCharacterizationIntegrationTest` (3 goldens : `sans_centre`, `arrondi_et_inactif`, `date_de_fin`) ; test du traducteur du rapport (repris ensuite dans `IndicateursDeLaDirectionIntegrationTest`).
- Existant, inchangé : DM8, DM9, PD1 à PD5. Parité 100 %, aucun écart, aucun `.approved` modifié.
- Tests ajoutés au module : `IndicateursDeLaSocieteTest` (4), `ConsulterLesIndicateursDeLaDirectionTest` (4), `TraducteursDesIndicateursTest` (3) ; règle ArchUnit « seule l'infrastructure lit les autres contextes ».
- `covered.json` : les 4 fichiers supprimés retirés ; `seamAllowed` = `[]` (aucun seam nécessaire).

## Données
Aucune table propre : lectures par langages publiés. Aucune migration.

## Plan de bascule
Sans objet (refactoring sur place). Retour arrière : revert du commit.

## Résiduels
PD6-R1 à R4 : voir ADR 0106 (nom du port `IndicateursDeLaDirectionFournis`, deux copies du seuil 5, KB-DIR-01/07 à confirmer, HTML client édité à la main).

## Reste à faire
Rien pour le Pilotage direction : PD1 à PD6 sont `built`. Reste hors Pilotage : PD5-R1 à R4 et R6 (propriétaires : Absences, Planification, GMAO, Stock, IAM).
