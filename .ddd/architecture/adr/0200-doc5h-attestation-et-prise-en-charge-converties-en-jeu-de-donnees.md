---
status: accepted (livré le 2026-11-15, slice DOC5h, suite des ADR 0193 à 0199)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: patient-couverture, organisation (annuaire publié), documents (port publié documents.v1), platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162, 0165, 0166 (règles d'impression), 0174 (annuaire des centres), 0196 (modèle de DOC5d), 0193 à 0199 (DOC5a à DOC5g) ; `doc5h-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0200 - DOC5h : l'attestation d'ouverture de droit et la prise en charge imprimées deviennent des jeux de données fournis par Patient et couverture

## Contexte
Après DOC5g (ADR 0199), 4 gabarits embarquaient encore une requête SQL servie par `ConnexionDeRendu` : `inventaire_stock` (Stock), `fiche_patient`, `attestation`, `prise_en_charge` (Patient et couverture). `attestation` et `prise_en_charge` ont la même forme : une table de couverture jointe à `patients` (même patient ET même centre) et à `centers` (le nom du centre, en titre), bornée au centre, un filtre à deux identifiants, `ORDER BY date_fin[_demande] DESC LIMIT 1`. Deux gabarits convertis, avec des filets, des ports, des règles et des tests séparés (modèle : DOC5d, une pièce imprimée à partir d'identifiants).

## Décision
Les deux gabarits perdent leur requête ; champs et paramètres inchangés. Patient et couverture fournit l'unique ligne sous `LIGNES` (liste vide si rien à imprimer).

| Élément | `attestation` (`ATTESTATION`) | `prise_en_charge` (`PEC`) |
|---|---|---|
| Champs | `NOM`, `PRENOM`, `SEXE`, `DATE_NAISSANCE`, `NUMERO_ASSURANCE`, `ADRESSE`, `TEL_MOBILE`, `DATE_DEBUT`, `DATE_FIN`, `CENTER_NAME` | `NOM`, `PRENOM`, `NUMERO_ASSURANCE`, `DATE_NAISSANCE`, `DATE_DEBUT_DEMANDE`, `DATE_FIN_DEMANDE`, `STATUT`, `DATE_DEBUT_EFFECTIF`, `DATE_FIN_EFFECTIF`, `CENTER_NAME` |
| Port sortant (application) | `AttestationAImprimer` : `parIdentifiant`, `laPlusRecenteDuPatient` | `PriseEnChargeAImprimer` : idem |
| Composition (application) | `ComposerLAttestationImprimee` : l'identifiant de l'attestation l'emporte sur celui du patient, aucun repli si elle est introuvable, aucun identifiant = rien ; le nom du centre vient du port `NomsDesCentres` | `ComposerLaPriseEnChargeImprimee` (idem) |
| Adapter | `AttestationAImprimerJdbc` (tables `attestation_droit` et `patients`, requête reprise telle quelle SANS la jointure sur `centers`) | `PriseEnChargeAImprimerJdbc` (tables `prise_en_charge` et `patients`) |
| Voie vers Documents | `RegleDImpressionDeLAttestation` (`interfaces/publie`, documents.v1) : pose `LIGNES` sur les deux voies, aucune garde | `RegleDImpressionDeLaPriseEnCharge` (idem) |
| Fake et contrat | `AttestationAImprimerEnMemoire` ; MÊME contrat (6 tests) sur le fake et sur H2 | `PriseEnChargeAImprimerEnMemoire` ; idem (6 tests) |

Le nom du centre ne lit plus `centers` : port `NomsDesCentres` (application) et ACL `NomsDesCentresPublies` (`infrastructure/acl/organisation`) sur `AnnuaireDesCentres.nomDuCentre` (organisation.v1), comme `NomsDesCentres` du Dossier médical (ADR 0196). Un centre inconnu ne donne rien (comme l'ancienne jointure interne).

Lecture des identifiants, reproduisant l'ancienne requête évaluée par H2 (`NULLIF(x, '')`) : absent ou vide = non fourni ; l'identifiant de l'attestation / de la prise en charge est lu EN PREMIER (illisible : 500 « Error executing SQL statement for: attestation. » / « ...prise_en_charge. », texte historique) ; celui du patient n'est lu que si le premier est absent (illisible : même 500) ; si le premier est fourni, l'identifiant du patient n'est jamais lu (même illisible : pas d'échec). Les dates sont posées en `java.sql.Date` et les valeurs absentes restent nulles.

Filet de rendu AVANT toute modification (approuvé sur l'ANCIEN code, vert à l'identique ensuite) : `characterization/dossiermedical/dm2/doc5h/*` (6 goldens, TOUT le texte du PDF par type et par modèle : par identifiant, par patient (la plus récente), les deux identifiants, aucun repli, patient sans état civil, dates et statut absents, ordre du SGBD sur une date de fin absente, centre et patient d'un autre centre, identifiants absents, vides, illisibles). Les goldens existants (`doc1`, `doc4b`, `doc4c`, `doc5f`, `doc5g`) restent inchangés. Les gabarits convertis sont ajoutés à `RenduSansModeleDesGabaritsConvertisTest` (chemin « centre sans modèle »).

## Comportements de bord (à confirmer métiers <= 2026-12-15)
- **KB-DOC5H-1** (même nature que KB-DOC5A-1 à KB-DOC5G-1) : le téléversement d'un modèle `ATTESTATION` ou `PEC` portant encore l'ANCIENNE requête est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement.
- **KB-DOC5H-2** : reproduit (types toujours non gardés par profil ; 400 par type, 500 par modèle sur identifiant illisible ; la plus récente d'un patient désignée par le SGBD : une date de fin de demande absente est classée selon le SGBD, comme avant).
- **KB-DOC5H-3** : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production ; `DocumentIdentityIntegrationTest` fournit maintenant la ligne de l'attestation).
- **KB-DOC5H-4** (limite, non observée) : sur la voie par modèle, une graphie d'identifiant que Java refuse mais que le `CAST` acceptait (32 chiffres sans tirets) est désormais un échec 500 ; la voie par type la refuse déjà en 400.

## Gardes datées
Aucune entrée de garde ne concernait ces gabarits (la garde ne scrute que le Java ; liste blanche réduite à PLACE et CYCLE de la Planification) : rien à retirer, aucun report. Un test de `TableDuPatientCouvertureTest` verrouille l'absence de requête et de table du contexte dans les deux gabarits.

## Conséquences
- 2 gabarits à SQL embarqué restent : `fiche_patient` (Patient et couverture, 45 colonnes, `LEFT JOIN centers`) et `inventaire_stock` (Stock). `ConnexionDeRendu` est conservé pour eux.
- Tests existants adaptés (goldens inchangés) : `JrxmlSecurityValidatorTest` et `GererLesGabaritsTest` (qui s'appuyaient sur la requête de `attestation` comme exemple de requête modifiée) prennent `fiche_patient` / une requête ajoutée ; `DocumentIdentityIntegrationTest` fournit la ligne de l'attestation.
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
