---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0193, KB-DOC5A-1 à 3, ADR 0024, 0144, 0162 à 0166, doc2/doc3/doc4a-c-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5, décision D1)
---

# DOC5a - réalisation : port de connexion transitoire et cahier de dialyse converti en jeu de données

## État d'avancement
| Étape | État |
|---|---|
| Port de connexion transitoire `ConnexionDeRendu` (Documents, application) + adapter `ConnexionDeRenduDeLaBase` (lecture seule, refermée) ; `JasperReportService` ne tient plus de `DataSource` | fait |
| Filet de rendu `doc5a/cahier` (2 tests, tout le texte du PDF : 5 séances, dossier, 3 prescriptions, EPO et fer, 2 prix d'un même article, entrée/ajustement, surcharges, médecin, infirmier, secrétaire sans volet médical ; patient nu), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Stock : langage publié `SortiesValoriseesDesSeances` + `SortiesValoriseesDesSeancesDuStock` (sous-requête reprise mot pour mot) | fait |
| Dossier médical : langage `seance.v1` `DossierEtTraitementDuCahier` + `DossierEtTraitementPourLeCahier` (dossier de base, prescription en vigueur, articles par `CatalogueDesArticles`) | fait |
| Séance : `ComposerLesLignesDuCahier`, ports `SeancesImprimablesDuCahier`, `SortiesDeLaSeance`, `DossierEtTraitementDuPatient`, `LigneDuCahier`, adapters (`SeancesImprimablesDuCahierJdbc`, ACL Stock, ACL Dossier médical), `DemandeDeCahier.lignes`, traducteur `CahierRenduParDocuments.champs` | fait |
| Gabarit `cahier_dialyse.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Gardes : `TableDesSeancesTest` (liste blanche vide, constante du butoir supprimée), `TableDuDossierMedicalTest` (reste l'ordonnance) | fait |
| `CahierDialyseReportIntegrationTest` : rempli par les lignes composées (mêmes 5 assertions) | fait |
| Tests unitaires : `ComposerLesLignesDuCahierTest` (28), `CahierRenduParDocumentsTest` (+2), `ImprimerLeCahierDeDialyseTest` (adapté), `ConnexionDeRenduEtGabaritsSansRequeteTest` (3 : lecture seule, fermeture sur échec, modèle stocké à l'ancienne requête sans connexion) | fait |
| ADR 0193 ; KB-DOC5A-1 à 3 ; RG-SEA-045b ; RG-DOC-008 (note DOC5a) ; glossaires Séance et Documents ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; filet doc5a, cahier PL5/SE7e, `characterization/documents/**`, gardes, validateur, compilation, référentiel) | vertes |
| Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5a-full.log`) | 3 556 tests dans `platform`, 1 échec : `InventairePl7...feuille_de_comptage` (connu, date) ; aucun autre [ERROR] ; processus Maven terminé |

## Parité
Textes du PDF identiques sur le filet `doc5a/cahier` (3 profils, 2 scénarios) approuvé sur l'ancien code ; goldens PL5 (8) et SE7e du cahier (3) inchangés ; aucun `*_nouveau_code`, aucune archive `_ecarts-legacy`. Aucun comportement de rendu ne change. Comportement de bord changé, consigné et à confirmer : KB-DOC5A-1 (téléversement d'un modèle du cahier portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles déjà stockés inchangés).

## Les 11 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`bon_intervention` (GMAO, 17 lignes de SQL, tables `gmao_interventions` et `gmao_lignes_cout_intervention`) **prochain candidat** ; `inventaire_stock` (Stock, dernier : 1 010 l.) ; `planning_presence_infirmiers`, `planning_optimise` (Planification) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge` (Patient et couverture) ; `ordonnance` (Dossier médical, garde `TableDuDossierMedicalTest` jusqu'au 2027-03-31). Méthode éprouvée : (1) filet de rendu sur l'ancien code ; (2) ports/langages du contexte propriétaire ; (3) mise en forme en Java, testée ; (4) retrait de la requête et des entrées de garde ; (5) un modèle stocké garde ses lignes (la source de données prime).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier `platform/src/main/java` modifié, donc aucune ligne à retirer de `covered.json`.
- Limites : les trois adapters JDBC nouveaux (séances imprimables, sorties valorisées, dossier et traitement) sont verrouillés par le filet de bout en bout sur H2 ; pas de test de contrat dédié fake/réel. Le forfait de la prise en charge passe par `ForfaitDeLaPriseEnCharge` (mêmes règles que l'ancienne sous-requête, vérifiées sur une prise en charge unique : plusieurs prises en charge se chevauchant ne sont pas dans le filet).
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, parfois `LancementEtSuiviDesOptimisations...`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
