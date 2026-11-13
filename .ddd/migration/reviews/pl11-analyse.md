---
status: proposed
date: 2026-11-13
sources: inventaire-platform-restant.md (B6), ADR 0070/0111/0121/0122/0124/0126/0128/0134, known-bugs.md (KB-ORG, KB-RF1..5), code lu au 2026-11-13
---

# PL11 : retrait de l'administration générique des référentiels (analyse, lecture seule)

Aucun code modifié. Butoir : 2027-03-31 (4,5 mois). Tout ce qui suit est constaté dans le code ; ce qui n'a pas été vérifié est marqué « non vérifié ».

## 0. Constat d'ensemble

- RF1 (transporteurs), RF2 (forfaits), RF3 (payeurs) ont déjà déplacé les **données et les règles** vers Patient et couverture ; il ne reste dans l'admin générique que leur **descripteur** (champs, alias d'en-têtes, modèle d'import, libellé) et un délégué ACL par type. Le SQL dynamique ne sert plus qu'à **3 tables : `salle`, `position_creneau`, `medecin`** (`ReferentialAdminTables.java:21-45`).
- Organisation possède déjà des routes **parallèles et incomplètes** sur les mêmes tables (voir §3.4) : deux jeux de règles pour une même ligne. C'est le vrai risque de PL11.
- Les 4 sous-slices de l'inventaire sont réalistes, mais (a) n'est pas indépendante du reste : les descripteurs ne peuvent quitter `ReferentialKind` qu'une fois un **point d'extension** (SPI) posé (voir §4, PL11-1).
- Le **filet est insuffisant** pour salles/créneaux/médecins : aucun golden pour PUT créneau/médecin, POST médecin (contenu), refus `REFERENTIAL_IN_USE`, `ALREADY_EXISTS`, validations de champ, import de salles/créneaux, invalidation de cache (§2.3). À compléter AVANT de toucher au code.

## 1. Inventaire exact

### 1.1 Routes (7, une seule classe : `ReferentialAdminRestController`, `@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")` au niveau classe)

Préfixe `/api/v1/admin/referentials`. Rôles : ADMIN (son centre uniquement, `CenterAccessGuard.requireCenter`) et SUPERADMIN ; tous les autres profils + anonyme refusés (golden `matrice-des-droits`, 11 profils + anonyme). `{kind}` = slug : `forfaits, creneaux, salles, medecins, caisses, agences, centres-payeurs, transporteurs`.

| # | Méthode | URL | Remarque |
|---|---|---|---|
| 1 | GET | `` | liste des 8 descripteurs, tri par `importOrder` |
| 2 | GET | `/{kind}?centerId&search&page&size` | page 20 par défaut, 100 max (`MAX_PAGE_SIZE`) |
| 3 | POST | `/{kind}` corps `{centerId, values{}}` | 201 |
| 4 | PUT | `/{kind}/{id}` | remplacement complet des colonnes du descripteur |
| 5 | DELETE | `/{kind}/{id}?centerId` | 204 ; 422 `REFERENTIAL_IN_USE` / `REFERENTIAL_NOT_FOUND` |
| 6 | POST | `/{kind}/import?centerId&dryRun=true` (multipart) | vérification par défaut ; tout ou rien ; 5000 lignes max |
| 7 | GET | `/{kind}/template?format=xlsx\|csv` | modèle d'import |

Garde transverse : `DatesIllisiblesDeLaDirectionAdvice` (400 `BAD_REQUEST` pour corps/UUID/page illisibles sur `/api/v1/admin/referentials/**`, 403 prioritaire ; KB-RF3-01/02, motif ancré KB-RF5-05) et `RoutesDeLAdministrationGeneriqueGardeesTest` (aucune route sans `@PreAuthorize`). Les deux sont adossés au préfixe : à conserver ou à déplacer avec les routes.

