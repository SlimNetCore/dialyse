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

## Conséquences
- 5 fichiers legacy en moins, 1 interface publiée et 1 cas d'usage en plus ; `AssureRepositoryPort` / `Assure` de `bc-assure` n'ont plus d'utilisateur
  (retrait du module en fin de S6).
- Jalons `TableDuPatientCouvertureTest` : 3 entrées retirées.
