---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0194, KB-DOC5B-1 et 2, ADR 0193, ADR 0024, doc5a-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5b - réalisation : le bon d'intervention converti en jeu de données fourni par la GMAO

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `bon_doc5b_*` (`BonDInterventionComplet5bCharacterizationIntegrationTest`, 2 scénarios, tout le texte du PDF : 8 lignes de coût de 4 types, libellés majuscule/minuscule/accent/chiffres, quantité décimale, grand montant, libellé long, lignes d'une autre intervention ; bon sans ligne), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| `BonRenduParDocuments.lignesDuBon` : une ligne par ligne de coût, libellé du type, montant quantité x prix, tri par libellé (stable), un enregistrement vide si aucune ligne ; paramètre `LIGNES` | fait |
| Gabarit `bon_intervention.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Aucun nouveau port ni adapter : les lignes sont déjà dans l'intervention chargée (bornée au centre) par `ImprimerLeBon` | fait |
| Tests unitaires `BonRenduParDocumentsTest` (+3 : traduction et ordre, libellé égal, bon sans ligne) ; `JrxmlSecurityValidatorTest` (+1 : ancienne requête refusée) | fait |
| Gardes datées : aucune entrée concernait ce gabarit, rien à retirer, aucun report | fait |
| ADR 0194 ; KB-DOC5B-1 et 2 ; RG-GMA-068 (note DOC5b) ; RG-DOC-004/008 (note) ; glossaires GMAO et Documents ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Première passe ciblée (filet doc5b, bon PL6, cahier, validateur, compilation, gardes, ArchUnit, catalogue) | verte : 302 tests du module `platform`, BUILD SUCCESS, goldens inchangés, aucun `*.received.json` |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale) | vertes : 325 tests chacune, BUILD SUCCESS |
| Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5b-full.log`) | 3 558 tests dans `platform`, 2 échecs, tous deux connus et non touchés : `InventairePl7...feuille_de_comptage_reprend_lots...` et `LancementEtSuiviDesOptimisations...l_arret_anticipe...` ; aucun autre [ERROR] ; processus Maven terminé |
| Nettoyage | `*.received.json`, `backend/.ddd`, `bash.exe.stackdump` supprimés |

## Parité
Textes du PDF identiques sur le filet `bon_doc5b_*` approuvé sur l'ancien code ; goldens PL6 du bon (16 + 9 tests de caractérisation du bon), `BonInterventionReportTemplateTest`, validateur et compilation des gabarits inchangés et verts ; aucun `*_nouveau_code` ajouté, aucune archive `_ecarts-legacy`. Aucun comportement de rendu ne change.

Comportements de bord (à confirmer métiers <= 2026-12-15) : KB-DOC5B-1 (téléversement d'un modèle du bon portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles déjà stockés inchangés, traitement identique à KB-DOC5A-1) ; KB-DOC5B-2 (ordre des libellés par caractères, collation de la base de production à confirmer).

## Les 10 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock, dernier : 1 010 l.) ; `planning_presence_infirmiers`, `planning_optimise` (Planification) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge` (Patient et couverture) ; `ordonnance` (Dossier médical, garde `TableDuDossierMedicalTest` jusqu'au 2027-03-31). **Prochain candidat : `ordonnance`** (butoir daté, 4 tables) ; alternative la moins coûteuse : `planning_optimise` (une table).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier `platform/src/main/java` modifié, donc aucune ligne à retirer de `covered.json`.
- Limite : pas d'adapter nouveau, donc pas de test de contrat fake/réel ; l'ordre sur une base à collation linguistique n'est pas dans le filet (H2).
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, parfois `LancementEtSuiviDesOptimisations...`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
