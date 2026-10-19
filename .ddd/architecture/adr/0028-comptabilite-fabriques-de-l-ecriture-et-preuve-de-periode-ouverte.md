---
status: accepted
date: 2026-10-19
deciders: utilisateur (Option A de la revue 2026-10-19-comptabilite)
---

# ADR 0028 — Comptabilité : les écritures naissent par des fabriques de l'agrégat, sur preuve de période ouverte

## Contexte

La revue `.ddd/reviews/2026-10-19-comptabilite.md` (constat major « tactique ») relevait que toutes les écritures
étaient
créées par `EcritureComptable.reconstituer` (fabrique de réhydratation, qui ne vérifie que R1/R2), alors que le modèle
tactique approuvé (`.ddd/tactical/comptabilite.md` §4) place R7 (période ouverte), R9 (extourne), R10 (composition d'une
pièce) et R21 (révision des sorties) dans l'agrégat. Ces règles vivaient dans les cas d'usage C4, C5 et C6.

Option A retenue par l'utilisateur : mettre le code en conformité avec le modèle, **sans changer aucun comportement
observable** (codes, messages, ordre des refus, goldens).

## Décision

1. **Preuve de période ouverte** : `PeriodeOuverte` n'est délivrée que par `PeriodeComptable.exigerOuverte()` (message
   `PERIODE_CLOTUREE` des écritures générées) ou `exigerOuverteALaSaisie()` (message historique de la saisie et de
   l'extourne, sans le centre, reproduit). Les fabriques de l'écriture l'exigent et vérifient qu'elle couvre la date :
   aucune écriture ne peut naître dans un mois clôturé (R7 tenu par le type).
2. **Fabriques de `EcritureComptable`** : `generer` (ventes, règlements, stock), `saisir` (≥ 2 lignes,
   `PIECE_MONTANTS_INVALIDES`), `exigerExtournable` + `extourner` (`PIECE_NON_SAISIE`, `PIECE_DEJA_EXTOURNEE`, lignes
   inversées), `reviserLesSorties` (refus si exportée, mêmes identité et numéro). `reconstituer` est réservé aux
   adaptateurs.
3. **`ModeleDePiece.composerLaPiece(montants, ...)`** porte R10 (un montant ≥ 0 par ligne, zéros omis, sens du modèle).
4. Les cas d'usage obtiennent la preuve **au moment où ils contrôlaient la période** et appellent `exigerExtournable`
   **en premier** : l'ordre des refus est inchangé. Ils gardent les règles d'ensemble qui exigent des lectures
   (idempotence par source, journal actif, comptes du plan, numérotation sous verrou).
5. Les identifiants des lignes sont fournis aux fabriques (`Supplier<UUID>`) ; les services de domaine de rédaction (C5,
   C6)
   tirent encore leurs identifiants au hasard (constat minor de la revue, non traité ici).

## Conséquences

- Parité prouvée : 20 suites (311 scénarios) de caractérisation Comptabilité vertes, goldens inchangés ; `check-arch`
  0 ;
  ArchUnit du module vert ; tests de domaine ajoutés (`EcritureComptableTest`, `ModeleDePieceTest`).
- Une nouvelle façon de créer une écriture doit passer par une fabrique et obtenir une `PeriodeOuverte` : le compilateur
  empêche d'oublier R7.
- Les deux formulations du refus `PERIODE_CLOTUREE` restent un écart historique reproduit (ADR 0019) ; les unifier
  serait
  un changement visible à décider par le métier.

