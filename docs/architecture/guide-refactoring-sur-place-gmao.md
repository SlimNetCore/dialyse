# Guide : refactoring sur place du contexte GMAO

Décision : ADR 0024 (`.ddd/architecture/adr/`). Contexte refait : plans de maintenance (G1), intervenants (G2), cycle de vie de l'équipement (G3) et interventions de maintenance (G4, temps 1 : les neuf routes existantes ; temps 2 : tâches et proposition de réforme, nouvelles fonctionnalités), pièces jointes d'intervention (G5) et consultation et pilotage (G6, temps 1 : fiche, disponibilité, indicateurs, chronologie, bon, statistiques, source de la direction) ; les autres slices GMAO suivront la même recette.

## 1. Objectif
Un vrai DDD simple et lisible, sans échafaudage : un domaine pur qui porte les règles métier, des cas d'usage qui orchestrent, des adaptateurs qui parlent à la base et à HTTP. Le stockage (tables `gmao_plans_maintenance`, `gmao_intervenants`, `gmao_equipements`, `gmao_equipement_statut_historique`, `gmao_interventions`, `gmao_lignes_cout_intervention`, `gmao_rectifications_intervention`, `gmao_documents_intervention`) et les URLs (`/api/v1/gmao/plans-maintenance`, `/api/v1/gmao/intervenants`, `/api/v1/gmao/equipements`, `/api/v1/gmao/interventions`, `.../interventions/{id}/documents`) ne changent pas : le reste de l'application et le frontend ne voient aucune différence, sauf les écarts décidés (section 5).

## 2. Carte des dossiers
`backend/contexts/gmao/src/main/java/com/hemodialyse/backend/contexts/gmao/`

| Dossier | Rôle | Dépend de |
|---|---|---|
| `domain/` | agrégats `PlanMaintenance`, `Intervenant`, `Equipement`, `Intervention` (avec `LigneDeCout` et `Rectification`), `PieceJointe` (satellite, avec `FormatReconnu` et `NomDeFichier`), objets-valeur, règles (R1 à R8, R10 à R27), événements de domaine, ports de repository, service de domaine `UniciteDuCodeEquipement` | rien |
| `application/` | cas d'usage (`EtablirPlan`, `EnregistrerIntervenant`...), ports de lecture, `SessionCourante`, `Horloge`, `PublicateurDEvenements` | domain |
| `infrastructure/` | adaptateurs JPA sur les tables existantes (`persistence/`), publication des événements (`evenements/`), lecture des équipements et de la session (`acl/`) | application, domain |
| `interfaces/http/` | contrôleurs REST, DTO JSON | application, domain |
| `configuration/` | `GmaoConfiguration` : assemble les objets | tout |

Le test `ArchitectureHexagonaleTest` (ArchUnit) et `tools/ddd-scan/check-arch.ts` font échouer le build si une dépendance va dans le mauvais sens.

