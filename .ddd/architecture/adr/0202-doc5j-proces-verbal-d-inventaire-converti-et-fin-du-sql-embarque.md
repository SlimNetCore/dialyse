---
status: accepted (livré le 2026-11-15, slice DOC5j FINALE, suite des ADR 0193 à 0201)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: stock, documents (port publié documents.v1, moteur de rendu, validateur), platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0146 (PL7), 0162, 0166 (règles d'impression), 0193 (port transitoire), 0201 (modèle de DOC5i) ; `doc5j-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0202 - DOC5j : le procès-verbal d'inventaire devient un jeu de données fourni par le Stock ; plus aucun gabarit n'embarque de SQL

## Contexte
Dernier gabarit à requête SQL embarquée : `inventaire_stock` (Stock). La requête lisait `inventaire_lignes` (un lot par ligne) filtrée sur l'inventaire (`INVENTAIRE_ID`) ET le centre, avec les libellés de motif, l'état de péremption (au `CURRENT_DATE`), l'écart et sa valeur, triée par libellé d'article sans casse, identifiant d'article, péremption, position.

## Décision
Le gabarit perd sa requête ; champs et paramètres inchangés (les paramètres `INVENTAIRE_ID` et `CENTER_ID` restent déclarés). Le Stock fournit les lignes sous `LIGNES`, sur ses deux voies :

| Élément | Décision |
|---|---|
| Composition | `LignesDuProcesVerbal` (ACL du Stock vers Documents, fonction pure) : mêmes champs (majuscules) et mêmes types que le gabarit ; ordre de l'ancienne requête (libellé sans casse ; identifiant d'article compris comme NON SIGNÉ, ordre de la base, et non comme `UUID.compareTo` ; péremption absente d'abord ; position) ; libellé de lot « sans lot » / « (trouvé) » ; motifs libellés, motif libre repris tel quel, « À justifier » pour un écart sans motif ; état de péremption au jour d'édition (expiré avant, proche dans les 90 jours) |
| Voie du procès-verbal du Stock | `ProcesVerbalRenduParDocuments.params` ajoute `LIGNES` à partir des lignes de l'inventaire déjà chargé : aucun nouveau port, aucune lecture de table |
| Voie de l'impression directe de Documents (par type et par modèle) | `RegleDImpressionDuProcesVerbalDInventaire` (documents.v1, aucune garde, comportement reproduit) pose `LIGNES` pour l'inventaire désigné par `INVENTAIRE_ID`, parmi ceux du centre demandé, par le port existant `Inventaires.parId` ; absent = aucune ligne ; vide ou illisible = 500 « Error executing SQL statement for: inventaire_stock. » (texte historique, `CAST` sans `NULLIF`) |

## Fin du SQL embarqué : retrait du port transitoire
Vérifié par Grep : aucun `.jrxml` livré ne contient `queryString` (14 gabarits sur 14). Supprimés : le port `ConnexionDeRendu` et son adapter `ConnexionDeRenduDeLaBase`, le bean et la dépendance de `JasperReportService` (qui n'ouvre plus aucune connexion : source de données fournie, sinon rapport rempli sans ligne). Les 14 gabarits restent rendus sur le chemin « centre sans modèle » (`RenduSansModeleDesGabaritsConvertisTest`, 12 gabarits à lignes dont `inventaire_stock`).

Garde PERMANENTE (sans date) :
- `JrxmlSecurityValidator` refuse toute requête (`QUERY_MODIFIED`), même si le modèle d'origine en portait une ; la comparaison « requête identique à l'original » disparaît ;
- `GabaritsSansRequeteTest.aucun_gabarit_livre_n_embarque_de_requete` parcourt tous les gabarits livrés ;
- `TableDuStockTest.le_proces_verbal_d_inventaire_n_embarque_plus_de_requete` verrouille le dernier gabarit converti.

Dépendance restante examinée : un modèle PERSONNALISÉ déjà stocké qui porte l'ancienne requête reste rendu (la source de données posée par le serveur prime, test conservé). Aucune dépendance bloquante : tous les types fournissent leurs lignes.

## Filet de rendu (approuvé sur l'ANCIEN code avant toute modification)
`characterization/dossiermedical/dm2/doc5j/*` (2 goldens, TOUT le texte du PDF) : procès-verbal du Stock (clôturé, en cours, annulé, sans ligne, inventaire d'un autre centre) sur un jeu aux ordres délicats (libellés égaux à la casse près, identifiants d'article de bit de poids fort différent, péremption absente, libellé absent, motifs de tous types, lots non comptés, trouvés, PMP absent, texte spécial) ; impression directe par type et par modèle (inventaire du centre, d'un autre centre, inconnu, identifiants absents, vides, illisibles). Les goldens existants (`inventaire_pl7`, `doc1`, `doc4b`, `doc4c`, `doc5a` à `doc5i`) restent inchangés.

## Comportements de bord (à confirmer métiers <= 2026-12-15)
- **KB-DOC5J-1** (même nature que KB-DOC5A-1 à KB-DOC5I-1) : le téléversement d'un modèle `INVENTAIRE_STOCK` portant encore l'ANCIENNE requête est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement. S'étend à tous les types : plus aucune requête n'est admise dans un modèle téléversé.
- **KB-DOC5J-2** : reproduit (type non gardé par profil, KB-DOC1-3 ; impression directe sans en-tête : « Inventaire au null » ; 500 sur identifiant vide ou illisible ; inventaire inconnu ou d'un autre centre = « Aucune ligne »).
- **KB-DOC5J-3** : un appel direct au moteur sans `LIGNES` donne un rapport sans ligne (aucun appelant de production).

## Gardes datées
Aucune entrée de garde ne concernait ce gabarit (les tables lues sont celles du Stock) : rien à retirer, aucun report.

## Conséquences
- Plus aucun gabarit à SQL embarqué : DOC5 est CLOS (14 gabarits sur 14 sans requête).
- Tests adaptés (goldens inchangés) : `JrxmlSecurityValidatorTest` (le gabarit d'inventaire cesse de servir de référence à une requête), `InventaireStockReportTemplateTest` (utilise les lignes du Stock), `RenduSansModeleDesGabaritsConvertisTest` (+1 gabarit, sans port de connexion), `ConnexionDeRenduEtGabaritsSansRequeteTest` devient `GabaritsSansRequeteTest` (tests de connexion supprimés).
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
