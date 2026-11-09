---
slice: pilotage-direction-pd2-instantanes-mensuels
status: built
date: 2026-11-09
order: 2
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0024, 0075, 0102, 0103, .ddd/safety-net/pilotage-direction.md, KB-DIR-03, KB-DIR-06]
---

# PD2 — Instantanés mensuels figés (lot B, construit)

Refactoring SUR PLACE (ADR 0024) : même table `direction_snapshot`, mêmes URL, rôles et codes ; aucune façade, flag, projection, métrique ni événement. Décisions et écarts : **ADR 0103** (à confirmer métiers/DPO au plus tard le 2026-12-15).

## Fait
| Couche | Livré |
|---|---|
| Domaine | `MoisDInstantane` (lecture `AAAA-MM`, capturable écoulé et <= 60 mois), `InstantaneMensuel` (agrégat immuable), `ContenuFige` |
| Application | `CapturerUnInstantaneMensuel`, `ConsulterUnInstantane`, `ListerLesInstantanes`, `CapturerLesInstantanesDuMoisEcoule` ; ports `InstantanesMensuels`, `ContenuDuMoisAFiger`, `SocietesActives` |
| Sortant | `InstantanesMensuelsJdbc` (SQL verbatim, `DuplicateKeyException` avalée), `ContenuFigeParLeTableauDeBord`, `SocietesEtCentresJdbc` (+ `SocietesActives`) |
| Entrant | langage publié `InstantanesMensuelsDeLaDirection` (adaptateur `InstantanesMensuelsPublies`), planificateur `CaptureDuMoisEcoulePlanifiee` (cron `0 30 2 1 * *`) |
| Plateforme | `DirectionSnapshotRestController` (jeton, garde, PDF) et `DirectionReportPdfService` : types seulement ; traducteur `IndicateursAFigerParLaPlateforme` ; retirés : `DirectionSnapshotService`, `DirectionSnapshotScheduler` |
| Gardes | `TableDuPilotageTest` (propriétaire nominatif), `TableDeLOrganisationTest`, ArchUnit du module, `FrontieresDesContextesTest` |

## Parité
Goldens PD2 4/4 inchangés (`instantanes/{cycle_de_vie,mois_refuses,cloisonnement_et_anciens,tache_planifiee}`), PD1/PD3/PD4 13/13, `DirectionSnapshotIntegrationTest` 5/5, `DirectionReportIntegrationTest` 3/3. **Écarts : aucun.**

## Bascule
Aucune (refactoring sur place) : pas de flag, pas de paliers, pas de métriques. Retour arrière = revert du commit (mêmes tables, aucune migration). Condition de mise en production : KB-DIR-01 (§69.1 de `verifications-production.md`) ; détections de données : §70.

## Résiduels nominaux (revue 2026-12-15, butoir 2027-03-31)
| Id | Résiduel |
|---|---|
| PD2-R1 | `SocietesEtCentresJdbc` lit `societes` (sociétés actives) en SQL : `organisation.v1` (jalon O4) |
| PD2-R2 | `IndicateursAFigerFournis` à valeur opaque, jusqu'à la reprise des indicateurs (PD5) |
| PD2-R3 | Contrôleur et jeton en plateforme (comme le lot A) |
| PD2-R4 | KB-DIR-01 : instantanés déjà figés avec totaux bruts, non retraités |
| PD2-R5 | Fuseau du mois courant (KB-DIR-03), futur en lecture et contenu illisible (KB-DIR-06 b, e) : statu quo, décisions métiers |
| PD2-R6 | Fuseau du centre annoncé par la fiche d'origine : non reproduit (le legacy ne le lit pas) |
| PD2-R7 | `ContenuFige(String)` : obsession de primitive assumée tant que le contenu reste opaque (voir PD2-R2) |
| PD2-R8 | TICKET SÉPARÉ (hors lot, préexistant) : 1 échec + 25 erreurs de la suite complète en ordre aléatoire (pollution d'état entre classes, 8 classes), identiques sur HEAD~1 |
| PD2-R9 | Détections 70.1 à 70.4 non rejouées sur PostgreSQL 16 (70.4 : `to_char(date_trunc(...))`) : à rejouer avant la revue du 2026-12-15 |

## Lot L1 (2026-11-09, ADR 0112) : instantanés relus masqués
Les instantanés figés avant la correction de KB-DIR-01 / KB-MED-104 portent des totaux bruts : ils sont masqués À LA LECTURE (`MasqueDesTotauxFiges` dans `InstantanesMensuelsPublies`) ; `direction_snapshot.payload` n'est JAMAIS réécrit (RG-DIR-090). Nouveau golden `ancien_instantane_masque_a_la_lecture_nouveau_code`. Détection §74.

## Reste à faire
Revue indépendante `.ddd/verify/` ; confirmations métiers/DPO ; PD3 (alertes) et PD5.
