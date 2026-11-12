---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B1/PL6), pl5-filet.md, doc1-realisation.md, known-bugs.md (KB-GMAO-69/70/75, KB-PL6-1..6)
---

# PL6 - filet de caracterisation du bon d'intervention

Aucun code de production modifie, seamAllowed = []. Rien commite.

## Constat de depart
- Route : `GET /api/v1/gmao/interventions/{id}/bon`, deja servie par `contexts/gmao` (`SuiviControleur.bon` -> `ImprimerLeBon` -> port `ImpressionDuBon`).
- Reste dans platform : l'adaptateur `infrastructure/reporting/BonInterventionReportService` (parametres Jasper), `ModeleDocumentPrinter`, `ModeleDocumentCatalog`, `reports/bon_intervention.jrxml` : c'est la partie a deplacer vers GMAO via `documents.v1`.
- Filet existant (GMAO-G6) : `BonPdfCharacterizationIntegrationTest` (15 tests, 12 goldens : nominal, arrondis, fuseaux, statuts, donnees manquantes, durees/types/marques, chronologie, erreurs, autorisations 11 profils, formats du modele, modele desactive, editeur) + `BonInterventionReportTemplateTest`.

## Ajout (9 scenarios, 9 goldens approuves un a un depuis les .received)
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/pilotage/BonPdfPl6CharacterizationIntegrationTest.java` (meme harnais/contexte que G6) ; goldens `.../resources/characterization/gmao/pilotage/bon_pl6_*.approved.json`.

| Scenario | Golden |
|---|---|
| Garde de centre par le jeton : `?centerId`/`?centreId` ignores, ADMIN sans centre / centre illisible / vide / inexistant / autre societe, SUPERADMIN | `bon_pl6_garde_de_centre` |
| Parametres hostiles : id (majuscules, nil, sans tirets, tronque, `.pdf`, `/` final, vide, numerique), tz (balise, gabarit Jasper, 3000 car., decalages, GMT+1, Etc/GMT-14) | `bon_pl6_parametres_hostiles` |
| Caracteres speciaux, retours a la ligne, `$P{}`/`${}`, SQL, unicode | `bon_pl6_caracteres_speciaux_texte` |
| Textes tres longs (pages, marqueurs de fin) | `bon_pl6_textes_tres_longs` |
| 60 lignes de cout (pagination, total exact) | `bon_pl6_nombreuses_lignes_de_cout` |
| Montants nuls, negatifs, enormes, tres precis | `bon_pl6_montants_limites` |
| Rectifications multiples, auteur inconnu, motif blanc | `bon_pl6_rectifications_multiples_texte` |
| Designation d'equipement tres longue | `bon_pl6_designation_tres_longue` |
| Audit : GET/POST non journalises, aucune ecriture | `bon_pl6_audit` |

## Execution
3 passes (la 3e `-Dsurefire.runOrder=random`) avec `BonPdfCharacterizationIntegrationTest` : 25 tests, 0 echec, 0 flaky. ~60 s le build, ~40 s la classe (1 contexte Spring). Commande : `locked.ts maven -- <chemin absolu>\backend\mvnw.cmd -f backend/pom.xml -o -pl platform -am test -Dtest=...`.

## Fichiers legacy proteges (covered.json, deja inscrits, aucun ajout)
`BonInterventionReportService.java`, `ModeleDocumentPrinter.java`, `reports/bon_intervention.jrxml`, `CenterAccessGuard.java`.

## Bugs connus (non corriges) : KB-PL6-1..6
1 ADMIN sans centre = 500, centre illisible = 400 avec message technique ; 2 ids mal formes = 500 ; 3 montant enorme rogne, montants negatifs acceptes ; 4 caracteres hors police perdus ; 5 designation longue tronquee ; 6 chronologie : « Terminee » dupliquee par rectification, motif vide, impression non journalisee.

## Limites
- Excel/HTML au niveau HTTP : seul le type de contenu est verrouille (G6) ; le contenu du modele personnalise par centre ne l'est pas.
- Logo et papier en-tete societe/centre : seuls les noms sont observes (papier en-tete couvert par DOC1).
- Horloge non injectable : « Edite le » masque, decalage de fuseau non fige a la minute.
- Anonyme = 403 (pas 401), transverse (KB-GMAO-19).

## Decisions a demander
1. KB-PL6-1/2 : 400 propre (ou 403) pour ADMIN sans centre et ids mal formes (comme PL3/PL4/PL5) ou reproduire ?
2. KB-PL6-6 : journaliser l'impression du bon (comme `CAHIER_DIALYSE_IMPRESSION`) ? Corriger les « Terminee » dupliquees ?
3. KB-PL6-3 : refuser les montants negatifs/quantites negatives ? Elargir le champ de montant ?
4. KB-PL6-5 : tronquer avec points de suspension ou passer a la ligne ?
