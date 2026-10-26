---
status: accepted
date: 2026-10-26
deciders: utilisateur (décision du 2026-10-25, tracée ici après la vérification du Stock)
related: ADR 0045 (décisions 1 et 2), ADR 0024 ; `.ddd/tactical/stock.md` (question ouverte 1, clôturée) ; `.ddd/migration/slices/stock-s6b-sortie-pour-seances.md` ; commit `eaa76b31` ; `.ddd/verify/verify-stock.md` (constat M2)
---

# ADR 0047 — Stock : une pièce est impérative le jour de la séance ; retrait du mode historique confirmé

## Contexte

L'ADR 0045 introduit la **sortie pour séances** (pièce préparée la veille) et laissait à trancher un « paramètre de bascule
par centre » : tant qu'il était désactivé, la Séance gardait la sortie immédiate FEFO (mode historique). Le 2026-10-24, le
mode historique (`ConsommationsDeSeanceLegacy`), l'aiguillage par centre, le paramètre `stock_parametres.sortie_pour_seances_activee`
(routes `/api/v1/stock/parametres`, port `ParametresDuStockDuCentre`) et les méthodes de séance de `BonSortieService` ont été
supprimés (commit `eaa76b31`) sans décision écrite. La vérification du Stock (`verify-stock.md`, M2) l'a relevé.

## Décision (utilisateur, 2026-10-25)

1. **Une pièce (bon de préparation, « sortie pour séances ») est impérative le jour de la séance** : le stock ne baisse qu'à
   l'enregistrement de la pièce, jamais à la séance.
2. **Le retrait du mode historique est confirmé** : plus de sortie immédiate FEFO à la séance, plus de paramètre ni
   d'aiguillage par centre. La question ouverte 1 du modèle tactique (« bascule ») est close ; il n'y a ni période de
   cohabitation ni retour arrière par paramètre.
3. Sans pièce couvrant la séance (salle ou demi-journée, puis « journée »), la consommation est **acceptée avec l'alerte
   `AUCUNE_PIECE`** (ADR 0045, décision 2) : aucune sortie n'est faite et le stock ne baisse pas.
4. La table `stock_parametres` (V45) est conservée orpheline (migration additive, aucun `DROP` sans accord écrit) ; son retrait
   éventuel est une tâche de nettoyage distincte (`verify-stock.md`, n2).

## Conséquences

- **Prérequis opérationnel à la mise en production** : dans chaque centre, une pièce « sortie pour séances » doit être
  préparée **avant la première séance** suivant le déploiement ; sinon les consommations deviennent des alertes
  `AUCUNE_PIECE` et le stock ne baisse plus. Contrôle de lecture seule : `verifications-production.md` § 31 (points 31.5 et 31.6).
- Les séances du jour J déjà validées sous l'ancien mode gardent leurs sorties historiques (`bons_sortie.seance_id`),
  lisibles ; elles ne sont pas reprises.
- Formation des infirmiers et du gestionnaire de stock avant le déploiement (la séance ne fait plus baisser le stock).
- Le retour arrière n'est plus un paramètre : il passe par le déploiement de la version précédente (les pièces déjà
  enregistrées devraient alors être rapprochées).
- Documents alignés par cet ADR : ADR 0045 (points restants), `tactical/stock.md` (question 1), `roadmap.md`, fiches S6b et S6.
