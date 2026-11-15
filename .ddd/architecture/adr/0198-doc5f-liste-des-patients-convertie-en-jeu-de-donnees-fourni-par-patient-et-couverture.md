---
status: accepted (livré le 2026-11-15, slice DOC5f, suite des ADR 0193 à 0197)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: patient-couverture, documents (port publié documents.v1), platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162, 0165, 0166 (règles d'impression), 0193 à 0197 (DOC5a à DOC5e) ; `doc5f-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0198 - DOC5f : la liste des patients devient un jeu de données fourni par Patient et couverture

## Contexte
Après DOC5e (ADR 0197), 7 gabarits embarquaient encore une requête SQL servie par le port transitoire `ConnexionDeRendu` : `inventaire_stock` (Stock) et six gabarits patient. Choix du plus simple des six, par analyse des requêtes : `liste_patients.jrxml` (type `LISTE_PATIENTS`) lit UNE seule table (`patients`, sans jointure, bornée au centre, `ORDER BY nom, prenom`, dix colonnes imprimées), là où `liste_attestations` et `liste_pec` joignent deux tables, `attestation` et `prise_en_charge` deux ou trois tables avec un filtre à deux identifiants et `LIMIT 1`, et `fiche_patient` (551 lignes) lit `patients` et `centers` sur 45 colonnes. Aucun autre gabarit ne partage exactement cette requête : un seul gabarit converti.

## Décision
Le gabarit perd sa requête ; ses champs (`CODE_PATIENT`, `NOM`, `PRENOM`, `SEXE`, `GROUPE_SANGUIN`, `DATE_NAISSANCE`, `DATE_ADMISSION`, `NUMERO_ASSURANCE`, `TEL_MOBILE`, `ETAT_PATIENT`) et ses paramètres (dont `CENTER_ID`) sont inchangés. Patient et couverture fournit les lignes sous `LIGNES`.

| Élément | Choix |
|---|---|
| Port sortant possédé | `ListeDesPatientsAImprimer.duCentre(centre)` (application) : les patients du centre, triés par nom puis prénom ; adapter `ListeDesPatientsAImprimerJdbc` (la seule table `patients`, requête reprise telle quelle, tri laissé au SGBD donc même collation qu'avant) |
| Voie vers Documents | `RegleDImpressionDeLaListeDesPatients` (`interfaces/publie`) implémente `RegleDImpressionDUnType` (documents.v1, ADR 0166) : `donneesDuServeur(centre, …)` pose `LIGNES` sur les deux voies d'impression ; `garder` ne fait rien (le type n'était pas gardé, il ne l'est toujours pas : KB-DOC1-3) |
| Dépendance | `contexts-patient-couverture` dépend de `contexts-documents-api` (comme `contexts-dossier-medical`, aucun cycle) |
| Fake | `ListeDesPatientsAImprimerEnMemoire` ; le MÊME contrat (5 tests) passe sur le fake et sur H2 (pas de variante PostgreSQL : même requête qu'avant) |
| Déclaration du type | inchangée : `TypesLivresDeDocuments` (Documents) continue de déclarer `LISTE_PATIENTS` |

Filet de rendu avant toute modification : `characterization/dossiermedical/dm2/doc5f/*` (2 scénarios : TOUT le texte du PDF par type et par modèle, cellules du tableur, nombre de pages) approuvé sur l'ANCIEN code puis vert à l'identique : 51 patients sur deux pages, nom en minuscules, accents, deux patients de même nom (prénoms en casse différente, ordre par points de code), champs absents ou vides, noms très longs, patient d'un autre centre écarté ; centre sans patient (page blanche, KB-DOC1-4). Les goldens existants (`doc1`, `doc4b`, `doc4c`) restent inchangés.

## Comportements de bord changés
- **KB-DOC5F-1** (même nature que KB-DOC5A-1 à KB-DOC5E-1) : le téléversement d'un modèle `LISTE_PATIENTS` portant encore l'ANCIENNE requête est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement (la source de données fournie prime). À confirmer métiers <= 2026-12-15.
- **KB-DOC5F-3** : le jeu de données est posé par le cas d'usage d'impression, pas par le moteur ; un appel direct à `MoteurDeRendu.rendreParType` sans `LIGNES` donne une page blanche. Aucun appelant de production ; le test DOC1 `PapierEnTeteEtModelesCharacterizationIntegrationTest` (qui appelle le moteur) pose maintenant les lignes via la règle publiée, ses goldens sont inchangés.
- Aucun écart de tri (le SGBD trie toujours), aucun changement de droit.

## Correction de la régression « centre sans modèle » (DOC5)
`MoteurDeRenduJasper.rendreSansModele` applique désormais `avecSourceDeDonnees` : les gabarits convertis rendent leurs lignes aussi pour un centre sans modèle du type (avant : page blanche, régression de DOC5e/DOC5c, le gabarit lisait la base avant la conversion). Test commun `RenduSansModeleDesGabaritsConvertisTest` sur les six gabarits convertis ; aucun golden modifié.

## Gardes datées
Aucune entrée de `TableDuPatientCouvertureTest` ne concernait ce gabarit (la garde ne scrute que le Java ; liste blanche réduite à PLACE et CYCLE de la Planification) : rien à retirer, aucune date reportée. Un test y est ajouté (le gabarit ne porte plus de requête ni de table du contexte).

## Conséquences
- 6 gabarits à SQL embarqué restent : `inventaire_stock` (Stock), `fiche_patient`, `attestation`, `liste_attestations`, `liste_pec`, `prise_en_charge` (Patient et couverture). Le port `ConnexionDeRendu` est conservé pour eux.
- Ordre de simplicité suggéré pour la suite : `liste_attestations` et `liste_pec` (même forme : jointure `patients` + une table, bornée au centre), puis `attestation` et `prise_en_charge` (`LIMIT 1`, filtre à deux identifiants, `centers` via l'annuaire d'Organisation), puis `fiche_patient`.
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
