---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0196, KB-DOC5D-1, KB-DOC5D-2, ADR 0195, 0194, 0193, 0166, 0174, 0024, doc5c-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5d - réalisation : l'ordonnance imprimée convertie en jeu de données fourni par le Dossier médical

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `OrdonnanceImprimee5dCharacterizationIntegrationTest` (3 tests, tout le texte du PDF et le nombre de pages : ordonnance riche à 14 lignes sur 2 pages par type ET par modèle ; ordonnance minimale ; états vides et identifiants de la voie par modèle), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Dossier médical : `ComposerLesLignesDeLOrdonnance`, `LigneDeLOrdonnanceImprimee`, ports `OrdonnancesAImprimer` et `NomsDesCentres`, adapters `OrdonnancesAImprimerJdbc` et `NomsDesCentresPublies` (organisation.v1), traducteur `ChampsDeLOrdonnanceImprimee`, `RegleDImpressionDeLOrdonnance.donneesDuServeur` (`LIGNES`), câblage `DossierMedicalConfiguration` | fait |
| Port existant réutilisé : `IdentitesDesPatients` (état civil, patient-couverture.v1) | fait |
| Gabarit `ordonnance.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Tests : `ComposerLesLignesDeLOrdonnanceTest` (5), `RegleDImpressionDeLOrdonnanceTest` (4), contrat `OrdonnancesAImprimerContrat` (4) sur le double en mémoire et H2 (pas de variante PostgreSQL IT), `JrxmlSecurityValidatorTest` (+1 : ancienne requête refusée `QUERY_MODIFIED`) | fait |
| Garde `TableDuDossierMedicalTest` : entrée `ordonnance.jrxml` (butoir 2027-03-31) SUPPRIMÉE, aucun report ; liste blanche VIDE ; constante du butoir supprimée ; +1 test (le gabarit ne lit plus ces tables, sans `queryString`) | fait |
| ADR 0196 ; KB-DOC5D-1, KB-DOC5D-2 ; RG-MED-055 et RG-DOC-008 (notes) ; glossaires Dossier médical et Documents ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; caractérisation documents et dossier médical, gardes et ArchUnit, modules documents et dossier-medical, référentiel des règles de gestion) | vertes : 619 tests de `platform` chacune, BUILD SUCCESS, goldens existants inchangés |
| Build complet détaché (`mvnw.cmd -o test -fae`, journal `backend/doc5d-full.log`) | 3 564 tests dans `platform`, 1 échec connu et non touché : `InventairePl7...feuille_de_comptage...` ; aucun autre [ERROR] ; processus Maven terminé |
| Nettoyage | `*.received.json`, `backend/.ddd`, `bash.exe.stackdump` supprimés |

## Parité
Textes du PDF identiques sur le filet `dm2/doc5d` approuvé sur l'ancien code (y compris l'ordre des lignes, égal à l'ordre d'insertion, « N° null » et « Prescripteur : null », la page blanche des états vides, le 500 de l'identifiant illisible par modèle) ; les 4 filets DM2 de l'impression (contenu Excel, statuts, erreurs, gardes de profil et de centre), le catalogue, la compilation des gabarits, le validateur inchangés et verts ; aucun `*_nouveau_code` ajouté, aucune archive `_ecarts-legacy`. Aucun comportement de rendu ne change.

Choix qui évite un écart : la lecture des lignes n'a PAS de `ORDER BY` (l'ancienne requête n'en avait aucun sur les lignes) ; un tri comme celui de `OrdonnancesJdbc` ou la reconstitution de l'agrégat auraient changé l'ordre imprimé et le traitement des statuts illisibles (ADR 0196).

Comportements de bord (à confirmer métiers <= 2026-12-15) : KB-DOC5D-1 (téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles déjà stockés inchangés) ; KB-DOC5D-2 (identifiant illisible par modèle : 500 au texte historique « Error executing SQL statement for: ordonnance. », reproduit alors qu'il n'y a plus de SQL).

## Les 8 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `planning_presence_infirmiers` (Planification) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge` (Patient et couverture). Plus aucune garde datée ne dépend de ces conversions côté Dossier médical.

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier `platform/src/main/java` modifié (seuls des tests de `platform` ajoutés ou adaptés), donc aucune ligne à retirer de `covered.json`.
- Limites : pas de variante PostgreSQL du contrat `OrdonnancesAImprimer` ; l'ordre des lignes sur PostgreSQL reste celui du tas, comme avant (non garanti) ; graphies d'UUID que Java refuse mais que `CAST` acceptait (32 chiffres sans tirets) désormais en échec sur la voie par modèle (non observé).
- Un incident de procédure : une première commande de passe a été refusée par le contrôle de permissions (redirection vers `/dev/null` écrite par erreur) ; relancée sans redirection, rien n'a été exécuté entre-temps.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, parfois `LancementEtSuiviDesOptimisations...`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
