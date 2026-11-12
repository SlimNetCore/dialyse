---
status: proposed
date: 2026-11-12
sources: pl6-filet.md, pl5-realisation.md, ADR 0142/0144/0145, known-bugs.md (KB-PL6-1..6)
---

# PL6 - realisation

Impression du bon d'intervention (`GET /api/v1/gmao/interventions/{id}/bon`) : l'adaptateur du port `ImpressionDuBon` est desormais une ACL de `contexts/gmao` vers `RenduDeDocument` (documents.v1). `BonInterventionReportService` supprime de platform. Refactoring sur place (ADR 0024), memes URL/roles/en-tetes. Decisions : ADR 0145. Aucun flag, rien commite.

## Cree / modifie
- gmao : `infrastructure/acl/documents/BonRenduParDocuments` (+ `BonRenduParDocumentsTest`, 8 tests) ; `SessionCourante.centrePourImpression`, `SessionSansCentre` (403), `CentreIllisible` (400), `IdentifiantIllisible` ; `SuiviControleur.bon` ; `ErreursDeLaGmaoAdvice` ; bean dans `GmaoConfiguration` ; `ImpressionDuBonDeTest` (double de `RenduDeDocument`).
- build : nouveau module `contexts/documents-api` (`RenduDeDocument`, `DocumentRendu` deplaces, meme paquet) car `documents -> organisation -> gmao` interdit `gmao -> documents` (cycle Maven). Poms : root, documents, gmao.
- platform : `BonInterventionReportService` supprime (git rm, retire de `covered.json`) ; `AuditActionResolver` (`BON_INTERVENTION_IMPRESSION`) ; `reports/bon_intervention.jrxml` (designation extensible + blocs Float, champs de montant elargis) ; `FrontieresDesContextesTest` (+2 regles) ; `BonInterventionReportTemplateTest` (params via l'ACL publique).
- docs : ADR 0145, KB-PL6-1..6 (CORRIGE/REPRODUIT), RG-GMA-068, annexe de tracabilite, glossaire gmao.

## Goldens (relus un par un depuis `.received`)
7 goldens passes en `*_nouveau_code.approved.json` (anciens archives `gmao/pilotage/_ecarts-legacy/*_avant_pl6.legacy.json`) : `bon_erreurs`, `bon_pl6_audit`, `bon_pl6_designation_tres_longue`, `bon_pl6_garde_de_centre`, `bon_pl6_parametres_hostiles`, `bon_pl6_rectifications_multiples_texte`, `bon_pl6_montants_limites`. Les 14 autres goldens G6/PL6 inchanges (dont `bon_nominal_texte`, autorisations, formats, modele desactive).

## Ecarts reproduits
KB-PL6-4 (caracteres hors police). `.../bon.pdf` et `.../bon/` restent en 500 (route inconnue, transverse). Lignes de cout negatives inserees hors API : imprimees telles quelles.

## Points a confirmer
- Chronologie : dedoublonnage limite aux clotures strictement identiques (instant + auteur) ; des clotures d'heures differentes restent. Si « une seule Terminee » est voulu, regle a durcir.
- Modeles de bon deja personnalises par un centre : ancienne mise en page.
- Doc fonctionnelle/maquette non regenerees.

## Bascule
Aucun flag. Retour arriere = revert du commit.