Lectures communes `/api/v1/referentials/{salles,positions,medecins}` : déjà dans Organisation (`ReferentielsLegacyControleur`), hors périmètre. Routes Organisation sur les mêmes tables : `POST /organisation/salles/{id}/ouvrir|fermer`, `POST|PUT|GET /organisation/creneaux`, `POST|PUT|DELETE|GET /organisation/referentiels/medecins` (ADMIN/SUPERADMIN).

### 1.2 Classes de platform (≈ 1 960 lignes, 25 fichiers de production + 6 de test)

| Groupe | Fichiers (lignes) | Sort |
|---|---|---|
| Contrôleur et DTO | `ReferentialAdminRestController` (7 routes), 4 DTO `web/dto/referential/*` | façade conservée (voir D1) |
| Application | `ReferentialAdminApplicationService` (54, `@Transactional`) | conservé ou supprimé selon D1 |
| Domaine générique | `ReferentialAdminDomainService` (299), `ReferentialValuesValidator` (184), `model/*` 8 fichiers (dont `ReferentialKind` 136, `ReferentialField` 98) | validation/import : généralisable ; `ReferentialKind` à vider |
| Ports | `ReferentialAdminRepositoryPort`, `ReferentialAdminDelegate`, `ReferentialAdminDelegates`, `ReferentialAdminUseCase` | `RepositoryPort` supprimé en (d) ; `Delegate` devient le SPI |
| **Adaptateur SQL dynamique** | `ReferentialAdminJdbcAdapter` (253), `ReferentialAdminTables` (90), `ReferentialCacheEvictor` (54) | **supprimés en (d)** |
| Délégués ACL (PC) | `TransporteursParPatientCouverture` (109), `ForfaitsParPatientCouverture` (110), `PayeursParPatientCouverture` (141) | descripteur absorbé par PC en (a) |
| Fichiers | `TabularFileReader` (235), `ReferentialTemplateWriter` (102), `MigrationTemplateWriter` (102, hors PL11) | restent génériques (inventaire A) |
| Consommateurs de `ReferentialKind` / `RepositoryPort` hors admin | `PatientMigrator:263-344`, `HistoryRules:113`, `MigrationConfig`, `DomainServiceConfig`, test `InMemoryMigrationPorts` | **bloquant pour (d)** : appariement code/nom de la reprise (§4, PL11-5) |
| Front | `frontend/.../features/admin/referentiels/*` (1 109 lignes), `core/api/referential-admin-api.service.ts` | inchangé si URLs conservées |

### 1.3 Tables, usages, caches

| Descripteur | Table | Clé naturelle | Colonnes | Usages (suppression) | Caches vidés |
|---|---|---|---|---|---|
| CRENEAU | `position_creneau` | `code` | code, libelle | `patients.position_id` | `ref.positions` |
| SALLE | `salle` | `code` | code, nom, isolement (NON/OUI), capacite (entier 1..999, facultatif) | `gmao_equipements.salle_id`, `patients.salle_id` | `ref.salles`, `ref.generateurs` |
| MEDECIN | `medecin` | `nom`+`prenom` | nom, prenom, specialite (+ `actif`, V52) | `patients.medecin_traitant_id`, `prescriptions_medicales.medecin_id` | `ref.medecins` |
| FORFAIT | `forfait` | `code` | code, libelle, prix | délégué PC (`UsageDuForfait`) | PC |
| CAISSE / AGENCE / CENTRE_PAYEUR | `caisse_assurance` / `agence` / `centre_payeur` | `code` | cf. `ReferentialKind` | délégué PC (borné au centre) | PC |
| TRANSPORTEUR | `transporteur` | `nom` | nom, telephone | délégué PC | PC |

Aucune clé étrangère en base sur `salle`, `position_creneau`, `medecin` (aucun `REFERENCES` dans `db/migration`).

### 1.4 Descripteurs et contexte cible

