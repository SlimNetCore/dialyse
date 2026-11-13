# DOC2 - réalisation (2026-11-13) - ADR 0162

Refactoring sur place (ADR 0024). Aucun golden modifié. Détail du placement, des décisions, du SQL des gabarits et de la question des polices : `.ddd/architecture/adr/0162-doc2-moteur-de-rendu-vers-le-contexte-documents.md`.

## Fait
- Moteur déplacé vers `backend/contexts/documents` : `JrxmlSecurityValidator`, `ModeleDocumentCatalog`, `FrenchFormatFactory` (langage publié) ; `JasperReportService`, `CustomTemplateCompiler`, `ModeleDocumentProvisioner`, `MoteurDeRenduJasper` (ex-`ModeleDocumentPrinter`) (infrastructure/rendu) ; 14 gabarits jrxml ; dépendances jasper/poi.
- Nouveaux éléments publiés : `RenduRefuse`, `GabaritPersonnaliseDuCentre` (fourni par la plateforme), `VerificationDeGabarit`, `MoteurDeRendu.rendreSansModele` ; `ErreursDuRenduAdvice` (422 identique).
- Plateforme : `ModeleDocumentTemplateService` implémente `GabaritPersonnaliseDuCentre` (DOC3), 2 ACL de Planification utilisent `MoteurDeRendu`, `DocumentRestController`/`ModeleDocumentTemplateRestController` changent d'import.
- Tests migrés avec le code : JrxmlSecurityValidatorTest, JasperTemplateCompileTest, ModeleDocumentCatalogTest. Règle ArchUnit anti-retour ajoutée.
- Tests de plateforme adaptés (chemins/imports) : PapierEnTeteEtModeles (appel via `MoteurDeRendu.rendreParType`, même goldens), IdentiteImprimeeCasLimites, Bon/Inventaire/Cahier/DocumentIdentity, FrontieresDesContextesTest, TableDesPayeursTest ; méta-assertions de liste blanche mises à jour (TableDesSeancesTest, TableDuDossierMedicalTest : les états Jasper changent seulement de chemin).

## Reste à faire
Voir la fin de l'ADR 0162 (décision police, DOC4 pour les 12 gabarits avec SQL, DOC3 pour le dépôt/versionnement).

## Écarts de parité
Aucun attendu ; résultats des passes ciblées et du build complet : voir le retour de l'agent.
