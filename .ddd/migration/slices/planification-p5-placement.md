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

## Fait — session 1/3 (2026-10-27) : domaine + application + ports publiés (aucun adapter, aucun contrôleur, legacy intact)

| Couche | Contenu (module `backend/contexts/planification`) |
|---|---|
| domaine | `Place` (VO, `PLACEMENT_INCOMPLET`), `CategorieDIsolement`, `RessourcesDuCentre`, `Occupation`, `PropositionsDePlace` (port de `PlanificationAffectationService` : score 0-100, espacement, 2 par salle/créneau, limite 30, grille, alertes), `PlacesDuCentre` (racine : `placer`, `verifier`, `replacerEnIsolement`, `proposerLeRetourEnSalleOrdinaire`, `capacite`, `patientsSur`), `EvenementDePlacement`, `IndisponibiliteDeGenerateur`, `IndexDOccupation` (interne) ; ports `PlacesDuCentreRepository`, `VerrouDesPlaces` (création de la ligne par défaut puis verrou, 409) ; `GenerateurId`, `JourDeLaSemaine.ecartAvec` ajoutés |
| application | `PlacerLePatient` (implémente `PlacementDesPatients`), `ReaffecterEnIsolementSiRisque` (implémente `ReaffectationEnIsolement`), `AlerterUnGenerateurIndisponible`, `ProposerDesPlaces`, `ConsulterLesPlacesDesPatients` (`PlacesDesPatients`), `ConsulterLaCapacite` (`CapaciteDesCentres`) ; ports `LectureDesRessources`, `RisquesInfectieux`, `NomsDesPatients`, `NotificationsDuPlacement` (+ existants `UniteDeTravail`, `HorlogeDesCentres`, `PatientsDuCentre`) |
| langage publié | `PlacementDesPatients` (`verifier`, `placer`, `Refus`, `EcritureConcurrente`), `ReaffectationEnIsolement`, `PlacesDesPatients`, `CapaciteDesCentres` |
| tests | domaine : `PlacesDuCentreTest` (35), `PropositionsDePlaceTest` (17), `IndisponibiliteDeGenerateurTest` (3) ; application : `PlacementApplicationTest` (26, double `PlacesEnMemoire` à transaction simulée) ; module : 355 verts, ArchUnit 11 verts, `check-arch.ts` 0 violation |

Décisions appliquées dans le domaine (27/10) : patient sain en salle d'isolement refusé (`PLACEMENT_ISOLEMENT_RESERVE`, code **nouveau** à valider) ; sérologie hors centre refusée (`PLACEMENT_PATIENT_INTROUVABLE`) ; sérologie + réaffectation en une transaction (le port rejoint celle de l'appelant ; test de retour arrière) ; `ISOLEMENT_IMPOSSIBLE` dédupliquée (patient + jour du centre) ; retour en salle ordinaire PROPOSÉ (`RETOUR_PROPOSE`, notification `retourEnSalleOrdinairePropose`, jamais de déplacement) ; alerte générateur sans déplacement, idempotente (générateur + jour du centre), 5 patients cités + total, exception propagée (l'événement est rejoué) au lieu d'être avalée.

**Seam Patient : NON ouvert en session 1** (ni `seams.md`, ni `covered.json`, aucun fichier legacy édité). Raison : l'appel à `PlacementDesPatients` ne peut pas rester neutre sans adapter (l'ACL et le verrou n'existent pas encore) et un point d'injection inactif n'apporterait aucune preuve ; le garder fermé évite d'ouvrir un fichier de production non filetté pour rien. À ouvrir en session 2, après les adapters, avec accord renouvelé : `PatientRestController.verifierPlacement` (appel de `PlacementDesPatients.verifier`/`placer`), et `Patient.libererPlacement` relève de P7 (non migré).

## Reste à faire

1. (session 2) Seam Patient : inscrire dans `seams.md` ET `covered.json` AVANT toute édition ; Read/Edit/Write ; fermer à la fin. Le contrat `Refus` -> `BusinessException` 422 `PLACEMENT_*` et `EcritureConcurrente` -> 409 se traduit dans l'ACL.
2. (session 2) Dépôt `PlacesDuCentreRepository` JDBC (colonnes de place de `patients`, occupations jugées à la date du centre RG-PLN-010, catégorie issue des sérologies), `LectureDesRessources` (Organisation + `GenerateursDuCentre` + jours d'ouverture + fermetures 90 j), `VerrouDesPlaces` (création de la ligne `planning_parametres` + FOR UPDATE + lock_timeout 5 s), `RisquesInfectieux`, `NomsDesPatients`, `NotificationsDuPlacement` (ACL du centre de notifications, clés déterministes), écouteur `gmao.v1`, câblage dans `PlanificationConfiguration`, **tests de contrat communs** au double `PlacesEnMemoire` et aux adapters (H2 + PG16).
3. (session 2) Non encore couvert côté module : liste paginée des salles (`GET /planning/salles`, size <= 100, négatifs 400) qui exige un port de lecture `VueDesSalles` (Organisation + GMAO) ; contrôleurs `GET /planning/affectations` ; `SallesDuCentre.marquerIsolement` inchangé.
4. Adapters : verrou du centre avec création de la ligne `planning_parametres` (409 `PLANIFICATION_ECRITURE_CONCURRENTE`, lock_timeout 5 s), colonnes de place de `patients`, `SallesDuCentre.marquerIsolement`, ACL `GenerateursDuCentre` (remplace le SQL sur `gmao_equipements` de `PlanningDonneesJdbcAdapter:147` et `SalleVueJdbcAdapter:68`), écouteur `gmao.v1` (remplace `GmaoEvenementsPlanificationListener`).
5. Interfaces : `GET /planning/affectations`, `GET /planning/salles` (size <= 100, négatifs 400), erreurs 400/403/404/409/422 décidées.
6. Parité du filet P5 (76 scénarios) ; goldens `_nouveau_code` avec ancien comportement archivé dans `_ecarts-legacy` AVANT réécriture ; retrait du legacy mort (`PlacementPatientService`, `PlanningAffectationQueryService`, `GenerateurIndisponibleService`, `PlanificationAffectationService` si plus lu par P8, `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`...).
7. Scripts `planification-p5-pg16-rejeu.sh`, `pit-p5-module.sh` ; ADR 0059 (proposed) ; ADR 0055 corrigée ; `known-bugs.md`, glossaire, RG + test garde, `verifications-production.md`, `roadmap.md` ; `state.ts set slices.planification-p5-placement built` seulement quand tout est vert.

## Points d'attention déjà repérés

- Le traducteur gmao.v1 est publié en plus de l'événement de domaine : l'écouteur de la plateforme (`GmaoEvenementsPlanificationListener`) écoute encore l'événement de domaine ; à basculer sur `GenerateurDevenuIndisponible` avec le module (les 2 tests GMAO qui publient des événements de domaine devront publier aussi l'événement publié).
- `GENERATEURS_AFFECTES` (SalleVueJdbcAdapter) exclut réformés/désactivés/supprimés : à reproduire côté module à partir de `GenerateursDuCentre` (statut + salle).
- Le filet P5 mentionne « rien de commité » en tête, mais il est commité en bbe85d02.
