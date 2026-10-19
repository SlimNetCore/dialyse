---
status: accepted
date: 2026-10-19
deciders: utilisateur (réponses aux questions de clôture des mineurs de la revue 2026-10-19-comptabilite)
---

# ADR 0029 — Mineurs de la revue Comptabilité : domaine déterministe, unicité par source, ordre du rejeu du PMP

## Contexte

La revue `.ddd/reviews/2026-10-19-comptabilite.md` laissait trois mineurs : aléa dans les services de domaine de la
Comptabilité, idempotence R6 tenue seulement par un verrou applicatif, corrections KB-STK du Stock en attente de
décision.

## Décisions

1. **Domaine déterministe (Comptabilité)** : `RedactionDesEcritures.ventes/reglement` et `SoldesDuStock.lignes`
   reçoivent
   leurs identifiants (`Supplier<UUID>`) ; aucun service ni agrégat du domaine ne tire d'identifiant ni ne lit l'heure.
   Règle ArchUnit `le_domaine_ne_tire_ni_identifiant_ni_heure` (module `contexts/comptabilite`).
2. **R6 en base** : index unique `uq_ecriture_center_source (center_id, source_id)` — migration additive V37, créée
   seulement si aucun doublon n'existe (sinon `WARNING`, rien n'est modifié) ; description JPA (H2) alignée. Le numéro
   de
   pièce reste sans contrainte (B1, reproduit). **Doublons historiques éventuels** : extourne après revue comptable,
   jamais
   de suppression ; une migration ultérieure créera alors l'index (`verifications-production.md` § 17).
   Écart au legacy (ADR 0019) : deux scénarios de `NumerotationDesPiecesCharacterizationIntegrationTest` fixaient
   l'absence de contrainte ; ils sont remplacés par leurs variantes `_nouveau_code` (la base refuse la seconde écriture
   d'une même source).
3. **KB-STK-01 validé, avec l'heure réelle de saisie** : le rejeu du PMP ordonne les mouvements par date de pièce, puis
   par heure réelle de saisie (`stock_movements.saisi_le`, migration additive V38), puis entrées avant sorties à défaut.
   Une sortie garde l'heure de création de son bon même quand le bon est modifié (mouvements recréés). Les mouvements
   antérieurs à V38 n'ont pas d'heure de saisie : la règle « entrées avant sorties » s'applique entre eux. Une sortie
   n'est jamais revalorisée à 0 si le rejeu ne donne aucun PMP.
4. **KB-STK-02 validé** : le jeu de démonstration date ses mouvements de leur pièce.

## Conséquences

- Le jour de la mise en production, un mouvement historique (heure de saisie nulle) passe avant un mouvement nouveau de
  la même date de pièce (`nullsFirst`) : ordre total et stable, sans effet au-delà de cette journée de transition.
- Une sortie saisie avant une réception du même jour est désormais valorisée au PMP de son heure de saisie (choix
  métier) ; s'il n'y avait aucun stock valorisé avant elle, elle garde la valeur enregistrée à la sortie.
- Le Stock n'a pas encore de filet de caractérisation : ces règles sont couvertes par `PmpCalculatorTest` et seront à
  reprendre dans le filet de la slice Stock.

