---
status: realise (comportement inchange ; KB-S6a-8/9 reproduits, a confirmer metiers/DPO <= 2026-12-15)
date: 2026-11-13
sources: ADR 0134, 0168, 0169, `inventaire-platform-restant-v2.md` ligne 9, `.ddd/safety-net/known-bugs.md`
---

# RP1 - realisation

| Etape | Resultat |
|---|---|
| Filet (sur le code d'avant) | `AnnulationDUnLotRp1CharacterizationIntegrationTest` : 3 goldens `annulation_rp1_*` (donnees hors lot et ordre des controles ; patient modifie depuis l'import et donnees voisines des centres A et B ; assure rattache par une affectation hors lot), approuves un par un depuis `.received` (renommage, jamais `-Dcharacterization.approve`). Deja couverts : nominal, lot inconnu, deja annule, autre centre (admin B), roles, PEC bloquante, assure rattache a un patient existant |
| Port publie | `patientcouverture.publishedlanguage.AnnulationDeLaReprise`, `application.AnnulerLaReprise` + port `DonneesDeLaReprise`, `domain.DonneesCreeesParLeLot`, `infrastructure.persistence.DonneesDeLaRepriseJdbc`, bean dans `PatientCouvertureConfiguration` |
| Platform | `MigrationRollbackJdbcAdapter` n'ecrit ni ne lit plus `patients`, `assure`, `assure_patient` ; PEC et attestations lues par le port. Autres lectures (seances, factures, dossier medical) inchangees |
| Butoir | entree de la liste blanche de `TableDuPatientCouvertureTest` SUPPRIMEE (butoir 2027-03-31 solde, aucun report) ; test de verrou ajoute |
| Parite | goldens S6a et RP1 inchanges : aucun `_nouveau_code`, aucune archive `_avant_rp1` |
| Constats | KB-S6a-8 (patient modifie supprime sans avertissement) et KB-S6a-9 (controles sans borne de centre, premiere table nommee) : REPRODUITS, a confirmer metiers/DPO <= 2026-12-15 |

Tests contexte : contrat `AnnulationDeLaReprise` en memoire et JDBC (H2 de test) : 12 tests verts. Fichiers legacy de platform modifies (tous deja dans `covered.json`) : `MigrationRollbackJdbcAdapter.java`. `seamAllowed` = []. Aucun report de butoir. Reste hors perimetre : `HistoricalRecordJdbcAdapter` (SQL construit sur attestations et PEC, liste blanche inchangee), RP2.

Journaux : `backend/rp1-filet1.log`, `rp1-ctx.log`, `rp1-p1.log`, `rp1-p2.log`, `rp1-p3.log`, `rp1-full.log`.