| Descripteur | Reste dans `ReferentialKind` | Contexte cible | Sous-slice |
|---|---|---|---|
| TRANSPORTEUR, FORFAIT, CAISSE, AGENCE, CENTRE_PAYEUR (5, butoir daté) | oui (résiduels) | Patient et couverture | (a) |
| CRENEAU, SALLE | oui (génériques) | Organisation | (b) |
| MEDECIN | oui (générique) | Organisation | (c) |
| — | — | adaptateur SQL, `Tables`, `RepositoryPort`, evictor | (d) |

## 2. Goldens `o2-administration` et filet

### 2.1 Les 31 goldens (`backend/platform/src/test/resources/characterization/organisation/o2-administration/`, test `AdministrationDesReferentielsCharacterizationIntegrationTest`, 5 méthodes) + 1 archive `_ecarts-legacy/medecin-lignes-en-base.approved.json`

| Descripteur | Goldens | Ce qu'ils verrouillent |
|---|---|---|
| Droits (transversal) | `matrice-des-droits` (1) | 12 routes x 11 profils + anonyme : statut et code. Salle : GET/POST/PUT/DELETE, créneau : GET/POST/DELETE, médecin : GET/POST/DELETE, `GET /admin/referentials`, `GET salles/template`. Statuts seuls, **pas les corps** |
| Lecture (transversal) | `liste-des-salles`, `liste-des-creneaux`, `liste-des-medecins`, `liste-des-salles-d-un-autre-centre` (garde de centre), `liste-sans-centre-demande`, `referentiel-inconnu` (6) | forme de page, tri par code, garde de centre ADMIN, référentiel inconnu |
| SALLE | `creation-salle`, `creation-salle-sans-nom` (REQUIRED), `creation-salle-dans-un-autre-centre`, `modification-salle` (isolement OUI), `suppression-salle`, `suppression-salle-inconnue`, `salles-en-base` (SQL) (7) | création/validation/garde, modification, **suppression physique** (ligne disparue) |
| MEDECIN (suppression) | `suppression-medecin`, `medecin-lignes-en-base_nouveau_code` (SQL : 1 ligne conservée, KB-ORG-03), `liste-des-medecins-apres-suppression`, `suppression-medecin-deja-supprime`, `suppression-medecin-d-un-autre-centre` (5) | désactivation logique V52, lecture qui masque les inactifs, 2e suppression = introuvable |
| MEDECIN (import) | `import-verification-par-defaut`, `import-rien-ecrit-apres-verification`, `import-reel`, `import-medecins-en-base`, `import-reel-rejoue`, `import-rejoue-medecins-en-base`, `import-colonne-obligatoire-manquante`, `import-fichier-vide`, `import-ligne-sans-nom`, `import-refuse-rien-ecrit`, `import-par-un-secretaire`, `import-dans-un-autre-centre` (12) | dry-run par défaut, tout ou rien, idempotence du rejeu (clé nom+prénom), en-têtes manquants/fichier vide, refus 403 secrétaire, garde de centre |
| CRENEAU | **aucun golden de contenu** (seulement lecture + 2 cellules de la matrice) | rien sur création/modification/suppression/import |

