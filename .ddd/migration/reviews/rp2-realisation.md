---
status: realise (comportement inchange ; KB-S6a-8/9 reproduits, a confirmer metiers/DPO <= 2026-12-15)
date: 2026-11-13
sources: ADR 0134, 0168, 0169, 0170, `inventaire-platform-restant-v2.md` ligne 10, `.ddd/safety-net/known-bugs.md`, `rp1-realisation.md`
---

# RP2 - realisation

| Etape | Resultat |
|---|---|
| Filet (sur le code d'avant) | `AnnulationDUnLotRp2CharacterizationIntegrationTest` : 3 goldens `annulation_rp2_*` (donnees du lot supprimees par nature et donnees voisines conservees, autre patient et autre centre ; donnees medicales hors lot et ordre des controles, y compris donnee d'un autre centre ; volet medical, deux volets, facturee avant completee), approuves un par un depuis `.received` (renommage, jamais `-Dcharacterization.approve`). Deja couverts : S6a, RP1, SE7e (volet paramedical, facturee, autre centre), roles, lot inconnu ou deja annule |
| Port Seance | `SeancesDeLaReprisePubliees.laSeanceEstCompletee` (volet medical OU paramedical, sans centre) + `SeancesDeLaRepriseJdbc` ; contrat E17 joue par le double en memoire et JDBC (H2) |
| Port Dossier medical | `publishedlanguage.DonneesMedicalesDeLaReprise` (`lignesDuPatient`, `supprimerLaDonneeDuLot`, natures en liste fermee et ordre historique), `infrastructure.persistence.DonneesMedicalesDeLaRepriseJdbc`, bean dans `DossierMedicalConfiguration` ; contrat double en memoire + JDBC (4 tests chacun). Analyses (`resultats_analyses`) : meme port (elles sont au Dossier medical) |
| Platform | `MigrationRollbackJdbcAdapter` ne lit ni n'ecrit plus les volets, ni les 13 tables du dossier medical et des analyses ; reste `bons_sortie` (Stock) en SQL et la suppression des attestations et PEC (nom calcule, RP3) |
| Butoirs | entrees de `MigrationRollbackJdbcAdapter` SUPPRIMEES de `TableDesSeancesTest` (2027-03-31), `TableDuDossierMedicalTest` et `TableDesAnalysesTest` (2027-06-30) : aucun report ; tailles 6 -> 5, 9 -> 8, 6 -> 5 ; 3 tests de verrou ; 2 regles ArchUnit (port reserve a l'adapter, adapter l'utilise) |
| Parite | goldens S6a, RP1, SE7e inchanges : aucun `_nouveau_code`, aucune archive `_avant_rp2`, aucune KB corrigee |
| Constats | KB-S6a-8 et KB-S6a-9 REPRODUITS (annotation RP2 ajoutee) : a confirmer metiers/DPO <= 2026-12-15 |

Fichiers legacy de platform modifies (tous deja dans `covered.json`, aucun blocage du hook) : `MigrationRollbackJdbcAdapter.java`. `seamAllowed` = []. `check-arch.ts` : 0 violation. `.ddd/state.json` non touche. Docs : RG-MIG-010 (14), 07-seances, 16-annexe, glossaires seance et dossier-medical, inventaire ligne 10 (soldee), ADR 0170.

Passes ciblees (201 tests plateforme + 44 contrat Seance + 8 contrat Dossier medical) : normale, `-Dsurefire.runOrder=random`, normale : verts. Journaux : `backend/rp2-filet1.log` a `rp2-filet4.log`, `rp2-ctx.log`, `rp2-p1.log`, `rp2-p2.log`, `rp2-p3.log`, `rp2-full.log`.

Reste hors perimetre : `HistoricalRecordJdbcAdapter` (liste blanche inchangee), controle `bons_sortie` par le Stock, RP3.
