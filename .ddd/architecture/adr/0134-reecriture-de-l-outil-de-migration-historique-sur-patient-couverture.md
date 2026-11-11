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

## Conséquences
- 5 fichiers legacy en moins, 1 interface publiée et 1 cas d'usage en plus ; `AssureRepositoryPort` / `Assure` de `bc-assure` n'ont plus d'utilisateur
  (retrait du module en fin de S6).
- Jalons `TableDuPatientCouvertureTest` : 3 entrées retirées.
