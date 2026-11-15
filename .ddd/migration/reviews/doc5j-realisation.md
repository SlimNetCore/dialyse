---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0202, KB-DOC5J-1 à KB-DOC5J-3, ADR 0201, 0193 à 0200, 0166, 0146, 0024, doc5i-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5j (FINALE) - réalisation : le procès-verbal d'inventaire converti en jeu de données fourni par le Stock ; fin du SQL embarqué

## Choix du gabarit
`inventaire_stock` seul (dernier gabarit à requête : `inventaire_lignes` filtrée sur l'inventaire ET le centre ; libellés de motif, état de péremption au `CURRENT_DATE`, écart et valeur ; tri libellé sans casse, identifiant d'article, péremption, position).

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `InventaireStock5jCharacterizationIntegrationTest` (2 tests, 2 goldens `dm2/doc5j`) : tout le texte du PDF ; procès-verbal du Stock (clôturé, en cours, annulé, sans ligne, autre centre) sur un jeu aux ordres délicats ; impression directe par type et par modèle (inventaire du centre, d'un autre centre, inconnu, identifiants absents, vides, illisibles) ; approuvé sur l'ANCIEN code puis vert à l'identique ; goldens `inventaire_pl7` existants verts et inchangés | fait |
| `LignesDuProcesVerbal` (ACL Stock vers Documents), `ProcesVerbalRenduParDocuments.params` ajoute `LIGNES`, `RegleDImpressionDuProcesVerbalDInventaire` (documents.v1, aucune garde) câblée dans `StockContexteConfiguration` | fait |
| Gabarit `inventaire_stock.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Retrait du port transitoire : `ConnexionDeRendu`, `ConnexionDeRenduDeLaBase`, bean, dépendance de `JasperReportService` (plus aucun chemin du moteur n'ouvre de connexion) | fait |
| Garde permanente : `JrxmlSecurityValidator` n'admet aucune requête ; `GabaritsSansRequeteTest` (tous les gabarits livrés) ; `TableDuStockTest` (+1) | fait |
| Tests : `LignesDuProcesVerbalTest` (4), `RegleDImpressionDuProcesVerbalDInventaireTest` (5), `JrxmlSecurityValidatorTest` (+1, 2 adaptés), `RenduSansModeleDesGabaritsConvertisTest` (+1 gabarit, sans port), `GabaritsSansRequeteTest` (3, ex-`ConnexionDeRenduEtGabaritsSansRequeteTest`), `InventaireStockReportTemplateTest` (lignes du Stock) | fait |
| Gardes datées | aucune entrée ne concernait ce gabarit : rien à retirer, aucun report |
| ADR 0202 ; KB-DOC5J-1 à -3 ; RG-DOC-008 (note) ; glossaires Documents et Stock ; inventaire (ligne DOC5, bilan des 14 gabarits) | fait |
| `check-arch.ts` | 0 violation |

## Bilan DOC5 (14 gabarits, tous sans requête)
2 déjà sans requête avant DOC5 (PL8 `synthese_mensuelle_facturation`, PL9 `rapport_direction`) ; 12 convertis : DOC5a cahier, DOC5b bon d'intervention, DOC5c planning proposé, DOC5d ordonnance, DOC5e planning de présence, DOC5f liste des patients, DOC5g listes des ouvertures de droit et des prises en charge, DOC5h attestation et prise en charge, DOC5i fiche signalétique, DOC5j procès-verbal d'inventaire. Grep : plus aucun `queryString` dans les ressources ; plus aucune occurrence de `ConnexionDeRendu`.

## Vérification
- `check-arch.ts` : 0 violation.
- Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; filet doc5j, caractérisation documents, `inventaire_pl7`, gardes `Table*Test`, ArchUnit, modules stock et documents, validateur, gabarits, `DocumentIdentityIntegrationTest`, `ModeleDocumentTemplateIntegrationTest`, référentiel des règles de gestion) : 406 tests dans `platform` à chaque passe, 29 dans le Stock, 156 dans Documents ; seul échec : le connu `InventairePl7...feuille_de_comptage`. Une première passe avait révélé un écart de libellé du refus `QUERY_MODIFIED` (golden `doc3` du téléversement) : texte historique rétabli.
- Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5j-full.log`, terminé) : 3 590 tests dans `platform`, 1 échec connu et non touché : `InventairePl7CharacterizationIntegrationTest.le_contenu_de_la_feuille_de_comptage_reprend_lots_peremptions_quantites_et_motifs_tels_quels` ; aucun autre [ERROR] (`LancementEtSuivi...l_arret_anticipe` n'a pas échoué cette fois).
- Nettoyage : `*.received.json` supprimés, `backend/.ddd`, `bash.exe.stackdump`, `backend/NUL` absents.

## Parité
Textes du PDF identiques sur les goldens `dm2/doc5j` approuvés sur l'ancien code et sur les goldens `inventaire_pl7` ; goldens existants inchangés. Détails reproduits : ordre de l'identifiant d'article NON SIGNÉ (ordre de la base ; `UUID.compareTo` est signé) ; péremption et libellé absents d'abord ; impression directe sans en-tête (« Inventaire au null »), 500 sur identifiant vide ou illisible. Aucun comportement de rendu ne change.

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5J-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; le validateur n'admet plus aucune requête, quel que soit le type ; modèles stockés inchangés.
- KB-DOC5J-2 : reproduit.
- KB-DOC5J-3 : appel direct au moteur sans `LIGNES` = rapport sans ligne (aucun appelant de production).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié, aucune ligne à retirer de `covered.json`.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