### 2.2 Filet complémentaire existant
- Patient et couverture : `characterization/patientcouverture/rf1` (35), `rf2` (29), `rf3` (51 + 2 `admin-generique`) goldens passent par les routes `/admin/referentials/{transporteurs,forfaits,caisses,agences,centres-payeurs}` ; ce sont les goldens qui verrouillent (a). `RefusDeFormeDeLAdministrationGeneriqueCharacterizationIntegrationTest` (400 sur toute l'admin).
- `ReferentialAdminIntegrationTest` (9 tests : 8 descripteurs dans l'ordre d'import, références résolues par code, suppression refusée si un enfant référence, centre propre, rôles, **éviction du cache d'un seul centre**, modèles d'import), `ReferentialAdminDomainServiceTest`, `ReferentialAdminDelegationTest`, `TabularFileReaderTest`.

### 2.3 Trous du filet à combler avant (b) et (c) (tests seuls, aucun code de production)
1. Créneau : POST/PUT/DELETE (contenu), unicité `code`, import, modèle.
2. Médecin : POST/PUT (contenu), unicité nom+prénom (`ALREADY_EXISTS`), refus d'usage (`REFERENTIAL_IN_USE`) avec un patient et une prescription, recherche `search`.
3. Salle : PUT sans capacité (efface-t-elle la capacité ?), capacité 0/1000/texte (`INTEGER_OUT_OF_RANGE`, `INVALID_INTEGER`), isolement hors liste (`INVALID_VALUE`), unicité code, refus d'usage (patient, équipement GMAO), import, modèle.
4. Cache : après chaque écriture, `ref.salles/ref.positions/ref.medecins/ref.generateurs` du centre seul (couvert en unitaire par `ReferentialAdminIntegrationTest`, pas au niveau HTTP Organisation).
5. Corps des `GET /admin/referentials` (descripteurs : libellés, champs, alias) : verrouillé seulement par `describesTheEightReferentialsInImportOrder`.
6. `DELETE` : `usages` d'autres tables (planification, séances, infirmiers : voir KB-PL11-1).

## 3. Invariants et règles en vigueur, écarts

### 3.1 Règles communes (RG-REF-001 à 012, `docs/reference/regles-de-gestion/14-referentiels-documents-reprise.md`)
- Créer/modifier : champ obligatoire (`REQUIRED`), longueur max (`TOO_LONG`), entier 1..999, énumération fermée tolérant casse/accents ; clé naturelle par centre insensible à la casse et aux espaces répétés (`ALREADY_EXISTS`) ; **modification = remplacement complet** des colonnes du descripteur.
- Supprimer : lecture de l'élément dans le centre (`REFERENTIAL_NOT_FOUND`), puis `COUNT` sur chaque colonne d'usage ; > 0 = 422 `REFERENTIAL_IN_USE`.
- Import : en-têtes par libellé/alias, colonne inconnue ignorée, doublon dans le fichier (`DUPLICATE_IN_FILE`), 500 anomalies rapportées max, 5000 lignes max, écriture seulement si zéro anomalie et `dryRun=false`, transaction unique, caches vidés après commit.
- Garde : centre de l'ADMIN (jamais un autre) ; SUPERADMIN doit indiquer le centre. Audit : non vérifié si les écritures de ces routes sont tracées (journal d'audit générique `AuditRequestFilter`, à confirmer par un test).

### 3.2 Par entité

| Entité | Créer | Modifier | Supprimer | Unicité | Suppr. logique/physique | Garde centre/société | Rôles | Audit |
|---|---|---|---|---|---|---|---|---|
| Salle | code+nom obligatoires, isolement NON par défaut, capacité facultative | remplace code, nom, isolement, capacité | **physique** (`DELETE FROM salle`), bloquée si patient ou équipement GMAO | `code` par centre, lecture puis écriture | physique | centre ; pas de contrôle de société | ADMIN/SUPERADMIN | non vérifié |
| Créneau | code+libellé | remplace | **physique**, bloquée si patient | `code` | physique | idem | idem | non vérifié |
| Médecin | nom obligatoire | remplace nom/prénom/spécialité | **logique** (`actif=false`, V52, décision 2026-11-02), bloquée si patient ou prescription | `nom`+`prenom` | logique | idem | idem | non vérifié |
| Descripteurs PC | portés par PC (KB-RF1/2/3) | idem | idem, usages bornés au centre | `nom` / `code` | physique | idem | idem | non vérifié |

### 3.3 Décisions déjà actées (ne pas rouvrir) : KB-ORG-02 (400 sur UUID illisible), KB-ORG-03 (tri par nom, suppression logique des médecins y compris admin), KB-ORG-05 (garde de centre sur `/referentials`), KB-RF3-01/02 (400 sur toute l'admin, **à confirmer métiers avant 2026-12-15**), O4-R13 (ADR 0111:38 : option 2 de l'ADR 0070, « décision explicite sur chaque invariant qui diffère »).

### 3.4 Écarts et anomalies visibles (futurs KB-PL11-n, à ne pas corriger ici)

