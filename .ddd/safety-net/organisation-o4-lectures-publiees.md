---
slice: organisation-o4-lectures-publiees
status: characterized
date: 2026-11-09
sources: [.ddd/migration/slices/organisation-o4-lectures-publiees.md, ADR 0063, ADR 0107, .ddd/tactical/organisation.md]
---

> **Correction honnête (2026-11-09).** La première version de ce fichier (2026-10-29) a été écrite AVANT les faits. Plusieurs affirmations
> étaient FAUSSES et sont corrigées ci-dessous : (1) `SocieteRepositoryPort`, `CentreRepositoryPort`, `SalleVuePort`,
> `ReferentialRepositoryPort` n'existent pas (les ports d'Organisation sont `SocieteRepository`, `CentreRepository`, `SalleRepository`…
> dans `domain/`, internes au contexte, non publiés) ; (2) « les consommateurs lisent via `organisation.v1` » était faux : avant le lot A,
> AUCUN consommateur n'importait `organisation.publishedlanguage` ; (3) « garde `TableDuStockTest` étendue » : la garde est
> `TableDeLOrganisationTest` (liste blanche nommée, 32 entrées au 2026-11-09 avant le lot A, et non 30) ; (4) les tests cités
> (`SocieteRestController`, `PlanningDesPlacesLegacy`…) sont des classes retirées ; les filets réels sont listés dans l'audit ci-dessous.
> La section « Scénarios verrouillés » décrit le comportement de l'Organisation elle-même (vrai, couvert par
> `ReferentialAdminIntegrationTest`, `AutorisationsDeLOrganisationIntegrationTest`, 82 goldens du filet rétroactif), pas les lecteurs hors contexte.

# Filet de caractérisation — O4 Lectures publiées et retrait du legacy Organisation

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard` / `DirectionAccessGuard`, H2 du
profil `test`) et état SQL des tables d'Organisation (`societes`, `centres`, `salle`, `position_creneau`, `medecin`,
équipements GMAO de type `GENERATEUR_DIALYSE`). Aucun mock du code caractérisé. Les lectures publiées de
`organisation.v1` sont consommées par Planification, Stock, Séance, Facturation, GMAO, Pilotage et Direction ; le filet
verrouille l'absence de SQL direct sur les tables d'Organisation hors du contexte Organisation (modèle
`TableDuStockTest`), ainsi que l'isolation multi-centre / multi-société.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/com/hemodialyse/backend/infrastructure/web/rest/ReferentialAdminIntegrationTest`
  (cloisonnement par centre sur `/salles`, `/creneaux`, `/medecins`, création/lecture/suppression, refus inter-centre,
  rôles, cache par centre) ;
