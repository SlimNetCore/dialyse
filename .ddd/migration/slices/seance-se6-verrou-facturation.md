---
slice: seance-se6-verrou-facturation
status: planned
date: 2026-11-08
order: 6
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074, .ddd/safety-net/seance-se6-verrou-facturation.md]
---

# SE6 : modification de date et suppression d'une séance

> Périmètre redéfini le 2026-11-08 (la clé d'état `seance-se6-verrou-facturation` est conservée). L'ancien intitulé « Verrou facturation
> (réception FacturationValidée) » ne correspondait à aucun code Séance : le verrou n'a pas de code propre (ADR 0074), il se réduit au statut
> `FACTUREE` + `facture_id` posés par la slice Facturation (hors périmètre). SE6 reprend les deux routes legacy restantes de la Séance.

## Cas d'usage

- Modifier la date d'une séance : `PUT /api/v1/seances/{id}` (`SeanceRestController.update`, `SeanceDomainService.updateDate`, `SeanceRepositoryAdapter.save`).
- Supprimer une séance : `DELETE /api/v1/seances/{id}?centerId=` (`SeanceSuppressionRestController`, `SuppressionSeanceService`, `Seance.verifierSuppressible`, table `seance_suppression`).

## Entrées legacy

`bc-seance` (`Seance`, `SeanceDomainService`, `SuppressionSeance`, `MotifSuppressionSeance`), `platform` (contrôleurs, `SuppressionSeanceService`, adapters).

## Filet

`backend/platform/src/test/java/.../characterization/seance/se6/` (20 scénarios), `.ddd/safety-net/seance-se6-verrou-facturation.md`, KB-SEA-50 à 56
(dont KB-SEA-50 : suppression d'une séance portant une facture, risque d'intégrité de facturation). KB-SEA-49 : `se5/date/`.

## Décisions à prendre dans l'ADR de la slice (reproduire ou corriger)

Refus de suppression/modification si `facture_id` renseigné (KB-SEA-50, 49) · date modifiable après validation/signature (KB-SEA-54) ·
contournement du verrou des jours passés (KB-SEA-55) · règles de calendrier de la date (KB-SEA-56) · restitution du stock (KB-SEA-51) · codes d'erreur 400 (KB-SEA-52).

## Données

Mêmes tables et URLs (refactoring sur place, ni façade ni routage) ; aucune migration.
