---
status: accepted (placement) ; accepted (polices : option A, DejaVu Sans, décision du 2026-11-16, ADR 0208) ; SQL des gabarits : levé par DOC5 (ADR 0202)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: documents, platform
related: ADR 0024, 0140, 0142, 0145-0148 ; `.ddd/safety-net/known-bugs.md` KB-PL6-4, KB-PL7-3, KB-PL8-3, KB-PL9-6 ; `.ddd/migration/reviews/doc2-realisation.md`
---

# 0162 - DOC2 : le moteur de rendu passe au contexte Documents

Refactoring sur place (ADR 0024) : aucun changement de comportement, aucun golden modifié. Le texte rendu des PDF (goldens comparés sur le texte extrait) et la sécurité des gabarits sont inchangés.

## Placement
| Avant (plateforme) | Après (`contexts/documents`) |
|---|---|
| `application.reporting.JrxmlSecurityValidator` (liste blanche) | `publishedlanguage.JrxmlSecurityValidator` (le téléversement DOC3 reste à la plateforme et l'utilise) |
| `infrastructure.reporting.ModeleDocumentCatalog` | `publishedlanguage.ModeleDocumentCatalog` (types livrés ; DOC4 : déclarés par les contextes propriétaires) |
| `FrenchFormatFactory` | `publishedlanguage.FrenchFormatFactory` |
| `JasperReportService`, `CustomTemplateCompiler`, `ModeleDocumentProvisioner` | `infrastructure.rendu.*` (beans déclarés par `DocumentsConfiguration`) |
| `ModeleDocumentPrinter` (implémente `MoteurDeRendu`) | `infrastructure.rendu.MoteurDeRenduJasper` ; l'API `print`/`Document` disparaît au profit de `MoteurDeRendu.rendreParType` |
| 14 gabarits `platform/src/main/resources/reports/*.jrxml` | `contexts/documents/src/main/resources/reports/` (même chemin de classpath `reports/x.jrxml` : les modèles en base ne changent pas) |
| Dépendances `jasperreports`, `poi`, `poi-ooxml` | `contexts/documents/pom.xml` (mêmes versions ; transitives pour la plateforme) |
| Tests `JrxmlSecurityValidatorTest`, `JasperTemplateCompileTest`, `ModeleDocumentCatalogTest` | migrés avec leur code, inchangés hors paquet et chemins |

Restent à la plateforme (DOC3) : `ModeleDocumentTemplateService` (dépôt/versionnement des gabarits personnalisés, JPA), `ModeleDocumentTemplateRestController`, `DocumentRestController` (`/modeles`, `/types`), `CacheConfig` (cache `reporting.customTemplates`).

## Décisions de conception
- D1. Deux ports nouveaux dans le langage publié, FOURNIS PAR LA PLATEFORME jusqu'à DOC3 : `GabaritPersonnaliseDuCentre` (le moteur lit la version personnalisée active ; implémenté par `ModeleDocumentTemplateService`) et, dans l'autre sens, `VerificationDeGabarit` (implémenté par `CustomTemplateCompiler`, consommé par le téléversement). Aucun nouveau fichier de plateforme.
- D2. `RenduRefuse` (langage publié, code stable) remplace la `BusinessException` du moteur : `ErreursDuRenduAdvice` (global, priorité maximale, précédent `ErreursDuPilotageAdvice`) répond 422 « Regle metier violee » + `code` à l'identique. L'ACL du calendrier de proposition (Planification) traduit `RenduRefuse` en `ImpressionRefusee`.
- D3. `MoteurDeRendu.rendreSansModele` (gabarit livré sans modèle de centre, lecture seule) remplace l'appel direct de l'ACL de présence à `JasperReportService`. L'ACL de présence n'importe plus ni Jasper ni le service.
- D4. Inséparables du moteur donc déplacés malgré DOC3 : le catalogue des types livrés, le provisionnement (`ModeleDocumentProvisioner`, lit l'annuaire des centres d'Organisation) et la lecture de `modele_document` par type. La gestion CRUD des modèles et des versions reste DOC3.
- D5. Règle ArchUnit anti-retour (`FrontieresDesContextesTest.le_moteur_de_rendu_n_est_plus_a_la_plateforme`).
- D6. Sécurité : validateur déplacé sans modification de code ; ses 3 suites (liste blanche, compilation, catalogue) migrent et restent vertes.

## SQL dans les gabarits livrés (prérequis de DOC4, Q6 de l'ADR 0140) - constat, non corrigé
Seuls `synthese_mensuelle_facturation.jrxml` (PL8) et `rapport_direction.jrxml` (PL9) sont sans requête (règle ArchUnit existante). Les 12 autres contiennent encore une `<queryString>` :
| Gabarit | Type | Tables lues | Contexte propriétaire |
|---|---|---|---|
| `cahier_dialyse` | CAHIER_DIALYSE | seances, volet_paramedical, volet_medical, dossier_medical_patient, forfait, prise_en_charge, prescriptions_medicales, articles, stock_movements | Séance (autres contextes lus : Dossier médical, Patient, Stock) - rendu déjà confié à Documents (PL5) mais requête encore dans le gabarit |
| `bon_intervention` | BON_INTERVENTION | gmao_interventions, gmao_lignes_cout_intervention | GMAO (PL6) |
| `inventaire_stock` | INVENTAIRE_STOCK | inventaire_lignes | Stock (PL7) |
| `attestation`, `liste_attestations` | ATTESTATION, LISTE_ATTESTATIONS | attestation_droit, patients, centers | Patient et couverture (centers : Organisation) |
| `fiche_patient`, `liste_patients` | FICHE_PATIENT, LISTE_PATIENTS | patients, centers | Patient et couverture / Organisation |
| `prise_en_charge`, `liste_pec` | PEC, LISTE_PEC | prise_en_charge, patients | Patient et couverture |
| `ordonnance` | ORDONNANCE | ordonnances, lignes_ordonnance, patients, centers | Dossier médical (garde de profil par `ReglesDImpressionDesTypesSensibles`) |
| `planning_presence_infirmiers` | PLANNING_PRESENCE_INFIRMIERS | infirmier_absence, infirmier_affectation, infirmier_remplacement, infirmier, salle, position_creneau | Planification (infirmiers, salles : Organisation) |
| `planning_optimise` | PLANNING_OPTIMISE | planification_calendrier_case | Planification |
Chacun lit au moins une table d'un autre contexte que son propriétaire ou une table du propriétaire par le moteur de Documents (connexion JDBC partagée). Le motif `LIGNES` (PL8/PL9) est le chemin de sortie : chaque contexte propriétaire fournit ses lignes et déclare son type (DOC4). Les trois gabarits de PL5-PL7 sont les premiers à sortir (rendu déjà confié à Documents).

## Caractères hors police (KB-PL6-4, PL7-3, PL8-3, PL9-6) - faisabilité, aucun changement de rendu
Constat : aucun gabarit ne déclare de `fontName` ni de `pdfFontName` ; Jasper 6.21.5 utilise sa police par défaut (Helvetica, encodage Cp1252, non embarquée). Cp1252 conserve accents, Œ/œ et € (conforme aux goldens) et perd tout le reste (japonais, chinois, étoile, arabe). Le moteur désormais dans Documents permet de choisir une police par propriété Jasper (`jasperreports.properties` du module) sans toucher aux gabarits. Options pour décision de l'utilisateur :
| Option | Couverture | Licence | Coût / risque |
|---|---|---|---|
| A. DejaVu Sans via l'artefact `net.sf.jasperreports:jasperreports-fonts:6.21.5` (déjà dans le dépôt Maven local) + police par défaut `DejaVu Sans`, PDF Identity-H embarqué | Latin étendu, grec, cyrillique, étoile et symboles, arabe (formes de présentation ; Jasper/OpenPDF ne fait pas de mise en forme bidi : lettres isolées, ordre non garanti) ; PAS de CJK | licence Bitstream Vera / DejaVu (libre, redistribuable, embarquement autorisé) - à re-vérifier sur le fichier de licence de l'artefact avant livraison | métriques différentes d'Helvetica : retours à la ligne et tailles de colonnes changent, tous les goldens PDF seraient à réviser |
| B. Noto Sans (+ Noto Sans CJK JP / Arabic en repli) | CJK, arabe, étoile | SIL OFL 1.1 (embarquement et redistribution autorisés, vente de la police seule interdite) | ajouter les fichiers (CJK : plusieurs Mo), famille de polices Jasper à écrire, mêmes impacts sur les goldens, PDF plus lourds |
| C. Statu quo + remplacement explicite | aucune | - | à la saisie (ou au rendu) : remplacer le caractère non imprimable par `?` au lieu de le perdre en silence ; changement visible minimal |
**Décision du 2026-11-16 : option A retenue (ADR 0208).**
Recommandation (historique) : décider d'abord le besoin métier (noms de patients ou d'articles non latins existent-ils ?) ; si non, option C ; si oui et sans CJK, option A ; avec CJK, option B. Toute option est un changement de rendu : slice dédiée avec révision des goldens, après DOC4.

## Conséquences
- Aucun golden modifié ; tests de plateforme qui lisaient `platform/src/main/resources/reports` adaptés au nouvel emplacement ; `FrontieresDesContextesTest`/`Table*Test` pointent le nouveau chemin.
- La plateforme ne garde de Jasper que les ACL de Planification (paramètres Jasper `JRParameter`, à nettoyer en PL10) et les tests de gabarits.
- Questions ouvertes : (1) police (options A/B/C) ; (2) calendrier DOC4 des 12 gabarits avec SQL ; (3) `ModeleDocumentTemplateService` implémente un port de Documents jusqu'à DOC3.
