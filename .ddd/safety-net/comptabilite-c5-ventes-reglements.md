---
status: parity-verified
date: 2026-10-19
slice: comptabilite-c5-ventes-reglements
sources: [.ddd/tactical/comptabilite.md §2 R6 R7 R17 R18 R19 P1 P2 §12 B3-B6 B10 B13 B14 B17, .ddd/migration/slices/comptabilite-c5-ventes-reglements.md, ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md KB-CPT-31 32 37-40]
---

# Filet de caractérisation — Comptabilité C5 « Ventes et règlements »

Refactoring sur place (ADR 0024). Le filet décrit le comportement **actuel** du legacy ; il n'introduit aucun seam et
ne modifie aucun fichier de production.

## Périmètre et point d'observation

Suite :
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/ecritures/VentesEtReglementsCharacterizationIntegrationTest.java`
(harnais partagé `EcrituresCharacterizationSupport`, goldens
`backend/platform/src/test/resources/characterization/comptabilite/ecritures/ventes/`).

- **P1** (`FacturationValideeComptabiliteListener`) et **P2** (`ReglementEncaisseComptabiliteListener`) : événements
  publiés par le vrai `ApplicationEventPublisher`, hors transaction et dans une transaction appelante
  (`TransactionTemplate`, comme `FacturationApplicationService.validate` et
  `ReglementApplicationService.registerPayment`).
- **`POST /api/v1/comptabilite/rebuild`** : MockMvc avec sécurité réelle et `RoleScopeFilter`.
- Observation : réponses HTTP normalisées, état SQL de `ecritures_comptables` et `lignes_ecriture`, exception remontée
  au commit de l'appelant. Factures et règlements insérés en SQL avec identifiants fixes (`<FACTURE_n>`,
  `<REGLEMENT_n>`).

## Scénarios (12 tests, 21 goldens approuvés après relecture)

| Test                                                                                     | Verrouille                                                                                                                                                                                    |
|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `p1_genere_une_vente_par_facture_dont_la_periode_est_incluse_dans_la_periode_validee`    | R17 (TTC/HT/TVA, TVA dérivée TTC − HT, pas de ligne TVA à 0), R18 (compte du payeur 411210, défaut 411500, patient 411100), date = fin de période, B14 (période incluse), isolation de centre |
| `p1_rejouee_ne_cree_pas_de_doublon_meme_si_le_compte_du_payeur_change`                   | R6 idempotence par source                                                                                                                                                                     |
| `p1_une_facture_desequilibree_arrete_les_factures_suivantes_sans_erreur_pour_l_appelant` | B3/B4 (KB-CPT-38)                                                                                                                                                                             |
| `p1_sur_une_periode_cloturee_ne_genere_rien_et_n_echoue_pas`                             | R7 hors transaction                                                                                                                                                                           |
| `p1_dans_la_transaction_appelante_un_refus_metier_est_observe_au_commit`                 | B17 (KB-CPT-37) : `UnexpectedRollbackException`, écriture de l'appelant perdue                                                                                                                |
| `p2_credite_le_compte_debite_par_la_vente_avec_l_id_du_reglement_retrouve_en_base`       | R19, B13 (identifiant retrouvé par facture/centre/date/montant/auteur), compte crédité = compte débité par la vente même après changement de paramétrage                                      |
| `p2_sans_reglement_en_base_derive_un_id_rejouable_et_prend_la_valeur_absolue`            | B13 (identifiant dérivé `nameUUIDFromBytes`), B6 (valeur absolue), compte client patient sans vente                                                                                           |
| `p2_sur_une_periode_cloturee_ne_genere_rien_et_n_echoue_pas`                             | R7 hors transaction                                                                                                                                                                           |
| `p2_dans_la_transaction_appelante_un_refus_metier_annule_le_reglement_de_l_appelant`     | B17 côté règlement (KB-CPT-37)                                                                                                                                                                |
| `rebuild_reconstitue_ventes_a_la_date_de_facturation_et_reglements_puis_est_rejouable`   | B10 (date de facturation), filtre par dates, compteurs (KB-CPT-39), isolation de centre, rejeu idempotent                                                                                     |
| `rebuild_s_arrete_au_premier_mois_cloture_et_garde_les_ecritures_deja_creees`            | B10 : 422 `PERIODE_CLOTUREE`, écritures précédentes conservées, règlements non traités                                                                                                        |
| `rebuild_roles_centre_et_validation_des_dates`                                           | SECRETAIRE 403, autre centre 403, `from` absent 400, dates inversées 200, corps absent 500, anonyme 403                                                                                       |

## Exécution

- Legacy : 12 tests, 0 échec, **3 passages consécutifs verts en ordre aléatoire**
  (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`) ; aucun `*.received.json`.
- Commande, depuis `backend/` :
  `./mvnw -o -pl platform -am test -Dtest=VentesEtReglementsCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`

## Fichiers legacy ajoutés à `covered.json`

`FacturationValideeComptabiliteListener`, `ReglementEncaisseComptabiliteListener`, `GenerateurEcritureComptable`.
Déjà couverts par C3 : `ComptabiliteRestController`, `ComptabiliteApplicationService`, `ComptabiliteService`,
`ComptabiliteUseCase`, `EcritureComptableJpaAdapter`, `PeriodeComptableJpaAdapter`. `seamAllowed` reste vide.

## Ce qui n'est pas verrouillé

- Base H2 seulement : les verrous PostgreSQL (`pg_advisory_xact_lock`) et la concurrence entre deux validations ou
  règlements simultanés ne sont pas exercés (couverts par le test H2 du port de séquence en C4).
- Le chemin complet `FacturationApplicationService.validate` / `ReglementApplicationService.registerPayment` n'est pas
  appelé : la transaction appelante est simulée par un `TransactionTemplate` (même propagation `REQUIRED`).
- B5 (compte de TVA vide au mapping) : non rejouable, C2 relit toujours `44571` (KB-CPT-14).
- Erreur technique non métier dans P1/P2 (panne base) : non injectable sans seam.
- Mode `CAISSE` : jamais produit par P2 ni `/rebuild` (B13) ; seul le cas d'usage le prendrait (déjà vu en C3).
- Avoirs (montants négatifs) en ventes : figés par C3 (KB-CPT-32), non rejoués ici.
- Aucune mutation (PIT) exécutée sur la zone legacy ; la couverture est comportementale.

## Bugs connus

KB-CPT-37 (B17, risque fort, décision requise avant la bascule), KB-CPT-38 (B3/B4), KB-CPT-39 (`/rebuild`),
KB-CPT-40 (libellés de règlement). Voir `.ddd/safety-net/known-bugs.md`.

## Parité sur le nouveau code (2026-10-19) — ADR 0027

La suite cible désormais le nouveau code (14 tests, aucun flag). Goldens inchangés : P1 nominal et rejeu, P2 nominal et
identifiant dérivé, périodes clôturées hors transaction, `/rebuild` nominal (réponses), lignes, rôles et validations.
Écarts décidés (goldens `*_nouveau_code`) : transactions appelantes conservées, facture déséquilibrée sans arrêt des
suivantes, `/rebuild` qui continue et rapporte ses refus, ventes régénérées datées de la fin de période, numérotation
numérique. Nouveaux scénarios : refus rapporté par `/rebuild`, parcours « facture refusée par P1 → listée par
`/ventes/non-comptabilisees` → corrigée → régénérée par `/rebuild` à la même date que P1 → plus rien à régénérer ».
Trois passages consécutifs verts en ordre aléatoire (avec la suite de numérotation : 30 tests).

