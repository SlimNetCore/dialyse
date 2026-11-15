---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0201, KB-DOC5I-1 à KB-DOC5I-3, ADR 0200, 0199, 0198, 0196, 0174, 0166, 0165, 0024, doc5h-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5i - réalisation : la fiche signalétique du patient imprimée convertie en jeu de données fourni par Patient et couverture

## Choix du gabarit
`fiche_patient` seul (35 colonnes sélectionnées, dont 11 jamais déclarées comme champs du gabarit ; `LEFT JOIN centers` ; filtre sur le patient ET le centre). Reste `inventaire_stock` (Stock).

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `FichePatient5iCharacterizationIntegrationTest` (3 tests, 3 goldens `dm2/doc5i`) : tout le texte du PDF par type et par modèle ; patient riche (accents, KT oui/non), valeurs absentes ou vides, patient inconnu, d'un autre centre, identifiants absents, vides, illisibles de la voie par modèle ; approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Port `FicheSignaletiqueAImprimer`, composition `ComposerLaFicheSignaletiqueImprimee` (nom du centre par `NomsDesCentres` de DOC5h, absent si centre inconnu = ancien `LEFT JOIN`), adapter `FicheSignaletiqueAImprimerJdbc`, règle `RegleDImpressionDeLaFicheSignaletique` (documents.v1, `LIGNES`, aucune garde), `IdentifiantsDuDocument.lireSansTolererLeVide`, câblage `PatientCouvertureConfiguration` | fait |
| Gabarit `fiche_patient.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Tests : contrat de 4 tests sur le fake et H2 (8), règle (6), `JrxmlSecurityValidatorTest` (+1 ; les deux tests qui s'appuyaient sur la requête de `fiche_patient` prennent `inventaire_stock`), `RenduSansModeleDesGabaritsConvertisTest` (+1 gabarit), `TableDuPatientCouvertureTest` (+1), `DocumentIdentityIntegrationTest` (fournit la ligne) | fait |
| Gardes datées | aucune entrée ne concernait ce gabarit : rien à retirer, aucun report |
| ADR 0201 ; KB-DOC5I-1 à -3 ; RG-DOC-008 (note) ; glossaires Documents et Patient et couverture ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | voir « Vérification » |

## Vérification
- `check-arch.ts` : 0 violation.
- Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; caractérisation documents, gardes `Table*Test`, ArchUnit, modules patient-couverture et documents, `DocumentIdentityIntegrationTest`, `ModeleDocumentTemplateIntegrationTest`, référentiel des règles de gestion) : BUILD SUCCESS à chaque passe, 52 tests patient-couverture et 79 documents exécutés.
- Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5i-full.log`, terminé) : 3 587 tests dans `platform`, 1 échec connu et non touché : `InventairePl7CharacterizationIntegrationTest.le_contenu_de_la_feuille_de_comptage_reprend_lots_peremptions_quantites_et_motifs_tels_quels` ; aucun autre [ERROR].
- Un premier build complet avait révélé un test manqué par mes passes (`TableDeLOrganisationTest` : un commentaire Java de `ComposerLaFicheSignaletiqueImprimee` contenait « LEFT JOIN centers », vu comme un nouveau lecteur de la table des centres) : commentaire reformulé, les passes (avec `Table*Test`) et le build complet refaits.
- Nettoyage : `*.received.json` supprimés, `backend/.ddd`, `bash.exe.stackdump`, `backend/NUL` absents.

## Parité
Textes du PDF identiques sur les 3 goldens `dm2/doc5i` approuvés sur l'ancien code ; goldens existants inchangés. Détail reproduit : sans `NULLIF` dans l'ancienne requête, un `patientId` VIDE par modèle donne le 500 « Error executing SQL statement for: fiche_patient. » (comme illisible), alors qu'absent donne une page blanche. Aucun comportement de rendu ne change.

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5I-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles stockés inchangés.
- KB-DOC5I-2 : reproduit (type non gardé, 400 par type, 500 par modèle sur identifiant vide ou illisible).
- KB-DOC5I-3 : appel direct au moteur sans `LIGNES` = page blanche (aucun appelant de production) ; centre inconnu de l'annuaire : fiche imprimée, titre « null » comme avant.

## Reste
`inventaire_stock` (Stock) : dernier gabarit à SQL embarqué ; `ConnexionDeRendu` disparaît avec lui.

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié, aucune ligne à retirer de `covered.json`.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
