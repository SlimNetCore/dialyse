---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0197, KB-DOC5E-1 à KB-DOC5E-3, ADR 0196, 0195, 0194, 0193, 0107 à 0111, 0167, 0024, doc5d-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5e - réalisation : le planning de présence des infirmiers converti en jeu de données fourni par la Planification

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `PlanningDePresence5eCharacterizationIntegrationTest` (3 scénarios, tout le texte du PDF et le nombre de pages : semaine riche sur 2 pages ; semaine sans ligne = une page blanche ; jours écrits de façon inhabituelle), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Planification : `LigneDuPlanningDePresence`, ports `PlanningDePresenceImprimable` et `RoulementImprimableDeLaSemaine`, adapter `RoulementImprimableDeLaSemaineJdbc` (tables de la Planification seules), traducteur `PlanningDePresenceImprimableAvecLesLibellesPublies` (organisation.v1 : `nomsDesSalles`, `creneauxDuCentre`), `PresenceImprimeeParDocuments` (paramètre `LIGNES`), câblage `ImpressionsDeLaPlanificationConfiguration` | fait |
| Gabarit `planning_presence_infirmiers.jrxml` : requête (120 lignes) retirée, champs et paramètres inchangés | fait |
| Tests : contrat `RoulementImprimableDeLaSemaineContrat` (10) sur le double en mémoire et H2 (pas de variante PostgreSQL IT), traducteur organisation.v1 (4), ACL Documents (+2 : traduction champ par champ, planning non lu pour un format non servi), `JrxmlSecurityValidatorTest` (+1 : ancienne requête refusée `QUERY_MODIFIED`), `TableDeLaPlanificationTest` (+1 : gabarit sans requête) | fait |
| Gardes datées | aucune entrée ne concernait ce gabarit : rien à retirer, aucun report |
| ADR 0197 ; KB-DOC5E-1, -2, -3 ; RG-INF-061 et RG-DOC-008 (notes) ; glossaires Planification et Documents ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; filets de la présence et des documents, architecture et gardes de tables, contextes) | vertes, BUILD SUCCESS ; goldens existants inchangés |
| Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5e-full.log`) | 3 568 tests dans `platform`, 1 échec connu et non touché : `InventairePl7...feuille_de_comptage` ; aucun autre [ERROR] ; processus Maven terminé |
| Nettoyage | `*.received.json`, `backend/.ddd`, `bash.exe.stackdump` |

## Parité
Textes du PDF identiques sur le filet `p4-presence/doc5e` approuvé sur l'ancien code (ordre des lignes, accents, noms sans prénom, absences aux bornes, remplaçants inactifs, sous-chaîne sensible à la casse sur les jours, page blanche de la semaine vide) ; les 8 tests et 11 goldens existants de l'impression de la présence, le catalogue, la compilation des gabarits, le validateur inchangés et verts.

## Incident de parcours
Première version de la conversion : l'adapter lisait `salle` et `position_creneau` (tables d'Organisation) depuis la Planification ; la garde permanente `TableDeLOrganisationTest` l'a refusé. Reprise par organisation.v1 (`LibellesDeLOrganisation`), ce qui rend le tri côté serveur (KB-DOC5E-2). La requête embarquée lisait ces deux tables sans qu'aucune garde ne la voie (les gardes ne scrutaient que le Java).

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5E-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles stockés inchangés.
- KB-DOC5E-2 : tri par points de code (identique en locale C et sur H2) au lieu de la collation du SGBD : ÉCART POSSIBLE en production selon la locale de la base ; à confirmer avec l'exploitation.
- KB-DOC5E-3 : salle ou créneau d'un autre centre référencé par erreur n'est plus imprimé (non observé).

## Les 7 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge` (Patient et couverture).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié (seuls des tests de `platform` ajoutés), donc aucune ligne à retirer de `covered.json`.
- Limites : pas de variante PostgreSQL du contrat du roulement ; lignes lues et triées en mémoire (quelques centaines au plus).
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
