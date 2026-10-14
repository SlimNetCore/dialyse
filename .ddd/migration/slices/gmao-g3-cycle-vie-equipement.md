---
status: approved
date: 2026-10-11
slice: gmao-g3-cycle-vie-equipement
order: 3 (score 18/30)
sources: [.ddd/tactical/gmao.md (Équipement, R1-R9, §7.2), ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md (KB-GMAO-32 à 41), .ddd/safety-net/gmao-g3-cycle-vie-equipement.md]
---
# G3 — Cycle de vie de l'équipement (enregistrer, corriger, mettre hors service, remettre en service, réformer, observer), refactoring sur place, état : `built`

> ADR 0024 : pas de façade, de routage, de projection ni de bascule par paliers. Mêmes tables, mêmes URLs, livraison en une fois ; le filet de caractérisation tourne contre le nouveau code.

**Comportement observable** : l'administrateur gère le parc du centre ; tout passage d'un générateur de dialyse de `EN_SERVICE` à un autre statut alerte la Planification et évince le cache `ref.generateurs`. Routes `/api/v1/gmao/equipements` (rôle `ADMIN`) : POST, GET liste paginée, GET `/{id}`, PUT `/{id}`, POST `/{id}/hors-service`, `/{id}/reactiver`, `/{id}/reformer` (`ADMIN` **et** `GMAO_REFORME`), `/{id}/observations`. Tables `public.gmao_equipements`, `public.gmao_equipement_statut_historique`, inchangées.

