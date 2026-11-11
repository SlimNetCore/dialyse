---
status: accepted
date: 2026-11-11
review-by: 2026-12-15
decision-makers: [utilisateur (« à confirmer <= 2026-12-15 »), architecte]
context-scope: platform, patient-couverture
related: ADR 0024, 0133 (S6), 0077 point 12, 0078 point 10 ; filet `.ddd/safety-net/patient-migration-historique.md`
---

# 0134 - Réécriture de l'outil de migration historique sur Patient et couverture

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes colonnes. S6 de l'ADR 0133 est livrée en trois sous-slices :
S6b-1 assurés, S6b-2 patients, S6b-3 affectations, soldes, annulation d'un lot.

## S6b-1 : assurés

### Décision
- **Propriété** : les tables `assure` / `assure_patient` appartiennent à `patient-couverture` ; l'outil de migration (platform) n'écrit plus
  `assure` par JPA. Il passe par un nouveau langage publié `RepriseDesAssures` (`trouver(numero)` exact tous centres ; `reprendre(centre, report)`).
- **Implémentation dans le contexte** : cas d'usage `ReprendreLesAssures` (application) sur le port existant `AssuresDuCentre`
  (`trouver` + `declarer`, SQL JDBC existant de `AssuresJdbc`) ; aucun port ni SQL nouveau. La règle « une reprise n'efface jamais une donnée »
  (seules les valeurs renseignées remplacent l'existant, le nom est toujours remplacé) vit dans ce cas d'usage, testée avec le double en mémoire.
- **Platform** : `AssureMigrator` ne garde que le contrôle du fichier (doublons, conflit d'identifiant d'origine, autre centre, rapprochement)
  et l'écriture de la correspondance d'identifiants ; `PatientMigrator` et `AffectationMigrator` ne lisent plus les assurés que par
  `RepriseDesAssures.trouver` (même sémantique : exacte, tous centres, centre comparé par l'appelant).
- **Retirés** : `AssureRepositoryAdapter`, `AssureJpaRepository`, `AssureJpaEntity` et leurs deux tests (caches `patient.assure.*` : plus de
  producteur ; les noms restent déclarés, vidés après import comme avant, à retirer en S6b-3 avec `MigrationApplicationService.PATIENT_CACHES`).
- Pas de port + ACL dédiés dans platform (contrairement à `GenerateursDeReprise`) : les migrateurs consomment directement l'interface publiée,
  le hook de sécurité legacy interdisant de créer de nouveaux fichiers dans `platform/src/main` hors `covered.json`.
- Le verrou du centre (ADR 0077 point 12) n'est pas pris par l'outil : inchangé, à lancer hors activité.

### Preuve
- Les 18 goldens `characterization/migration/historique/**` inchangés (aucun `-Dcharacterization.approve`).
- Écart de bord assumé, hors invariants : une ligne `assure` existante sans `created_at` le garde nul (le legacy le posait à la reprise).

### Bugs connus : aucun corrigé dans S6b-1 (décision de périmètre)
Le code de KB-S6a-2 (restauration d'un assuré seulement rapproché : `MigrationRollbackJdbcAdapter` + mémoire de l'état d'origine) et de
KB-S6a-5 (recherche d'assuré des affectations : `AffectationMigrator`, qui doit aussi écrire le numéro canonique de l'assuré) n'est pas
dans les fichiers réécrits ici ; ils sont corrigés en S6b-3 avec leur golden `*_nouveau_code` (protocole d'écart). KB-S6a-1 (rejeu d'affectations),
KB-S6a-3 (patient supprimé hors outil) : S6b-2/3. KB-S6a-6 (SUPERADMIN refusé) : conservé.

## S6b-2 : patients

### Décision
- **Propriété** : les tables `patients` (hors colonnes de place, voir plus bas) et `assure_patient` appartiennent à `patient-couverture`. `PatientMigrator`
  ne touche plus `domain.patient.*` ni `domain.assure.*` : il passe par le langage publié `RepriseDesPatients` (`duCentre(centre)` pour rapprocher
  et détecter les doublons ; `reprendre(centre, patient, report)` écrit la fiche et renvoie son identifiant). Il garde les contrôles du fichier
  (doublons, numéro et code pris, dates, références aux référentiels, assuré du centre via `RepriseDesAssures.trouver`) et la correspondance d'identifiants.
- **Implémentation dans le contexte** : cas d'usage `ReprendreLesPatients` (application) sur un port sortant `PatientsDeLaReprise` (adapter JDBC
  `PatientsDeLaRepriseJdbc`, SQL borné au centre, aucune dépendance au legacy) et le port existant `AssuresDuCentre` (affectation principale :
  `principalActuel`, `retirerLePrincipal`, `ajouterUneAffectationPrincipale`). Règles dans le cas d'usage, testées avec des doubles en mémoire :
  une reprise n'efface jamais une donnée (nom, prénom, sexe, naissance, admission, numéro, type, état et qualité toujours remplacés ; le reste
  seulement si renseigné ; jours remplacés ensemble) ; l'assuré lui-même n'a aucune affectation principale et recopie sa fiche d'assuré
  (`assure_*`) ; l'ayant droit a pour unique affectation principale celle de son assuré, comparée sans casse, commençant à l'admission.
- **Écriture ciblée** : l'adapter n'écrit que les colonnes que la reprise renseigne. L'ancien chemin (JPA, fiche relue puis réécrite en entier) remettait à
  null `pieces_jointes_json` d'un patient rapproché (non relu par `PatientMapper.toDomain`) : écart de bord assumé, NON verrouillé par les goldens, qui
  rend la règle « une reprise n'efface jamais une donnée » vraie (pièces jointes, photo, EPO, fer, historique de l'assuré ne sont plus touchés).
  Les booléens et `nombre_enfants` nuls d'un patient rapproché ne sont plus normalisés à faux / 0 (non observable : lus comme faux / 0).
