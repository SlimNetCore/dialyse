---
status: accepted (livré le 2026-11-15, slice DOC5g, suite des ADR 0193 à 0198)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: patient-couverture, documents (port publié documents.v1), platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162, 0165, 0166 (règles d'impression), 0193 à 0198 (DOC5a à DOC5f) ; `doc5g-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0199 - DOC5g : les listes des ouvertures de droit et des prises en charge deviennent des jeux de données fournis par Patient et couverture

## Contexte
Après DOC5f (ADR 0198), 6 gabarits embarquaient encore une requête SQL servie par `ConnexionDeRendu` : `inventaire_stock` (Stock), `fiche_patient`, `attestation`, `liste_attestations`, `liste_pec`, `prise_en_charge` (Patient et couverture). Choix : `liste_attestations` et `liste_pec` ont EXACTEMENT la même forme (une table de couverture jointe à `patients` sur le patient ET le centre, bornée au centre, un seul tri décroissant sur la date de fin, six ou sept colonnes, aucun `LIMIT`, aucun paramètre autre que le centre), alors que `attestation` et `prise_en_charge` filtrent sur deux identifiants avec `LIMIT 1` et joignent `centers` (annuaire d'Organisation) et que `fiche_patient` lit 45 colonnes. Les deux listes sont converties ensemble (requêtes quasi identiques), avec des filets, des ports et des tests séparés.

## Décision
Les deux gabarits perdent leur requête ; champs et paramètres inchangés. Patient et couverture fournit les lignes sous `LIGNES`.

| Élément | `liste_attestations` (`LISTE_ATTESTATIONS`) | `liste_pec` (`LISTE_PEC`) |
|---|---|---|
| Champs | `CODE_PATIENT`, `NOM`, `PRENOM`, `NUMERO_ASSURANCE`, `DATE_DEBUT`, `DATE_FIN` | idem avec `DATE_DEBUT_DEMANDE`, `DATE_FIN_DEMANDE`, `STATUT` |
| Port sortant (application) | `ListeDesAttestationsAImprimer.duCentre(centre)` | `ListeDesPrisesEnChargeAImprimer.duCentre(centre)` |
| Adapter | `ListeDesAttestationsAImprimerJdbc` (tables `attestation_droit` et `patients`, requête reprise telle quelle, tri par date de fin décroissante laissé au SGBD) | `ListeDesPrisesEnChargeAImprimerJdbc` (tables `prise_en_charge` et `patients`, tri par date de fin de demande décroissante) |
| Voie vers Documents | `RegleDImpressionDeLaListeDesAttestations` (`interfaces/publie`, documents.v1) : pose `LIGNES` sur les deux voies, aucune garde | `RegleDImpressionDeLaListeDesPrisesEnCharge` (idem) |
| Fake et contrat | `ListeDesAttestationsAImprimerEnMemoire` ; MÊME contrat (6 tests) sur le fake et sur H2 | idem (6 tests) |

Les dates sont posées en `java.sql.Date` et les valeurs absentes restent nulles (le gabarit imprime « null » pour un texte absent et la date avec l'heure `00:00`, comme avant). Aucune nouvelle dépendance de module (`documents-api` déjà ajoutée en DOC5f). La déclaration des types reste dans `TypesLivresDeDocuments` (Documents).

Filet de rendu AVANT toute modification (approuvé sur l'ANCIEN code, vert à l'identique ensuite) : `characterization/dossiermedical/dm2/doc5g/*` (4 goldens : TOUT le texte du PDF par type et par modèle, cellules du tableur, pages ; 46 et 47 lignes sur 2 pages, accents, noms en minuscules, codes vides, dates et statuts absents, ligne d'un autre centre, ligne dont le patient est d'un autre centre, centre sans ligne = page blanche KB-DOC1-4). Les goldens existants (`doc1`, `doc4b`, `doc4c`, `doc5f`) restent inchangés. Le gabarit converti est ajouté à `RenduSansModeleDesGabaritsConvertisTest` (chemin « centre sans modèle », leçon DOC5f : 8 cas).

## Comportements de bord changés
- **KB-DOC5G-1** (même nature que KB-DOC5A-1 à KB-DOC5F-1) : le téléversement d'un modèle `LISTE_ATTESTATIONS` ou `LISTE_PEC` portant encore l'ANCIENNE requête est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement. À confirmer métiers <= 2026-12-15.
- **KB-DOC5G-2** : reproduit (types toujours non gardés par profil, tri toujours par le SGBD, jointure toujours bornée au patient du même centre).
- **KB-DOC5G-3** : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production ; le test d'identité de document `DocumentIdentityIntegrationTest` fournit maintenant sa ligne).

## Gardes datées
Aucune entrée de `TableDuPatientCouvertureTest` ne concernait ces gabarits (la garde ne scrute que le Java ; liste blanche réduite à PLACE et CYCLE de la Planification) : rien à retirer, aucun report. Un test y est ajouté (les deux gabarits ne portent plus de requête ni de table du contexte).

## Conséquences
- 4 gabarits à SQL embarqué restent : `inventaire_stock` (Stock), `fiche_patient`, `attestation`, `prise_en_charge` (Patient et couverture). `ConnexionDeRendu` est conservé pour eux.
- Ordre suggéré : `attestation` et `prise_en_charge` (`LIMIT 1`, filtre à deux identifiants, `centers` via l'annuaire d'Organisation), puis `fiche_patient`, puis `inventaire_stock`.
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
