---
status: accepted (livrée le 2026-11-14 ; sous-slice S-DDL2b = DDL à faire)
date: 2026-11-14
decision-makers: [utilisateur (D7), architecte]
context-scope: comptabilite, stock, dossier-medical, platform (gardes)
related: ADR 0024, 0108, 0181 ; `inventaire-platform-restant-v2.md` ligne 17
---

# 0185 - S-DDL2 : `app_settings` (numérotation par centre) appartient à la Comptabilité

## Décision (D7)
La Comptabilité possède les compteurs de numérotation par centre et par type de document. Plus aucun SQL sur `app_settings` hors comptabilité.

## Inventaire (Grep) avant
| Écrivain/lecteur | Clés | Algorithme |
|---|---|---|
| Stock : `SequenceDesPiecesJdbc` (BR, BS, INV, SPS) et `BonsDeCommandeJdbc` (BC) | `SEQ_BR/BS/BC/SPS`, `INV` | `UPDATE +1` ; si 0 ligne `INSERT 1` (doublon : `UPDATE`) ; lecture préfixe + `%05d` |
| Dossier médical : `NumerotationDesOrdonnancesJdbc` | `SEQ_ORD` | `INSERT` conditionnel à 0, `SELECT … FOR UPDATE`, `UPDATE` ; verrou refusé (55P03) -> `EcritureConcurrente` |
| Comptabilité : `SequenceDesNumerosDePiecesJdbc` | `SEQ_ECRITURE_*`, `SEQ_SOURCE_*` | ligne de verrou (H2) ; inchangé |
| Plateforme : `schema.sql:266` (DDL H2), `seed.sql` (4 `MERGE` BR/BS), Flyway `V1` | | DDL/seed (étape 3, voir ci-dessous) |
| Gardes : `TableDuStockTest` (`TABLES_LUES_AILLEURS`), `TableDuDossierMedicalTest` (`ecrites.remove`) | | exceptions SOLDÉES (retirées) |
Tests/scripts qui préparent ou lisent la table (caractérisation, `*Contrat`, `*pg16-rejeu.sh`) : hors production, conservés.

## Conception
- `comptabilite.publishedlanguage.NumerotationDesDocuments.suivant(centre, type, préfixeParDéfaut)` ; adapter `NumerotationDesDocumentsJdbc` = algorithme du Stock (le plus utilisé) à l'identique. Contrat unique : double en mémoire, H2, PostgreSQL 16 (`-Dcomptabilite.pg16.url`, IT désactivé sans propriété).
- Dossier médical : ACL `infrastructure/acl/comptabilite/NumerotationDesOrdonnancesParLaComptabilite` (type `SEQ_ORD`, préfixe `ORD-`) ; la traduction du verrou refusé en `EcritureConcurrente` reste côté consommateur. Dépendance Maven ajoutée (déjà transitive par `contexts-seance`, aucun cycle) ; la règle ArchUnit autorise `comptabilite.publishedlanguage`.
- Stock : CYCLE MAVEN (comptabilite -> stock). Direction adaptée comme pour `VerrouDuCentreFourni` (ADR 0108) : le Stock publie le port `NumerotationDesPiecesFournie` ; la Comptabilité l'implémente (`infrastructure/acl/stock/NumerotationDesPiecesDuStockParLaComptabilite`) et le fournit en bean. Les tests du Stock utilisent un double en mémoire.

## Écarts de comportement (aucun observé par le filet ; analysés)
1. `SEQ_ORD` : l'ancien algorithme ignorait le préfixe enregistré (toujours `ORD-`), le nouveau utilise le préfixe enregistré (par défaut `ORD-`). Colonne `prefixe` NOT NULL, seule écriture historique `ORD-` : équivalent en pratique.
2. `SEQ_ORD` première création concurrente : ancien = doublon de clé remonté ; nouveau = doublon capturé puis incrément (H2 réussit ; PostgreSQL : transaction abandonnée, erreur). Cas jamais couvert par le filet ; **à confirmer techniquement avec les métiers/DPO <= 2026-12-15** s'il faut un `ON CONFLICT`.
Le trou de numéro sur refus de signature (KB-MED-20) est inchangé : reproduit, à confirmer métiers <= 2026-12-15.

## Étape 3 (DDL hors `schema.sql`) : NON faite dans cette slice (budget de production dépassé : 14 fichiers)
Sous-slice **S-DDL2b** : déplacer `CREATE TABLE app_settings` de `platform/.../db/schema.sql` vers un script de la comptabilité et les 4 `MERGE` de `seed.sql` vers le seed des contextes (voir la déclaration des emplacements de schéma des contextes déjà migrés). Aucune FK vers `app_settings` : ordre H2 sans contrainte. Butoir 2027-06-30, à solder en le supprimant.
