---
status: realise (comportement inchange ; KB-RP3B-1/2 reproduits, a confirmer metiers/DPO <= 2026-12-15)
date: 2026-11-13
sources: ADR 0134, 0168, 0169, 0170, 0171, 0172, `inventaire-platform-restant-v2.md` ligne 11, `.ddd/safety-net/known-bugs.md`, `rp3a-realisation.md`
---

# RP3b - realisation

| Etape | Resultat |
|---|---|
| Constat | Aucun golden d'import n'existait pour les cinq natures medicales (dossier de base, antecedents, serologies, abords, analyses) ni pour le controle `bons_sortie` : filet ecrit AVANT le code, sur le code d'avant |
| Filet | `HistoriqueMedicalRp3bCharacterizationIntegrationTest` : 7 goldens `*_rp3b` : import de chaque nature (controles, formats de date en simulation, doublons dans le fichier, periode de reprise, champs vides, rejeu et mise a jour, ligne de la plateforme a la meme cle) ; autre centre et roles (5 roles x 5 natures, 403 avant 400, `centerId` d'un autre centre, lot d'un autre centre, lot inconnu) ; annulation et `bons_sortie` (patient du lot, autre patient, sans patient, autre centre, ordre des controles par rapport aux analyses et aux bilans pre-greffe). Approuves UN PAR UN par renommage de `.received` (jamais `-Dcharacterization.approve`). Le golden des abords, corrige apres une erreur de fixture (booleen illisible), a ete regenere sur le code d'AVANT (copie `git worktree` de HEAD, sans stash) puis compare au code nouveau : identiques octet pour octet ; copie supprimee |
| Port Dossier medical | `publishedlanguage.RepriseDeLHistoriqueMedical` (`dejaPresente`, `reprendre`, `completer` ; cinq natures en liste fermee), adapter `RepriseDeLHistoriqueMedicalJdbc` (descripteurs et requetes repris mot pour mot), bean dans `DossierMedicalConfiguration` ; contrat joue par le double en memoire et JDBC (H2, 5 tests chacun) |
| Port Stock | `publishedlanguage.BonsDeSortieDeLaReprise` (`bonsDeSortieDuPatient`, `NATURE`), adapter `BonsDeSortieDeLaRepriseJdbc`, bean dans `StockContexteConfiguration` ; contrat double + JDBC (3 tests chacun) |
| Platform | `HistoricalRecordJdbcAdapter` : plus aucun SQL, plus de `JdbcTemplate`, descripteur `TABLES` remplace par une table `MigrationEntity` -> nature ; `MigrationRollbackJdbcAdapter` : plus de `JdbcTemplate`, plus aucun SQL |
| Butoirs | entrees de `HistoricalRecordJdbcAdapter` SUPPRIMEES de `TableDuDossierMedicalTest` (8 -> 7) et `TableDesAnalysesTest` (5 -> 4) (2027-06-30) ; exclusion « /migration/ » de `TableDuStockTest` supprimee ; exemption `MigrationEntity.java` de `TableDesSeancesTest` soldee (`MENTIONS_NON_SQL` vide) ; 3 tests de verrou, 4 regles ArchUnit. Aucun report |
| Parite | goldens S6a, RP1, RP2, RP3a, SE7e inchanges : aucun `_nouveau_code`, aucune archive `_avant_rp3b`, aucune KB « CORRIGE » |
| Constats | KB-RP3B-1 (champs vides jamais ecrits, cle d'un abord sans date reduite a patient + type) et KB-RP3B-2 (bon de sortie de tout centre bloque l'annulation) REPRODUITS : a confirmer metiers/DPO <= 2026-12-15 |

Fichiers legacy de platform modifies : `HistoricalRecordJdbcAdapter.java`, `MigrationRollbackJdbcAdapter.java` (deja dans `covered.json`, aucun blocage du hook). `seamAllowed` = []. `check-arch.ts` : 0 violation. `.ddd/state.json` non touche. Docs : RG-MIG-010 (14), glossaires dossier-medical et stock, inventaire ligne 11, ADR 0172, known-bugs.

Passes ciblees (266 tests plateforme + contrats) : normale, `-Dsurefire.runOrder=random`, normale : vertes. Journaux : `backend/rp3b-filet1.log`, `rp3b-filet2.log`, `rp3b-ctx.log`, `rp3b-p1.log` a `rp3b-p3.log`, `rp3b-full.log`.

Echec « uniquement en build complet » (serologies, analyses : `modifie_apres_creation` false) : CAUSE = l'horloge systeme de Windows avance par paliers (mesure : pas moyen ~3 ms, max ~16 ms, `OffsetDateTime.now()` lu deux fois donne le meme instant) ; en JVM chaude l'import et son rejeu (`created_at` puis `updated_at = now()`) tombent dans le meme palier, d'ou `updated_at = created_at`. Ce n'est ni l'ordre des classes (bissection : dm*, migration, 2 moities de characterization verts ; l'ajout d'une trace ralentissant le test fait disparaitre l'ecart) ni le code de production (SQL repris mot pour mot de l'ancien adapter, meme comportement).
CORRECTIF (test seul, goldens `.approved.json` inchanges) : `importerRendu` attend que l'horloge ait depasse l'instant de la fin de l'import precedent avant d'en lancer un autre ; le rejeu est donc strictement posterieur a la creation, deterministe.
Verification : 3 passes ciblees (normale, random, normale : migration + dossiermedical) vertes ; build complet `backend/rp3b-full4.log`.

Reste hors perimetre : RP3c (`OpeningBalanceJdbcAdapter`, seance historique).
