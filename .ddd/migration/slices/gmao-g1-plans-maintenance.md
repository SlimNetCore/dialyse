---
status: approved
date: 2026-10-11
slice: gmao-g1-plans-maintenance
sources: [.ddd/tactical/gmao.md (Plan de maintenance, R25-R27), ADR 0019, ADR 0024, .ddd/safety-net/gmao-g1-plans-maintenance.md]
---
# G1 — Plans de maintenance préventive (refactoring sur place, état : `built`)

**Comportement observable** : un administrateur de centre établit, consulte, exécute, suspend et reprend les plans de maintenance préventive d'un équipement ; l'application signale les plans en retard. 9 endpoints `/api/v1/gmao/plans-maintenance`, `@PreAuthorize("hasRole('ADMIN')")`.

## Réalisation
| Couche | Contenu (`backend/contexts/gmao/.../`) |
|---|---|
| domain | `PlanMaintenance` (racine), `FrequenceMaintenance`, `StatutPlan`, identifiants typés, erreurs de domaine, événements `PlanMaintenanceEtabli`, `MaintenancePreventiveExecutee`, `PlanMaintenanceSuspendu`, `PlanMaintenanceRepris`, port `PlanMaintenanceRepository` |
| application | cas d'usage `EtablirPlan`, `EnregistrerExecution`, `SuspendrePlan`, `ReprendrePlan`, `Consulter/ListerPlans*` ; ports `LecturePlans`, `EquipementExiste`, `SessionCourante`, `Horloge`, `PublicateurDEvenements`, `UniteDeTravail` |
| infrastructure | `PlanMaintenanceJpa` sur la table `gmao_plans_maintenance` (mêmes colonnes que l'ancienne entité, dont `tachesaeffectuer`, KB-GMAO-21), mapper explicite, adaptateurs `PlanMaintenanceRepositoryJpa` et `LecturePlansJpa` ; `EquipementRepositoryJpa` répond au port `EquipementExiste` (GMAO possède `gmao_equipements` depuis G3 ; l'ACL JDBC a été supprimée) ; ACL `SessionLegacy` (principal de sécurité) |
| interfaces | `PlanMaintenanceControleur` (mêmes routes, même `@PreAuthorize`, mêmes DTO JSON) |
| configuration | `GmaoConfiguration` |

Événements : publiés par les cas d'usage dans la transaction (registre Spring Modulith, ADR 0013). Aucun consommateur métier pour l'instant ; un consommateur minimal journalise et acquitte (`JournalDesEvenements`). Futurs consommateurs : Planification, Pilotage, Notifications.

