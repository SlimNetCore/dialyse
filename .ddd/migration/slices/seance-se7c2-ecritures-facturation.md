---
slice: seance-se7c2-ecritures-facturation
status: built
date: 2026-11-08
order: 7.3.2
context: seance
parent: seance-se7c-lectures-facturation-stock
depends_on: [seance-se7c1-lectures-stock]
sources: [ADR 0097, .ddd/safety-net/seance-se7c2-ecritures-facturation.md, .ddd/safety-net/known-bugs.md (KB-SEA-47, KB-SEA-71 à 76, KB-FAC-17, KB-FAC-54), .ddd/migration/verifications-production.md §62]
---

# SE7c2 : écritures et lectures de la Facturation sur la table des séances (ARGENT)

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL, aucun flag, aucune migration. Décisions : ADR 0097 (accepted, à confirmer au plus tard le 2026-12-15).

## Lots livrés
| Lot | Commit | Contenu |
|---|---|---|
| Filet | `0815b467` | Caractérisation des écritures de la Facturation sur `seances` : H2 5 tests (`EcrituresEtLecturesDeSeancesParLaFacturationCharacterizationIntegrationTest`, goldens `se7c2-ecritures`), PG16 3 tests ; KB-SEA-71 à 75 consignés. Vert sur le legacy avant tout code |
| A | `0dc32f38` | ADR 0097 ; 4 ports publiés `seance.v1` (`SeancesAFacturerPubliees`, `ExclusionsEtForfaitsDesSeancesPublies`, `LiaisonDesSeancesAUneFacturePubliee`, `SeancesDesFacturesPubliees`), non câblés |
| c2a | `03088e80` | `FacturationDesSeancesJdbc` (Séance) ; contrat `FacturationDesSeancesContrat` (double en mémoire, H2, PG16 jetable `seance-se7c2a-pg16-rejeu.sh`), non câblé |
| c2b + B | `42108ce2` | `LiaisonDesSeancesJdbc` déplacée dans `contexts/seance/infrastructure/persistence` ; KB-SEA-71 corrigé (`facture_id IS NULL` sur `lierALAbsence`) ; 4 langages fournis côté Facturation (`ExclusionsDeSeancesFournies`, `LiaisonDesSeancesFournie`, `SeancesAFacturerFournies`, `SeancesDesFacturesFournies`) et traducteurs `platform/infrastructure/acl/facturation/*ParLaSeance` ; suppression de `SeancesDeFacturationJdbc`, `SeancesFacturablesJdbc`, `LiaisonDesSeancesJdbc` (Facturation) et des tests `SeancesJdbcContrat`, `SeancesJdbcContratTest`, `SeancesPostgresContractIT` ; `TableDesSeancesTest` à 17 lecteurs ; règle ArchUnit `la_facturation_ne_depend_pas_de_la_seance` ; `LiaisonConcurrentePostgresContractIT` déplacé dans la Séance |
| C | (non commité) | RG-SEA-061, annexe 16, glossaire, §62, KB-SEA-71 à 76 (faits avec c2b) ; cette fiche, bilan de l'ADR 0097, correspondance des contrats, état `built` |

## Parité
| Élément | Résultat |
|---|---|
| Goldens `characterization/facturation/**` et `se7c2-ecritures` | Inchangés et verts, sauf l'écart décidé ci-dessous |
| Écart unique | KB-SEA-71 CORRIGÉ : `lier_a_l_absence_une_seance_absente_deja_facturee_nouveau_code.approved.json` ; legacy archivé en R100 (`_ecarts-legacy/se7c2-ecritures/lier_a_l_absence_une_seance_absente_deja_facturee_avant_se7c2.legacy.json`) |
| Reproduits | KB-SEA-47, 72, 73 / KB-FAC-17, 74, 75 / KB-FAC-54 |
| KB-SEA-76 | Test instable du journal d'audit asynchrone, non reproduit, résidu daté (known-bugs) |
| Flag / bascule | Aucun (ADR 0024) ; rollback = retour au commit précédent ; contrôle SELECT seule `verifications-production.md` §62 |