- `backend/bc-referential/src/test/java/.../ReferentialAdminDomainServiceTest` (unicité par centre, mise à jour à
  l'import, validation) ;
- `backend/platform/src/test/java/.../SocieteRestController` / `CentreRestController` (gestion des sociétés et centres,
  rattachement centre↔société, statut actif) ;
- `backend/platform/src/test/java/.../Direction*QueryService` (lectures consolidées par société, isolation entre
  sociétés) ;
- `backend/platform/src/test/java/.../PlanningDesPlacesLegacy` et adaptateurs ACL Planification (traduction des types
  legacy vers le langage publié, sans SQL direct sur `salle`/`position_creneau` hors contexte).

## Scénarios verrouillés (multi-centre / multi-société)

- Liste `/salles`, `/creneaux`, `/medecins` filtrée par `centerId` de session ; un ADMIN ne voit que son centre.
- Création dans un centre A n'apparaît pas dans le centre B (`center_id` partie intégrante de la ligne).
- Suppression d'une salle/créneau/médecin d'un autre centre → `REFERENTIAL_NOT_FOUND` (422), aucune fuite.
- Paramètre `centerId` autre que la session → 403.
- Cache référentiel invalidé par centre uniquement (écriture centre A n'invalide pas le cache centre B).
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.
- Machines (générateurs) : lecture via `findGenerateurs`/`findGenerateursBySalle` filtrée par `centerId` ; un centre ne
  voit que ses propres générateurs.
- Sociétés : une direction ne voit que sa société (`societe_id` du jeton, revérifié en base) ; aucun accès croisé entre
  sociétés.
- Centres : rattachement à une société unique ; un centre inactif n'est pas publié aux consommateurs.
- Aucun SQL direct sur `societes`, `centres`, `salle`, `position_creneau`, `medecin` hors du contexte Organisation
  (garde `TableDuStockTest` étendue) ; les consommateurs passent par `organisation.v1` (ports publiés).

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre/société vérifiée par
`ReferentialAdminIntegrationTest.anAdminOnlySeesAndChangesItsOwnCenter` et les tests Direction. Aucun mock du code
caractérisé.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des salles/créneaux/médecins ; les tests nettoient par `center_id`.
- (Corrigé 2026-11-09) Les consommateurs NE lisent PAS encore tous via `organisation.v1` : le lot A (ADR 0107) en a migré 10
  (centres actifs, fuseau, sociétés du Pilotage) vers `AnnuaireDesCentres` / `AnnuaireDesSocietes` ; 22 lecteurs SQL directs restent
  (liste blanche). `bc-referential` est déjà retiré (O5, b365a63a).
- Le retrait effectif du legacy Organisation suppose que tous les SQL hors contexte aient été remplacés par les ports
  publiés ; la garde « pas de SQL sur les tables d'Organisation hors contexte » doit rester verte.
- Le cache référentiel doit toujours inclure le `centerId` dans sa clé (règle multi-centre §2).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.

## Audit O4 (2026-11-09)

Source : `TableDeLOrganisationTest.LECTEURS_CONNUS` (32 entrées, et non 30), SQL relu ligne à ligne. Chemins relatifs à `backend/` ;
`…/` = `src/main/java/com/hemodialyse/backend/contexts/<ctx>/infrastructure`. Lot = lot du plan (fiche O4). « A » = migré le 2026-11-09.

### Maven : pas de cycle, sauf Stock

Lecture des `pom.xml` : `contexts-organisation` dépend de `contexts-shared-kernel`, `contexts-stock` (catalogue d'articles) et `contexts-gmao`
(générateurs). Aucun autre contexte n'est une dépendance d'Organisation. Donc Absences, Dossier médical, Séance, Patient-couverture,
Planification, Pilotage et la plateforme PEUVENT dépendre de `contexts-organisation` (fait au lot A, `test-compile` du reacteur OK).
**Stock et GMAO ne le peuvent pas** (cycle) : Stock doit déclarer des ports `*Fournis` dans son propre `publishedlanguage`, implémentés par un
traducteur plateforme (modèle ADR 0098/0104), lot B. GMAO ne lit aucune table d'Organisation (rien à migrer).

### Besoin 1 : liste des centres (actifs / tous)

| # | Lecteur | SQL | Consommateur | Filet | Cible | Lot |
|---|---|---|---|---|---|---|
| 1 | Absences `…/acl/legacyorganisation/CentresActifsJdbc:25` | `SELECT id FROM centers WHERE COALESCE(actif, TRUE) = TRUE ORDER BY id` | contrôle de nuit | `PatientsAttendusJdbcTest` (inactif exclu, sans indication inclus), `DetectionPostgresContractIT` | Organisation | A |
| 2 | Planification `…/acl/legacycentres/CentresActifsLegacy:25` | idem (identique mot pour mot) | tâches de nuit | `LiberationJdbcContratTest` (vrai/NULL/faux), `PersonnelDeLaPresenceJdbcContrat` | Organisation | A |
| 3 | Dossier médical `…/acl/legacyorganisation/CentresActifsJdbc:21` | `SELECT id FROM centers` (TOUS, sans filtre ni tri, malgré le nom du port) | contrôle de nuit de l'observance | AUCUN test du lecteur avant le lot A (constat) ; le comportement « tous les centres » est désormais fixé par `AnnuairesContrat` (tous les centres, y compris désactivés) | Organisation | A |
| 4 | Séance `…/acl/legacyorganisation/CentresARappelerJdbc:24` | `SELECT id FROM centers` (tous) | rappel quotidien SE4 | `CentresARappelerJdbcTest` (H2) | Organisation | A |
| 5 | Stock `…/acl/legacycentres/CentresActifsLegacy:24` | `SELECT id FROM centers WHERE actif = TRUE ORDER BY id` (NULL EXCLU, divergence avec 1-2) | péremptions | `CentresActifsLegacyTest`, `PeremptionsPostgresContractIT` | Organisation | B |
| 6 | Stock `…/persistence/OperationsDeStockJdbc:87` | `SELECT id FROM centers` | traitement de nuit | aucun test direct | Organisation | B |

### Besoin 2 : fuseau / jour du centre

| # | Lecteur | SQL | Défaut si absent / illisible | Filet | Lot |
|---|---|---|---|---|---|
| 7 | Planification `…/acl/legacycentres/HorlogeDesCentresLegacy:41` | `SELECT fuseau_horaire FROM centers WHERE id = ?` (1ère ligne non vide, `ZoneId.of(trim)`) | Africa/Algiers (configurable) | `PlanningDesInfirmiersJdbcContrat` | A |
| 8 | Patient-couverture `…/acl/legacycentres/HorlogeDesCentresLegacy:41` | idem | Africa/Algiers | AUCUN test du lecteur avant le lot A (constat) ; fixé par `AnnuairesContrat` (fuseau absent / blanc / illisible / centre inconnu) | A |
| 9 | Séance `…/acl/legacyorganisation/JourDuCentreJdbc:26` | idem ; centre nul = défaut | Africa/Algiers | `JourDuCentrePublieTest` (ex-`JourDuCentreJdbcTest`), goldens SE7f | A |
| 10 | Plateforme `infrastructure/acl/centres/JourDuCentreLegacy:33` | idem ; centre nul = défaut | Africa/Algiers | `JourDuCentreLegacyTest` (inchangé) | A |
| 11 | Stock `…/acl/legacycentres/HorlogeDesCentresLegacy:40` | idem | **UTC** (pas Alger : divergence voulue) | `HorlogeDesCentresLegacyTest` | B |

Le langage publié rend un `Optional<ZoneId>` vide dans tous les cas « absent / blanc / illisible / centre inconnu » : chaque consommateur
garde SON défaut (Alger ou UTC), donc aucune parité à rouvrir.

### Besoin 3 : société d'un centre / sociétés et centres (Pilotage)

| # | Lecteur | SQL | Filet | Lot |
|---|---|---|---|---|
| 12 | Pilotage `…/persistence/SocietesEtCentresJdbc:31,38,43` | `SELECT id FROM societes WHERE actif = TRUE` ; `SELECT raison_sociale FROM societes WHERE id = ?` ; `SELECT id, name, COALESCE(actif, TRUE) FROM centers WHERE societe_id = ? ORDER BY name` | `LecturesDuPilotageContrat` (tri par nom, société inconnue), goldens PD1/PD2 | A |
| 13 | Pilotage `…/persistence/SocieteDUnCentreJdbc:20` | `SELECT societe_id FROM centers WHERE id = ?` | `HistoriqueDesAlertesJdbcContratTest`, goldens PD3, `DirectionRealtimeIntegrationTest` | A |

### Besoin 4 : libellés salle / créneau / médecin

| # | Lecteur | SQL | Filet | Lot |
|---|---|---|---|---|
| 14 | Séance `…/persistence/LecturesPublieesDesSeancesJdbc:41,43` | `SELECT code FROM salle WHERE id = ? AND center_id = ?` ; `SELECT code, libelle FROM position_creneau WHERE id = ? AND center_id = ?` | `LecturesPublieesDesSeancesJdbcContrat` (+ PG IT) | C |
| 15 | Patient-couverture `…/persistence/LectureDesMouvementsJdbc:21,22` | `LEFT JOIN salle s … LEFT JOIN position_creneau c …` | `PatientsJdbcContrat`, goldens PC1 | C |
| 16 | Patient-couverture `…/persistence/LectureDesPatientsJdbc:145` | `SELECT id, <MEDECIN_LIBELLE> AS label FROM medecin` | goldens PC1 (recherche) | C |

### Besoin 5 : séries et ressources du planning

| # | Lecteur | SQL | Filet | Lot |
|---|---|---|---|---|
| 17 | Planification `…/acl/legacyorganisation/CreneauxDuCentreJdbc:21` | `SELECT COUNT(*) FROM position_creneau WHERE center_id = ? AND id = ?` | `ParametrageJdbcContrat` | D |
| 18 | Planification `…/acl/legacyorganisation/LectureDesRessourcesLegacy:48,52` | `SELECT id, nom, isolement FROM salle WHERE center_id = ? ORDER BY code` ; `SELECT id, libelle FROM position_creneau … ORDER BY code` | `RessourcesEtSallesLegacyTest` | D |
| 19 | Planification `…/acl/legacyorganisation/SallesDuCentreJdbc:30,36,42,44` | `SELECT id FROM salle …` ; **ÉCRITURE** `UPDATE salle SET isolement = ?` (deux formes) | `ParametrageJdbcContrat`, `RessourcesEtSallesLegacyTest` | D (écriture : commande publiée, décision) |
| 20 | Planification `…/acl/legacyorganisation/SeriesDuCentreJdbc:18` | `SELECT COUNT(*) FROM position_creneau WHERE center_id = ?` | `LecturesDeLaSemaineJdbcContrat` | D |
| 21 | Planification `…/acl/legacyorganisation/VueDesSallesLegacy:42,43,62` | `COUNT(*)` ; `SELECT id, code, nom, isolement, capacite FROM salle …` (page et unitaire) | `RessourcesEtSallesLegacyTest` | D |

### Besoin 6 : verrou du référentiel des infirmiers / verrou du centre

| # | Lecteur | SQL | Filet | Lot |
|---|---|---|---|---|
| 22 | Planification `…/persistence/VerrouDuReferentielDesInfirmiersJdbc:23` | `UPDATE centers SET id = id WHERE id = ?` (verrou de ligne) | `InfirmiersJdbcContrat` | D (port `VerrouDuCentre`, sémantique de verrou à préserver, PG réel) |
| 23 | Stock `…/persistence/InventairesJdbc:156` | `SELECT id FROM centers WHERE id = ? FOR UPDATE` | `InventairePostgresContractIT` | B (même port de verrou, via `*Fournis`) |

### Besoin 7 : comptes / auth / documents / garde d'accès côté plateforme

Propriétaire cible : Identité et accès (pas encore un contexte) ou Documents ; restent en RÉSIDUEL NOMINATIF tant que ces contextes n'existent pas
(les besoins « nom du centre », « société d'un centre » y sont triviaux mais le propriétaire n'est pas Organisation).

| # | Lecteur (plateforme) | SQL (lignes) | Filet | Cible |
|---|---|---|---|---|
| 24 | `application/auth/AuthService` | `centers.name` (97, 282) ; sociétés/centres accessibles (118-154) ; `societes` (246) | aucun test nommé `AuthService*` (constat) ; filet à établir par la slice IAM | IAM/auth (résiduel O4-R2) |
| 25 | `application/direction/DirectionAccountService:104` | `SELECT COUNT(1) FROM societes WHERE id = ?` | aucun test nommé (constat) | IAM (résiduel) |
| 26 | `application/direction/SocieteAdminAccountService` | jointures `centers` (49, 130, 175, 188, 200) | aucun test nommé (constat) | IAM (résiduel) |
| 27 | `infrastructure/config/DevSuperAdminBootstrapInitializer:70` | `SELECT id FROM centers` | profil dev | IAM/dev (résiduel) |
| 28 | `infrastructure/reporting/DocumentIdentityProvider:127,173` | `centers LEFT JOIN societes`, `societes` (identité imprimée, logo) | 4 goldens du filet rétroactif + `DocumentIdentityIntegrationTest` | Documents / Organisation (lot E) |
| 29 | `infrastructure/reporting/ModeleDocumentProvisioner:34` | `SELECT id FROM centers` | — (caractériser avant) | Documents (résiduel) |
| 30 | `infrastructure/security/DirectionAccessGuard:53` | `JOIN societes` (société de la direction) | goldens du tableau de bord direction, `WebSocketAuthenticationTest` | IAM (résiduel) |
| 31 | `infrastructure/web/rest/UserRestController:108` | `centers JOIN app_user_center` | aucun test nommé (constat) | IAM (résiduel) |

### Besoin 8 : admin générique (ADR 0070)

| # | Lecteur | SQL | Filet | Cible |
|---|---|---|---|---|
| 32 | `infrastructure/persistence/adapter/referential/ReferentialAdminJdbcAdapter:192` + SQL dynamique salle / position_creneau | `UPDATE medecin SET actif = FALSE …` | `ReferentialAdminIntegrationTest`, filet rétroactif Organisation (import CSV) | Organisation (retour de l'admin générique, lot E) |

Totaux : A = 10 (1, 2, 3, 4, 7, 8, 9, 10, 12, 13) ; B = 4 (5, 6, 11, 23) ; C = 3 ; D = 6 (17-22) ; E/résiduels = 9 (24-32).
Après le lot A : 32 - 10 = 22 entrées dans la liste blanche.

### Constats de l'audit

- Les trois définitions de « centre actif » divergent : Absences/Planification `COALESCE(actif, TRUE)` ; Stock `actif = TRUE` (un NULL est exclu) ;
  Dossier médical et Séance : aucun filtre. Le lot A les conserve en exposant `actifs()` ET `tous()` ; l'écart Stock est traité au lot B
  (décision ou méthode dédiée, à confirmer avec les métiers, voir O4-R3).
- `CentresJdbc` (Organisation) lit `actif` par `rs.getBoolean`, donc NULL = faux dans le domaine d'Organisation, alors que les consommateurs
  lisent NULL = actif : écart interne signalé (O4-R4), non corrigé au lot A.
- Le record `CentresActifs` de `publishedlanguage/` ne convient pas (un `(id, nom)` et non un port ; SQL des consommateurs = `id` seul) : conservé tel quel, jamais consommé, à supprimer ou réutiliser au lot suivant.