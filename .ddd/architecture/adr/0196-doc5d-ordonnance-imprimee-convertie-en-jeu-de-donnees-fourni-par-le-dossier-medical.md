---
status: accepted (livré le 2026-11-15, slice DOC5d, suite des ADR 0193 à 0195)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: dossier-medical, documents, platform (filet et garde)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162 à 0167 (Documents), 0166 (règle d'impression publiée), 0174 (nom du centre), 0193 (DOC5a), 0194 (DOC5b), 0195 (DOC5c) ; `doc5d-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0196 - DOC5d : l'ordonnance imprimée devient un jeu de données fourni par le Dossier médical

## Contexte
Après DOC5c (ADR 0195), 9 gabarits embarquaient encore une requête SQL servie par le port transitoire `ConnexionDeRendu`. L'ordonnance (`ordonnance.jrxml`, type `ORDONNANCE`, RG-MED-055) joignait en SQL QUATRE tables de TROIS contextes : `ordonnances` et `lignes_ordonnance` (Dossier médical), `patients` (Patient et couverture : nom, prénom, naissance, numéro d'assurance) et `centers` (Organisation : nom du centre). C'était la dernière entrée de la garde `TableDuDossierMedicalTest` (butoir 2027-03-31). Le Dossier médical possède le type, publie déjà sa règle d'impression à Documents (`RegleDImpressionDeLOrdonnance`, ADR 0166) et pose déjà côté serveur des paramètres de ce type.

## Décision
Le gabarit perd sa requête (`queryString`) ; champs et paramètres sont inchangés (`CENTER_ID` et `ordonnanceId` restent déclarés). La règle d'impression publiée pose les lignes sous `LIGNES` par `donneesDuServeur` (mécanisme déjà utilisé par la synthèse de facturation), sur les deux voies (par type et par modèle).

| Élément | Choix |
|---|---|
| Use case | `ComposerLesLignesDeLOrdonnance` (application) : compose, comme les jointures internes d'avant, une ligne par ligne d'ordonnance ; vide si l'ordonnance est inconnue ou d'un autre centre, si le patient n'est pas du centre, si le centre est inconnu ou s'il n'y a aucune ligne |
| Port nouveau | `OrdonnancesAImprimer` (lecture brute de `ordonnances` et `lignes_ordonnance`, sans l'agrégat) + adapter `OrdonnancesAImprimerJdbc` (la part Dossier médical de l'ancienne requête) |
| Port existant | `IdentitesDesPatients` (état civil, `FichesDesPatients` de Patient et couverture) : aucun SQL sur `patients` |
| Port nouveau | `NomsDesCentres` + ACL `NomsDesCentresPublies` sur `AnnuaireDesCentres.nomDuCentre` (organisation.v1) : aucun SQL sur `centers` |
| Mise en forme | `COALESCE(libellé du code, nom saisi)` en Java (`ComposerLesLignesDeLOrdonnance.medicament`) ; traducteur `ChampsDeLOrdonnanceImprimee` (noms et types du gabarit : `java.sql.Date` pour naissance et prescription, entier pour durée et quantité, une valeur absente reste nulle) |
| Contrat | `OrdonnancesAImprimerContrat` joué sur le double en mémoire et H2 (même jeu) |

## Pourquoi un port de lecture brute plutôt que `Ordonnances` (l'agrégat)
L'état n'a jamais contrôlé ni le statut ni le contenu des lignes (filet DM2 : `BROUILLON`, `ANNULEE`, `IMPRIMEE` s'impriment tous). Reconstituer l'agrégat aurait changé le rendu : une ordonnance au statut illisible est ignorée (KB-MED-102), les validations de `LigneDOrdonnance` et `Medicament` (posologie, durée, quantité, format de code) refuseraient des lignes anciennes, et l'agrégat se cherche par patient alors que la route n'en reçoit pas. Refusé.

## Ordre des lignes
L'ancienne requête n'avait AUCUN ordre sur les lignes (`ORDER BY p.nom`, constant pour une ordonnance) : l'ordre est celui où la base rend la jointure, observé égal à l'ordre d'insertion (filet). La lecture des lignes reste donc SANS `ORDER BY` (contrairement à `OrdonnancesJdbc`, qui trie libellé puis posologie pour l'API) : un tri changerait l'ordre imprimé d'une ordonnance existante. Conséquence assumée : sur PostgreSQL l'ordre reste celui du tas, comme avant (non garanti, KB-MED-16 inchangé pour l'état imprimé).

## Parité
Filet `characterization/documents/doc5d` (`OrdonnanceImprimee5dCharacterizationIntegrationTest`, 3 tests, TOUT le texte du PDF et le nombre de pages), approuvé sur l'ANCIEN code puis vert à l'identique : ordonnance riche à 14 lignes sur 2 pages (code affiché prioritaire, libellé seul, code sans libellé, ligne sans médicament, voie vide ou absente, durée et quantité absentes, instructions longues sur trois lignes, accents, naissance), imprimée par type ET par modèle ; ordonnance minimale (sans naissance, assurance vide, sans prescripteur ni numéro : « N° null », « Prescripteur : null » reproduits) ; états vides (inconnue, sans ligne, d'un autre centre, patient d'un autre centre : PDF d'une page blanche, 200) ; voie par modèle sans identifiant (page blanche), identifiant illisible ou vide (500). Les filets DM2 de l'impression (Excel : contenu, statuts, erreurs, gardes), le catalogue, la compilation des gabarits et le validateur restent verts, aucun golden modifié.

## Comportements de bord (consignés, à confirmer métiers <= 2026-12-15)
- KB-DOC5D-1 : le téléversement d'un modèle `ORDONNANCE` portant l'ANCIENNE requête (fichier téléchargé avant DOC5d) est refusé `QUERY_MODIFIED` (RG-DOC-004) ; les modèles déjà stockés s'impriment sans changement (la source de données fournie prime, mêmes noms de champs). Même nature que KB-DOC5A-1, KB-DOC5B-1, KB-DOC5C-1.
- KB-DOC5D-2 : sur la voie par modèle, un identifiant d'ordonnance illisible reste un échec d'impression (500) au MÊME texte de corps (« Error executing SQL statement for: ordonnance. ») bien qu'il n'y ait plus de SQL ; le texte est reproduit pour ne rien changer du contrat observé ; à nettoyer si les métiers acceptent un message honnête.
- Identifiant accepté : `UUID.fromString` (la voie par type valide déjà ainsi) ; les graphies de `CAST(... AS UUID)` que Java refuse (32 chiffres sans tirets) étaient acceptées par la base et seraient désormais en échec : non observé, non reproduit.

## Gardes datées
`TableDuDossierMedicalTest` : l'entrée `ordonnance.jrxml` (butoir 2027-03-31) est SUPPRIMÉE, aucun report ; la liste blanche est VIDE (le cahier, DOC5a, et les trois scripts de données, SEED2, étaient soldés avant). La constante du butoir est supprimée, un test verrouille que le gabarit ne lit plus ces tables (ni entrée, ni lecteur, ni `queryString`).

## Conséquences
- 8 gabarits à SQL embarqué restent : `inventaire_stock` (Stock), `planning_presence_infirmiers` (Planification), six gabarits patient (`fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge`, Patient et couverture). Le port `ConnexionDeRendu` est conservé pour eux.
- Aucun changement de route, de droit, de code d'erreur ni de rendu ; aucune exigence de profil ajoutée sur la voie par modèle (question ouverte de l'ADR 0142 inchangée) ; `seamAllowed` reste vide ; aucun fichier legacy de la plateforme modifié.