## Correspondance des cas des contrats Facturation supprimés
Les 3 fichiers supprimés ne déclaraient que 2 cas propres (`SeancesPostgresContractIT`) ; `SeancesJdbcContrat` (décor SQL) et `SeancesJdbcContratTest` (H2) rejouaient les 25 cas de `facturation/contrat/SeancesContrat`. Le double en mémoire de la Facturation joue toujours `SeancesContrat` (`SeancesEnMemoireContratTest`). 27 cas, 27 retrouvés, **0 recréé** au lot C ; après la revue du 2026-11-08, les cas 5, 6, 7, 24 et 25 sont de nouveau joués sur JDBC (`FAC-JDBC`, extraits de `SeancesContrat` sans duplication).

`FDS` = `seance/.../contrat/FacturationDesSeancesContrat` (mémoire, H2, PG16) ; `FAC-JDBC` = `facturation/.../contrat/ForfaitAppliqueContrat` (5 cas extraits de `SeancesContrat`, qui l'étend : mémoire ; `ForfaitAppliqueJdbcContratTest` H2 ; `ForfaitAppliquePostgresContractIT` PG16 rejoué par `seance-se7c2-pg16-rejeu.sh` ; rétabli après la revue du 2026-11-08) ; `FDS-JDBC` = `FacturationDesSeancesJdbcContrat` (H2, PG16) ; `TDS` = `facturation/.../acl/seance/TraducteursDesSeancesTest` ; `PFA` = `PolitiqueDeForfaitAppliqueTest` ; `Aperçu` / `Excl.` = goldens `ApercuCharacterizationIntegrationTest` / `ExclusionEtForfaitCharacterizationIntegrationTest` (bout en bout, adapters réels).

| # | Cas supprimé (`SeancesContrat` via JDBC, sauf 26-27) | Équivalent |
|---|---|---|
| 1 | `should_read_only_validated_or_signed_seances_not_invoiced_of_the_centre_within_the_period` | FDS, même nom |
| 2 | `should_include_the_first_and_the_last_day_of_the_period` | FDS, même nom |
| 3 | `should_order_the_seances_by_patient_name_then_date` | FDS, même nom |
| 4 | `should_read_the_patient_of_each_seance` | FDS `should_read_the_patient_the_payer_and_the_imposed_forfait_of_each_seance` ; traduction TDS `should_apply_the_imposed_forfait_before_the_prise_en_charge` |
| 5 | `should_apply_the_forfait_of_the_prise_en_charge_covering_the_date` | **FAC-JDBC, même nom (vrai `CouvertureDesPatientsJdbc`, H2 et PG16)** ; PFA, même nom ; TDS (cas `false`) ; Aperçu `le_forfait_applique_suit_la_surcharge_puis_la_pec_couvrant_la_date_puis_la_pec_la_plus_recente` |
| 6 | `should_apply_the_forfait_imposed_on_the_seance_before_the_prise_en_charge` | **FAC-JDBC, même nom** ; FDS cas 4 (lecture de la surcharge) ; TDS (cas `true`) ; PFA `should_apply_the_forfait_imposed_on_the_seance_before_any_prise_en_charge` ; Aperçu idem |
| 7 | `should_apply_the_most_recent_prise_en_charge_even_when_it_has_ended_then_the_undefined_forfait` | **FAC-JDBC, même nom** ; PFA `should_fall_back_on_the_most_recent_prise_en_charge_even_when_it_has_ended`, `should_apply_the_undefined_forfait_at_zero_without_any_prise_en_charge` ; TDS `should_read_the_prises_en_charge_of_a_patient_once_and_keep_the_order_and_the_undefined_forfait` ; Aperçu (`PEC_ECHUE_05`, `SANS_PEC_03`) |
| 8 | `should_find_a_seance_of_the_centre_with_its_state` | FDS, même nom ; TDS `should_translate_the_seance_found_with_its_memory_of_the_exclusion` |
| 9 | `should_not_find_an_unknown_seance_nor_one_of_another_centre` | FDS `should_not_find_an_unknown_seance_nor_one_of_another_centre_and_see_an_invoiced_one_as_invoiced` |
| 10 | `should_see_an_invoiced_seance_as_invoiced` | FDS, idem 9 |
| 11 | `should_exclude_a_seance_remember_its_status_and_remove_it_from_the_facturables` | FDS `should_exclude_a_seance_remember_its_status_and_remove_it_from_the_seances_to_invoice` |
| 12 | `should_remember_the_absence_declared_by_the_exclusion` | FDS, même nom |
| 13 | `should_reinclude_an_excluded_seance_with_its_former_status_and_forget_the_exclusion` | FDS, même nom |
| 14 | `should_refuse_to_exclude_a_seance_in_another_status_invoiced_unknown_or_of_another_centre` | FDS, même nom (+ message exact) |
| 15 | `should_refuse_to_link_an_absence_to_a_seance_that_was_not_excluded` | FDS, même nom (+ séance facturée, autre centre) ; FDS `should_refuse_to_link_an_absence_to_an_absent_seance_that_already_carries_an_invoice` (KB-SEA-71) |
| 16 | `should_refuse_to_reinclude_a_seance_that_was_not_excluded_by_the_facturation` | FDS `should_refuse_to_reinclude_a_seance_that_was_not_excluded_invoiced_unknown_or_of_another_centre` |
| 17 | `should_impose_a_forfait_on_a_seance_and_read_it_back_in_the_facturables` | FDS `should_impose_a_forfait_on_a_seance_and_read_it_back_without_writing_its_code` |
| 18 | `should_refuse_to_impose_a_forfait_on_an_excluded_invoiced_unknown_or_foreign_seance` | FDS `should_refuse_to_impose_a_forfait_on_an_excluded_invoiced_unknown_foreign_or_lower_case_seance` |
| 19 | `should_read_the_registration_number_and_the_payer_of_the_patient` | FDS cas 4 (`numeroAssurance`, `centrePayeur`, `agence`) ; TDS (`numeroDImmatriculation`) |
| 20 | `should_read_a_patient_without_payer` | FDS cas 4 (seconde séance : payeur et agence nuls) |
| 21 | `should_link_the_seances_to_the_invoice_and_bill_them` | FDS, même nom |
| 22 | `should_refuse_to_link_a_seance_that_is_no_longer_eligible` | FDS `should_refuse_to_link_a_seance_that_is_no_longer_eligible_with_the_exact_message` |
| 23 | `should_refuse_to_link_a_seance_of_another_centre` | FDS `should_refuse_to_link_a_seance_of_another_centre_and_never_link_it_twice` ; course : `LiaisonConcurrentePostgresContractIT` |
| 24 | `should_find_a_forfait_of_the_centre_with_its_label_and_price` (`ForfaitsDuCentreJdbc`, table `forfait`, resté à la Facturation) | **FAC-JDBC, même nom (H2 et PG16, rétabli après revue)** ; Excl. golden `forfait_nominal_200` ; double : `SeancesEnMemoireContratTest` |
| 25 | `should_not_find_an_unknown_forfait_nor_one_of_another_centre` | **FAC-JDBC, même nom (H2 et PG16)** ; Excl. goldens `forfait_inconnu`, `forfait_du_centre_B` ; double : `SeancesEnMemoireContratTest` |
| 26 | PG16 `should_write_the_author_and_the_instant_of_the_forfait_imposed` | FDS-JDBC, même nom (H2 et PG16) |
| 27 | PG16 `should_keep_the_memory_of_the_exclusion_in_the_two_columns_added_by_v39` | FDS-JDBC, même nom (H2 et PG16) |

Note (corrigée après la revue du 2026-11-08, constat m1) : `ForfaitsDuCentreJdbc` (table `forfait`) et la politique de forfait de bout en bout avec le vrai `CouvertureDesPatientsJdbc` (3 cas de prise en charge) sont de nouveau joués sur H2 et PostgreSQL 16 par `ForfaitAppliqueContrat`, sans aucun SQL sur `seances` : les séances y arrivent par le port `SeancesAFacturerFournies`, que la plateforme implémente (`SeancesFacturablesParLaSeance`, lecture déjà prouvée sur H2 et PG16 par FDS) ; le module Facturation ne pouvant pas dépendre de la Séance (cycle Maven), un double de ce port respectant son contrat documenté (casse exacte, non facturées, période incluse, tri nom, prénom, date, identifiant) le remplace dans ce contrat. Les lignes `patients` ne sont posées que pour les clés étrangères de `prise_en_charge` du schéma réel.

## Bilan nominatif des résidus (revue ≤ 2026-12-15 ET butoir propre)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| C2-R1 | ~~Aucune clé étrangère ni contrainte sur `seances.facture_id` (KB-SEA-74)~~ corrigé le 2026-11-09 (lot L3, ADR 0114) : garde applicative + V54 `NOT VALID` ; reste à VALIDER la contrainte à la main après §62.2/§76.2 | 2026-12-15 | 2027-03-31 |
| C2-R2 | KB-SEA-47 : écrire `forfait_override_code` ou ne plus le lire | 2026-12-15 | 2027-01-31 (et avant toute mise en production) |
| C2-R3 | ~~KB-SEA-72, KB-SEA-73 (mémoire blanche, garde du port)~~ corrigés le 2026-11-09 (lot L3, ADR 0114) ; casse du statut (KB-FAC-17, KB-SEA-82) : V55 `NOT VALID`, reste à normaliser les données (§76.1) puis VALIDER à la main | 2026-12-15 | 2027-03-31 |
| C2-R4 | KB-SEA-75 / KB-FAC-54 : une seule définition de « séance facturée » | 2026-12-15 | 2027-01-31 |
| C2-R5 | 4 langages `*Fournies` et traducteurs `*ParLaSeance` logés dans la plateforme (cycle Maven Séance -> Absences-patients -> Facturation) | 2026-12-15 | 2027-06-30 |
| C2-R6 | `SeancesAFacturerPubliees` lit `patients` et `centre_payeur` (jointure, entrée de `TableDuPatientCouvertureTest`) : à remplacer par un langage publié de Patient et couverture | 2026-12-15 | 2027-03-31 |
| C2-R7 | Courses exclusion / exclusion, exclusion / émission et interblocage de deux émissions : non rejoués | 2026-12-15 | 2027-03-31 |
| C2-R8 | KB-SEA-76 (test d'audit asynchrone instable) ; ~~`ForfaitsDuCentreJdbc` sans test d'adapter dédié~~ soldé le 2026-11-08 (`ForfaitAppliqueContrat`, H2 + PG16) | 2026-12-15 | 2027-01-31 |
| C2-R9 | Port `exclure(centre, séance, statutAvant)` orienté données : la règle « séance excluable » reste dans `SeanceDeFacturation` (Facturation) et la Séance subit la transition sans passer par son modèle ; à remplacer par une commande d'intention côté Séance (`exclureDeLaFacturation`) quand l'agrégat Séance portera ses transitions (suggestion de la revue ; ADR 0097) | 2026-12-15 | 2027-06-30 |
| C2-R10 | Écrivains de `seances.facture_id` hors Séance, révélés par `EcrivainsDeLaFactureDesSeancesTest` (voir ci-dessous) | 2026-12-15 | 2027-06-30 |

## Suites de la revue indépendante (2026-11-08, `.ddd/verify/verify-seance-se7c2.md`)

**KB-SEA-71 : portée réelle (constat m2).** La garde `facture_id IS NULL` de `lierALAbsence` est une **défense en profondeur sur le port**, observée par le golden au niveau du port (`lier_a_l_absence_une_seance_absente_deja_facturee_nouveau_code`, appel direct du port) et par le contrat FDS `should_refuse_to_link_an_absence_to_an_absent_seance_that_already_carries_an_invoice`. **Sans effet aujourd'hui sur le trafic HTTP** : `ExclureUneSeance` (seul appelant) fait refuser une séance facturée par le domaine (`SeanceDeFacturation.exclure`) puis par la garde de `exclure`, avant `lierALAbsence`, dans la même transaction. Aucun code de production changé. La recette (e) du §62 est réécrite (refus observable de l'exclusion, en HTTP) ; la garde du port ne se recette pas en HTTP.

**Écrivains de `seances.facture_id` (constat m4).** Garde `platform/src/test/java/com/hemodialyse/backend/architecture/EcrivainsDeLaFactureDesSeancesTest.java` (même scan que `TableDesSeancesTest`, Séance comprise ; compte asserté 3 ; revue au plus tard le 2026-12-15) :

| Écrivain | Contexte | Nature | Butoir |
|---|---|---|---|
| `contexts/seance/.../persistence/LiaisonDesSeancesJdbc.java` | Séance | `SET statut = 'FACTUREE', facture_id = ?` à l'émission ; le SEUL écrivain voulu (via `LiaisonDesSeancesAUneFacturePubliee`) | — |
| `platform/.../persistence/adapter/SeanceRepositoryAdapter.java` | hors Séance (legacy) | réécriture JPA de la ligne ENTIÈRE (`toJpa` ne porte pas `factureId`) : `facture_id` redevient NULL lors d'un changement de date legacy (`SeanceDomainService.updateDate`, `PUT /api/v1/seances/{id}`, route non reprise) ; KB-SEA-49, ARGENT, déjà consigné et caractérisé (`se5/date/…/facture_effacee`), non corrigé | 2027-06-30 |
| `platform/src/main/resources/db/seed.sql` | hors Séance (amorçage) | jeu de développement : rattache les séances `FACTUREE` sans facture aux factures `AUTO-` du mois ; jamais sur une base partagée | 2027-06-30 |

La phrase de l'ADR « seul `LiaisonDesSeancesAUneFacturePubliee` écrit `facture_id` » est donc vraie pour le code de la nouvelle architecture, pas pour le dépôt : les deux écrivains legacy sont listés plutôt que masqués (C2-R10). Les migrations Flyway et les entités JPA sont hors du scan (comme `TableDesSeancesTest`) ; aucune migration n'écrit `seances.facture_id` (vérifié par recherche le 2026-11-08).

**Nits et suggestion.** En-tête de `seance-se7c2-pg16-rejeu.sh` réécrit (tests et variables réels) ; entrée déplacée de `TableDuPatientCouvertureTest` datée « 2026-11-08 (déplacée) » avec une seule échéance (revue 2026-12-15, butoir 2027-03-31) ; suggestion consignée en C2-R9 (pas de changement du port : reproduction décidée par l'ADR). Renommage des fournisseurs `SeancesDeFacturationParLaSeance` / `SeancesFacturablesParLaSeance` sur l'interface implémentée (constat m6, « éventuellement ») : **non fait**, ce sont des fichiers de production de la plateforme hors seam ouvert et le gain est seulement de nommage ; noms et synonymes consignés au glossaire de la Facturation (questions ouvertes).

## Reste à faire
Confirmation métiers / DPO / DBA de l'ADR 0097 (au plus tard 2026-12-15) ; exécuter les requêtes §62 sur une copie de production ; résidus C2-R1 à C2-R10 ; `/ddd-verify`.
