---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B1/PL7), pl6-filet.md, pl5-filet.md, known-bugs.md (KB-PL7-1..5)
---

# PL7 - filet de caracterisation du proces-verbal d'inventaire et de la feuille de comptage

Aucun code de production modifie, seamAllowed = []. Rien commite.

## Constat de depart
- Routes deja servies par `contexts/stock` (`InventairesControleur`) : `GET /api/v1/stock/inventaires/{id}/rapport` (PV PDF), `GET|POST /{id}/feuille-comptage`.
- Reste dans platform (a deplacer) : `InventaireReportService` (parametres Jasper, indicateurs, motifs), `reports/inventaire_stock.jrxml` (+ `ModeleDocumentPrinter`/`Catalog`), `FeuilleComptageExcel` (ecriture/lecture POI, 328 l.).
- Filet S7 existant (`InventaireCharacterizationIntegrationTest`, 13 tests) ne verrouillait du PV que statut, type, en-tete `%PDF` ; la feuille : structure aveugle/non aveugle et un import mixte.

## Ajout : `InventairePl7CharacterizationIntegrationTest` (14 tests, 24 goldens approuves un a un depuis les .received)
Meme harnais/contexte que S7 (`ReferentielStockCharacterizationSupport`, 1 contexte Spring). Goldens : `backend/platform/src/test/resources/characterization/stock/referentiel/inventaire_pl7/`. PDF compare sur le texte extrait (PDFBox), heures et references masquees.

| Famille | Golden(s) |
|---|---|
| PV provisoire, en cours partiellement compte, cloture avec ecarts par motif, annule | `pv_pl7_provisoire_texte`, `pv_pl7_en_cours_partiellement_compte_texte`, `pv_pl7_cloture_ecarts_texte`, `pv_pl7_annule_texte` |
| Inventaire sans ligne (en cours, cloture) | `pv_pl7_sans_ligne_texte`, `pv_pl7_sans_ligne_cloture_texte` |
| Cas limites : lot perime, textes speciaux/longs, `$P{}`, unicode, montants enormes | `pv_pl7_contenu_limites_texte` |
| Pagination 60 lignes (4 pages) | `pv_pl7_soixante_lignes` |
| Fuseau (stockage +01:00/+05:00 affiche en UTC) | `pv_pl7_fuseau_texte` |
| Garde de centre, 11 profils, anonyme, `?centerId`, ids/aveugle illisibles, en-tetes, methodes | `pv_pl7_garde_roles_parametres` |
| Feuille : cellules (lot perime, decimal, motif, libelle special/long, theorique) ; sans ligne ; inventaire annule | `feuille_pl7_contenu_limites`, `feuille_pl7_sans_ligne`, `feuille_pl7_inventaire_annule` |
| Import : virgule, formule, motif libre, repli code+lot, negatif, doublon ; formats/en-tetes/xls ; refus (gros, zip/OLE2 bidon, roles, autre centre) | `import_pl7_cas_mixtes(+_lignes)`, `import_pl7_formats_et_entetes(+_lignes)`, `import_pl7_refus(+_lignes)` |
| Audit : GET non suivis, POST import journalise `STOCK_CREATION` | `pv_pl7_audit` |

## Execution
3 passes (la 3e `-Dsurefire.runOrder=random`) avec S7 + PL7 : 27 tests, 0 echec, 0 flaky. PL7 ~4 s apres le demarrage ; contexte Spring ~30 s ; build ~60 s. 1 contexte Spring. Commande : `locked.ts maven -- <abs>\backend\mvnw.cmd -f backend/pom.xml -o -pl platform -am test -Dtest=InventairePl7CharacterizationIntegrationTest,InventaireCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`.

## Fichiers legacy proteges (covered.json)
`InventaireReportService.java`, `FeuilleComptageExcel.java` (deja inscrits) ; ajout de `reports/inventaire_stock.jrxml`.

## Bugs connus (non corriges) : KB-PL7-1..5
1 parametres illisibles = 500 (ids, centerId, aveugle, methodes, partie file absente) ; 2 PV sans ligne : ligne fantome « Article / null » ; 3 caracteres hors police perdus, tirets finaux d'un n° de lot ; 4 import : titre absent = aucune verification de reference, 1re feuille seule, motif sans ecart conserve, « 1e2 » accepte ; 5 (a confirmer) n° de lot = reference d'inventaire apres cloture pour le « sans lot », lots negatifs/0,001 absents, theorique arrondi a 2 decimales, PV non journalise.

## Limites
- Excel/HTML du PV : seul le PDF est verrouille (le modele personnalise par centre, `modele_document`, n'est pas teste ici).
- Horloge non injectable : « Edite le » et heures d'ouverture/cloture masquees ; fuseau fige par UPDATE SQL de `created_at/closed_at`.
- Logo et papier en-tete non observes. Anonyme = 403 (pas 401), transverse.
- Le positionnement du texte PDF (tableau des motifs) est verrouille dans l'ordre d'extraction PDFBox, pas visuellement.

## Decisions a demander
1. KB-PL7-1 : 400 propre pour ids/`centerId`/`aveugle` illisibles et methodes non gerees (comme PL3-PL6) ou reproduire ?
2. KB-PL7-2 : supprimer la ligne fantome du PV vide (message « Aucune ligne ») ?
3. KB-PL7-4 : exiger le titre de reference a l'import (refuser une feuille sans titre) ? Refuser un motif sans ecart ?
4. KB-PL7-5 : afficher « sans lot » apres cloture ? Journaliser l'impression du PV et le telechargement (comme `CAHIER_DIALYSE_IMPRESSION`) ?
5. Police : couvrir CJK/etoile (lie a KB-PL6-4/DOC2) ?
