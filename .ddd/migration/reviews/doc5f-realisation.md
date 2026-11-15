---
status: done (en attente de validation)
date: 2026-11-15
sources: ADR 0198, KB-DOC5F-1 à KB-DOC5F-3, ADR 0197, 0196, 0195, 0194, 0193, 0166, 0165, 0024, doc5e-realisation.md, inventaire-platform-restant-v2.md (ligne DOC5)
---

# DOC5f - réalisation : la liste des patients convertie en jeu de données fourni par Patient et couverture

## Choix du gabarit
`liste_patients` : la requête la plus simple des six (une seule table `patients`, aucune jointure, bornée au centre, tri nom puis prénom) ; `liste_attestations` et `liste_pec` joignent deux tables, `attestation` et `prise_en_charge` trois avec `LIMIT 1`, `fiche_patient` lit 45 colonnes sur 551 lignes. Aucun autre gabarit ne partage cette requête : un seul converti.

## État d'avancement
| Étape | État |
|---|---|
| Filet de rendu `ListeDesPatients5fCharacterizationIntegrationTest` (2 scénarios : texte du PDF par type et par modèle, cellules du tableur, pages ; centre sans patient), approuvé sur l'ANCIEN code puis vert à l'identique | fait |
| Patient et couverture : port `ListeDesPatientsAImprimer`, adapter `ListeDesPatientsAImprimerJdbc` (table `patients` seule), règle `RegleDImpressionDeLaListeDesPatients` (documents.v1, pose `LIGNES`, aucune garde), câblage `PatientCouvertureConfiguration`, dépendance `contexts-documents-api` | fait |
| Gabarit `liste_patients.jrxml` : requête retirée, champs et paramètres inchangés | fait |
| Tests : contrat `ListeDesPatientsAImprimerContrat` (5) sur le double en mémoire et H2, règle (4), `JrxmlSecurityValidatorTest` (+1 : ancienne requête refusée `QUERY_MODIFIED`), `TableDuPatientCouvertureTest` (+1 : gabarit sans requête) | fait |
| Test existant adapté (goldens inchangés) : `PapierEnTeteEtModelesCharacterizationIntegrationTest` appelle le moteur directement ; il pose maintenant les lignes par la règle publiée (3 appels) | fait |
| Gardes datées | aucune entrée ne concernait ce gabarit : rien à retirer, aucun report |
| ADR 0198 ; KB-DOC5F-1, -2, -3 ; RG-DOC-008 (note) ; glossaires Documents et Patient et couverture ; inventaire (ligne DOC5) | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; filet doc5f, doc1, doc4b/4c, règle, contrats, validateur, gardes de tables, ArchUnit, contextes) | vertes |
| Build complet détaché (`doc5f-full.log`, 3 571 tests dans `platform`) | 1 seul échec, connu et non touché : `InventairePl7...feuille_de_comptage` ; processus Maven terminé |

## Parité
Textes du PDF et cellules du tableur identiques sur le filet `dm2/doc5f` approuvé sur l'ancien code (51 patients sur 2 pages, casse et accents, même nom, champs absents, noms longs, patient d'un autre centre écarté, centre vide = page blanche) ; goldens existants `doc1`, `doc4b`, `doc4c` inchangés.

## Comportements de bord (à confirmer <= 2026-12-15)
- KB-DOC5F-1 : téléversement d'un modèle portant l'ancienne requête refusé `QUERY_MODIFIED` ; modèles stockés inchangés.
- KB-DOC5F-2 : reproduit, type toujours non gardé, tri toujours par le SGBD (aucun écart).
- KB-DOC5F-3 : un appel direct au moteur sans `LIGNES` donne une page blanche (aucun appelant de production).

## Régression DOC5 découverte et CORRIGÉE (décision de la coordination)
`MoteurDeRenduJasper.rendreSansModele` (chemin « centre sans modèle » de `rendreParTypeSansProvisionner`, KB-PLN-51) ne transformait pas `LIGNES` en source de données (seul `rendreParModele` appelait `avecSourceDeDonnees`) : un gabarit converti sans requête s'imprimait en page blanche pour un centre sans modèle du type. Avant DOC5 le gabarit lisait la base, donc c'était une régression de nos conversions (présence DOC5e ; DOC5c si passé par ce chemin), pas un changement voulu. Correction : `rendreSansModele` applique `avecSourceDeDonnees`. Preuve : `RenduSansModeleDesGabaritsConvertisTest` (6 cas : cahier, bon d'intervention, planning optimisé, ordonnance, présence, liste des patients ; fields générés depuis le gabarit, rendu tableur, aucune connexion ouverte). Aucun golden modifié ni réapprouvé : le golden DOC5e « semaine vide » reste une page blanche légitime (aucune ligne), les deux autres du filet 5e (contenu) redeviennent verts.

Pourquoi les 2 tests DOC5e échouaient (build complet et ciblé) : le centre de test n'a aucun modèle `PLANNING_PRESENCE_INFIRMIERS`, donc chemin « sans modèle » = page blanche ; dans le rapport DOC5e ils passaient parce qu'un autre test avait laissé un modèle actif de ce centre (état partagé, ordre d'exécution). Mes premiers « passages » en ciblé depuis `backend/` étaient faux : `mvnw.cmd` n'était pas reconnu sous `cd /d` et le filtre masquait l'erreur. Le test n'a plus de dépendance d'état : les deux chemins rendent les lignes.

## Les 6 gabarits restants (à SQL embarqué, servis par `ConnexionDeRendu`)
`inventaire_stock` (Stock) ; `fiche_patient`, `attestation`, `liste_attestations`, `liste_pec`, `prise_en_charge` (Patient et couverture). Suivant conseillé : `liste_attestations` ou `liste_pec` (même forme que celle-ci, avec la jointure sur `patients`).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` et `.ddd/safety-net/covered.json` non touchés : aucun fichier legacy de `platform/src/main/java` modifié (seuls des tests de `platform` touchés), aucune ligne à retirer de `covered.json`.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage`, `LancementEtSuiviDesOptimisations...l_arret_anticipe`.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
