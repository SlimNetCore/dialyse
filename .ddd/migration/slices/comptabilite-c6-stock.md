---
slice: comptabilite-c6-stock
status: built
date: 2026-10-19
sources: [.ddd/tactical/comptabilite.md §2 R6 R7 R15 R16 B8 B9 B15 B16, ADR 0024, ADR 0027, .ddd/safety-net/comptabilite-c3-ecritures.md KB-CPT-33, .ddd/migration/slices/comptabilite-c5-ventes-reglements.md]
---

# C6 — Comptabilisation du stock

## Périmètre

Refactoring sur place dans `backend/contexts/comptabilite`, mêmes tables, aucune migration de schéma :

- `POST /api/v1/comptabilite/stock/synchroniser` (ADMIN) : met la comptabilité d'un centre en accord avec son stock sur
  une période de 1 à 366 jours (contrôle du contrôleur) ;
- tâche planifiée `ComptabiliteStockScheduler` (tous les jours à 03:15, fenêtre glissante de 35 jours, un centre en
  erreur n'arrête pas les autres) ;
- legacy : `ComptabiliteStockApplicationService`, `ComptabiliteStockService`, `GenerateurEcritureStock`,
  `ComptabiliteStockUseCase`, `OperationsStockPort`, `OperationsStockJdbcAdapter`.

Tables écrites : `ecritures_comptables`, `lignes_ecriture` (et `app_settings` pour le verrou H2 des séquences).
Tables lues : `bons_reception`, `bons_reception_lignes`, `stock_movements`, `inventaires`, `inventaire_lignes`,
`articles` (comptes propres à l'article), `mapping_comptable`, `periodes_comptables`, `centers`.

## Règles tactiques à caractériser sur le legacy

- Réception validée : débit stock (compte de l'article sinon du centre) / crédit factures non parvenues, hors taxe ; une
  écriture par bon, une seule fois ; brouillons ignorés ; date = date de réception (à défaut date de création).
- Sorties : une écriture par jour (UTC, B15), consommation / stock, valorisées au prix appliqué, sinon PMP après
  mouvement, sinon PMP courant ; mise à jour en place tant que l'écriture n'est ni exportée ni dans un mois clos (B9),
  sinon écriture de complément (datée du jour d'origine, ou d'aujourd'hui si ce mois est clos ; B8) ; au plus 50
  compléments ; une journée dont les sorties diminuent ou disparaissent est corrigée par la différence.
- Inventaire clôturé : excédent débit stock / crédit boni, manquant débit mali / crédit stock ; inventaires en cours
  ignorés ; une écriture par inventaire, une seule fois.
- Mois clos : pièce « ignorée » (compteur) au lieu d'une erreur ; arrondi HALF_UP par ligne ; mapping des comptes du
  stock
  et des journaux (R15) ; isolation de centre ; compteurs de la réponse.

Les détails d'erreurs HTTP, de numérotation et d'effets SQL sont figés par le filet. Aucun seam de production n'est
autorisé par cette fiche.

## Hors périmètre

C1 à C5, valorisation du stock (PMP, FEFO : module Stock), écran Angular, export Sage 100, migration de schéma.

## Implémentation et validation (2026-10-19)

1. Décision utilisateur (KB-CPT-42 et 44, prolonge l'ADR 0027) : lister dans un champ `echecs` les pièces ignorées (mois
   clos, plafond de compléments) et les réceptions ou sorties sans valeur.
2. Contexte `backend/contexts/comptabilite` :
    - domaine `domain/stock` : port `OperationsDeStock` (réceptions, sorties par jour UTC, inventaires, articles sans
      valeur) et service pur `SoldesDuStock` (schémas réception / sorties / inventaire en soldes par compte, différence,
      lignes) ;
    - cas d'usage `ComptabiliserLeStock` : même algorithme que le legacy (pièce unique, journée révisée en place ou
      complétée, mois clos, 50 compléments), une transaction par centre, verrous de source et de séquence partagés,
      bilan `Synchronisation` (compteurs du legacy + `echecs`) ;
    - adaptateur `OperationsDeStockJdbc` (mêmes requêtes que le legacy, code de l'article en plus) ;
    - interfaces : `ComptabilisationDuStockControleur` (`POST /stock/synchroniser`, même contrat, `echecs` si non vide)
      et
      `ComptabilisationDuStockPlanifiee` (03:15, fenêtre de 35 jours, un centre en erreur n'arrête pas les autres).
3. Legacy retiré : `ComptabiliteRestController` (dernière route), `ComptabiliteStockApplicationService`,
   `ComptabiliteStockScheduler`, `OperationsStockJdbcAdapter` (plateforme) ; `ComptabiliteStockService`,
   `GenerateurEcritureStock`, `ComptabiliteStockUseCase`, `OperationsStockPort`, tests `ComptabiliteStockServiceTest`,
   `ComptabiliteStockSchedulerTest` (remplacé par `ComptabilisationDuStockPlanifieeTest`), fixture
   `ComptabiliteFixtures`
   (`bc-comptabilite`). `ComptabiliteStockIntegrationTest` est réécrit sur le nouveau cas d'usage, assertions
   inchangées.
4. Validation : 371 tests (caractérisation C1 à C6, intégration, contexte 273, facturation, règlement, absences) verts ;
   suites stock vertes 3 fois de suite en ordre aléatoire ; `check-arch.ts` : 0 violation.
5. Reste pour C7 : 21 classes de `bc-comptabilite` (ancien agrégat d'écriture, ports, `ComptabiliteService`) utilisées
   par
   l'ACL transitoire `ComptabiliteApplicationService`, les ACL C1/C2, `EcritureComptableJpaAdapter`,
   `PeriodeComptableJpaAdapter`, `Sage100ExportAdapter` legacy et `ForfaitAbsenceJdbcAdapter` (TVA des Absences).

