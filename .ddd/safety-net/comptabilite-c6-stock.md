---
status: parity-verified
date: 2026-10-19
slice: comptabilite-c6-stock
sources: [.ddd/migration/slices/comptabilite-c6-stock.md, .ddd/tactical/comptabilite.md B8 B9 B15 B16, ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md KB-CPT-33 41-45]
---

# Filet de caractérisation — Comptabilité C6 « Comptabilisation du stock »

Refactoring sur place (ADR 0024). Le filet décrit le comportement **actuel** du legacy ; aucun seam, aucun fichier de
production modifié.

## Périmètre et point d'observation

Suite :
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/ecritures/ComptabilisationDuStockCharacterizationIntegrationTest.java`
(harnais `EcrituresCharacterizationSupport`, goldens `.../characterization/comptabilite/ecritures/stock/`).
Entrée : `POST /api/v1/comptabilite/stock/synchroniser` (MockMvc, sécurité réelle), qui appelle le même cas d'usage que
la tâche de nuit. Observation : compteurs de la réponse, écritures et lignes en base. Données insérées en SQL (articles,
bons de réception, mouvements, inventaires) sur septembre 2026.

## Scénarios (8 tests, 26 goldens approuvés après relecture)

| Test                                                                                               | Verrouille                                                                                                                                                                                                                                                                               |
|----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `un_mois_de_stock_donne_une_ecriture_par_bon_valide_par_jour_de_sorties_et_par_inventaire_cloture` | schémas réception / sorties / inventaire, comptes de l'article ou du centre, brouillons et inventaires en cours ignorés, mouvements d'entrée ignorés, lignes d'inventaire non comptées ignorées, valorisation (KB-CPT-42), jour UTC (KB-CPT-41), isolation de centre, numérotation AC/ST |
| `un_rejeu_ne_cree_rien_et_un_mouvement_tardif_met_a_jour_l_ecriture_du_jour_en_place`              | idempotence, révision en place (B9)                                                                                                                                                                                                                                                      |
| `des_sorties_qui_diminuent_ou_disparaissent_sont_corrigees_par_la_difference`                      | mise à jour à la baisse, contre-écriture de complément (KB-CPT-45)                                                                                                                                                                                                                       |
| `une_journee_deja_exportee_recoit_un_complement_date_du_jour_d_origine`                            | immutabilité de l'exportée, complément                                                                                                                                                                                                                                                   |
| `dans_un_mois_clos_les_pieces_sont_ignorees_et_un_complement_de_sorties_est_date_d_aujourd_hui`    | `ignorees`, complément daté d'aujourd'hui avec date de pièce d'origine (B8, KB-CPT-44)                                                                                                                                                                                                   |
| `si_le_mois_d_aujourd_hui_est_aussi_clos_le_complement_est_ignore`                                 | KB-CPT-44                                                                                                                                                                                                                                                                                |
| `les_journaux_du_stock_suivent_le_mapping_du_centre`                                               | journaux STOCK_* du mapping (R15), numérotation dans le journal choisi                                                                                                                                                                                                                   |
| `la_route_controle_roles_centre_et_periode`                                                        | 1 à 367 jours (KB-CPT-43), validations, rôles, centre, anonyme                                                                                                                                                                                                                           |

## Exécution

8 tests, **3 passages consécutifs verts en ordre aléatoire**, aucun `*.received.json`. Tests existants
conservés : `ComptabiliteStockIntegrationTest` (4), `ComptabiliteStockServiceTest` (11),
`ComptabiliteStockSchedulerTest`
(2 : fenêtre de 35 jours, un centre en erreur n'arrête pas les autres), `ComptabiliteStockMigrationTest` (3).
Commande, depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest=ComptabilisationDuStockCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`

## Fichiers legacy ajoutés à `covered.json`

`ComptabiliteStockApplicationService`, `ComptabiliteStockService`, `GenerateurEcritureStock`,
`ComptabiliteStockUseCase`,
`OperationsStockPort`, `OperationsStockJdbcAdapter`, `ComptabiliteStockScheduler` (déjà couverts :
`ComptabiliteRestController`,
`EcritureComptableJpaAdapter`, `PeriodeComptableJpaAdapter`, `MappingComptableDuContexte`). `seamAllowed` reste vide.

## Ce qui n'est pas verrouillé

- Tâche planifiée de 03:15 : elle dépend de l'horloge (`LocalDate.now(UTC)`) ; seule sa logique (fenêtre de 35 jours,
  isolement des erreurs par centre) est couverte par le test unitaire existant, pas son déclenchement.
- Limite de 50 compléments par journée ; concurrence de deux synchronisations (verrous PostgreSQL) ; base H2 seulement.
- Transaction unique par centre : une erreur technique au milieu annule toute la synchronisation (non injectée).
- Valeurs PostgreSQL (échelles numériques) : seules les échelles H2 sont observées.
- Aucune mutation (PIT) exécutée ; couverture comportementale.

## Bugs connus

KB-CPT-41 à 45 (`.ddd/safety-net/known-bugs.md`) ; KB-CPT-33 (C3) confirmé.

## Parité sur le nouveau code (2026-10-19)

La suite cible le nouveau code (aucun flag). Écritures et lignes : tous les goldens inchangés. Réponses : six goldens
`*_nouveau_code` portent le champ `echecs` décidé (KB-CPT-42 et 44), dont un de la suite de clôture C3 ; le mois courant
cité par un refus est normalisé `<MOIS_COURANT_UTC>`. Trois passages consécutifs verts en ordre aléatoire (avec
`ComptabiliteStockIntegrationTest` : 12 tests).
