---
status: proposed
date: 2026-11-12
sources: pl7-filet.md, pl6-realisation.md, ADR 0145/0146, known-bugs.md (KB-PL7-1..5)
---

# PL7 - realisation

PV d'inventaire (`GET /api/v1/stock/inventaires/{id}/rapport`) et feuille de comptage (`GET|POST .../{id}/feuille-comptage`) migres de platform vers `contexts/stock` (refactoring sur place, ADR 0024, memes URL/roles). Decisions : ADR 0146. Aucun flag, rien commite.

## Cree / modifie
- stock : `infrastructure/acl/documents/ProcesVerbalRenduParDocuments` (ACL vers `RenduDeDocument`, type INVENTAIRE_STOCK, `params` public) ; `infrastructure/classeur/FeuilleDeComptageExcel` (POI, deplace de platform) ; `application/inventaire/FeuilleInvalide` (400) ; `interfaces/http/ErreursDesInventairesAdvice`, `IdentifiantIllisible` ; `InventairesControleur` (parametres lus a la main, verbes 405) ; `GestionDesInventaires` (motif sans ecart refuse) ; beans dans `StockContexteConfiguration` ; pom : documents-api + poi-ooxml 5.2.5.
- platform : `InventaireReportService`, `FeuilleComptageExcel` supprimes (retires de `covered.json`) ; `AuditActionResolver` (INVENTAIRE_PV_IMPRESSION, INVENTAIRE_FEUILLE_TELECHARGEMENT) ; `inventaire_stock.jrxml` (« Aucune ligne », pied d'article conditionnel) ; `FrontieresDesContextesTest` (+2 regles) ; `InventaireStockReportTemplateTest`.
- docs : ADR 0146, KB-PL7-1..5, RG-STK-086, annexe, glossaire stock.
- tests : stock `ProcesVerbalRenduParDocumentsTest` (2), `FeuilleDeComptageExcelTest` (4, +titre absent).

## Goldens (relus un par un depuis `.received`)
9 passes en `*_nouveau_code.approved.json` (anciens dans `inventaire_pl7/_ecarts-legacy/*_avant_pl7.legacy.json`) : `pv_pl7_garde_roles_parametres`, `pv_pl7_sans_ligne_texte`, `pv_pl7_sans_ligne_cloture_texte`, `pv_pl7_audit`, `import_pl7_cas_mixtes(+_lignes)`, `import_pl7_formats_et_entetes(+_lignes)`, `import_pl7_refus`. Autres goldens PL7 et S7 (13 tests) inchanges.
Adaptation des fixtures : les feuilles d'import des scenarios « formats », « refus », « audit » portent maintenant un titre ; la ligne L-TARD du cas mixte et le cas « titre en minuscules » n'ont plus de motif sans ecart (sinon 400 masquerait le reste). Le refus 400 « motif sans ecart » est observe (message dans le diff) mais n'a pas de golden dedie : a ajouter si souhaite.

## Execution
3 passes ciblees (3e `-Dsurefire.runOrder=random`) : 87 tests (PL7 14, S7 13, template PV, ArchUnit, stock) verts. check-arch : 0 violation. Build complet `-o test -fae` : BUILD SUCCESS (platform 3444 tests, 0 echec, 4 ignores ; stock 208 ; 0 echec).

## Reproduit / a confirmer metiers avant 2026-12-15
KB-PL7-3 (police), lot « sans lot » = reference d'inventaire apres cloture, lots negatifs/0,001 absents, theorique arrondi a 2 decimales. Barre finale et `.pdf` : 500 (route inconnue, transverse). Un motif sans ecart rejette toute la feuille (pas une anomalie par ligne) : a confirmer.

## Limites
Modele de PV personnalise par un centre : ancienne mise en page. Doc fonctionnelle/maquette non regenerees.

## Bascule
Aucun flag. Retour arriere = revert du commit.
