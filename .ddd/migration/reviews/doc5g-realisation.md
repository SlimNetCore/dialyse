---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0199, KB-DOC5G-1 à KB-DOC5G-3, ADR 0198, 0197, 0166, 0165, 0024, doc5f-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5g - réalisation : la liste des ouvertures de droit et la liste des prises en charge converties en jeux de données fournis par Patient et couverture

## Choix des gabarits
`liste_attestations` et `liste_pec` : requêtes de même forme (une table de couverture jointe à `patients` sur le patient ET le centre, bornée au centre, un tri décroissant sur la date de fin, aucun `LIMIT`), les plus simples des cinq restants ; `attestation` et `prise_en_charge` filtrent sur deux identifiants avec `LIMIT 1` et joignent `centers`, `fiche_patient` lit 45 colonnes. Deux gabarits convertis, filets, ports, règles et contrats séparés.

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `ListeDesAttestations5g…` et `ListeDesPrisesEnCharge5g…` (2 scénarios chacun : texte du PDF par type et par modèle, cellules du tableur, pages ; centre sans ligne), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Ports `ListeDesAttestationsAImprimer`, `ListeDesPrisesEnChargeAImprimer` ; adapters JDBC (requêtes reprises telles quelles, paramètre de centre lié) ; règles `RegleDImpressionDeLaListeDesAttestations`, `…DesPrisesEnCharge` (documents.v1, `LIGNES`, aucune garde) ; câblage `PatientCouvertureConfiguration` | fait |
| Gabarits `liste_attestations.jrxml`, `liste_pec.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Tests : 2 contrats de 6 tests chacun sur le fake et H2 (24), règles (8), `JrxmlSecurityValidatorTest` (+2 : ancienne requête refusée `QUERY_MODIFIED`), `RenduSansModeleDesGabaritsConvertisTest` (+2, 8 cas : chemin « centre sans modèle »), `TableDuPatientCouvertureTest` (+1) | fait |
| Test existant adapté (goldens inchangés) : `DocumentIdentityIntegrationTest` fournit la ligne de l'attestation au gabarit (KB-DOC5G-3) | fait |
| Gardes datées | aucune entrée ne concernait ces gabarits : rien à retirer, aucun report |
| ADR 0199 ; KB-DOC5G-1 à -3 ; RG-DOC-008 (note) ; glossaires Documents et Patient et couverture ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Passes ciblées et build complet | voir la section « Vérification » |

## Parité
Textes du PDF et cellules du tableur identiques sur les 4 goldens `dm2/doc5g` approuvés sur l'ancien code (46 et 47 lignes sur 2 pages, accents, minuscules, codes vides, dates et statuts absents « null », ligne d'un autre centre et ligne dont le patient est d'un autre centre écartées, centre vide = page blanche) ; goldens existants `doc1`, `doc4b`, `doc4c`, `doc5f` inchangés.

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5G-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles stockés inchangés.
- KB-DOC5G-2 : reproduit (types non gardés, tri par le SGBD, jointure au patient du même centre).
- KB-DOC5G-3 : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production).

## Les 4 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `fiche_patient`, `attestation`, `prise_en_charge` (Patient et couverture). Suivant conseillé : `attestation` et `prise_en_charge` (`LIMIT 1`, filtre à deux identifiants, `centers` via l'annuaire d'Organisation), puis `fiche_patient`.

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié, aucune ligne à retirer de `covered.json`.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