- **Place** (`salle_id`, `position_id`, `generateur_id`, `jour_*`) : propriété de la Planification (ADR 0052) pour la fiche à l'écran, mais la reprise
  les écrit directement, comme avant, SANS contrôle de capacité ni verrou (l'outil se lance hors activité du centre, point 12 de l'ADR 0077) ; passer
  par `PlacementDesPatients.placer` ajouterait des refus que l'outil ne connaît pas aujourd'hui (comportement différent). **À CONFIRMER par l'utilisateur**
  (voir questions ouvertes) ; la reprise n'a aucun consommateur côté Planification.
- **Caches** : inchangés (`MigrationApplicationService.PATIENT_CACHES` vidé après import, il couvre aussi `PatientRepositoryAdapter` encore lu par
  `AffectationMigrator`, `OpeningBalanceMigrator` et les autres contextes).
- **Retiré de `platform`** : plus aucun import `domain.patient.*` / `domain.assure.*` dans `PatientMigrator` ; `AffectationMigrator`, `OpeningBalanceMigrator`
  et `MigrationRollbackJdbcAdapter` (S6b-3) gardent leurs ports legacy (`PatientRepositoryPort`, `AssurePatientRepositoryPort`), intacts.

### Bug corrigé : KB-S6a-3 (patient supprimé hors outil)
Avant : la correspondance vers un patient supprimé était ignorée, le patient recréé sous un NOUVEL identifiant, une nouvelle affectation principale
écrite et les anciennes lignes `assure_patient` restaient orphelines. Après : la fiche est recréée sous l'identifiant de la correspondance ; ses
affectations existantes lui sont rattachées (plus d'orphelin) et l'affectation principale n'est recréée que si l'assuré principal a changé.
Identifiant déjà porté par une fiche d'un autre centre (cas théorique) : nouvel identifiant. Golden `patients_rejeu_sans_effacement_et_changement_d_assure`
remplacé par `…_nouveau_code` (ancien archivé dans `_ecarts-legacy/`).
Aucun autre bug connu de `known-bugs.md` ne vit dans les fichiers de S6b-2 : KB-S6a-1 (rejeu d'affectations) et KB-S6a-5 (recherche d'assuré des
affectations) sont dans `AffectationMigrator`, KB-S6a-2 (annulation d'un assuré rapproché) dans `MigrationRollbackJdbcAdapter` : S6b-3.

### Preuve (S6b-2)
- Rouge vu : `ReprendreLesPatientsTest` (10 tests) rouge sur un squelette (`UnsupportedOperationException`) avant le code. Le golden de KB-S6a-3 était l'unique
  écart des 97 tests ciblés après la réécriture (diff relu : seules les lignes d'affectations de `5_etat` changent, orphelins -> `ZP111`), approuvé à la main
  (copie du `.received`, jamais `-Dcharacterization.approve`).
- 17 goldens sur 18 inchangés, 3 passes ciblées vertes dont 2 avec `-Dsurefire.runOrder=random`, `./mvnw -o test -fae` : BUILD SUCCESS (platform 3375 tests, 0 échec).
- Écarts de bord assumés : voir KB-S6b-1 (pièces jointes), booléens / `nombre_enfants` nuls non normalisés, `created_at` d'une fiche créée posé par l'horloge du contexte.

### Questions ouvertes
- Colonnes de place écrites par la reprise (Planification propriétaire, ADR 0052) : à confirmer ; alternative `PlacementDesPatients.placer` (refus de capacité, verrou) =
  comportement différent, donc décision à part.
- Recréation d'une fiche supprimée sous son ancien identifiant (KB-S6a-3) : si des lignes d'autres tables (mouvements, séances) pointent encore cet identifiant sans
  clé étrangère, elles se rattachent de nouveau à la fiche recréée ; alternative écartée : supprimer les affectations orphelines (efface une donnée).

## S6b-3 : affectations, soldes d'ouverture, annulation d'un lot

### Décision (conception, 2026-11-11)
- **Affectations** : nouveau langage publié `RepriseDesAffectations` (`historique(centre, patient)` pour rapprocher une ligne d'historique ;
  `reprendre(centre, patient, affectation, report)` écrit la ligne) et cas d'usage `ReprendreLesAffectations` (application) sur les ports existants
  `AssuresDuCentre` (trois méthodes ajoutées : `trouverSansCasse`, `ajouterUneAffectation`, `remplacerUneAffectation`) et `PatientsDeLaReprise`
  (colonne `patients.assure_numero_assurance` de l'assuré principal, réécrite par `mettreAJour` d'une fiche relue). `AffectationMigrator` garde les contrôles du
  fichier (patient repris, assuré du centre, dates, principale unique) et la correspondance d'identifiants.
- **Soldes d'ouverture** : `RepriseDesPatients.trouver(centre, patient)` rend le `PatientRepris` (code, nom, prénom, état, numéro d'assurance, centre payeur) que la
  facture de reprise fige ; `OpeningBalanceMigrator` n'importe plus rien du patient.
- **Annulation** : `MigrationRollbackJdbcAdapter` n'importait déjà aucune classe `domain.patient` / `domain.assure` ; ses suppressions (`assure_patient`,
  `patients`, `assure`) et ses contrôles (liens et patients rattachés à un assuré) restent du SQL borné au centre, inchangé (reste à S6c, voir ADR 0133).
  La restauration d'un assuré rapproché (KB-S6a-2) passera par le langage publié `RepriseDesAssures` (`memoriser`, `restaurer`) : en attente d'approbation, voir plus bas.
- **Caches** : `PATIENT_CACHES` ne garde que les caches encore produits (listes de résumés, prises en charge, attestations) ; les caches `patient.byId`,
  `patient.byNumeroAssurance`, `patient.byCenter`, `patient.countByCenter`, `patient.assure.*`, `patient.assignment.*`, `patient.pec.byId`, `patient.attestation.existsValidAt`
  n'ont plus de producteur une fois `PatientRepositoryAdapter` et `AssurePatientRepositoryAdapter` retirés.
- **Place** : colonnes `salle_id`, `position_id`, `generateur_id`, `jour_*` toujours écrites directement par la reprise (décision S6b-2, « à confirmer »).

### Bugs corrigés (décisions de l'utilisateur, 2026-11-11)
- **KB-S6a-1** : rejouer un fichier d'affectations ne rétrograde plus l'affectation principale (l'ancien chemin JPA retirait le caractère principal par
  une mise à jour en masse, puis réenregistrait une entité dont l'état en mémoire n'avait pas changé : aucune écriture). La ligne principale est écrite
  explicitement après le retrait du principal des autres lignes.
- **KB-S6a-2 : CORRIGÉ (2026-11-11, après S6b-3).** Annuler un lot restaure un assuré seulement rapproché : `AssureMigrator` lit l'état d'origine
  (`RepriseDesAssures.memoriser`) AVANT la première reprise de l'identifiant d'origine et le range dans `IdMapping.etatOrigine` (colonne additive nullable
  `migration_id_map.etat_origine`, V57, `schema.sql`, §87) ; `MigrationDomainService.cancel` relit les correspondances UPDATED du lot ayant un état et
  `MigrationRollbackPort.restore` (adapter : `RepriseDesAssures.restaurer`) remet toutes les valeurs de l'assuré, `created_at` intact. Une correspondance déjà connue
  garde l'état du premier lot ; celles d'avant V57 n'en ont pas, leur annulation ne restaure rien (comme avant). Golden
  `annulation_supprime_ce_que_le_lot_a_cree_nouveau_code` (ZR1 : `EXISTANT` au lieu de `MIS-A-JOUR` en `1_etat` et `2_etat`, 2 lignes seulement), ancien archivé dans
  `_ecarts-legacy/annulation/`. Chaîne Flyway V1..V57 rejouée sur PostgreSQL 16 jetable (`backend/scripts/patient-couverture-s6b3-pg16-rejeu.sh`, port 55851).
  Taille du mémento (revue du 2026-11-11, constat 2) : la colonne est `TEXT`, pas `VARCHAR(4000)`. Pire cas du schéma `assure` : huit champs de 255 caractères,
  chacun de trois octets UTF-8 (`%E2%82%AC` = 9 caractères encodés) = 8 x 2295 + clés + `v=1&dateNaissance=…` = **18 467 caractères** (test
  `IdMappingEtatOrigineMaximalIntegrationTest` sur H2, rouge sur `VARCHAR(4000)` puis vert ; rejeu PG16 du script S6b-3). Réaliste : des champs accentués remplis à
  plus de la moitié suffisent à dépasser 4000. V57 n'étant pas déployée, elle est modifiée en place.
  Historique de la conception (conservé) : `RepriseDesAssures.memoriser(numero)` (état de l'assuré, « mémento » v1 `application/x-www-form-urlencoded`, format décidé par le
  contexte) et `restaurer(centre, numero, memento)`, testés (`ReprendreLesAssuresTest`). Reste côté outil : mémoriser le mémento dans la correspondance d'identifiants
  (colonne **additive** nullable `migration_id_map.etat_origine`, Flyway V57 + `schema.sql` H2 + §87 de `verifications-production.md` en SELECT), le relire dans
  `MigrationDomainService.cancel` (qui ne passe aujourd'hui que les correspondances CREATED) et l'appliquer dans `MigrationRollbackJdbcAdapter`. Ce chemin exige de
  modifier `IdMapping.java`, `IdMappingJdbcAdapter.java` et `MigrationRollbackPort.java` (record, adapter et port de la correspondance d'identifiants), que le hook de sécurité
  legacy refuse : absents de `.ddd/safety-net/covered.json` (ils sont pourtant exercés par les goldens `assures_*`, `annulation_*`). Les ajouter à `covered.json` demande l'accord
  de l'utilisateur. Golden `annulation_supprime_ce_que_le_lot_a_cree` inchangé en attendant (KB-S6a-2 toujours consigné tel quel).
- **KB-S6a-5** : la recherche de l'assuré d'une affectation ignore la casse (`za901` trouve `ZA901`) et la ligne d'affectation reçoit le numéro canonique de
  l'assuré. Les autres recherches de l'outil (assurés, patients) restent exactes (non décidé).
- Non corrigés (consignés) : KB-S6a-4 (jour « Lunch » accepté), KB-S6a-6 (SUPERADMIN refusé, 403 ROLE_SCOPE), KB-S6a-7.

### Preuve (S6b-3)
- Rouge vu : `ReprendreLesAffectationsTest` (7), nouveaux tests de `ReprendreLesAssuresTest` / `ReprendreLesPatientsTest` rouges sur un squelette (`UnsupportedOperationException`) ;
  `MigrationDomainServiceTest.replayingAPrimaryAffectationKeepsItPrimaryAndTheAssureIsFoundWithoutRegardToCase` rouge (`ASSURE_NOT_FOUND` pour `za901`) avant le passage à
  `trouverSansCasse`.
- Écarts de golden (protocole : `.received` lu, copié à la main, jamais `-Dcharacterization.approve`) : `affectations_controles_import_et_rejeu` remplacé par
  `…_nouveau_code` (ancien archivé dans `_ecarts-legacy/affectations/…_avant_s6.legacy.json`). Diff relu : (1) l'erreur `ASSURE_NOT_FOUND` de `za901` (ligne 10 de `1_refus`)
  disparaît, la ligne reste refusée comme doublon ; (2) AF12 reste principale en `4_etat` / `5_etat` (et son alias `…_true`) ; (3) ajout d'une étape `6_` qui importe
  `za902` et écrit `ZA902`. Les 17 autres goldens sont inchangés.
- Retirés de `platform` : `PatientRepositoryAdapter`, `AssurePatientRepositoryAdapter`, `PatientMapper`, `PatientJpaRepository`, `AssurePatientJpaRepository`,
  `AssurePatientJpaEntity` et deux tests (Grep : plus aucun utilisateur). `PatientJpaEntity` est GARDÉE : `schema.sql` n'a pas de `CREATE TABLE patients`, Hibernate (`ddl-auto: update`)
  crée la table du H2 de test depuis cette entité ; la retirer demande d'écrire ce DDL (S6c). `AssurePatientJpaEntity` retirée : la table `assure_patient` du H2 vient maintenant de
  `schema.sql` (`numero_assurance VARCHAR(100)` au lieu de 255 créé par Hibernate : sans effet sur les goldens, numéros de 3 à 10 caractères).

### Écarts de bord assumés (non verrouillés par les goldens)
- Le patient d'une affectation principale supprimé hors de l'outil entre-temps : l'ancien chemin levait `NoSuchElementException`, le nouveau lève `PatientIntrouvable`
  (la transaction est annulée dans les deux cas).

## Conséquences
- 5 fichiers legacy en moins, 1 interface publiée et 1 cas d'usage en plus ; `AssureRepositoryPort` / `Assure` de `bc-assure` n'ont plus d'utilisateur
  (retrait du module en fin de S6).
- Jalons `TableDuPatientCouvertureTest` : 3 entrées retirées.
