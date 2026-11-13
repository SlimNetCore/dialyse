---
status: realise (comportement inchange ; D5 colonnes de place : ecriture directe gardee, a confirmer metiers/DPO <= 2026-12-15)
date: 2026-11-13
sources: ADR 0134, 0168, 0169, 0170, 0171, `inventaire-platform-restant-v2.md` ligne 11, `.ddd/safety-net/known-bugs.md` (KB-S6a-3), `rp2-realisation.md`
---

# RP3a - realisation

| Etape | Resultat |
|---|---|
| Constat | Aucun golden d'import n'existait pour les attestations et les prises en charge (seules les annulations RP1/RP2 les touchaient) : filet ecrit AVANT le code, sur le code d'avant (production temporairement remisee par `git stash`, puis restauree) |
| Filet | `CouvertureHistoriqueRp3aCharacterizationIntegrationTest` : 5 goldens `*_rp3a` : import des attestations (controles, 13 formats de date en simulation, doublons dans le fichier, periode reprise, rejeu, mise a jour, ligne de la plateforme au meme debut) ; import des attestations, autre centre et roles (5 roles, 403 avant 400, `centerId` d'un autre centre, lot d'un autre centre, lot inconnu) ; import des prises en charge (forfait inconnu / d'un autre centre, dates, statut, valeurs par defaut de la demande, rejeu, meme debut dans un autre centre, roles) ; annulation du lot avec lignes voisines d'un autre centre ; annulation refusee par une ligne de la plateforme puis acceptee. Approuves UN PAR UN par renommage de `.received` (jamais `-Dcharacterization.approve`), identiques avant et apres |
| Port Patient et couverture | `publishedlanguage.RepriseDeLHistoriqueDeCouverture` (6 operations + constantes de nature), cas d'usage `ReprendreLHistoriqueDeCouverture`, port sortant `CouvertureDeLaReprise`, adapter `CouvertureDeLaRepriseJdbc`, bean dans `PatientCouvertureConfiguration`. `AnnulationDeLaReprise` : `supprimerUneAttestation`, `supprimerUnePriseEnCharge` |
| Contrat | `RepriseDeLHistoriqueDeCouvertureContrat` joue par le double en memoire et JDBC (H2, 5 tests chacun) ; contrat d'annulation etendu (7 tests chacun) |
| Platform | `HistoricalRecordJdbcAdapter` : `TABLES` 7 -> 5, plus de `new Table("attestation_droit"...)` ni `"prise_en_charge"` ; `MigrationRollbackJdbcAdapter` : plus de `DELETE FROM` a nom calcule, branche generique supprimee |
| Butoir | entree de `HistoricalRecordJdbcAdapter` SUPPRIMEE de `TableDuPatientCouvertureTest` (aucun report), constante `HORS_VERROU` retiree, test de verrou ajoute ; 2 regles ArchUnit (port reserve aux deux adapters de la reprise, l'import l'utilise) |
| Parite | goldens S6a, RP1, RP2, SE7e inchanges : aucun `_nouveau_code`, aucune archive `_avant_rp3a`, aucune KB corrigee |
| D5 | Colonnes de place ecrites par la reprise des patients (ADR 0134, KB-S6a-3) : ecriture directe GARDEE (decision utilisateur par defaut), tracee dans l'ADR 0171 : **a confirmer metiers/DPO <= 2026-12-15** ; aucun code touche |

Fichiers legacy de platform modifies : `HistoricalRecordJdbcAdapter.java`, `MigrationRollbackJdbcAdapter.java` (deja dans `covered.json`, aucun blocage du hook). `seamAllowed` = []. `.ddd/state.json` non touche. Docs : RG-MIG-010 (14), glossaire patient-couverture, inventaire ligne 11 (avancement partiel), ADR 0171.

Passes ciblees : voir la reponse (journaux `backend/rp3a-filet1.log`, `rp3a-p1.log` a `rp3a-p3.log`, `rp3a-full.log`).

Reste hors perimetre : RP3b (import dossier medical, antecedents, serologies, abords, analyses ; controle `bons_sortie` par le Stock), RP3c (`OpeningBalanceJdbcAdapter`, seance historique).