| KB | Constat | Preuve |
|---|---|---|
| KB-PL11-1 | **Usages incomplets** : une salle utilisée par l'affectation d'un infirmier, un mouvement de patient, la planification (calendrier, déplacement temporaire, préférences) ou une séance (`seances.salle_id`) se supprime sans refus ; idem un créneau (`infirmier_affectation.creneau_id`, `mouvement_patient.position_id`, `planification_calendrier_case.creneau_id`...). Sans clé étrangère, la ligne devient orpheline | `ReferentialAdminTables.java:24,29,35` ; migrations V11, V17, V25, V26, V28 |
| KB-PL11-2 | **Compte d'usage non borné au centre** (`WHERE colonne = ?` par identifiant seul), alors que KB-RF1-03/RF2-02/RF3-05 ont été corrigés (bornés au centre) pour les descripteurs de PC | `ReferentialAdminJdbcAdapter.java` `countUsages` |
| KB-PL11-3 | **Échec technique du compte d'usage = « 0 usage »** : `DataAccessException` avalée (journal debug) dans `countUsages` ; une table absente ou une requête en erreur autorise la suppression physique d'une salle | idem, bloc `catch (DataAccessException)` |
| KB-PL11-4 | **Deux jeux de règles sur la même table** : la route d'Organisation `DELETE /organisation/referentiels/medecins/{id}` désactive SANS contrôle d'usage (`desactiverMedecin`), l'admin refuse 422 si le médecin est référencé. La suppression logique V52 existe pour conserver l'historique, mais l'admin bloque justement les médecins utilisés | `ReferentielsOrganisationService.java:desactiverMedecin` ; `ReferentialAdminDomainService.delete` |
| KB-PL11-5 | **Aucune unicité dans Organisation** (`referencerMedecin`, `creerCreneau`) alors que l'admin refuse `ALREADY_EXISTS` (RG-REF-002) ; unicité aussi sans contrainte en base (comme KB-RF1-04, KB-RF2-03, KB-RF3-04) | `ReferentielsOrganisationService.java` ; `OrganisationService.java:220` |
| KB-PL11-6 | **Médecin désactivé invisible à l'appariement** : la clé naturelle ignore les inactifs, donc on peut recréer ou réimporter un médecin désactivé (doublon d'identité, l'ancien n'est jamais réactivé, aucune route de réactivation) ; 2e suppression = 422 `REFERENTIAL_NOT_FOUND` (golden) et non idempotente | `actifSeulement` ; goldens `suppression-medecin-deja-supprime` |
| KB-PL11-7 | **Salle : deux vues partielles d'une ligne** : l'admin écrit isolement et capacité, jamais `ouverte` ; l'agrégat `Salle` d'Organisation écrit `ouverte` et ignore isolement/capacité (`UPDATE salle SET code, nom, ouverte`). Fermer une salle n'a aucun effet (KB-ORG-07) | `SallesJdbc.java:45` ; `Salle.java` |
| KB-PL11-8 | **Modifier sans capacité l'efface** (remplacement complet, NULL = capacité illimitée), comme KB-RF2-04 pour le prix ; changer la capacité ne contrôle pas les générateurs déjà affectés (RG-REF-006) | `update` : toutes les colonnes du descripteur sont affectées |
| KB-PL11-9 | **Deux stratégies d'invalidation de cache** : l'admin retire les clés préfixées par le centre après commit ; Organisation retire `ref.medecins` pour tous les centres (`retirer("ref.medecins", null)`) et `ref.positions`/`ref.salles` par centre | `ReferentialCacheEvictor` ; `MedecinsJdbc.java:43` ; `CreneauxJdbc.java:39` |
| KB-PL11-10 | **Capacité et isolement ne sont pas dans le langage d'Organisation** : `Salle` (domaine) n'a ni isolement ni capacité ; ils sont lus par `IsolementDesSallesJdbc`, `SalleVuePort` (GMAO/Planification) | `Salle.java` ; `DefinirLesSallesDIsolement` |
| KB-PL11-11 | Divers : Javadoc du contrôleur périmée (cite générateurs et forfaits comme gérés ici) ; l'`importOrder` 2/3 n'a de sens que pour les payeurs ; `ordonnances.medecin_id` est un `varchar(100)` non compté dans les usages du médecin | `ReferentialAdminRestController.java:44` ; V1 l.1020 |

