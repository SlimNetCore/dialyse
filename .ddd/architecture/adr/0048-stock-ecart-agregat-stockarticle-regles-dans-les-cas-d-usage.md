---
status: proposed
date: 2026-10-26
deciders: à valider par l'utilisateur
related: ADR 0024 (refactoring sur place), ADR 0045 ; `.ddd/tactical/stock.md` (§ 9, T5 à T8) ; `.ddd/verify/verify-stock.md` (constat M1)
---

# ADR 0048 — Stock : écart au modèle tactique, l'agrégat `StockArticle` n'est pas extrait

## Contexte

Le modèle tactique approuvé (2026-10-23) présente `StockArticle` comme le « cœur monétaire » du Stock : il porte les
invariants T5 (restant d'un lot jamais < 0), T6 (stock = Σ restants), T7 (FEFO) et T8 (article inactif : sortie refusée).
La construction (S5 à S6b) ne l'a **pas créé** : le domaine (`contexts/stock/domain`) n'a pas de classe `StockArticle`.
Les règles vivent dans les cas d'usage, sous verrou de ligne de l'article, dans une transaction par pièce, avec le
PMP rejoué par `RejeuDuPmp`. Cet écart n'était tracé nulle part ; les fiches S5, S6 et S6b citent encore `StockArticle`.

## Décision proposée (à valider)

**Assumer** le modèle « script transactionnel » pour les règles de stock : elles restent dans les cas d'usage
(`GestionDesSorties`, `GestionDesSortiesPourSeances`, `GestionDesReceptions`), protégées par les tests d'intégration et les
goldens de caractérisation (points d'argent § 11 du modèle). **Aucun refactoring** n'est fait maintenant. La matrice
invariant → test (§ 9, T5 à T8) est lue comme « couverts par les tests de cas d'usage et les goldens », non par des tests
d'un agrégat.

## Dette chiffrée

| # | Endroit | Règle portée | Fichier:ligne (au 2026-10-26) |
|---|---|---|---|
| 1 | Sortie manuelle | restant du lot suffisant (T5), garde d'inventaire, valorisation au PMP courant | `GestionDesSorties.java:198-201` |
| 2 | Sortie pour séances | boucle **FEFO dupliquée deux fois** dans la même classe (T7), restant ≥ 0 (T5) | `GestionDesSortiesPourSeances.java:115-134` et `:222-235` |
| 3 | Réception (correction) | restant = nouvelle quantité − consommé ≥ 0 (T5, T15) | `GestionDesReceptions.java:192-201` |

Soit **3 cas d'usage, 4 sites de code**, dont une seule règle complexe dupliquée (FEFO : tri par péremption croissante,
lots sans péremption en dernier, manque). T6 et T8 n'ont pas de test de propriété dédié.

## Critère de reprise (extraire `StockArticle`, ou au minimum un service de domaine pur `SortieFefo`)

Reprendre dès que l'une de ces conditions est vraie :

1. une **quatrième** règle ou un quatrième cas d'usage touche les lots (par exemple une sortie par lot ou une transformation
   de lots) ;
2. un **bug** de stock (lot négatif, stock ≠ Σ lots) est attribuable à une divergence entre les deux boucles FEFO ;
3. la règle FEFO ou la gestion des lots **change** (par exemple règle de péremption, sortie partielle en cas de manque) :
   changer deux copies est le seuil de reprise ;
4. l'écart « stock article ≠ Σ lots » relevé par `verifications-production.md` § 31.6 est confirmé en production et
   attribué à du code applicatif.

Forme de la reprise : un service de domaine pur `SortieFefo` (entrée : lots, quantité ; sortie : prélèvements ou manque)
avec les tests T5 à T8 en propriété, appelé par les trois cas d'usage, sans changer les tables ni les URLs (ADR 0024).

## Conséquences

- Avantage : pas de risque sur le code monétaire validé par les goldens ; aucun coût immédiat.
- Coût : la dette ci-dessus reste ; le modèle tactique doit être lu avec cet écart (renvoi ajouté en tête de § 9).
- Si l'ADR est refusé : ouvrir une slice de reprise (service `SortieFefo` d'abord), avant la modélisation d'un nouveau
  contexte qui réutiliserait les lots.
