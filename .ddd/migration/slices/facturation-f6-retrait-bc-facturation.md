---
slice: facturation-f6-retrait-bc-facturation
status: built
date: 2026-10-21
sources: [ .ddd/migration/roadmap.md § 1 ter, .ddd/safety-net/facturation-f6-retrait-bc-facturation.md, ADR 0024, ADR 0030..0035, slices gmao-g6 temps 2 et comptabilite-c7 ]
---

# F6 : retrait de `bc-facturation` et derniers consommateurs de la Facturation

## Objectif

Décommissionner le module `backend/bc-facturation` (vide depuis F5) et faire passer les derniers lecteurs et écrivains des
tables de la Facturation par son langage publié (`contexts/facturation/publishedlanguage`). Refonte sur place (ADR 0024) :
mêmes tables, mêmes URL, ni façade, ni routage, ni projection. Deux écarts de comportement décidés par l'utilisateur le
2026-10-21 (KB-FAC-67 et KB-FAC-70).

## Réalisation (2026-10-21)

**Étape A, retrait du module.** `<module>` et `dependencyManagement` de `backend/pom.xml`, dépendance de `platform/pom.xml`,
ligne `COPY` du `Dockerfile`, boucles et `--sourceDirs` des scripts `pit-c3-ecritures.sh` et `pit-f3-validation.sh` ;
dossier `backend/bc-facturation/` supprimé (pom, README, `target/`). Mentions restantes dans `.idea/` (`compiler.xml`,
`encodings.xml`, `workspace.xml`, `copilotDiffState.xml`) : fichiers d'IDE, non modifiés (l'IDE les régénère).

**Étape B, langage publié.**

