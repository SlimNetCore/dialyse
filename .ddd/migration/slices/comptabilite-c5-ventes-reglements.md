---
slice: comptabilite-c5-ventes-reglements
status: built
date: 2026-10-19
sources: [.ddd/tactical/comptabilite.md §2 R6 R7 R17 R18 R19, P1, P2, B3-B6, B10, B13, B14, B17, ADR 0024, ADR 0025, .ddd/safety-net/comptabilite-c3-ecritures.md, .ddd/migration/slices/comptabilite-c4-modeles-pieces.md]
---

# C5 — Ventes et règlements

## Périmètre

Refactoring sur place dans `backend/contexts/comptabilite`, mêmes tables, aucune migration de schéma. Le périmètre de
caractérisation et de remplacement est :

- politique **P1** : écouteur de `FacturationValideeEvent` (`FacturationValideeComptabiliteListener`) qui relit les
  factures du centre dont la période est incluse dans la période validée et génère une écriture de ventes par facture ;
- politique **P2** : écouteur de `FactureReglementRecordedEvent` (`ReglementEncaisseComptabiliteListener`) qui retrouve
  l'identifiant du règlement en SQL (ou le dérive) et génère une écriture de règlement ;
- `POST /api/v1/comptabilite/rebuild` (ADMIN) : reconstitution des écritures de ventes (par date de facturation) et de
  règlement d'un intervalle de dates ;
- cas d'usage legacy `ComptabiliteUseCase.genererEcritureFacturation` / `genererEcritureReglement`
  (`ComptabiliteApplicationService`, `ComptabiliteService`, `GenerateurEcritureComptable`).

Tables écrites : `ecritures_comptables`, `lignes_ecriture` (et `app_settings` pour le verrou H2 des séquences).
Tables lues : `factures`, `facture_reglements`, `mapping_comptable`, `comptes_payeurs`, `periodes_comptables`.

## Règles tactiques à caractériser sur le legacy

- R17 ventes : débit compte client = TTC ; crédit ventes = HT ; crédit TVA collectée si TVA > 0, TVA dérivée de TTC − HT
  si la TVA de la facture est ≤ 0 ; date d'écriture = fin de période facturée (P1) ou date de facturation (`/rebuild`,
  B10) ; axe analytique centre ; statut `VALIDEE`.
- R18 compte client : compte du payeur paramétré, sinon compte client par défaut ; sans payeur, compte client patient.
- R19 règlement : journal et compte banque (le mode est toujours `BANQUE` par P2 et `/rebuild`, B13) ; montant en valeur
  absolue (B6) ; crédit du compte débité par l'écriture de la facture, sinon compte client résolu.
- R6 idempotence par source (facture, règlement) ; R7 période clôturée : refus `PERIODE_CLOTUREE`, avalé par P1/P2.
- B3/B4 : une facture déséquilibrée arrête la génération des suivantes ; B14 : pas de filtre de statut ;
  B17 : P1/P2 synchrones dans la transaction appelante.
- `/rebuild` : rôles, garde de centre, validation des dates, compteurs, arrêt au premier mois clos (B10).

Les détails d'erreurs HTTP, de numérotation et d'effets SQL sont figés par le filet. Aucun seam de production n'est
autorisé par cette fiche.

## Hors périmètre

C1 à C4, comptabilisation du stock (C6), retrait de `bc-comptabilite` (C7), export Sage 100, interface Angular,
migration de schéma, publication des événements par Facturation et Règlement (seule leur consommation est couverte).

## Implémentation et validation (2026-10-19) — ADR 0027

1. Décision utilisateur (KB-CPT-37) : la facture ou le règlement est toujours conservé, même sans écriture comptable,
   avec possibilité de régénérer l'écriture des sources qui ont échoué.
2. Contexte `backend/contexts/comptabilite` :
    - domaine `domain/vente` : `FactureAComptabiliser`, `ReglementAComptabiliser`, ports `FacturesAComptabiliser`,
      `ReglementsAComptabiliser`, service pur `RedactionDesEcritures` (R17, R18, R19, libellés et arrondis du legacy) ;
      refus `PeriodeCloturee` (`PERIODE_CLOTUREE`, même message) ;
    - cas d'usage `ComptabiliserLesVentesEtReglements` : une source par transaction (`PROPAGATION_REQUIRES_NEW`), verrou
      de source et de séquence partagés (B1/B2), bilan des sources comptabilisées et refusées ;
    - adaptateurs JDBC de lecture `FacturesAComptabiliserJdbc`, `ReglementsAComptabiliserJdbc` (tables `factures`,
      `facture_reglements`, aucune migration) ;
    - HTTP `VentesEtReglementsControleur` : `POST /rebuild` (déplacé du legacy, même contrat, plus `echecs`) et
      `GET /ventes/non-comptabilisees` (nouveau).
3. Plateforme : écouteurs P1/P2 en `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` délégant au
   contexte ; `/rebuild` retiré de `ComptabiliteRestController` (qui ne garde que le stock, C6) ;
   `ComptabiliteApplicationService` réduit à une **ACL transitoire** (port legacy `ComptabiliteUseCase`, utilisé par les
   suites de caractérisation C1 à C3) qui délègue au contexte et traduit les refus en `BusinessException`.
4. Legacy retiré : `GenerateurEcritureComptable`, génération de `ComptabiliteService`, tests unitaires
   `GenerateurEcritureComptableTest`, `ParametrageSoupleTest`, `JournauxDuMappingTest` (règles couvertes par la
   caractérisation).
5. Validation : 315 tests comptables (caractérisation C1 à C5, intégration, stock) et 272 tests du contexte verts ;
   suites C5 et numérotation vertes 3 fois de suite en ordre aléatoire ; facturation, règlement, absences : 83 tests
   verts ; `check-arch.ts` : 0 violation. Écarts décidés : voir `known-bugs.md` « Décisions C5 ».
6. Reste pour C7 : le port legacy `ComptabiliteUseCase` (génération) et son ACL, tant que les suites C1 à C3
   l'appellent.