## Réalisation
| Couche | Contenu (`backend/contexts/gmao`) |
|---|---|
| domain (`domain/equipement`) | `Equipement` (racine : enregistrer, corrigerIdentification, mettreHorsService, remettreEnService, constaterEtat, reformer, consignerObservation), `StatutEquipement`, `TypeEquipement`, `CodeEquipement`, `SalleId`, `PrixAcquisition`, `ChangementDeStatut` (historique), erreurs nommées (`EquipementReformeImmuable`, `EquipementDejaHorsService`, `RemiseEnServiceImpossible`, `EquipementDejaReforme`, `EtatNonFixableParIntervention`, `CodeEquipementDejaUtilise`, `PrixNegatif`, `TypeEquipementInconnu`), service de domaine `UniciteDuCodeEquipement` (R7) + port `CodesEquipementDuCentre`, port `EquipementRepository`, événements `EquipementEnregistre`, `IdentificationEquipementCorrigee`, `EquipementMisHorsService` (avec `cause`), `EquipementRemisEnService`, `EquipementReforme` |
| application (`application/equipement`) | `EnregistrerEquipement`, `CorrigerIdentification`, `MettreHorsService`, `RemettreEnService`, `Reformer`, `ConsignerObservation`, `ConstaterEtat` (implémente le langage publié `EtatEquipementPort`), `ConsulterEquipement`, `ListerParc` ; ports `LectureEquipements`, `CapaciteDesSalles` |
| publishedlanguage | `EtatEquipementPort` : appliquer l'état constaté par une intervention (utilisé par la clôture d'intervention legacy) |
| infrastructure | `EquipementJpa` (table `gmao_equipements`, mêmes colonnes, longueurs et contrainte `uk_gmao_equipement_centre_code`), `EquipementStatutHistoriqueJpa`, mapper explicite, `EquipementRepositoryJpa` (écrit l'équipement et ses lignes d'historique dans la transaction du cas d'usage ; implémente aussi `CodesEquipementDuCentre`), `LectureEquipementsJpa` ; `JournalDesEvenements` journalise les événements du parc |
| interfaces | `EquipementControleur` (mêmes routes, mêmes `@PreAuthorize` sauf KB-GMAO-32), requêtes et `EquipementReponse` (mêmes champs et ordre JSON) |
| platform (nouveaux fichiers, `infrastructure/acl/gmao`) | `GmaoEvenementsPlanificationListener` : écouteur Spring Modulith (`@ApplicationModuleListener`, livré après commit, asynchrone) de deux événements du parc (`EquipementMisHorsService`, `EquipementReforme`) ; appelle `GenerateurIndisponibleService.signaler` (non modifié : règle KB-GMAO-37 conservée). Il n'évince pas le cache : l'éviction de `ref.generateurs` est un `@CacheEvict` synchrone d'`EquipementControleur`. `CapaciteDesSallesLegacy` : ACL `CapaciteDesSalles` vers `SalleGenerateursService` (refus 422 `SALLE_CAPACITE_DEPASSEE` inchangé) |
| platform (modifié) | `InterventionEquipementStatutService` : contrat inchangé, `appliquerEtat` délègue à `EtatEquipementPort` (plus d'écriture directe de l'équipement, de l'historique, ni d'alerte) |

Une commande = une transaction : l'équipement, l'historique et la publication des événements s'écrivent ensemble (KB-GMAO-38, écart bénin accepté).

## Preuve d'équivalence
Suite unique `.../characterization/gmao/equipements/EquipementHttpCharacterizationIntegrationTest` (54 scénarios, goldens `src/test/resources/characterization/gmao/equipements/`), verte contre le nouveau code, y compris l'alerte Planification (`notification_evenement`), l'éviction du cache et la clôture d'intervention. L'alerte étant livrée de façon asynchrone par le registre d'événements (le cache, lui, est évincé de façon synchrone), le harnais attend que le registre soit vide après chaque appel HTTP (Awaitility, `event_publication`) ; aucune assertion de contenu n'a changé. Tests du module : domaine (`EquipementTest`, `UniciteDuCodeEquipementTest`), cas d'usage sur adapters in-memory (`CasDUsageEquipementTest`), contrat du repository, des codes et du read model (`EquipementContrat` : double in-memory et JPA/H2), outbox (`RegistreDEvenementsEquipementTest` : événement enregistré avec la donnée, rien après rollback), ArchUnit.

## Écarts de comportement décidés
| KB | Décision (utilisateur, 2026-10-11) | Golden |
|---|---|---|
| KB-GMAO-32 | **ÉCART** : la réforme exige `ADMIN` **et** `GMAO_REFORME` ; l'habilitation seule répond 403 (le legacy répondait 200) | `reformer_habilitation_seule_sans_admin_nouveau_code` (remplace `reformer_habilitation_seule_sans_admin`, retiré) |
| KB-GMAO-38 | **ÉCART BÉNIN** : équipement, historique et publication dans une seule transaction (le legacy faisait trois écritures séparées) | non verrouillé par golden ; `RegistreDEvenementsEquipementTest` |
| KB-GMAO-33, 34, 35, 36, 37, 39, 40, 41 | **REPRODUITS** (ADR 0019) : code comparé exactement, supprimé logique ressuscité par une écriture, statuts HTTP et limites de base, PUT total, règle d'alerte `EN_SERVICE` vers autre statut, lecteurs SQL, intervention qui contourne le cycle de vie, motif de l'historique | goldens existants |

Éviction du cache : synchrone (point validé n°1) par `@CacheEvict` sur `EquipementControleur` ; la clôture d'intervention garde son éviction synchrone d'origine.

## Reste à retirer avec G4 et G6
Fichiers legacy **conservés sans modification** car encore référencés :
| Fichier | Référencé par | Retrait avec |
|---|---|---|
| `bc-gmao` : `Equipement`, `EquipementStatutHistorique`, `StatutEquipement`, `TypeEquipement`, `EquipementRepositoryPort`, `EquipementStatutHistoriqueRepositoryPort`, `IndisponibiliteCalculator`, `IndicateursIntervention`, `AideDecisionMaintenance` | `EquipementFicheRestController`, `GmaoStatsRestController`, `DirectionGmaoQueryService`, `InterventionSuiviQueryService`, `BonInterventionReportService`, `InterventionRestController`, `GenerateurIndisponibleService`, `PatientMigrator` (bc-migration), `Intervention` (`StatutEquipement`) | G4 (interventions), G6 (fiche, statistiques, pilotage) |
| `platform` : `EquipementRepositoryAdapter`, `EquipementStatutHistoriqueRepositoryAdapter`, `EquipementEntity`, `EquipementStatutHistoriqueEntity`, `EquipementJpaRepository`, `EquipementStatutHistoriqueJpaRepository`, `EquipementResponse` (`EquipementFicheResponse`) | idem (lecture) ; `MigrationConfig` | G6 |
| `InterventionEquipementStatutService` (modifié) | `InterventionRestController` | G4 : la clôture appellera directement `EtatEquipementPort` |
| `GenerateurIndisponibleService`, `SalleGenerateursService` | écouteur et ACL ci-dessus ; wizard planification | extraction de Planification |
Les méthodes d'écriture de `Equipement` (legacy : `marquerHorsService`, `reactiver`, `changerStatutIntervention`, `reformer`...) ne sont plus appelées par le code de production ; elles partent avec la classe.
**Lecteurs SQL de `gmao_equipements`** (inchangés, schéma inchangé) : `SalleVueJdbcAdapter`, `PlanningDonneesJdbcAdapter`, `OptimisationDonneesJdbcAdapter`, `MouvementPatientJdbcAdapter`, `ReferentialRepositoryAdapter`, `ReferentialAdminTables`, `DirectionCapaciteQueryService`, `DirectionGmaoQueryService`, `SeanceRestController:456`, `PatientRestController:335`.
**Deux modèles de persistance sur la même table** (`EquipementJpa` du module, `EquipementEntity` legacy) jusqu'à ce retrait : même déclaration de colonnes et de contrainte, à garder alignées. Le test `ModelesDePersistanceAlignesIntegrationTest` (platform) échoue si table, colonnes (nom, longueur des chaînes, précision, échelle, nullable, définition, identifiant) ou contraintes d'unicité divergent (prouvé le 2026-10-12 sur une divergence volontaire de l'échelle du prix, retirée ; il a révélé et fait corriger la définition `TIMESTAMP WITH TIME ZONE` des dates de `PlanMaintenanceJpa`) ; **à retirer avec G6** (équipements ; G4 pour plans et intervenants, G1/G2).

## Livraison
En une fois, sans migration Flyway, avec G1 et G2. Retour arrière : redéployer la version précédente (mêmes tables, aucune donnée à rapatrier). À vérifier avant déploiement (KB-GMAO-33/34, questions ouvertes) : contraintes d'unicité réelles de `gmao_equipements` en production (V5 déclare `code UNIQUE` global), présence de lignes `DESACTIF`, `A_REFORMER` ou `deleted_at` non nul. Rejeu PostgreSQL 16 : `backend/scripts/gmao-pg16-rejeu.sh` (G1, G2 et G3).

### Constat sur PostgreSQL 16 réel (2026-10-12, cluster jetable, port 55432, détruit)
- **Rejeu** : contrat PG des trois agrégats (`EquipementPostgresContractIT` 19, `PlanMaintenancePostgresContractIT` 15, `IntervenantPostgresContractIT` 14) et suites HTTP (G1 36, G2 39, G3 54 scénarios) verts sur le schéma Flyway V1..V38 puis `ddl-auto=update`. Ce rejeu a révélé deux défauts du **banc** (pas du code de production) : le contrat PG des plans ne créait pas les équipements, centres et utilisateurs exigés par les clés étrangères réelles (il n'avait donc jamais passé depuis le retour au schéma historique), et un nettoyage de la matrice G3 supprimait un équipement encore référencé par une intervention. Corrigés dans les tests.
- **Unicité du code (KB-GMAO-33)** : sur le schéma Flyway **pur**, `gmao_equipements` porte `gmao_equipements_code_key UNIQUE (code)` (V5, unicité **globale**) et aucune contrainte (centre, code). Après démarrage d'Hibernate (`ddl-auto=update`, comme la production historique) la base porte **les deux** : `gmao_equipements_code_key UNIQUE (code)` et `uk_gmao_equipement_centre_code UNIQUE (centre_id, code)`. Conséquence réelle : **un même code dans deux centres est refusé par PostgreSQL** (500 `DATA_ACCESS_ERROR`), alors que le contrôle applicatif (centre, code) l'accepte. Le contrat PG (`should_refuse_the_same_code_twice_in_a_centre_and_accept_it_in_another`) vérifie ce comportement selon les contraintes présentes. Le filet HTTP retire la contrainte globale de la base jetable avant ses scénarios (il insère le même code dans deux centres) ; ce n'est donc **pas** une preuve que la production l'accepte : le point 2 de `verifications-production.md` reste à faire. Comportement du nouveau code identique au legacy (mêmes tables, mêmes contraintes).
- **Colonne des tâches (KB-GMAO-21)** : sur le schéma Flyway pur, `gmao_plans_maintenance` n'a que `taches_a_effectuer` ; `tachesaeffectuer` n'apparaît qu'après `ddl-auto=update` (Hibernate l'ajoute) et les deux colonnes coexistent alors. Avec `ddl-auto=validate` (profil `prod`), un schéma Flyway pur ferait échouer le démarrage de G1 **comme du legacy** : la production doit déjà porter `tachesaeffectuer` (point 1 de `verifications-production.md`).
- **Isolation de centre** : déjà appliquée sur toutes les routes d'équipement (GET 404, commandes 400, listes filtrées) : pas de défaut du type KB-GMAO-15 pour G3.

## Questions ouvertes
1. L'événement `ObservationConsignee` du modèle tactique n'est pas publié (aucun consommateur) ; l'ajouter avec le premier consommateur.
(Les anciennes questions 2 et 3 sont traitées : voir « Points validés ».)

## Points validés (2026-10-12)
1. **Cache `ref.generateurs`** : évincé de façon synchrone, avant la réponse HTTP, par `@CacheEvict(cacheNames = "ref.generateurs", allEntries = true)` sur `enregistrer`, `corriger`, `mettreHorsService`, `remettreEnService` et `reformer` d'`EquipementControleur` (même nom et même éviction totale que la clôture d'intervention ; clés du `@Cacheable` : `centre` et `centre:salle:id`). L'éviction s'exécute au retour de la méthode, donc après le commit du cas d'usage (`UniteDeTravailSpring`) ; pas d'éviction si la commande est refusée ni pour les observations (statut et identité inchangés). L'écouteur de platform ne fait plus que l'alerte. Preuve : `EvictionCacheGenerateursSynchroneIntegrationTest` (cache lu juste après la réponse, sans attendre l'écouteur ; rouge avant l'annotation, vert après).
2. **Événements non versionnés** : les événements de domaine restent les événements publiés ; l'écouteur de platform dépend des classes d'événements ; à versionner (`gmao.v1`) quand un 2e consommateur ou une séparation de service apparaîtra. Aucun code ajouté.
3. **Deux modèles de persistance** : garde `ModelesDePersistanceAlignesIntegrationTest` (table, colonnes, unicité ; H2, méta-modèle Hibernate), à retirer avec G6 (équipements) / G4 (plans et intervenants).
4. **Vérifications de production** : `.ddd/migration/verifications-production.md` (SQL en lecture seule, à exécuter par vous avant déploiement).
5. **Règles de gestion** : `09-gmao.md` (RG-GMA-014 et rôles) et l'annexe de traçabilité (`EquipementControleur`, réforme `ADMIN` et `GMAO_REFORME`) cohérentes avec le code ; garde `ReglesDeGestionDocumentationTest` verte. La documentation fonctionnelle générée n'est pas régénérée (reste à faire).
