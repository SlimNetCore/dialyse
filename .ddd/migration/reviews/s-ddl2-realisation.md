---
status: delivered (étapes 1 et 2) ; étape 3 = sous-slice S-DDL2b
date: 2026-11-14
sources: [ADR 0185, inventaire-platform-restant-v2.md ligne 17, décision D7]
---
# S-DDL2 : `app_settings` à la Comptabilité

## Étape 1 : inventaire
Voir le tableau de l'ADR 0185. Écrivains de production : Stock (`SequenceDesPiecesJdbc`, `BonsDeCommandeJdbc`), Dossier médical (`NumerotationDesOrdonnancesJdbc`), Comptabilité (`SequenceDesNumerosDePiecesJdbc`, verrou). DDL : `platform/.../schema.sql:266`, Flyway `V1`, 4 `MERGE` dans `seed.sql:2402-2411`. Gardes citant la table : `TableDuStockTest` (`TABLES_LUES_AILLEURS`) et `TableDuDossierMedicalTest` : les deux exceptions sont soldées (supprimées).

## Étape 2 : fichiers de production (14)
| Contexte | Fichier |
|---|---|
| comptabilite | `publishedlanguage/NumerotationDesDocuments` (nouveau) ; `infrastructure/persistence/NumerotationDesDocumentsJdbc` (nouveau) ; `infrastructure/acl/stock/NumerotationDesPiecesDuStockParLaComptabilite` (nouveau) ; `configuration/ComptabiliteConfiguration` (2 beans) |
| stock | `publishedlanguage/NumerotationDesPiecesFournie` (nouveau) ; `BonsDeCommandeJdbc`, `BonsDeReceptionJdbc`, `BonsDeSortieJdbc`, `InventairesJdbc`, `SortiesPourSeancesJdbc` (port injecté) ; `StockContexteConfiguration` ; `SequenceDesPiecesJdbc` SUPPRIMÉ |
| dossier-medical | `infrastructure/acl/comptabilite/NumerotationDesOrdonnancesParLaComptabilite` (nouveau) ; `DossierMedicalConfiguration` ; `pom.xml` ; `NumerotationDesOrdonnancesJdbc` SUPPRIMÉ |

Cycle Maven (comptabilite -> stock) : direction adaptée, le Stock publie un port fourni implémenté par la Comptabilité (modèle `VerrouDuCentreFourni`, ADR 0108).

## Filet et tests
Filet existant réutilisé (goldens inchangés, aucun `.approved` modifié, aucun `-Dcharacterization.approve`) : stock (compteurs `SEQ_BC/BR/BS` : `goldenRequete creation_nominale_compteur`, concurrence réceptions/sorties/inventaires), dossier médical dm2 (`LesOrdonnancesCharacterization…` : compteurs, deux centres, rejeu). Nouveau : contrat `NumerotationDesDocumentsContrat` (démarrage à vide, séquence, deux centres/types, préfixe enregistré, au-delà de cinq chiffres) passé par le double en mémoire, H2 (+ rollback + concurrence sur compteur existant) et PostgreSQL 16 (`NumerotationDesDocumentsPostgresContractIT`, `-Dcomptabilite.pg16.url`, non exécuté ici : pas de base PG jetable fournie). Doubles côté Stock : `NumerotationDesPiecesEnMemoire`.

## Écarts, décisions
Aucun écart de parité observé. Deux écarts théoriques non couverts (préfixe enregistré de `SEQ_ORD` ; première création concurrente de `SEQ_ORD`) : voir ADR 0185, « reproduit / à confirmer ≤ 2026-12-15 ». Aucun changement de comportement : pas de `*_nouveau_code`, pas d'archive `_ecarts-legacy`, pas de ligne KB « CORRIGÉ ».

## Reste à faire : S-DDL2b (butoir 2027-06-30, solder en supprimant)
FAIT le 2026-11-14 : voir `s-ddl3-realisation.md` et ADR 0186 (`CREATE TABLE app_settings` et 4 `MERGE` vers `contexts/comptabilite/.../db/h2/`).