| Port | Où il est publié | Fournisseur | Consommateur |
|---|---|---|---|
| `FacturationDuPatient.periodeFacturee` | Facturation | `FacturationDuPatientJdbc` | `AbsenceDonneesJdbcAdapter` (Absences) |
| `RepriseDesSoldes` (ouvrir, remplacer, supprimer, règlements hors reprise, factures d'un patient) | Facturation | `RepriseDesSoldesJdbc` (SQL déplacé tel quel) | `OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter` (ACL fines ; la Migration garde ses ports) |
| `FacturesEmises`, `ReglementsEncaisses` | **Comptabilité** (voir ADR 0035 : la Facturation connaît la Comptabilité, un cycle Maven interdit l'inverse) | `FacturesEmisesJdbc`, `ReglementsEncaissesJdbc` (`infrastructure/acl/comptabilite`) | `FacturesAComptabiliserDepuisLaFacturation`, `ReglementsAComptabiliserDepuisLaFacturation` (ACL de la Comptabilité) |
| `SyntheseDeFacturation`, `SyntheseMensuelle`, `FormatDImpression`, `BarresDeRepartition`, `ImpressionDeLaSynthese` | Facturation (types déplacés de `application.pilotage`) | `ConsulterLaSynthese` via le bean de configuration | `DocumentRestController`, `FacturationSyntheseMensuelleReportService` |
| `DeclarationDesAbsences` | Facturation (besoin fourni par Absences) | `AbsencesDesPatientsLegacy` (platform) | `AbsencesDesPatientsPubliees` (ACL de la Facturation) |

Retirés : `FacturesAComptabiliserJdbc`, `ReglementsAComptabiliserJdbc` (Comptabilité), `sansEcritureEntre` du port
`FacturesAComptabiliser` (la liste « sans écriture » est calculée par le cas d'usage avec `EcritureComptableRepository.parSource`),
`SeanceBilledStockModificationException` (bc-stock). Les trois exemptions de la règle ArchUnit
`le_legacy_n_utilise_la_facturation_que_par_son_langage_publie` sont supprimées (0 exemption).

Conservés sans changement (hors périmètre, lecture de la table de la Séance) : `SeanceBillingStatusJdbcAdapter.isBilled`
(Stock), statut `FACTUREE` de la Séance ; `ReferentialAdminTables` (blocage de suppression d'un forfait par
`facture_lignes.forfait_id`, usage déclaré).

## Écarts de comportement décidés (2026-10-21)

1. **KB-FAC-67** : le rebuild de la Comptabilité (`POST /comptabilite/rebuild`, `GET /comptabilite/ventes/non-comptabilisees`,
   comptabilisation P1) **exclut** les factures de reprise (`numero_facture` commençant par `REPRISE-`) et leurs règlements de
   reprise (auteur `REPRISE`) ; un encaissement saisi ensuite dans la plateforme sur une telle facture reste comptabilisé.
   Goldens `reprise/compta_*_nouveau_code` (aucune pièce générée ; les trois anciens goldens retirés).
   Contrôle de production : `verifications-production.md` point 22 ; extourne des écritures déjà générées à décider avec le
   comptable (non exécutée).
2. **KB-FAC-70** : toute modification refusée parce que la séance est `FACTUREE` répond **422 `SEANCE_FACTUREE_NON_MODIFIABLE`**
   (titre « Regle metier violee », détail « La seance facturee ne peut plus etre modifiee »). Conditions de refus et définition de
   « séance facturée » inchangées (KB-FAC-69 reproduit). Goldens `seance-facturee/*_nouveau_code` (11, diff relu avant
   promotion : seuls `code`, et pour trois routes `detail`/`title`, changent ; l'état SQL après refus est inchangé).

| Route | Avant | Après |
|---|---|---|
| date, forfait, valider, consommables (ajout, modification, retrait), volet médical, volet paramédical | 422 `BUSINESS_RULE_VIOLATION` | 422 `SEANCE_FACTUREE_NON_MODIFIABLE` |
| signature médecin | 422 `BUSINESS_RULE_VIOLATION` (« signature autorisée uniquement après validation infirmière ») | 422 `SEANCE_FACTUREE_NON_MODIFIABLE` |
| déverrouillage de régularisation | 422 `SEANCE_DEVERROUILLAGE_INVALIDE` | 422 `SEANCE_FACTUREE_NON_MODIFIABLE` (les autres refus du déverrouillage gardent leur code) |
| administration de traitement, bon de sortie (PUT, annulation des sorties) | 422 `SEANCE_BILLED_STOCK_EXIT_IMMUTABLE` | 422 `SEANCE_FACTUREE_NON_MODIFIABLE` |
| suppression | 422 `SEANCE_FACTUREE_NON_SUPPRIMABLE` | inchangé |

3. **Reproduits** (décision) : KB-FAC-68 (rejeu refusé annonce `updated:1`), KB-FAC-71 (`saisi_par` = principal), KB-FAC-69
   (trois définitions de séance facturée).

## Tests

- 27 scénarios de caractérisation F6 + 3 règles ArchUnit : verts sur le nouveau code (goldens inchangés hors les 14 écarts décidés).
- Nouveaux : contrats `LecturesPubliquesContrat` (10 tests : période facturée, factures émises, règlements encaissés, exclusion de la
  reprise) et `RepriseDesSoldesContrat` (8) exécutés contre le double en mémoire, JDBC H2 et PostgreSQL 16 (`*PostgresContractIT`,
  plus propriétés PG : arrondi `NUMERIC(14,2)`, casse du préfixe `REPRISE-`).
- Script PG16 : `backend/scripts/facturation-pg16-rejeu.sh` étendu (étape F6).

## Plan de bascule

Refonte sur place : pas de flag. Déploiement : contrôles `verifications-production.md` point 22 (SELECT seulement) ; informer
l'équipe frontend du nouveau code d'erreur (voir ci-dessous). Retour arrière = commit précédent (aucune migration).

## À faire côté frontend (non modifié ici)

Gérer `SEANCE_FACTUREE_NON_MODIFIABLE` (422) sur : modification de la date, du forfait, validation, signature médecin, ajout,
modification et retrait de consommable, volets médical et paramédical, déverrouillage de régularisation, administration de
traitement (anémie), bon de sortie. Les codes `SEANCE_BILLED_STOCK_EXIT_IMMUTABLE` (plus émis) et `BUSINESS_RULE_VIOLATION` pour
ces cas ne se produisent plus ; le frontend ne référençait pas `SEANCE_BILLED_STOCK_EXIT_IMMUTABLE` (grep du 2026-10-21).

## État final

`backend/bc-facturation` supprimé. Facturation hors F7 terminée. Reste : F7 (clôture mensuelle, nouvelle fonctionnalité, ADR 0009).
