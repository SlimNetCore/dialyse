---
status: proposed
date: 2026-11-12
sources: pl8-filet.md, pl7-realisation.md, ADR 0147 (decisions), known-bugs.md (KB-PL8-1..7, KB-RF4-02, KB-FAC-59)
---

# PL8 - realisation

Synthese mensuelle de facturation + RF3c-J1 (synthese) : refactoring sur place (ADR 0024), memes URL/roles/codes. ADR 0147. Aucun flag, retour arriere = revert du commit. seamAllowed = [], aucun fichier platform bloque, rien commite.

## Cree / modifie
- facturation : `SyntheseMensuelle.lignes` ; `application/pilotage/CaissesDesAgences` + ACL `infrastructure/acl/patientcouverture/CaissesDesAgencesParLAnnuaire` (annuaire `PayeursDuCentre`) ; `LectureDeLaSyntheseJdbc` (plus de jointure payeurs, code normalise, tableau, tri Java, arrondi a la paire, seances `FACTUREE`) ; `publishedlanguage/ModeleDeLaSynthese` (parametres, `LIGNES`, classement CNAS/CASNOS/CAMSSP/Autres + `AVEC_GRAPHIQUE` pour la voie documents) ; `infrastructure/acl/documents/SyntheseRendueParDocuments` ; `infrastructure/classeur/SyntheseEnClasseur` (POI) ; `SyntheseRequete`, `ErreursDeLaSyntheseAdvice`, `SyntheseControleur` (audit) ; pom (documents-api) ; config.
- seance : `SeancesDesFacturesPubliees.nombreParFactureAuStatutFacturee` (nouvelle lecture, l'existante et le tableau de bord inchanges) + Jdbc, fake, contrat.
- documents : `ParametresReserves` (+LIGNES, TOTAL_*, DISTRIBUTION_*).
- platform : gabarit `synthese_mensuelle_facturation.jrxml` sans requete (tableau croise + graphique conditionnel) ; `ModeleDocumentPrinter` (LIGNES -> source de donnees pour toute generation), `JasperReportService.fillReport` ; `DocumentRestController.donneesDuServeur` ; `AuditActionResolver`, `AuditRequestFilter.ATTRIBUT_LIBELLE` ; 405 par prefixe dans `DatesIllisiblesDeLaDirectionAdvice` ; `SeancesDesFacturesParLaSeance`.
- Supprimes : `FacturationSyntheseMensuelleReportService`, `CaissesDesAgences`, `CaissesDesAgencesParLAnnuaire` (platform), `CodeDeCaissePiegeSyntheseMensuelleTest`, `SyntheseMensuelleSqlPostgresContractIT` (testaient le SQL supprime) ; `covered.json` mis a jour.
- Tests : `SyntheseRendueParDocumentsTest` (3), contrat JDBC `should_build_the_forfait_by_fund_table...` (tri en ordre des caracteres, part du TTC, HT nul ; rejoue par `PilotagePostgresContractIT` avec `-Dfacturation.pg16.url`), contrat seance `...invoiced_status_seances...` ; `TableDesPayeursTest` (2 entrees retirees, effectif 3) ; `FrontieresDesContextesTest` (+3 regles : classes supprimees, ACL -> `RenduDeDocument`, gabarit sans requete).

## Goldens (relus un par un depuis `.received`, protocole `*_nouveau_code.approved.json`)
24 goldens promus ; anciens archives dans `_ecarts-legacy/*_avant_pl8.legacy.json` (synthese-pl8, synthese-imprimee, synthese-modele-document), anciens approved non suffixes `git rm`. Ecarts constates et rattaches aux decisions : donnees identiques (montants, indicateurs, repartitions) ; colonne Inconnu unique et code normalise (chaines atypiques) ; seances `FACTUREE` (sans_seance : 1 -> 0) ; troncature corrigee (textes longs sur 2 pages) ; 400/405/415 ; audit ; Excel tabulaire ; pages 4 -> 3 (60 forfaits). Ecarts de mise en page NON decides, consequences du gabarit statique unique : ordre d'extraction des en-tetes, separateur de milliers « 1 150,45 » (moteur de documents), tuiles reordonnees, `]]>` d'un libelle de caisse rendu tel quel (avant : `] ]>`). Voie documents : classement et graphique conserves, donnees identiques. `le_format_absent_est_un_pdf` : echec dû a la pagination (2 pages), corrige en reglant les largeurs.

## Reproduit / questions ouvertes (metiers, <= 2026-12-15)
KB-PL8-3 (police, DOC2) ; critere « seance valide » = `FACTUREE` (conservateur) ; date blanche = 1970-01-01 ; periode illisible sur la voie documents = 500 (message nouveau) ; coupure horizontale du tableau au-dela de 4 caisses (A4) ; somme des cellules arrondies a la paire vs « Montant TTC » enregistre (ecart de quelques centimes possible) ; tri par ordre des caracteres (ex. `FORFAIT 01` < `Forfait 01` < `forfait 01`).

## RF3c-J1 : reste hors synthese (slice PL8b)
`LectureDuTableauDeBordJdbc` (jointures non bornees, KB-FAC-65), `SituationsDesFacturesJdbc` (COALESCE instantane / payeur courant, filtres/pagination), `SyntheseFinanciereDesCentresJdbc` (KB-RF3-07) : 3 entrees de butoir 2027-03-31 dans `TableDesPayeursTest`. Prerequis : lecture publiee groupee de la chaine des payeurs d'une facture + parite PG16.

## Execution
Voir le rapport final de l'agent (3 passes ciblees dont random, build complet `-o test -fae`, check-arch).