## 3. Couches, en pratique
- Une règle métier est une méthode de l'agrégat nommée par l'intention (`plan.suspendre(...)`, `intervenant.desactiver(...)`), pas un setter.
- Un cas d'usage = une classe, une transaction, un agrégat. Il charge, appelle l'agrégat, enregistre, publie les événements levés.
- Les adaptateurs de persistance mappent explicitement entité JPA et agrégat ; l'entité reprend **exactement** les noms de colonnes historiques (y compris `tachesaeffectuer`, KB-GMAO-21). Aucune migration de schéma.
- Les contrôleurs sont des `@RestController` ordinaires : ils passent par la chaîne de filtres commune (sécurité, licence, rôle, audit).
- Événements : chaque cas d'usage publie ses événements dans la transaction (registre Spring Modulith, table `event_publication`). Un consommateur minimal (`JournalDesEvenements`) journalise et acquitte, ce qui évite l'accumulation de publications incomplètes. Depuis G3, un premier consommateur métier existe côté `platform` : `GmaoEvenementsPlanificationListener` (`infrastructure/acl/gmao`) écoute les événements du parc (`EquipementMisHorsService`, `EquipementReforme`, `EquipementRemisEnService`...), appelle `GenerateurIndisponibleService.signaler` (alerte « générateur indisponible », règle inchangée). Il est livré après commit et de façon asynchrone ; les tests qui observent ses effets attendent que le registre `event_publication` soit vide. Le cache `ref.generateurs`, lui, est évincé de façon **synchrone** par `@CacheEvict` sur les méthodes d'écriture d'`EquipementControleur` (au retour de la méthode : après le commit du cas d'usage, avant la réponse HTTP), pour que le wizard ne lise jamais l'ancien statut. Les événements ne sont pas versionnés : les événements de domaine sont les événements publiés et l'écouteur dépend de leurs classes ; à versionner quand un 2e consommateur ou une séparation de service apparaîtra. Les autres futurs contextes (Pilotage, Notifications) s'abonneront de la même façon. Les métriques ont été retirées à la demande de l'utilisateur.
- Démarrer ou terminer une intervention (G4) change l'état de l'équipement par le langage publié `EtatEquipementPort` (module GMAO, `publishedlanguage/`), dans **la même transaction** que l'intervention, ses lignes de coût, l'historique des statuts et la publication des événements (KB-GMAO-56) ; le cas d'usage de l'équipement (`ConstaterEtat`) y participe sans transaction propre. `InterventionControleur` évince le cache `ref.generateurs` par `@CacheEvict` (démarrer, terminer), comme `EquipementControleur`. À la clôture, la nature et le tarif de l'intervenant sont lus par le port `IntervenantsDuCentre` (KB-GMAO-46) ; l'événement `InterventionTerminee` est publié par l'outbox et n'a pas encore de consommateur.

## 4. Comment le filet de caractérisation prouve l'équivalence
1. Avant de toucher au code, des tests HTTP décrivent le comportement **actuel** du legacy, bugs compris, scénario par scénario, avec un fichier golden lisible par scénario (`backend/platform/src/test/resources/characterization/gmao/`). Valeurs volatiles (horloge, UUID générés) normalisées.
2. Ces tests sont verts sur le legacy.
3. Le nouveau code remplace le legacy derrière les mêmes URLs ; **la même suite** doit rester verte : G1 36 scénarios, G2 39 scénarios, G3 54, G4 67 (68 moins `taches_aucune_route`, caduc depuis le temps 2), G5 48 tests (dont 11 scénarios aux goldens `*_nouveau_code` pour les écarts décidés) ; les nouvelles fonctionnalités ont leurs propres goldens `*_nouveau_code` (12 scénarios). Un écart = un défaut du nouveau code à corriger, sauf décision tracée.
4. Une exigence importante : on ne modifie jamais un golden pour « faire passer » un test ; un golden change seulement avec une décision écrite.

Lancer : `./mvnw -o -pl platform -am test -Dtest='PlanMaintenanceHttpCharacterizationIntegrationTest,IntervenantHttpCharacterizationIntegrationTest,EquipementHttpCharacterizationIntegrationTest,Intervention*CharacterizationIntegrationTest,Documents*CharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` depuis `backend/` (G3 : 54 scénarios ; G4 : 68 tests en trois classes).

## 5. Ce qui est reproduit, ce qui est corrigé
Par défaut, les bugs du legacy sont **reproduits** (ADR 0019 ; liste dans `.ddd/safety-net/known-bugs.md`) pour ne pas mélanger refactoring et changement de comportement. Écarts décidés :
- KB-GMAO-14 : établir un plan pour un équipement inconnu répond 400 `EQUIPEMENT_INCONNU` (le legacy répondait 500).
- KB-GMAO-22 : `GET /intervenants/{id}` répond 200 pour un intervenant du centre, 404 sinon (le legacy répondait 500).
- KB-GMAO-15 (décision du 2026-10-12) : un plan d'un autre centre est traité comme inexistant (404 sur les `GET`, 400 « Plan non trouvé » sur `executer`, `desactiver`, `reactiver`) ; le legacy le lisait et le modifiait.
Ces cas ont un golden `*_nouveau_code`. G3 ajoute :
- KB-GMAO-32 : la réforme d'un équipement exige le rôle `ADMIN` **et** l'habilitation `GMAO_REFORME` (le legacy se contentait de l'habilitation) ; golden `reformer_habilitation_seule_sans_admin_nouveau_code`.
- KB-GMAO-38 : l'équipement, son historique de statuts et la publication des événements s'écrivent dans une seule transaction (écart bénin, le legacy faisait trois écritures séparées).
G4 ajoute (décisions du 2026-10-12) :
- KB-GMAO-52 : la rectification d'une intervention exige le rôle `ADMIN` **et** l'habilitation `GMAO_RECTIFICATION` (le legacy se contentait de l'habilitation) ; golden `rectifier_habilitation_seule_sans_admin_nouveau_code`. Les utilisateurs qui n'ont que cette habilitation perdent l'accès, comme pour la réforme.
- KB-GMAO-56 : l'intervention, l'équipement (statut et historique) et la publication des événements s'écrivent dans une seule transaction (écart bénin, comme KB-GMAO-38).
- Reproduits à dessein, correction à planifier (date à fixer par l'utilisateur) : KB-GMAO-46 (le tarif et la nature de l'intervenant sont lus à la clôture, aucun instantané) et KB-GMAO-44 (échelles des montants : quantité 3 décimales, prix 2, montant jusqu'à 5 décimales, tels que le legacy les renvoie). Nouveau : l'événement `InterventionTerminee`. Les tâches (KB-GMAO-55) restent pour le temps 2.
- **Temps 2 (2026-10-12), nouvelles fonctionnalités demandées par l'utilisateur, sans équivalent legacy** : tâches d'intervention (KB-GMAO-55, routes `POST /interventions/{id}/taches`, `.../taches/{tacheId}/demarrer`, `.../achever`, migration additive V36) et notification de proposition de réforme (KB-GMAO-57, `REFORME_PROPOSEE` pour `GMAO_REFORME`). Ce ne sont pas des écarts de parité : ils ont des goldens `*_nouveau_code` et sont **à valider par le métier** (statuts, limite de 30, habilitation, texte et destinataires).

G5 ajoute (pièces jointes d'intervention, décisions du 2026-10-12 ; détail, impacts et ancien comportement dans `.ddd/safety-net/known-bugs.md`, « Décisions G5 ») :
- Les octets restent **en base** (`bytea`) : même table `gmao_documents_intervention`, aucune migration, aucun fichier sur disque. `PieceJointe` est un agrégat satellite : la liste ne lit jamais la colonne des octets.
- KB-GMAO-59 : la **structure** du fichier est vérifiée (PDF : `%PDF-x.y` et `%%EOF` en fin ; PNG : blocs et sommes de contrôle, IEND en dernier ; JPEG : `FFD8FF` et `FFD9`) et l'extension du nom doit correspondre au format reconnu. Des fichiers aujourd'hui acceptés seront refusés (PDF sans marqueur de fin, JPEG tronqués, `photo.pdf` contenant un JPEG, `.exe`).
- KB-GMAO-60 : nom assaini (plus de `..`, de caractères de contrôle ni de sens d'écriture comme U+202E ou U+0085, 150 caractères sans couper un emoji). KB-GMAO-61 : nom et type MIME re-contrôlés à la liste et au téléchargement (`application/octet-stream` pour une pièce héritée hors des trois formats).
- KB-GMAO-64 : **ajouter et retirer une pièce sont refusés (422) sur une intervention annulée, terminée ou supprimée** ; on ne joint donc plus de justificatif après la clôture (rectifier l'intervention d'abord). Lister et télécharger restent permis.
- KB-GMAO-58 : le quota de 50 est tenu sous concurrence : l'intervention est verrouillée (`SELECT ... FOR UPDATE`) le temps de compter et d'insérer ; test de concurrence dans `RegistreDEvenementsPieceJointeTest`.
- Nouveaux événements `PieceJointeAjoutee` et `PieceJointeRetiree` (outbox, sans octets, sans consommateur). Reproduits : codes d'erreur transverses, audit POST/DELETE seul, ADMIN seul, 5 Mo exacts, doublons, suppression physique.

G6 ajoute (consultation et pilotage, temps 1, décisions du 2026-10-12 ; détail dans `.ddd/safety-net/known-bugs.md`, « Décisions G6 ») :
- Les lectures de fiche, disponibilité pour un patient, indicateurs, chronologie, statistiques et données du bon sont des **read models** du module (aucun agrégat) ; les trois calculs sont des **services de domaine purs**. Mêmes URLs, mêmes tables, mêmes réponses JSON (échelles hétérogènes reproduites).
- Le **rendu du bon** reste dans la plateforme derrière le port `ImpressionDuBon` défini par le module. Le tableau de bord et la capacité de la direction lisent leurs chiffres GMAO par `SyntheseGmaoDuCentre` et `GenerateursInstalles` (langage publié) ; le contexte Direction n'est pas extrait.
- KB-GMAO-69 : une intervention supprimée logiquement se lit comme un identifiant inconnu (400). KB-GMAO-72 : le seuil de réforme (0,60) est comparé au ratio **exact** (l'écran affiche 4 décimales, mais 0,59996 n'est plus recommandé). KB-GMAO-73 : un équipement **désactivé** n'est plus disponible pour un patient.
- Reproduits : GET du bon qui écrit la ligne de modèle de document (KB-GMAO-70), fiche sans filtre de centre (71), fuseau, édité par, codes d'erreur 400/403/500, échelles JSON, total d'équipements différent de la somme des statuts, événement MODIFIEE parasite, seuil global.

## 6. Ce qui reste du legacy, et pourquoi
**Plus rien** (G6 temps 2, 2026-10-14) : le module `backend/bc-gmao` est supprimé, avec les adaptateurs, entités et dépôts JPA legacy de la plateforme et les DTO `InterventionResponse`, `LigneCoutResponse`, `TerminerInterventionRequest`. Aucun fichier hors `contexts/gmao` ne référence plus `com.hemodialyse.backend.domain.gmao` ; seul le littéral `domain.gmao.model…` du détail de trois erreurs 400 est conservé (texte renvoyé aux clients).

Les derniers consommateurs de l'état d'un équipement passent par le langage publié du module : la reprise des patients lit `GenerateursDuCentre` par un port propre (`GenerateursDeReprise`) et une ACL ; l'alerte « générateur indisponible » reçoit les valeurs (type, centre, code, statut, statut précédent) de l'événement du module. Les fixtures de caractérisation G1 à G3 sèment en SQL ; `ModelesDePersistanceAlignesIntegrationTest` compare chaque modèle de persistance du module à la description figée de sa table.

Bugs reproduits à dessein, correction à planifier après retrait (ADR 0019/0024) : KB-GMAO-80 (alerte non dédoublonnée au rejeu), 81 (reprise acceptée sur un générateur hors service), 82 (compte rendu de reprise trompeur sur erreur), 83 (patient sorti sans date ignoré de l'alerte).

## 7. Refaire sur un autre projet
1. Cartographier (scan, glossaire) et choisir une petite slice verticale.
2. Écrire le filet de caractérisation HTTP et le rendre vert sur l'existant.
3. Écrire le domaine en TDD, puis les cas d'usage avec des faux en mémoire, puis les adaptateurs (même suite de contrat pour le faux et l'adaptateur réel), puis les contrôleurs.
4. Retirer le contrôleur d'origine, relancer le filet, consigner chaque écart.
5. Garder l'ancien code seulement tant qu'un consommateur le référence, et lister ce qu'il reste à retirer.
6. Rejouer sur une vraie base PostgreSQL jetable : `backend/scripts/gmao-pg16-rejeu.sh` (jamais le port 5432).
