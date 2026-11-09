---
slice: pilotage-direction-pd6-composition-des-indicateurs
status: planned
date: 2026-11-09
order: 6
context: pilotage-direction
sources: [ADR 0105 (PD5-R5, revue M1), .ddd/safety-net/pilotage-direction.md (Audit PD5), .ddd/safety-net/known-bugs.md (KB-DIR-01, KB-DIR-07), .ddd/verify/verify-pilotage-direction-lot-d-pd5.md]
---

# PD6 — Composition des indicateurs de la direction

Créée par la revue du lot D (M1) : `DirectionIndicatorsQueryService` (159 lignes, `platform/application/direction/`) n'est pas une simple lecture d'un autre contexte. Refactoring SUR PLACE si engagé (ADR 0024/0075) : mêmes URL, rôles, codes ; aucune façade ni flag. Décisions en vigueur, à confirmer métiers / DPO au plus tard le 2026-12-15 (KB-DIR-01, KB-DIR-07).

## Cas d'usage
- `indicators(société, from, to)` : indicateurs cliniques (DM9) et du stock par centre et en total, masqués par `AnonymityPolicy` (route `/api/v1/direction/indicators`).
- Composition pour le Pilotage : tableau de bord, alertes et politique d'alertes (langages publiés du Pilotage) + indicateurs cliniques (`IndicateursCliniquesPublies`) + stock ; alimente les alertes figées, le rapport et le temps réel.

## Entrées legacy
| Élément | Rôle |
|---|---|
| `application/direction/DirectionIndicatorsQueryService` | composition ; stock évalué à la fin de période (KB-DIR-07) ; `valeurStock` = quantité x PMP |
| `infrastructure/acl/pilotagedirection/IndicateursAFigerParLaPlateforme`, `IndicateursDuRapportParLaPlateforme`, `IndicateursDuTempsReelParLaPlateforme` | traducteurs vers les ports sortants du Pilotage (instantanés, rapport, temps réel) |
| `DirectionDashboardRestController` (route `/indicators`), `DirectionGmaoQueryService` (appelant) | consommateurs |
| `AnonymityPolicy` (plateforme) / `SeuilDAnonymat` (module) | deux copies du seuil 5 (PD5-R9, égalité testée) |

## Filet existant et manques
- Existant : `LesIndicateursMedicauxDeLaDirectionCharacterizationIntegrationTest` (DM8), `LaFuiteParDifferenceDesIndicateursDeLaDirectionCharacterizationIntegrationTest` (DM9, KB-MED-104), golden `pd3/alertes/seuils` (KB-DIR-07), `IndicateursAFigerParLaPlateformeIntegrationTest`, `IndicateursDuTempsReelParLaPlateformeIntegrationTest`.
- Manques à confirmer avant tout déplacement : pas de test du traducteur `IndicateursDuRapportParLaPlateforme` au niveau du service seul ; couverture de la composition complète (alertes + indicateurs) à vérifier par `/ddd-safety-net` ; `covered.json` à contrôler pour ce fichier.
- Absence de cycle de modules non démontrée : le module dépend déjà de `contexts-dossier-medical` (qui dépend de `contexts-stock`) ; à prouver (`mvn dependency:tree`) avant de choisir l'option.

## Données
Aucune table propre : lectures par langages publiés (dossier médical, stock, Pilotage). Aucune migration.

## Options de migration (à trancher à la reprise, ADR dédié)
| Option | Description | Risque |
|---|---|---|
| a | Le Pilotage possède la composition (cas d'usage dans le module) ; lit dossier médical et stock par leurs langages publiés ; la plateforme garde le contrôleur et les traducteurs minces | dépendance module vers dossier-médical/stock à valider sans cycle |
| b | Scinder : le Pilotage garde la composition alertes/tableau de bord, une lecture « indicateurs cliniques et stock » est publiée par Dossier médical et Stock, le Pilotage la consomme | plus de langages publiés à créer |
| c | Maintien en plateforme, décision motivée par ADR (refus) | contredit le propriétaire cible retenu (Pilotage) |
Défaut proposé : a, après preuve d'absence de cycle et complément de filet.

## Plan de bascule
Sans objet (refactoring sur place). Retour arrière : revert du commit.

## Reste à faire
Tout (statut `planned`) : prouver le graphe de modules, compléter le filet, choisir l'option, ADR, migrer, retirer la ligne de `RESTE_DANS_APPLICATION_DIRECTION` (`TableDuPilotageTest`).
