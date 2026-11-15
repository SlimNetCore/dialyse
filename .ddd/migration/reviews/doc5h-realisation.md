---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0200, KB-DOC5H-1 à KB-DOC5H-4, ADR 0199, 0198, 0196, 0174, 0166, 0165, 0024, doc5g-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5h - réalisation : l'attestation d'ouverture de droit et la prise en charge imprimées converties en jeux de données fournis par Patient et couverture

## Choix des gabarits
`attestation` et `prise_en_charge` : même forme (une table de couverture jointe à `patients` sur le patient ET le centre, et à `centers` pour le nom du centre ; un filtre à deux identifiants ; `ORDER BY date_fin[_demande] DESC LIMIT 1`). Deux gabarits convertis (le périmètre reste raisonnable : un port, une composition, un adapter, une règle et un contrat par gabarit), filets, ports, règles et contrats séparés. `fiche_patient` et `inventaire_stock` restent.

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `AttestationImprimee5h…` (3 tests) et `PriseEnChargeImprimee5h…` (3 tests) : tout le texte du PDF par type et par modèle ; par identifiant, par patient (la plus récente), les deux identifiants, aucun repli, patient sans état civil / aux valeurs vides, dates et statut absents, ordre du SGBD sur une date de fin absente, centre et patient d'un autre centre, identifiants absents, vides, illisibles ; approuvé sur l'ANCIEN code (6 goldens `dm2/doc5h`) puis vert à l'identique | fait |
| Ports `AttestationAImprimer`, `PriseEnChargeAImprimer`, `NomsDesCentres` ; compositions `ComposerLAttestationImprimee`, `ComposerLaPriseEnChargeImprimee` ; adapters `AttestationAImprimerJdbc`, `PriseEnChargeAImprimerJdbc` (requêtes reprises sans la jointure `centers`, identifiants liés) ; ACL `NomsDesCentresPublies` (`AnnuaireDesCentres.nomDuCentre`, organisation.v1) ; règles `RegleDImpressionDeLAttestation`, `RegleDImpressionDeLaPriseEnCharge` (documents.v1, `LIGNES`, aucune garde) ; câblage `PatientCouvertureConfiguration` | fait |
| Gabarits `attestation.jrxml`, `prise_en_charge.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Tests : 2 contrats de 6 tests chacun sur le fake et H2 (24), règles (8 + 8), `JrxmlSecurityValidatorTest` (+2 : ancienne requête refusée `QUERY_MODIFIED`), `RenduSansModeleDesGabaritsConvertisTest` (+2 gabarits, 10 cas), `TableDuPatientCouvertureTest` (+1) | fait |
| Tests existants adaptés (goldens inchangés ; leur « requête modifiée » s'appuyait sur celle de `attestation`) : `JrxmlSecurityValidatorTest` (deux tests : `fiche_patient` / requête ajoutée), `GererLesGabaritsTest`, `ModeleDocumentTemplateIntegrationTest`, `VersionsDeGabaritCharacterizationIntegrationTest` (le golden « 07 requete SQL modifiee » reste 422 `QUERY_MODIFIED`), `DocumentIdentityIntegrationTest` (fournit la ligne de l'attestation) | fait |
| Gardes datées | aucune entrée ne concernait ces gabarits : rien à retirer, aucun report |
| ADR 0200 ; KB-DOC5H-1 à -4 ; RG-DOC-008 (note) ; glossaires Documents et Patient et couverture ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Passes ciblées et build complet | voir la section « Vérification » |

## Vérification
- Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; caractérisation documents, gardes et ArchUnit, modules patient-couverture et documents, `DocumentIdentityIntegrationTest`, `ModeleDocumentTemplateIntegrationTest`, référentiel des règles de gestion) : BUILD SUCCESS, 591 tests patient-couverture, 255 documents, 293 platform à chaque passe.
- Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5h-full.log`, processus terminé) : 3 583 tests dans `platform`, 1 échec connu et non touché : `InventairePl7CharacterizationIntegrationTest.le_contenu_de_la_feuille_de_comptage_reprend_lots_peremptions_quantites_et_motifs_tels_quels` ; aucun autre [ERROR].
- Un premier build complet avait révélé un test manqué par les passes (`ModeleDocumentTemplateIntegrationTest`, qui s'appuyait sur la requête de `attestation`) : adapté, puis les trois passes et le build complet ont été refaits.
- Incident de procédure : deux commandes d'attente (boucles de lecture du journal) ont contenu une redirection (`2>&1`, `2>/dev/null`) ; elles ne servaient qu'à lire le journal, aucun fichier n'a été écrit par elles.
- Nettoyage : `*.received.json`, `backend/.ddd`, `bash.exe.stackdump`, `backend/NUL` absents.

## Parité
Textes du PDF identiques sur les 6 goldens `dm2/doc5h` approuvés sur l'ancien code ; goldens existants (`doc1`, `doc3`, `doc4b`, `doc4c`, `doc5f`, `doc5g`) inchangés. Aucun comportement de rendu ne change. Détail reproduit : l'identifiant de l'attestation (de la prise en charge) est lu d'abord (illisible : 500 « Error executing SQL statement for: attestation. » / « …prise_en_charge. ») ; celui du patient n'est lu que si le premier est absent ou vide ; s'il est fourni, un identifiant de patient illisible ne fait pas échouer (comme l'ancienne requête évaluée par H2).

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5H-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles stockés inchangés.
- KB-DOC5H-2 : reproduit (types non gardés, 400 par type / 500 par modèle sur identifiant illisible, plus récente désignée par le SGBD).
- KB-DOC5H-3 : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production).
- KB-DOC5H-4 : limite non observée, graphie d'UUID sans tirets refusée par la voie par modèle.

## Les 2 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `fiche_patient` (Patient et couverture, 45 colonnes, `LEFT JOIN centers`). Suivant conseillé : `fiche_patient`, puis `inventaire_stock`.

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié, aucune ligne à retirer de `covered.json`.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
