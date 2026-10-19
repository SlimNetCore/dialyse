---
status: accepted
date: 2026-10-19
decision-makers: [ utilisateur ]
context-scope: slice:comptabilite-c5-ventes-reglements
research: [ ]
---

# 0027 — Comptabiliser ventes et règlements après le commit, une source par transaction, refus régénérables

## Contexte et problème

Le filet C5 a confirmé B17 (KB-CPT-37) : les politiques P1 et P2 s'exécutaient de façon synchrone **dans** la
transaction de `FacturationApplicationService.validate` et de `ReglementApplicationService.registerPayment`. Un refus
métier (mois comptable clôturé) marquait cette transaction *rollback-only* : la facture ou le règlement de l'utilisateur
était perdu (`UnexpectedRollbackException`). De plus, une facture refusée arrêtait la comptabilisation des suivantes
(KB-CPT-38) et `/rebuild` s'arrêtait au premier refus (B10).

## Options considérées

1. Isoler la comptabilisation : après le commit de l'appelant, chaque facture ou règlement dans sa propre transaction ;
   les refus sont rapportés et régénérables.
2. Refuser en amont la facturation ou le règlement d'un mois comptable clos (ADR 0009).
3. Reproduire tel quel et corriger plus tard.

## Décision

Option 1, choisie par l'utilisateur le 2026-10-19 : « la facture ou le règlement est alors toujours conservé, même sans
écriture comptable, avec possibilité de régénérer l'écriture comptable pour les factures qui ont échoué ».

- Écouteurs `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` dans la plateforme ; ils délèguent au
  cas d'usage `ComptabiliserLesVentesEtReglements` du contexte `comptabilite`.
- Chaque source est comptabilisée dans une transaction « nouvelle » (`PROPAGATION_REQUIRES_NEW`, unité de travail propre
  à C5) ; un refus est rapporté dans un bilan et journalisé, les sources suivantes sont traitées (KB-CPT-38 corrigé).
- Régénération : `GET /api/v1/comptabilite/ventes/non-comptabilisees?from&to` liste les factures émises sans écriture ;
  `POST /api/v1/comptabilite/rebuild` les régénère, continue après un refus et le rapporte dans `echecs` (champ absent
  quand il n'y a pas de refus : la réponse nominale est inchangée). Une vente régénérée est datée de la **fin de sa
  période facturée**, comme par P1 (B10 corrigé : le legacy la datait de la date de facturation).

## Conséquences

- La facturation et l'enregistrement d'un règlement ne peuvent plus échouer à cause de la comptabilité.
- Une facture ou un règlement peut exister sans écriture : l'écart est visible (`/ventes/non-comptabilisees`, journaux
  d'avertissement) et se résorbe par `/rebuild` ; une source d'un mois clos ne peut être régénérée qu'à la condition que
  le mois soit ouvert (une clôture est irréversible : la correction passe par une pièce saisie).
- Si l'appelant annule sa transaction, aucune écriture n'est produite (l'écouteur ne s'exécute qu'après commit).
- Les ventes et règlements suivent la numérotation numérique du contexte (KB-CPT-24, déjà appliquée aux pièces en C4).
- Écran Angular de régénération : non livré (les routes existent).