## Preuve d'équivalence
Suite de caractérisation unique : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/plans/PlanMaintenanceHttpCharacterizationIntegrationTest` (36 scénarios : les 35 d'origine, dont 3 « autre centre » réécrits pour KB-GMAO-15, plus la consultation d'un plan d'un autre centre séparée de la suppression logique ; goldens `src/test/resources/characterization/gmao/plans/`), verte contre le nouveau code. Tests du module : domaine, cas d'usage avec fakes, contrat de repository (en mémoire + JPA/H2, même suite), outbox, architecture (ArchUnit).

## Écarts de comportement décidés
| KB | Décision | Golden |
|---|---|---|
| KB-GMAO-14 | équipement inconnu : 400 `EQUIPEMENT_INCONNU` (le legacy répondait 500 sur PostgreSQL, 201 sous H2) | `etablir_equipement_inconnu_refuse_400_nouveau_code` |
| KB-GMAO-15 | **isolation de centre CORRIGÉE dans cette livraison (décision utilisateur 2026-10-12)** : `GET /{id}` et `GET /equipement/{id}` ne voient que les plans du centre de la session (autre centre : 404, et liste vide) ; `executer`, `desactiver`, `reactiver` sur un plan d'un autre centre répondent comme « plan inconnu » : 400 « Plan non trouvé » (KB-GMAO-16), plan non modifié. Le legacy répondait 200 et modifiait le plan. Filtre dans les cas d'usage (`ConsulterPlan`, `EnregistrerExecution`, `SuspendrePlan`, `ReprendrePlan`, `ListerPlansDEquipement`) | `consulter_plan_d_un_autre_centre_404_nouveau_code`, `lister_plans_equipement_2_vu_du_centre_B_nouveau_code`, `executer_plan_d_un_autre_centre_400_nouveau_code`, `desactiver_plan_d_un_autre_centre_400_nouveau_code`, `reactiver_plan_d_un_autre_centre_400_nouveau_code` |
Goldens « autre centre » devenus caducs, retirés et remplacés (aucun autre golden ni assertion modifié) : `consulter_plan_d_un_autre_centre_200`, `lister_plans_equipement_2_vu_du_centre_B`, `executer_plan_d_un_autre_centre_200`. Les scénarios `desactiver`/`reactiver` d'un autre centre n'existaient pas au filet : ajoutés (goldens `*_nouveau_code`).
Reproduits à l'identique : KB-GMAO-17 (suppression logique effacée à la réécriture du plan), KB-GMAO-12, KB-GMAO-16. Le golden H2 historique `etablir_equipement_inconnu_date_passee_201` a été retiré (scénario remplacé).
**Autres lecteurs du plan** : l'écran legacy « fiche équipement » lit les plans par l'adaptateur legacy (`PlanMaintenanceRepositoryPort`), hors de cette correction.

## Legacy retiré / conservé
| Retiré | `PlanMaintenanceRestController`, `CreatePlanMaintenanceRequest`, `ReactiverPlanRequest` |
|---|---|
| Conservé (consommateurs legacy : fiche équipement, statistiques GMAO) | `bc-gmao` : `PlanMaintenance`, `FrequenceMaintenance`, `StatutPlan`, `PlanMaintenanceRepositoryPort` ; `platform` : `PlanMaintenanceRepositoryAdapter`, `PlanMaintenanceEntity`, `PlanMaintenanceJpaRepository`, `PlanMaintenanceResponse` (utilisé par `EquipementFicheResponse`) |
À retirer quand G6 sera migré (fiche équipement et statistiques). `PlanMaintenanceJpa` (module) et `PlanMaintenanceEntity` (legacy) décrivent la même table : garde `ModelesDePersistanceAlignesIntegrationTest` (platform), à retirer avec l'entité legacy (G6). Vérifications de production : `.ddd/migration/verifications-production.md`.

## Livraison
En une fois, avec le déploiement du backend. Aucune migration Flyway pour G1 (la migration V35 du registre d'événements `event_publication` est la seule nouvelle). Retour arrière : redéployer la version précédente (mêmes tables, aucune donnée à rapatrier). Rejouer sur PostgreSQL 16 réel : `backend/scripts/gmao-pg16-rejeu.sh` (cluster jetable, port 5432 interdit).

## Nettoyage de la base de dev
Des migrations V34 à V38 (supprimées du dépôt, jamais déployées) ont peut-être été appliquées à votre base de dev locale par un démarrage antérieur de l'application. V35 (registre `event_publication`) existe toujours, avec le même contenu : **ne supprimez pas la ligne 35**. **Attention au numéro 36** : l'ancienne V36 (projection, supprimée) et la **V36 actuelle** (`V36__gmao_taches_intervention.sql`, tâches d'intervention, conservée, description Flyway `gmao taches intervention`) portent le même numéro. Au prochain démarrage, la validation Flyway échouerait pour les versions 34, 37, 38 appliquées mais absentes, et pour une ligne 36 dont le contenu n'est pas celui de la V36 actuelle. **C'est votre décision, et uniquement sur votre base de dev locale** (jamais sur une base partagée ou de production). À exécuter vous-même, après vérification :

```sql
-- 1. Vérifier ce qui est appliqué ; la ligne 36 est l'ANCIENNE V36 seulement si sa description n'est PAS 'gmao taches intervention'
SELECT version, description, success, checksum FROM flyway_schema_history WHERE version::int BETWEEN 34 AND 38 ORDER BY installed_rank;
-- 2. Retirer les traces des migrations supprimées (34, 37, 38 ; V35 est conservée ; 36 seulement si c'est l'ancienne)
DELETE FROM flyway_schema_history WHERE version IN ('34', '37', '38');
DELETE FROM flyway_schema_history WHERE version = '36' AND description <> 'gmao taches intervention';  -- ne supprime rien si la 36 est la V36 actuelle
-- 3. Retirer les objets qu'elles ont créés (schéma gmao : plan_maintenance, routage, journal de routage, suivis de projection, intervenant)
DROP SCHEMA IF EXISTS gmao CASCADE;
```
Les tables `public.gmao_plans_maintenance` et `public.gmao_intervenants` ne sont pas touchées. Si V35 a été appliquée avec un autre contenu ou si la table `event_publication` contient des lignes obsolètes, elle peut être vidée par `DELETE FROM event_publication;` (même remarque : base de dev locale seulement).
