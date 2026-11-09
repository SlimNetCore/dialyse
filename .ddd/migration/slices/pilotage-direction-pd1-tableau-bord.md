---
slice: pilotage-direction-pd1-tableau-bord
status: built
date: 2026-11-09
order: 1
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0075, ADR 0102, .ddd/safety-net/pilotage-direction.md]
---

# PD1 — Tableau de bord direction (lectures)

Refactoring SUR PLACE (ADR 0024/0075, ADR 0102) : mêmes tables, mêmes URL `/api/v1/direction/{societe,overview,breakdown}`, mêmes rôles et codes ; aucun flag, aucune façade, aucune bascule. Plan de bascule : sans objet (le code remplace le code, goldens inchangés) ; rollback = revert du lot.

## Fait (lot A, 2026-11-09)
| Couche | Contenu |
|---|---|
| Langage publié | `TableauDeBordDeLaDirection` (société, vue d'ensemble, répartition), `IndicateursDeLaDirectionFournis` (fourni par la plateforme) |
| Domaine | `Periode`, `SeuilDAnonymat`, `SocieteSuivie`, `BilanDuCentre`, `ChiffresFinanciers`, `VueDEnsembleDeLaSociete`, `RepartitionDeLaSociete`, `TrancheDAge` |
| Application | `ConsulterLaSociete`, `ConsulterLaVueDEnsemble`, `ConsulterLaRepartition`, 5 ports sortants, `HorlogeDuPilotage` |
| Adaptateurs | `SocietesEtCentresJdbc`, `PatientsDesCentresJdbc` + `PredicatDEffectif` (SQL verbatim), ACL `SeancesRealiseesParLaSeance`, `FacturationParLaFacturation`, `AnemieParLeDossierMedical` |
| Plateforme | `DirectionDashboardRestController` allégé (reste en plateforme : jeton, garde de société, routes d'autres contextes) ; `DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `EffectifSql` supprimés |

## Parité
Goldens PD1 (6 tests), PD2, PD3, PD4, SE7e, PC4, PC5, DM8/DM9 : inchangés, aucun `.approved` modifié. Contrat des lectures : H2 et PostgreSQL 16 (7 tests chacun), rejeu `backend/scripts/pilotage-direction-pg16-rejeu.sh 55552` code 0. Aucun écart assumé.

## Reste à faire (résidus nominatifs)
- `PatientsDesCentresJdbc` lit `patients` et `SocietesEtCentresJdbc` lit `societes`/`centers` en SQL : à remplacer par les langages publiés du Patient et de l'Organisation (gardes `TableDuPatientCouvertureTest`, `TableDeLOrganisationTest`, revue 2026-12-15, butoir 2027-03-31).
- Indicateurs (clinique, stock, alertes) servis par la plateforme via `IndicateursDeLaDirectionFournis` : à reprendre avec PD3.
- Questions ouvertes : KB-DIR-01 (DPO, priorité), KB-DIR-02, KB-DIR-03 (voir ADR 0102).

## Suites de la revue du lot A (2026-11-09)
- **Tests de domaine ajoutés (m3)** : `RepartitionDeLaSocieteTest` (10), `MatriceDesCaissesTest` (5), `EvolutionMensuelleTest` (3), `ConstituerLeRapportDeSyntheseTest` (8, fakes en mémoire des ports fournis), `HorlogeDuPilotageTest` (2) : seuil d'anonymat 5, totaux sur effectifs bruts (KB-DIR-01), tri des caisses, initiale du sexe, périodes ; aucun code de production modifié, aucun golden modifié.
- **PD1-R1 (m2, trois copies du prédicat d'effectif, RG-PAT-032)** : `PredicatDEffectif` (module Pilotage), `EffectifDeReference` (test, PC4/PC5) et `CompteursDeCouvertureJdbc` (Patient et couverture, comparée à la référence par PC4/PC5). Garde de dérive du module contre la référence : `PredicatDEffectifDuPilotageGardeIntegrationTest` (plateforme, 56 patients : sorties, séjours, dates limites). Résidu : convergence vers UNE lecture publiée de Patient et couverture (effectif d'une période) au PD5 ; les copies disparaissent alors (butoir 2027-03-31).
- **PD1-R2 (n1)** : société inconnue : le legacy levait `EmptyResultDataAccessException` (500, `code: DATA_ACCESS_ERROR`), le module lève `SocieteInconnue` (500, `code: INTERNAL_ERROR`) ; même statut, `code` différent ; cas inatteignable (`requireSociete()` revérifie l'appartenance avant). Écart assumé, non golden.
- **PD1-R3 (n2)** : l'égalité entre le fuseau de la `Clock` du Pilotage (`Clock.systemUTC()`) et celui de la JVM (`TimeZone.setDefault("UTC")` dans `BackendApplication.main`) n'est pas gardée par un test (elle exigerait de lancer `main`) ; `HorlogeDuPilotageTest` fige seulement le comportement de l'horloge injectée. Résidu à lever avec la décision KB-DIR-03.
- **PD1-R4 (n3)** : `RepartitionDeLaSociete.ProfilDuCentre.ages` (une `Map` mutable) n'est pas copiée par le record : sans effet (jamais modifiée après construction) ; correction (`Map.copyOf`) reportée car elle touche `src/main` (hors périmètre de la revue).
- **PD1-R5 (n4)** : le contrat `parCaisse` n'exerce que le cas sans caisse ; la jointure à une vraie caisse est verrouillée par le golden PD1 et le rejeu PG16 D2 (CNAS, sans caisse). Résidu : une caisse dans `LecturesDuPilotageContrat` (H2 + PG16).

## Lot L1 (2026-11-09, ADR 0112) : KB-DIR-01 CORRIGÉ
Comportement CHANGÉ à la demande de l'utilisateur (pas une parité) : `totaux.patients`, `totaux.patientsSousKt` et `caisseTotaux[].patients` sont `null` dès qu'un centre composant est masqué (RG-DIR-111). Goldens `overview` et `breakdown` : anciens archivés dans `pd1/_ecarts-legacy/tableau_de_bord/*_avant_l1.legacy.json`, nouveaux `*_nouveau_code`. Validation DPO attendue ≤ 2026-12-15 ; résiduels (effectif d'un centre vs sa ventilation) non traités.
