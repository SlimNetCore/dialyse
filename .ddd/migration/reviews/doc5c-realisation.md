---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0195, KB-DOC5C-1, ADR 0194, ADR 0193, ADR 0024, doc5b-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5c - réalisation : le planning proposé converti en jeu de données fourni par la Planification

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `planning_doc5c_*` (`PlanningOptimise5cCharacterizationIntegrationTest`, 2 scénarios, tout le texte du PDF : 2 semaines, 3 salles « 1 / 10 / Étoile », 2 créneaux, lignes écrites dans le désordre, cases vides, FERMÉ avec motif, textes longs, plusieurs pages, lignes d'une autre proposition, d'un autre centre et du même identifiant dans un autre centre ; puis une ligne unique à jours absents), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Port existant `CalendriersDesPropositions.imprimables` (+ record `CaseImprimableDuCalendrier`) ; adapter JDBC (ancienne requête reprise côté propriétaire de la table) ; fake en mémoire ; test de contrat commun fake / H2 (/ PostgreSQL IT) | fait |
| ACL `CalendrierDeLaPropositionImprimeParDocuments` : traduction champ par champ en `LIGNES` (types déclarés) ; test unitaire (+1) | fait |
| Gabarit `planning_optimise.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| `JrxmlSecurityValidatorTest` (+1 : ancienne requête refusée `QUERY_MODIFIED`) | fait |
| Gardes datées : aucune entrée concernait ce gabarit, rien à retirer, aucun report | fait |
| ADR 0195 ; KB-DOC5C-1 ; RG-PLN-101 (note DOC5c) ; RG-DOC-004/008 (note) ; glossaires Planification et Documents ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale) | vertes : 256 tests de `platform` chacune (+ modules amont), BUILD SUCCESS, goldens inchangés ; une première passe avait révélé un défaut du nouveau filet (modèle du centre laissé désactivé par le filet voisin), corrigé avant les trois passes |
| Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5c-full.log`) | 3 560 tests dans `platform`, 2 échecs, tous deux connus et non touchés : `InventairePl7...feuille_de_comptage...` et `LancementEtSuiviDesOptimisations...l_arret_anticipe...` ; aucun autre [ERROR] ; processus Maven terminé |
| Nettoyage | `*.received.json`, `backend/.ddd`, `bash.exe.stackdump` supprimés |

## Parité
Textes du PDF identiques sur le filet `planning_doc5c_*` approuvé sur l'ancien code ; les 17 tests de caractérisation de l'optimisation (dont l'impression : statut, formats du modèle, modèle désactivé, exécution sans calendrier), le catalogue, la compilation des gabarits et le validateur inchangés et verts ; aucun `*_nouveau_code` ajouté, aucune archive `_ecarts-legacy`. Aucun comportement de rendu ne change.

Point d'attention du filet : les filets d'impression voisins désactivent ou changent le format du modèle du centre sans le remettre ; le nouveau filet repart donc d'un modèle supprimé (reprovisionné au défaut PDF) à chaque rendu.

Comportement de bord (à confirmer métiers <= 2026-12-15) : KB-DOC5C-1 (téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles déjà stockés inchangés).

## Les 9 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `planning_presence_infirmiers` (Planification) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge` (Patient et couverture) ; `ordonnance` (Dossier médical, garde `TableDuDossierMedicalTest` jusqu'au 2027-03-31).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier `platform/src/main/java` modifié (seul du code de test de `platform` ajouté), donc aucune ligne à retirer de `covered.json`.
- Un accès de plus du fake aux textes : `CalendrierEnTexte` devient public (même texte à l'écriture et dans le fake).
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, parfois `LancementEtSuiviDesOptimisations...`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
