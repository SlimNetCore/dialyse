---
status: accepted (livré le 2026-11-15, slice DOC5i, suite des ADR 0193 à 0200)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: patient-couverture, organisation (annuaire publié), documents (port publié documents.v1), platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162, 0165, 0166 (règles d'impression), 0174 (annuaire des centres), 0200 (modèle de DOC5h), 0193 à 0200 (DOC5a à DOC5h) ; `doc5i-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0201 - DOC5i : la fiche signalétique du patient imprimée devient un jeu de données fourni par Patient et couverture

## Contexte
Après DOC5h (ADR 0200), 2 gabarits embarquaient encore une requête SQL servie par `ConnexionDeRendu` : `inventaire_stock` (Stock) et `fiche_patient` (Patient et couverture). La requête de `fiche_patient` sélectionne 35 colonnes de `patients` (dont 11 jamais déclarées comme champs du gabarit) et `LEFT JOIN centers` pour le nom du centre, filtrée sur le patient ET le centre.

## Décision
Le gabarit perd sa requête ; champs et paramètres inchangés. Patient et couverture fournit l'unique ligne sous `LIGNES` (liste vide si rien à imprimer).

| Élément | `fiche_patient` (`FICHE_PATIENT`) |
|---|---|
| Champs | les 24 champs déclarés (`CODE_PATIENT` à `ASSURE_PRENOM`, dont `DATE_NAISSANCE` et `DATE_ADMISSION` en `java.sql.Date`, `NOMBRE_ENFANTS` en entier, `SOUS_KT` en booléen) et `CENTER_NAME` ; les colonnes non déclarées (EPO, fer, assuré détaillé) ne sont plus lues |
| Port sortant (application) | `FicheSignaletiqueAImprimer.duPatient(centre, patient)` |
| Composition (application) | `ComposerLaFicheSignaletiqueImprimee` : patient absent ou d'un autre centre = rien ; le nom du centre vient du port `NomsDesCentres` (DOC5h) ; centre inconnu de l'annuaire = fiche imprimée SANS nom (ancien `LEFT JOIN`) |
| Adapter | `FicheSignaletiqueAImprimerJdbc` (table `patients`, filtre patient + centre, identifiants liés) |
| Voie vers Documents | `RegleDImpressionDeLaFicheSignaletique` (`interfaces/publie`, documents.v1) : pose `LIGNES` sur les deux voies, aucune garde |
| Fake et contrat | `FicheSignaletiqueAImprimerEnMemoire` ; MÊME contrat (4 tests) sur le fake et sur H2 |

Lecture de l'identifiant, reproduisant l'ancienne requête (`CAST($P{patientId} AS UUID)` SANS `NULLIF`, contrairement à l'attestation) : absent = rien (page blanche) ; VIDE ou illisible = 500 « Error executing SQL statement for: fiche_patient. » (texte historique). Par type, la demande refuse déjà `patientId` absent, blanc ou illisible en 400 (`DemandeDImpression`, inchangé).

Filet de rendu AVANT toute modification (approuvé sur l'ANCIEN code, vert à l'identique ensuite) : `characterization/dossiermedical/dm2/doc5i/*` (3 goldens, TOUT le texte du PDF par type et par modèle : patient riche, patient hors KT, valeurs absentes ou vides, patient inconnu, d'un autre centre, identifiants absents, vides, illisibles). Les goldens existants (`doc1`, `doc4b`, `doc4c`, `doc5f` à `doc5h`) restent inchangés. Le gabarit converti est ajouté à `RenduSansModeleDesGabaritsConvertisTest` (chemin « centre sans modèle »).

## Comportements de bord (à confirmer métiers <= 2026-12-15)
- **KB-DOC5I-1** (même nature que KB-DOC5A-1 à KB-DOC5H-1) : le téléversement d'un modèle `FICHE_PATIENT` portant encore l'ANCIENNE requête est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement.
- **KB-DOC5I-2** : reproduit (type non gardé par profil ; 400 par type, 500 par modèle sur identifiant vide ou illisible ; page blanche pour un patient inconnu ou d'un autre centre).
- **KB-DOC5I-3** : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production ; `DocumentIdentityIntegrationTest` fournit la ligne).

## Gardes datées
Aucune entrée de garde ne concernait ce gabarit : rien à retirer, aucun report. Un test de `TableDuPatientCouvertureTest` verrouille l'absence de requête et de table du contexte dans le gabarit.

## Conséquences
- 1 gabarit à SQL embarqué reste : `inventaire_stock` (Stock). `ConnexionDeRendu` est conservé pour lui.
- Tests existants adaptés (goldens inchangés) : `JrxmlSecurityValidatorTest` (deux tests qui s'appuyaient sur la requête de `fiche_patient` prennent `inventaire_stock`) ; `DocumentIdentityIntegrationTest` fournit la ligne de la fiche.
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