## 4. Découpage recommandé : 7 slices (6 livrables + 1 de tests seuls)

Principe : mêmes URLs, mêmes tables, mêmes rôles (refactoring sur place, ADR 0024/0075). Un comportement visible à la fois, jamais de big bang. Tous les contrôles de la garde d'architecture vérifiés après chaque slice (`check-arch`).

| Slice | Contenu | Routes touchées | Fichiers / lignes | Contexte cible | Ports à créer | Risque | Dépend de |
|---|---|---|---|---|---|---|---|
| **PL11-0** Filet | combler §2.3 (tests seuls) + test d'audit de l'admin | 0 (tests) | ≈ 6 tests, ≈ 700 l. | — | — | faible | — |
| **PL11-1** SPI + (a) descripteurs PC | SPI `AdministrationDUnReferentiel` (descripteur + list/create/update/delete/importRows, dont le descripteur lui-même) porté par PC ; `ReferentialKind` perd 5 entrées ; modèles d'import et libellés viennent de PC | 7 (aucune URL ne change) ; effet sur les 5 `{kind}` | ≈ 14 fichiers, ≈ 800 l. | Patient et couverture | `DescripteurDeReferentiel` (publié, `patientcouverture.v1`), SPI dans platform | moyen : ~115 goldens PC doivent rester **identiques** | PL11-0, D1, D2 |
| **PL11-2** (b1) Créneaux | commandes Organisation `modifier/supprimer créneau` + unicité + usages ; l'admin les appelle ; import | CRUD créneau + import + modèle (6) : à scinder CRUD (4) / import-modèle (2) si > 5 | ≈ 12 fichiers, ≈ 900 l. | Organisation | `UsageDuCreneau` (Patient, Planification, Infirmiers) | moyen | PL11-1, D3, D4 |
| **PL11-3** (c) Médecins | l'admin passe par `referencerMedecin/modifierMedecin/desactiverMedecin` (un seul jeu de règles) ; usages ; import | idem (6 -> 4 + 2) | ≈ 12 fichiers, ≈ 900 l. | Organisation | `UsageDuMedecin` (Patient, Dossier médical) | **élevé** (KB-PL11-4/5/6) | PL11-2, D5, D6 |
| **PL11-4** (b2) Salles | idem + isolement/capacité (agrégat `Salle` enrichi, ou port existant) + suppression physique + usages | idem | ≈ 15 fichiers, ≈ 1 200 l. | Organisation | `UsageDeLaSalle` (Patient, GMAO, Planification, Séance), capacité | **élevé** (KB-PL11-1/3/7/8, cache `ref.generateurs`, KB-ORG-07) | PL11-3, D3, D7 |
| **PL11-5** Appariement de la reprise | `PatientMigrator`/`HistoryRules` apparient salle, créneau, médecin, payeur, transporteur, forfait par lectures publiées des contextes au lieu de `findAllForMatching` | 0 (outil de reprise, 3 routes migration non touchées) | ≈ 9 fichiers, ≈ 700 l. | Organisation + PC (lectures) | `AppariementDeReferentiels` (publié) | moyen (goldens de reprise `characterization/migration/**`) | PL11-1 à PL11-4 |
| **PL11-6** (d) Retrait | supprimer `ReferentialAdminJdbcAdapter`, `Tables`, `RepositoryPort`, evictor, `ReferentialKind` (si SPI complet), tests associés ; solder les butoirs (§6) ; ADR de clôture (ADR 0070 `superseded`) | 0 | ≈ 15 fichiers supprimés, ≈ 1 500 l. retirées | — | — | faible si 1 à 5 verts | PL11-5 |

