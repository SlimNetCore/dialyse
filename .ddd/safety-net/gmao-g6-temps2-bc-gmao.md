---
status: proposed
date: 2026-10-14
sources: ADR 0024 (+ amendements G3/G4/G6), docs/architecture/guide-refactoring-sur-place-gmao.md, gmao-g3-cycle-vie-equipement.md, gmao-g6-consultation-pilotage.md, seams.md, grep des imports et pom.xml
---
# Filet G6 temps 2 : retrait du module `backend/bc-gmao`

Objectif : verrouiller les lectures de l'état d'un équipement/générateur faites **hors contexte GMAO** par du code qui importe `com.hemodialyse.backend.domain.gmao.*`, pour pouvoir supprimer `bc-gmao`. Refactoring sur place (ADR 0024), mêmes tables et URLs. Aucun code de production modifié, aucun seam.

## 1. Inventaire exact des références à `bc-gmao` (grep imports + pom, 2026-10-14)

| Module | Prod (`src/main`) | Test (`src/test`) |
|---|---|---|
| `backend/pom.xml` | `<module>bc-gmao</module>` (l.38), dependencyManagement `bc-gmao` (l.122) | |
| `backend/platform/pom.xml` | dépendance `bc-gmao` (l.69) (+ `contexts-gmao` déjà ajouté) | |
| `backend/bc-migration/pom.xml` | dépendance `bc-gmao` (l.34) | |
| `backend/bc-migration` | `domain/migration/service/PatientMigrator.java` (imports `Equipement`, `EquipementRepositoryPort`; lit `findByCentreIdAndType(centre,"GENERATEUR_DIALYSE")`, `getId`, `getSalleId`, `getCode`) | `InMemoryMigrationPorts.java` (classe interne `Equipements implements EquipementRepositoryPort`, construit des `Equipement`) |
| `backend/platform` hors adaptateurs | `application/planning/GenerateurIndisponibleService.java` (`Equipement`, `StatutEquipement`, `TypeEquipement`), `infrastructure/acl/gmao/GmaoEvenementsPlanificationListener.java` (reconstruit un `Equipement` via `Equipement.reconstruct(...)` à 18 arguments), `infrastructure/config/MigrationConfig.java` (injecte `EquipementRepositoryPort` dans `PatientMigrator`), `web/dto/response/gmao/InterventionResponse.java`, `LigneCoutResponse.java` (enums/modèle `Intervention`, `LigneCoutIntervention`) | `application/planning/GenerateurIndisponibleServiceTest` ; `characterization/gmao/{equipements,intervenants,plans}/*HttpCharacterizationIntegrationTest` (semis via ports/modèles bc-gmao) ; `infrastructure/persistence/adapter/gmao/InterventionCostQueriesIntegrationTest` ; `infrastructure/web/dto/GmaoUtcSerializationIntegrationTest` |
| `backend/platform` adaptateurs legacy | `persistence/adapter/gmao/{Equipement,EquipementStatutHistorique,Intervenant,Intervention,PlanMaintenance}RepositoryAdapter` (implémentent les ports bc-gmao) ; entités `persistence/entity/gmao/*` (mappers `toDomain/fromDomain`) | |
| `backend/contexts/gmao` | aucune dépendance de compilation ; seulement le **texte** `com.hemodialyse.backend.domain.gmao.model.T...` dans le détail des erreurs 400 de `EquipementControleur:153`, `IntervenantControleur:103`, `PieceJointeControleur:109` (littéral, golden de compatibilité d'erreur : à ne PAS changer en supprimant le module) | |
| `backend/bc-gmao` lui-même | 25 classes (19 model, 5 port, 1 service `IndisponibiliteCalculator`) | 5 tests : `EquipementTest`, `IntervenantTest`, `InterventionTest`, `LigneCoutInterventionTest`, `IndisponibiliteCalculatorTest` |

Ne référencent PAS `domain.gmao` (vérifié) bien que citant « Equipement » : `GmaoEvenementsReformeListener` (événements du contexte), `ReferentialRepositoryAdapter` (cache `ref.generateurs`, lit `EquipementEntity`/JPA), `PatientRestController`, `SeanceRestController`, `BonInterventionReportService`, `EquipementEntity`. Aucun autre module (`bc-seance`, `bc-pec`, etc.) n'importe `domain.gmao`. `CapaciteDesSallesLegacy`/`SalleGenerateursService` : aucun import bc-gmao (lecture JDBC/JPA), donc hors périmètre du retrait ; à vérifier seulement si leurs fichiers sont touchés (déjà couverts par G3 : disponibilité des salles).

## 2. Scénarios (point d'observation)

Point d'observation : **registre d'événements Spring Modulith réel** (publication d'un `EquipementMisHorsService`/`EquipementReforme` dans une transaction, Awaitility sur la livraison) puis **état de la table durable `notification_evenement`** (alertes normalisées : ids/dates masqués, golden JSON) ; migration : **HTTP MockMvc** `POST /api/v1/admin/migration/batches/{lot}/import/patients` (chaîne de sécurité réelle, JPA/JDBC H2) puis état de `patients` ; H2 uniquement.

| Fichier | Scénarios |
|---|---|
| `characterization/gmao/planification/GenerateurIndisponibleCharacterizationIntegrationTest` (10) | mise hors service : alerte ssi le générateur quitte `EN_SERVICE` ; réforme : alerte ssi précédent `EN_SERVICE` ; la cause n'influence pas l'alerte ; état constaté `A_REFORMER` (clôture d'intervention) alerte + proposition de réforme ; seul `GENERATEUR_DIALYSE` alerte ; patients cités = ceux du centre de l'événement sur ce générateur ; alerte déposée dans le centre de l'événement sans relire l'équipement ; patients sortis ignorés (dont décédé sans date, KB-GMAO-83) ; rejeu = alerte en double (KB-GMAO-80) ; la livraison ne touche pas au cache `ref.generateurs` (éviction synchrone côté contrôleur) |
| `characterization/migration/PatientsGenerateursMigrationCharacterizationIntegrationTest` (8) | générateur du centre retrouvé par code, la salle du générateur devient celle du patient ; recherche insensible à casse/accents/ponctuation mais pas aux séparateurs ; inconnu / autre centre / supprimé / autre type refusé ; hors service, maintenance, réformé acceptés sans avertissement (KB-GMAO-81) ; chaque centre résout parmi ses propres générateurs ; colonne vide n'efface pas le générateur déjà repris ; en-têtes `machine`/`numero generateur` ; réservé aux administrateurs ; compte rendu trompeur sur erreur (KB-GMAO-82) |
| existant `GenerateurIndisponibleServiceTest` (4, unitaire) | règle KB-37 sur objets, types, déjà indisponible, échec d'alerte jamais propagé |

## 3. Déjà verrouillé par G3/G4/G6 (non dupliqué) vs manquant

- **G3** : `EquipementHttpCharacterizationIntegrationTest` et `EvictionCacheGenerateursSynchroneIntegrationTest` verrouillent côté équipement les transitions de statut, l'historique, l'événement publié et l'éviction **synchrone** du cache ; G4 : clôture d'intervention -> état constaté, `REFORME_PROPOSEE` dédoublonnée (`InterventionCycleDeVie…`, `InterventionReformeProposeeNouveauCode…`) ; G6 : lectures (fiche, disponibilité patient, stats, direction) via les vues du contexte.
- **Ajouté par ce filet** : tout ce qui est en §2 (consommation par la Planification, migration vers un générateur). Seul le côté **consommateur** manquait : G3 ne couvrait pas `GenerateurIndisponibleService` (« non couvert au sens covered.json »).
- Manquant résiduel (sans risque pour le retrait) : alerte quand la création d'un équipement (précédent `null`) : non atteignable par l'écouteur (l'événement porte toujours un statut précédent), seulement par le test unitaire.

## 4. Non verrouillé

- Concurrence/ordre de livraison multi-événements ; reprise après redémarrage du registre (rejeu simulé par republication, pas par `IncompleteEventPublications`).
- Texte exact de l'alerte au-delà du golden normalisé ; destinataires (habilitations) : G4.
- Performances ; dates relatives (`LocalDate.now(UTC)` du service) : tests semés relativement à « maintenant ».
- Fichier CSV : colonnes autres que `generateur` ; mapping des autres champs de la reprise de patients (hors périmètre de la sortie de bc-gmao).
- Les poms ne sont pas observables par un test ; leur validité est vérifiée par la compilation.

## 5. Seams

Aucun appliqué ni nécessaire (`seamAllowed` vide). Optionnel, non demandé : `Equipement.reconstruct` à 18 arguments dans l'écouteur (remplacé de toute façon au retrait) ; voir `seams.md`.

## 6. `covered.json` : justification

Déjà présents (ajoutés avant par les tentatives précédentes) : `GenerateurIndisponibleService`, `PatientMigrator`, `MigrationConfig`, `GmaoEvenementsPlanificationListener`, les 25 classes de `bc-gmao/src/main`, les 5 adaptateurs, entités, DTO gmao. Le hook ne garde que `backend/**/src/main/java/**` : les tests et poms ne sont pas bloqués et n'ont donc pas besoin d'être listés (« pas plus que protégé »). Ajouté ici : `GmaoEvenementsReformeListener` (modifié si son import d'événements change ; exercé par `InterventionCycleDeVie…` et le test de planification).

## 7. Plan de retrait recommandé (étapes, une compilation verte entre chaque)

1. Port de lecture : exposer par l'interface publiée de `contexts/gmao` (`LectureEquipements` / `EquipementVue`, déjà utilisée par G6) une requête « générateurs de dialyse d'un centre : id, code, salle, statut, supprimé » ; si elle manque, un nouveau port publié `GenerateursDuCentre`. Aucun accès à `EquipementEntity` depuis `bc-migration` (module sans JPA).
2. `bc-migration` : remplacer `EquipementRepositoryPort` par un port propre à la reprise (`GenerateursDeReprise`, valeur `GenerateurRepris(id, code, salleId)`), adapté dans `MigrationConfig` sur le port publié (ACL) ; réécrire `InMemoryMigrationPorts.Equipements` sur ce port ; retirer la dépendance `bc-gmao` de `bc-migration/pom.xml`. Rejouer les 8 scénarios de migration (parité, KB-81/82 reproduits).
3. `GenerateurIndisponibleService` : remplacer `Equipement` par des valeurs (type, code, id, centre, statut, précédent) ; l'écouteur n'a plus à reconstruire un `Equipement`. Adapter `GenerateurIndisponibleServiceTest` (seul test à toucher). Rejouer les 10 scénarios.
4. `InterventionResponse`/`LigneCoutResponse` et adaptateurs/entités legacy : vérifier qu'ils ne servent plus qu'aux fixtures de G1-G3/G4 ; remplacer les semis des tests (`*HttpCharacterization…`, `InterventionCostQueriesIntegrationTest`, `GmaoUtcSerializationIntegrationTest`) par des `INSERT` SQL (JdbcTemplate) dans les tables `gmao_*`, puis supprimer adaptateurs, entités, `JpaRepository` devenus morts.
5. Supprimer le module : `<module>` et `dependencyManagement` (`backend/pom.xml`), dépendance de `platform/pom.xml`, dossier `backend/bc-gmao` ; relancer `check-arch.ts` et la suite complète. Conserver les littéraux d'erreur 400 des contrôleurs de `contexts/gmao`.
6. Décision séparée : corriger KB-GMAO-80 à 83 après retrait (ADR 0019), jamais pendant.

## 8. Questions ouvertes

- Port publié existant suffisant ou nouveau port `GenerateursDuCentre` ? (étape 1)
- Reproduire ou corriger KB-GMAO-81 (reprise sur générateur hors service) : par défaut reproduire.

## 9. Exécution

`cd backend && ./mvnw -o -q -pl platform -am test -Dtest='GenerateurIndisponibleCharacterizationIntegrationTest,PatientsGenerateursMigrationCharacterizationIntegrationTest,GenerateurIndisponibleServiceTest' -Dsurefire.failIfNoSpecifiedTests=false` : 22 tests (10 + 8 + 4), 3 exécutions consécutives vertes, 0 flaky. Ordre aléatoire JUnit non activé (non configuré dans le projet) ; chaque test nettoie ses données. Couverture de branches et mutation (PIT) non mesurées : les classes de la zone sont minuscules (2 branches de décision dans le service, résolution par code dans `PatientMigrator`) et les scénarios en parcourent toutes les branches.

## 10. État final (2026-10-14) : module retiré

`backend/bc-gmao` est **supprimé** (25 classes de production, 5 tests ; retiré de `backend/pom.xml` (module et dependencyManagement), `platform/pom.xml`, `bc-migration/pom.xml` et du `Dockerfile`). Plus aucun fichier hors `contexts/gmao` ne référence `com.hemodialyse.backend.domain.gmao` ; seul le **littéral** `domain.gmao.model…` du détail des 3 erreurs 400 (`EquipementControleur`, `IntervenantControleur`, `PieceJointeControleur`) est conservé (texte renvoyé aux clients).

Réalisation, dans l'ordre du plan §7 :
1. Nouveau port publié `publishedlanguage/GenerateursDuCentre` (`duCentre(centreId)` : id, code, salle, statut, centre ; tous statuts, supprimés exclus, par code), adapté par `GenerateursDuCentreJpa` (lecture de `gmao_equipements`), `GenerateursInstalles` non élargi. Contrat `PilotageContrat` (+1 scénario, 12) contre le double, JPA/H2 et PG16.
2. `bc-migration` : port propre `GenerateursDeReprise` (`GenerateurRepris(id, code, salleId)`), `PatientMigrator` sans dépendance GMAO, ACL `infrastructure/acl/gmao/GenerateursDeRepriseGmao` dans `platform` câblée par `MigrationConfig` ; `InMemoryMigrationPorts.Generateurs`.
3. `GenerateurIndisponibleService.signaler(type, centre, equipement, code, statut, précédent)` travaille sur des valeurs ; `GmaoEvenementsPlanificationListener` les lit dans l'événement du module (plus de `Equipement.reconstruct`).
4. Fixtures G1/G2/G3 semées par SQL ; `ModelesDePersistanceAlignesIntegrationTest` compare le modèle du module à la description figée des tables. Supprimés : 5 adaptateurs, 7 entités, 7 dépôts JPA legacy, `InterventionResponse`, `LigneCoutResponse`, `TerminerInterventionRequest`, `InterventionCostQueriesIntegrationTest`, `GmaoUtcSerializationIntegrationTest` (code mort ; coûts et aller-retour couverts par `PilotageContrat` et `InterventionContrat`), `scripts/pit-g3-equipement.sh` (ciblait le legacy).

Parité : 10 + 8 scénarios de caractérisation et 4 tests unitaires de `GenerateurIndisponibleServiceTest` verts contre le nouveau code, sans changer un golden. KB-GMAO-80 à 83 **reproduits** à l'identique ; correction à planifier après retrait (ADR 0019/0024), sur décision explicite.
