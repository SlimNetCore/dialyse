---
slice: planification-p5-placement
status: in-progress (étape 0 GMAO faite ; P5 proprement dit NON commencé)
date: 2026-10-27
order: 5
context: planification
sources: [.ddd/safety-net/planification-p5-placement.md, .ddd/tactical/planification.md (P1-P8, P26), ADR 0049 à 0058, KB-PLN-52 à 64]
---

# P5 — Placement (en cours)

Refonte sur place (ADR 0024) : pas de façade ni de drapeau. Décisions utilisateur du 2026-10-27 : voir la demande d'origine (verrou avec création de la
ligne `planning_parametres` + 409, garde de centre et rôles sur la fiche, 400 paramètres illisibles, fuseau du centre, seam Patient, isolement/sérologie, GMAO d'abord).

## Fait — étape 0 : évolution additive de GMAO (décision 4)

| Élément | Contenu |
|---|---|
| événements `gmao.v1` | `publishedlanguage.GenerateurDevenuIndisponible` (équipement, centre, code, **désignation**, statut précédent, statut, instant) ; publiée par `PublicateurSpring` à côté de l'événement de domaine (traducteur `VersLeLangagePublie`, générateurs de dialyse seulement) |
| événements de domaine | `EquipementMisHorsService` et `EquipementReforme` portent la `designation` |
| `GenerateursDuCentre` | `Generateur` enrichi de `designation` (après `code`) ; requête JPA adaptée |
| `IndisponibilitesDesGenerateurs` | nouveau port publié `entre(centre, du, au)` (immobilisations des générateurs par intervention planifiée/en cours, jours UTC, mêmes règles que `OptimisationDonneesJdbcAdapter.indisponibilites`) + adapter JPA ; **non encore consommé** (P8) |
| tests | `VersLeLangagePublieTest` (3), contrat du pilotage étendu (double, JPA H2, PG16 `PilotagePostgresContractIT` — non rejoué) ; `EquipementTest` et 2 tests de la plateforme : seul l'argument `designation` ajouté |
| preuves | module GMAO : 749 tests verts ; filets GMAO de la plateforme (`characterization.gmao.**`) : 333 verts ; aucun golden modifié |

## Reste à faire (rien n'est fait de ceci)

1. Seam Patient : inscrire `PatientRestController.verifierPlacement` et `Patient.libererPlacement` dans `seams.md` ET `covered.json` AVANT toute édition ; éditer par Read/Edit/Write ; fermer à la fin.
2. Domaine `PlacesDuCentre` / `Place` / `CategorieDIsolement` / propositions et grille (port de `PlanificationAffectationService`, tests de la matrice invariant -> test).
3. Application : `PlacerLePatient` (port publié `PlacementDesPatients`), `ProposerDesPlaces`, `ConsulterLaCapacite`, `ReaffecterEnIsolementSiRisque` (transaction commune avec la sérologie), `AlerterUnGenerateurIndisponible` (clé générateur + jour).
4. Adapters : verrou du centre avec création de la ligne `planning_parametres` (409 `PLANIFICATION_ECRITURE_CONCURRENTE`, lock_timeout 5 s), colonnes de place de `patients`, `SallesDuCentre.marquerIsolement`, ACL `GenerateursDuCentre` (remplace le SQL sur `gmao_equipements` de `PlanningDonneesJdbcAdapter:147` et `SalleVueJdbcAdapter:68`), écouteur `gmao.v1` (remplace `GmaoEvenementsPlanificationListener`).
5. Interfaces : `GET /planning/affectations`, `GET /planning/salles` (size <= 100, négatifs 400), erreurs 400/403/404/409/422 décidées.
6. Parité du filet P5 (76 scénarios) ; goldens `_nouveau_code` avec ancien comportement archivé dans `_ecarts-legacy` AVANT réécriture ; retrait du legacy mort (`PlacementPatientService`, `PlanningAffectationQueryService`, `GenerateurIndisponibleService`, `PlanificationAffectationService` si plus lu par P8, `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`...).
7. Scripts `planification-p5-pg16-rejeu.sh`, `pit-p5-module.sh` ; ADR 0059 (proposed) ; ADR 0055 corrigée ; `known-bugs.md`, glossaire, RG + test garde, `verifications-production.md`, `roadmap.md` ; `state.ts set slices.planification-p5-placement built` seulement quand tout est vert.

## Points d'attention déjà repérés

- Le traducteur gmao.v1 est publié en plus de l'événement de domaine : l'écouteur de la plateforme (`GmaoEvenementsPlanificationListener`) écoute encore l'événement de domaine ; à basculer sur `GenerateurDevenuIndisponible` avec le module (les 2 tests GMAO qui publient des événements de domaine devront publier aussi l'événement publié).
- `GENERATEURS_AFFECTES` (SalleVueJdbcAdapter) exclut réformés/désactivés/supprimés : à reproduire côté module à partir de `GenerateursDuCentre` (statut + salle).
- Le filet P5 mentionne « rien de commité » en tête, mais il est commité en bbe85d02.
