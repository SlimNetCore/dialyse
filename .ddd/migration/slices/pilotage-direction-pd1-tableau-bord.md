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
