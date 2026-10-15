---
status: draft
date: 2026-10-09
sources: [.ddd/migration/slices/gmao-g1-plans-maintenance.md]
---
# Seams — slice gmao-g1-plans-maintenance

Aucun seam appliqué ni demandé : `seamAllowed` reste vide, aucun fichier de production modifié.

## Seam optionnel (NON appliqué, accord utilisateur requis)
| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `backend/bc-gmao/.../domain/gmao/model/PlanMaintenance.java:55,64,120,126,134,153,166` (`UUID.randomUUID()`, `OffsetDateTime.now(ZoneOffset.UTC)`) | *Parameterize method* : surcharges `creer(..., Clock, Supplier<UUID>)` / `enregistrerExecution(user, Clock)` déléguant avec `Clock.systemUTC()` | permettrait de verrouiller exactement l'exécution MENSUELLE un 31 (`plusMonths`), BIMENSUELLE, et les bornes `<=` de `/en-retard` | modification de legacy hors câblage ; faible (comportement par défaut inchangé) |

Sans ce seam, la clôture de fin de mois (31 -> 28/30) et la borne exacte de retard ne sont pas verrouillées (voir limites dans `gmao-g1-plans-maintenance.md`).

# Seams — slice gmao-g2-intervenants

Aucun seam appliqué ni demandé : `seamAllowed` reste vide, aucun fichier de production modifié. Aucun non-déterminisme bloquant (le seul aléa est `UUID.randomUUID()` dans `Intervenant.creer`, normalisé par alias ; aucune horloge dans les réponses). Seule modification hors production : `GoldenMaster` (test G1) rendu `public` avec une surcharge `verify(Path dir, …)` pour réutiliser le moteur d'approbation.

Seam optionnel (NON appliqué, non nécessaire) : `Intervenant.java:21-30` (`UUID.randomUUID()`) -> *parameterize method* avec `Supplier<UUID>` ; gain nul pour le filet HTTP (alias déjà stables).

# Seams — slice gmao-g3-cycle-vie-equipement

Aucun seam appliqué ni demandé : `seamAllowed` reste vide, aucun fichier de production modifié (ni `GenerateurIndisponibleService`, ni `InterventionEquipementStatutService`). Alerte observée par la table durable `notification_evenement` (aucun espion), éviction du cache par `CacheManager` (`ref.generateurs`), instants et UUID normalisés dans les goldens.