Ordre : 0, 1, 2, 3, 4, 5, 6. Raison : du plus sûr (le filet, le déjà-migré) au plus risqué ; médecins avant salles car la règle d'usage et de désactivation est d'abord à trancher ; salles en dernier car c'est la table lue par le plus de contextes. Compte tenu du butoir, prévoir **PL11-1 avant fin novembre, PL11-4 avant fin janvier, PL11-6 en février** (marge de 6 semaines). Une slice peut glisser, jamais les gardes : si PL11-4 n'est pas livrée, reconduire par ADR motivé (cf. §6) plutôt que repousser en silence.

## 5. Décisions à demander à l'utilisateur (12)

| # | Question simple | Recommandation |
|---|---|---|
| D1 | Garde-t-on UN écran/une route d'administration unique `/admin/referentials/{kind}` (la plateforme distribue aux contextes), ou une route par contexte aux mêmes URLs ? | Une façade fine qui distribue (le mécanisme de délégués existe déjà), les contextes portent règles et données ; plus rien de SQL dans platform. Écran inchangé |
| D2 | Les 5 descripteurs de Patient et couverture : les contextes publient-ils leurs champs et alias (libellés, modèle d'import) ? | Oui, par un port publié `DescripteurDeReferentiel` ; le lecteur de fichiers reste générique |
| D3 | Un référentiel utilisé (salle, créneau) doit-il refuser la suppression en comptant TOUS les endroits où il sert (infirmiers, planification, séances, mouvements), pas seulement patients/équipements ? | Oui (corrige KB-PL11-1) ; sinon reproduire à l'identique et assumer des lignes orphelines |
| D4 | Une salle ou un créneau supprimé doit-il rester supprimé physiquement, ou devenir « désactivé » comme les médecins ? | Désactivation logique (comme V52) : plus sûr, mais change le golden `suppression-salle` ; à confirmer |
| D5 | Peut-on désactiver un médecin qui a des patients/prescriptions ? (admin : non ; route Organisation : oui) | Oui : c'est le but de la désactivation (historique conservé) ; supprimer le refus d'usage pour les médecins, garder « introuvable » inchangé |
| D6 | Un médecin désactivé peut-il être réactivé (ou réimporté) au lieu de créer un doublon ? | Oui : l'import et la création réactivent le médecin de même nom+prénom ; à valider métiers |
| D7 | Les routes d'Organisation (créer créneau/médecin) doivent-elles refuser les doublons comme l'admin ? | Oui, une seule règle d'unicité par centre (code, ou nom+prénom), refus `ALREADY_EXISTS` |
| D8 | Modifier une salle sans préciser la capacité doit-il effacer la capacité ? | Non : conserver (corrige KB-PL11-8) ; ou reproduire si l'écran envoie toujours le champ |
| D9 | Fermer une salle doit-il avoir un effet (planification, listes) ? (KB-ORG-07) | Hors PL11 ; garder tel quel et le noter, décision séparée |
| D10 | Échec technique du contrôle d'usage : refuser la suppression (sûr) ou l'autoriser (actuel) ? | Refuser (500 explicite) : une suppression doit échouer quand on ne sait pas vérifier |
| D11 | Les corrections d'écart sont-elles faites pendant PL11 ou après (reproduire d'abord) ? | Reproduire d'abord (règle legacy-safety n°4), corriger par slice dédiée après décision, comme RF1c/RF2c/RF3d |
| D12 | Butoir 2027-03-31 : acceptez-vous un report motivé de PL11-4/6 si une revue métiers n'est pas faite avant ? | Oui, par ADR (nouvelle date au plus tard 2027-06-30), jamais en silence ; les revues « avant 2026-12-15 » de RF1-RF4 sont à confirmer en parallèle |

## 6. Solder les butoirs 2027-03-31

