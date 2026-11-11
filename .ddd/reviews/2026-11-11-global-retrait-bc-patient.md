---
status: proposed (revue interrompue par la limite de tours : provisoire)
date: 2026-11-11
sources: ADR 0133, 0134 ; commits b1bb8325..24a64f24 ; .ddd/safety-net/known-bugs.md ; git show/diff en lecture seule (aucun test, build ni scan)
---

# Revue globale - retrait de bc-patient / bc-assure (S1-S6c)

**Verdict provisoire : pass-with-actions** - 0 blocker, 0 major, 6 minors. Points 1 a 4 en grande partie verifies ; points 5 et 6 partiels.

## Constats (tous minors)
| # | Sev | Constat | Preuve | Action |
|---|---|---|---|---|
| 1 | minor | Propriete `app.cache.ttl.patient-count` devenue morte (plus de lecteur dans `CacheConfig` depuis S6c) | backend/platform/src/main/resources/application.yml:74 ; CacheConfig diff S6c | la retirer |
| 2 | minor | Memento d'etat d'origine (URL-encode, 9 champs) stocke en VARCHAR(4000) : un assure aux champs accentues/longs peut depasser et faire echouer l'import en ecriture | V57__reprise_etat_d_origine_des_assures.sql:8 ; ReprendreLesAssures.java:57-71 | borner ou passer en TEXT (migration additive) |
| 3 | minor | Le script PG16 S6b-3 ne rejoue que les requetes de `migration_id_map` ; le INSERT/UPDATE de `PatientsDeLaRepriseJdbc` (litteraux `false`, `OffsetDateTime`) n'est verifie que sur H2 | backend/scripts/patient-couverture-s6b3-pg16-rejeu.sh:36-47 ; PatientsDeLaRepriseJdbc.java:48-53 | ajouter ces requetes au rejeu PG16 |
| 4 | minor | Jalons de garde perimes/vagues : `MigrationRollbackJdbcAdapter` garde l'echeance "reprise de la migration par le module" sans butoir date alors que la migration est reprise ; il supprime encore `patients`/`assure` en SQL | TableDuPatientCouvertureTest.java:54-55, ~85 ; MigrationRollbackJdbcAdapter.java:145-153 | dater un butoir ou reformuler le jalon |
| 5 | minor | Javadoc "une reprise n'efface jamais une donnee" depasse le code : etat, qualite, vacancier, nom, dates remplacent sans condition (identique au legacy `apply`, donc parite OK) | ReprendreLesPatients.java:19-20, 111-112 | preciser le Javadoc |
| 6 | minor | Recherches S5a : `parCode`/`parNumeroDAssurance` prennent le 1er par `ORDER BY id` ; le legacy (`Optional` JPA) levait en cas de doublon | FichesPublieesJdbc.java (S5a, commit 31d3335e) | consigner l'ecart (non verrouille par un test) |

## Points verifies OK
- Parite lecteurs : S2 `noms` borne au centre (FichesPublieesJdbc.java:42) ; S3/S4 `carte` borne au centre ; S5b champs generateur nom/marque/etat toujours nuls comme le legacy (le mapper legacy ne les posait jamais : git grep f2af4349^) ; salle/creneau/generateur via `PlacesDesPatients.deLaFiche` ; `tous` mappe les 4 memes champs.
- Reecriture S6b : domaine dans patient-couverture (ReprendreLesPatients, ReprendreLesAssures) ; SQL borne au centre (`WHERE center_id = ?`, PatientsDeLaRepriseJdbc.java:46-49, 111-112, 120) ; memes colonnes ; defauts a la creation (en_sommeil, sous_kt, epo, fer = false ; nombre_enfants 0 ; jours false) identiques au `PatientMapper` legacy ; KB-S6b-1 (pieces jointes preservees) obtenu par ecriture ciblee ; ecriture directe des colonnes de place honnetement consignee (PatientsDeLaRepriseJdbc.java:27-29 ; ADR 0134:106, "a confirmer" avant le 2026-12-15).
- Goldens : diffs `_ecarts-legacy/*` contre `*_nouveau_code` ne montrent QUE les ecarts decides : affectations (erreur ASSURE_NOT_FOUND de `za901` disparue, principale conservee, etape 6 ajoutee), annulation (`MIS-A-JOUR` -> `EXISTANT`, 2 lignes), patients (orphelin -> `ZP111`). Archives presentes ; anciens approved supprimes (historique/ ne contient que les approved attendus).
- V57 additive, nullable, `ADD COLUMN IF NOT EXISTS` ; schema.sql coherent (colonne identique, ligne 1094) ; section 87 de verifications-production.md en SELECT seul et honnete (non executee en production).
- S6c : DDL `patients` de schema.sql couvre les colonnes de l'ancienne entite (champ par champ, VARCHAR(255)/TEXT/nullites) ; aucun reste de `bc-patient`/`bc-assure` dans poms, Dockerfile, scripts (git grep hors logs) ; reliquats seulement dans GEL.md (historique), logs a la racine, `.idea`, un golden legacy (type_inconnu.legacy.json:2) ; entrees `covered.json` retirees = uniquement des fichiers supprimes (aucun fichier existant) ; caches morts retires sans reference restante (grep backend).

## Questions ouvertes (non verifiees, tours epuises)
1. `seamAllowed` vide : `.ddd/config.json` non relu (git grep n'a rien renvoye pour la cle).
2. `FrontieresDesContextesTest` : non relu apres S6c.
3. Passe aleatoire rouge (`ControleDesPeremptionsCharacterizationIntegrationTest`, PK `factures`, seed 12709620220600) : cause NON verifiee. Hypothese : defaut d'isolation preexistant, la classe herite de `ReferentielStockCharacterizationSupport` et le seed (`application.yml:26`, `db/seed.sql`, factures a identifiants fixes) est rejoue quand un contexte Spring est recree sur la meme base H2 en memoire partagee selon l'ordre des classes ; a confirmer en relisant le support et l'URL H2 (DB_CLOSE_DELAY). S6c (DDL `patients` dans schema.sql, donc cree avant Hibernate) pourrait modifier l'ordre mais rien ne le prouve.
4. Portee de `ReprendreLesAffectations` (parite de nulls) et PecValidation non relues.