## Seams optionnels (NON appliqués, accord utilisateur requis)
| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `backend/bc-gmao/.../domain/gmao/model/Equipement.java:65,78,157,173,188,210,227,239-240` ; `EquipementStatutHistorique.java:49-51` (`UUID.randomUUID()`, `OffsetDateTime.now(UTC)`) | *Parameterize method* : surcharges avec `Clock`/`Supplier<UUID>`, comportement par défaut inchangé | verrouiller exactement `dateModification`, `changed_at`, l'horodatage inséré dans les observations, la durée d'indisponibilité en valeur absolue (aujourd'hui seulement relationnelle) | modification de legacy hors câblage ; faible |
| `GenerateurIndisponibleService.java:44-51` (alerte dont l'échec est avalé) | *Extract interface* sur `GenerateurImpactPort`/`NotificationService` ou constructeur injectable (déjà injectable : un `@MockitoBean` de test suffit sans toucher la production) | provoquer l'échec de l'alerte pour verrouiller « l'alerte qui échoue ne fait pas échouer le changement de statut » | aucun côté production ; nécessite un contexte Spring de test distinct (non fait ici) |
| `EquipementRestController.java:192-196` (3 écritures non transactionnelles) | aucun seam mécanique : l'atomicité est une décision de conception | verrouiller l'état en cas d'échec du journal | hors périmètre du filet (voir KB-GMAO-38) |

# Seams — slice gmao-g4-interventions

Aucun seam appliqué ni **nécessaire** : `seamAllowed` reste vide, aucun fichier de production modifié.

**Pourquoi l'horloge n'est pas un obstacle** : `Intervention.java` appelle `OffsetDateTime.now(UTC)` pour `dateCreation`, `demarreLe`, `clotureLe`, `annuleLe`, `dateModification`, les rectifications et la chronologie, mais **ni la durée valorisée ni les montants n'en dépendent** : `appliquerTarifIntervenant` calcule `Duration.between(dateDebut, dateFin)` à partir de dates **fournies par le client** (création et clôture) ; heures, arrondi HALF_UP, montants et lignes sont donc exacts et reproductibles sans seam. Les instants posés par `now()` sont normalisés en `<HORLOGE>` (clés `dateCreation`, `dateModification`, `at` de la chronologie) ; `enRetard`/`echeanceDepassee` sont rendus déterministes par des dates fixes dans le passé (2026-03) ou le futur (2099). `UUID.randomUUID()` : alias `<UUID_GENERE_n>`.

## Seam optionnel (NON appliqué, accord utilisateur requis — non nécessaire au filet actuel)
| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `backend/bc-gmao/.../domain/gmao/model/Intervention.java:117,191,227,254,301,328,354,439` ; `TacheIntervention.java:33,59` (`OffsetDateTime.now(UTC)`) ; `Intervention.java:108,303` (`UUID.randomUUID()`) | *Parameterize method* : surcharges avec `Clock`/`Supplier<UUID>`, comportement par défaut inchangé | verrouiller exactement `clotureLe`, `dateModification`, l'ordre horodaté de la chronologie (évènement `MODIFIEE` parasite, KB-GMAO-50) et les instants de `gmao_rectifications_intervention` ; **inutile pour l'argent** | modification de legacy hors câblage ; faible. Dans la refonte, `Horloge` (module `contexts/gmao`) remplace ces appels : les goldens ne changent pas (instants déjà normalisés) |
| `InterventionRestController.java:131-134,161-163` (appliquerEtat avant save, KB-GMAO-56) | aucun seam mécanique : l'atomicité est une décision de conception | verrouiller l'état en cas d'échec de `save` | hors périmètre du filet |

# Seams — slice gmao-g5-pieces-jointes

Aucun seam appliqué ni **nécessaire** : `seamAllowed` inchangé, aucun fichier de production modifié. `OffsetDateTime.now(UTC)` et `UUID.randomUUID()` dans `DocumentIntervention.creer` ne changent aucune valeur à figer : `ajouteLe` est normalisé en `<HORLOGE>`, les ids en alias, et l'ordre de la liste est rendu déterministe en posant `ajoute_le` en base.

## Seams optionnels (NON appliqués, accord utilisateur requis, non nécessaires)
| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `backend/bc-gmao/.../domain/gmao/model/DocumentIntervention.java:36-37` | *Parameterize method* : surcharge avec `Clock`/`Supplier<UUID>` | verrouiller l'horodatage exact et l'ordre des ex aequo de la liste (KB-GMAO-67) | modification de legacy hors câblage ; faible |
| `InterventionDocumentRestController.java:56-58` (compte puis insère) | aucun seam mécanique | verrouiller la course du quota (KB-GMAO-58) : exige un test concurrent, non déterministe | hors périmètre du filet |

# Seams — slice gmao-g6-consultation-pilotage

Aucun seam appliqué ni **nécessaire** : `seamAllowed` inchangé (vide), aucun fichier de production modifié.

**Pourquoi l'horloge n'est pas un obstacle** : les lectures de G6 lisent « maintenant » (fenêtre glissante de 12 mois de la fiche et des indicateurs, retards des statistiques, horodatage d'édition du bon), mais le filet place toutes les dates semées **relativement à l'instant du test** (intervalles clos, dans la fenêtre, en minutes entières : valeurs exactes) et **masque** les instants relatifs dans les goldens (`<DATE>`) ; les cas dont le résultat dépend de l'instant d'exécution (panne ouverte jusqu'à maintenant, durée d'une intervention en cours) sont vérifiés par encadrement de ±2 minutes ; le pilotage direction prend des dates de période fixes. L'horodatage « Édité le » du PDF est masqué dans le texte extrait.

## Seams optionnels (NON appliqués, accord utilisateur requis, non nécessaires)
| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `EquipementFicheRestController.java:66`, `InterventionSuiviQueryService.java:51`, `GmaoStatsRestController.java:56-57`, `DirectionGmaoQueryService.java:68,80`, `DirectionCapaciteQueryService.java:73,83` (`OffsetDateTime.now(UTC)` / `LocalDate.now()`) | *Parameterize constructor* : injecter un `Clock` (valeur par défaut `Clock.systemUTC()`), comportement inchangé | figer la fenêtre de 12 mois et supprimer l'encadrement de ±2 minutes ; verrouiller les retards à la seconde près | modification de legacy hors câblage ; faible ; inutile si la refonte remplace ces classes |
| `BonInterventionReportService.java:167` (`OffsetDateTime.now()` de l'horodatage d'édition) | idem (`Clock`) | golden du PDF complet octet par octet (aujourd'hui : texte extrait, horodatage masqué) | le PDF contient aussi des métadonnées de création : un hash du binaire resterait instable ; le texte extrait suffit |

# Seams — filet G6 temps 2 (`gmao-g6-temps2-bc-gmao`, 2026-10-14)

Aucun seam appliqué ni nécessaire ; `seamAllowed` vide, aucun fichier de production modifié.

| Fichier : ligne | Refactoring mécanique | Gain | Risque |
|---|---|---|---|
| `GmaoEvenementsPlanificationListener.java` (`Equipement.reconstruct` à 18 arguments) | aucun : sera remplacé par des valeurs au retrait (étape 3 du plan) | néant | néant |
| `GenerateurIndisponibleService.java:44-51` (échec d'alerte avalé) | aucun (voir seam G3) ; couvert par le test unitaire existant `should_never_fail_the_status_change_when_the_alert_cannot_be_built` | — | — |

# Seams — slice comptabilite-c1-referentiel (2026-10-14)

Aucun seam appliqué ni **nécessaire** : `seamAllowed` inchangé (vide), aucun fichier de production modifié. Les routes
C1 n'écrivent ni horloge ni aléa dans leurs réponses ; `UUID.randomUUID()` n'alimente que des clés techniques de lignes
(`comptes_comptables.id`, `journaux_comptables.id`) jamais exposées ; les listes sont triées côté SQL
(`ORDER BY numero`, `code`, `nom, code`) ou dans l'ordre de déclaration (journaux par défaut, plan SCF).

## Seams optionnels (NON appliqués, accord utilisateur requis, non nécessaires)

| Fichier : ligne                                                                                                   | Refactoring mécanique                                                              | Gain                                                                                                                                                 | Risque                                 |
|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| `PlanComptableJdbcAdapter.java:38-59`, `ComptePayeurJdbcAdapter.java:37-53` (tri `ORDER BY` collation PostgreSQL) | aucun seam : rejeu sur PostgreSQL 16 (`backend/scripts/gmao-pg16-rejeu.sh` adapté) | verrouiller l'ordre `numero` (alphabétique : `10` < `4456` < `9`) et `nom, code` sous collation PostgreSQL, qui peut différer de H2 (casse, accents) | aucun (test seulement) ; Docker requis |

# Seams — slice comptabilite-c2-mapping-tva (2026-10-15)

Aucun seam appliqué ni **nécessaire** : `seamAllowed` inchangé (vide), aucun fichier de production modifié. Les routes
`/mapping` et `/regles-tva` n'écrivent ni horloge ni aléa dans leurs réponses ; `UUID.randomUUID()` / `RANDOM_UUID()`
n'alimentent que les clés techniques de `mapping_comptable` et `tva_types` (jamais exposées, jamais lues). Deux
non-déterminismes **legacy** sont contrôlés sans seam : (1) `Map.of` des réponses de `/regles-tva` (ordre des clés
variable d'une JVM à l'autre) : le golden trie les clés ; (2) `Map.copyOf` des journaux du mapping, parcouru par le
contrôle `JOURNAL_INCONNU` : l'opération citée dans le message n'est vérifiée que par motif quand plusieurs opérations
sont fautives (KB-CPT-15). Le cache `tva-types-list` de la Facturation (6 h) est vidé au début de chaque test
(`CacheManager` réel, pas de mock).

## Seams optionnels (NON appliqués, accord utilisateur requis, non nécessaires)

| Fichier : ligne                                                                                                            | Refactoring mécanique                                                                                                                                                                                                                                     | Gain                                                                                           | Risque                                                                          |
|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `ParametrageFiscalJdbcAdapter.java:70` (`RANDOM_UUID()` dans l'INSERT de `POST /regles-tva`)                               | **aucun seam** : le défaut (KB-CPT-21, fonction inexistante sur PostgreSQL) se corrige à la refonte (identifiant généré en Java) ; le filet H2 ne peut pas le verrouiller, le constat PostgreSQL 16 est `backend/scripts/comptabilite-c2-pg16-constat.sh` | prouver le comportement de production sans modifier le legacy                                  | aucun (script jetable, port non standard, 5432 refusé)                          |
| `ComptabiliteService.java:161` (`mapping.journaux().forEach` sur `Map.copyOf`)                                             | aucun : l'ordre stable (par `OperationComptable.values()`) sera celui du nouveau code                                                                                                                                                                     | message `JOURNAL_INCONNU` déterministe quand plusieurs opérations sont fautives                | modification de legacy hors câblage ; inutile si la refonte remplace ce service |
| `ParametrageFiscalJdbcAdapter.java:30-47` (`findActiveAt` : `ORDER BY date_debut_validite DESC LIMIT 1`, égalité de début) | aucun : tri total (début, puis `created_at`, `id`) à la refonte                                                                                                                                                                                           | verrouiller le choix entre deux règles de même début (aujourd'hui indéterminé, non verrouillé) | idem                                                                            |

# Seams — slice comptabilite-c3-ecritures (2026-10-15)

Aucun seam appliqué ni **nécessaire** : `seamAllowed` inchangé (vide), aucun fichier de production modifié. Les routes
d'écritures n'écrivent pas d'horloge dans leurs réponses (`cloturee_at`, `created_at`, `updated_at` ne sont jamais
exposés : seul « renseigné ou non » est testé) ; les identifiants aléatoires des écritures et lignes générées sont
normalisés par le golden (alias par ordre d'apparition, lignes triées par compte et montants car `@OrderBy("id")` suit
des UUID aléatoires) ; les écritures de fixture ont des identifiants fixes (ordre des lignes déterministe) ; «
aujourd'hui » n'intervient dans aucune fixture (dates fixes mars/avril/septembre 2026, 2025-12-31).

## Seams optionnels (NON appliqués, accord utilisateur requis, non nécessaires)

| Fichier : ligne                                                                                                         | Refactoring mécanique                                                                                                                                                                       | Gain                                                                                                                                  | Risque                                                                                                        |
|-------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `EcritureComptableJpaAdapter.java:93-108` (`nextNumeroPiece` : lecture du plus grand numéro puis écriture, sans verrou) | aucun seam : le verrou est la correction elle-même (décision 1 du 2026-10-14), à écrire dans le nouveau code ; la course reste observable seulement par un test concurrent non déterministe | verrouiller la course B1/B2 (deux numérotations simultanées)                                                                          | test concurrent instable : non verrouillé, constaté par la lecture du code et l'absence de contrainte en base |
| `EcritureComptableJpaAdapter.java:85-91` (`findForExport` sans `ORDER BY`)                                              | aucun : tri total (date d'écriture, numéro de pièce, identifiant de ligne) à la refonte                                                                                                     | ordre des écritures du fichier d'export déterministe (aujourd'hui non défini : le golden trie les lignes de fichiers multi-écritures) | modification de legacy hors câblage ; inutile si la refonte remplace l'adaptateur                             |
| `PeriodeComptableJpaAdapter.java:47` (`OffsetDateTime.now()` de `cloturee_at`)                                          | extraire une `Clock` injectée                                                                                                                                                               | verrouiller la date de clôture exacte                                                                                                 | modification de legacy ; inutile : la colonne n'est jamais exposée par l'API                                  |
| `ComptabiliteRestController.java:129-132` (auteur = `req.userId()`)                                                     | aucun seam : décision 12 (auteur = utilisateur de la session)                                                                                                                               | —                                                                                                                                     | —                                                                                                             |