| Table d'exceptions | Entrées à retirer ou modifier | Slice |
|---|---|---|
| `DescripteursResiduelsDeLAdministrationGeneriqueTest` | `RESIDUELS` : TRANSPORTEUR, FORFAIT, CAISSE, AGENCE, CENTRE_PAYEUR (5, effectif asserté à 5) ; `ENCORE_GENERIQUES` : CRENEAU, SALLE, MEDECIN (3) ; la 4e méthode vérifie 4 lignes d'ADR : `0122` `\| RF1-R1 \|`, `0124` `\| RF2-R1 \|`, `0126` `\| RF3-R1 \|`, `0128` `\| RF3-R1 \|` (mettre à jour ces lignes en « soldé par PL11 » et supprimer le test avec l'enum) | PL11-1 (5 résiduels), PL11-2 à 4 (3), PL11-6 (test supprimé) |
| `TableDeLOrganisationTest.LECTEURS_CONNUS` | `ReferentialAdminJdbcAdapter.java` (écrit `medecin`, `salle`, `position_creneau`) ; les 6 autres entrées (AuthService, DirectionAccountService, SocieteAdminAccountService, DevSuperAdminBootstrapInitializer, SessionLegacy, UserRestController) relèvent d'Identité et accès, pas de PL11 : le test (et son butoir) subsiste | PL11-6 |
| `TableDuPatientCouvertureTest` | `ReferentialAdminTables.java` (l.102, sans date, « usages » patients/prise_en_charge) | PL11-6 |
| `TableDesEquipementsDeLaGmaoTest` | `ReferentialAdminTables.java` (l.38, « usages » de `gmao_equipements`) | PL11-4 (usage par port GMAO), retrait PL11-6 |
| `TableDesSeancesTest` (liste épinglée « les 4 fichiers de SE7e restés en plateforme », l.205-217) | `ReferentialAdminJdbcAdapter` et `ReferentialAdminTables` ; l'effectif « 4 » devient 2 | PL11-6 (et PL11-4 pour l'usage `seances.salle_id`) |
| Tests sans butoir daté touchés | `ReferentialAdminIntegrationTest` (8 descripteurs), `ReferentialAdminDomainServiceTest`, `ReferentialAdminDelegationTest`, `InMemoryReferentialAdminRepository`, `InMemoryMigrationPorts`, `RoutesDeLAdministrationGeneriqueGardeesTest` (si la façade subsiste, inchangé), `NomsDeTableCalcules` (commentaire) | PL11-1 à 6 |
| Documents | ADR 0070 (`superseded`), ADR 0111 O4-R13 (soldé), fiche O4, ADR 0121 l.54 (« faux propriétaire »), `seams.md`, `docs/reference/regles-de-gestion/14-referentiels-documents-reprise.md` (RG-REF), référentiel fonctionnel régénéré | chaque slice |

Hors PL11 mais portant le même butoir : `TableDesForfaitsTest` (`ForfaitsDeSeanceJdbc`), `TableDuPatientCouvertureTest` (lecteurs Séance/Pilotage, `MigrationRollbackJdbcAdapter`), `PatientsDesCentresJdbc` (Pilotage), `TableDeLaPlanificationTest` : à ne pas compter dans PL11.

## 7. Risques et points de vigilance
- Aucun routage Strangler, flag ou projection (ADR 0024/0075) : bascule directe, filet vert avant et après, goldens existants inchangés (sauf décisions D4 à D8, qui produisent des `_nouveau_code` explicites, legacy archivé).
- Le hook `guard-legacy.ts` bloque l'édition des fichiers de `legacyPaths` hors filet : enregistrer les nouveaux goldens dans `.ddd/safety-net/covered.json` avant PL11-2.
- Le tri `ORDER BY code` / `nom, prenom` suit la collation de la base (KB-RF2-06) : H2 et PG16 peuvent diverger ; les goldens H2 ne prouvent rien sur PG (contrat PG16 à ajouter pour `salle`/`medecin`).
- `GET /admin/referentials` (descripteurs) est lu par le front pour ses onglets : un changement d'ordre ou de libellé casse l'écran sans casser un golden.
