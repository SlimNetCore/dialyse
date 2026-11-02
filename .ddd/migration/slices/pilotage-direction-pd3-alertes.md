---
slice: pilotage-direction-pd3-alertes
status: planned
date: 2026-11-02
order: 3
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066, 0075, backend/contexts/pilotage-direction/GEL.md]
---

# PD3 — Alertes direction

> **GEL (2026-11-02, ADR 0075)** : module gelé, statut réel `planned`. La table parallèle `alertes_direction` est abandonnée. Reprise sur la table legacy **`direction_alert_history`** (V1 : `societe_id`, `center_id`, `centre_nom`, `code`, `severity`, `valeur`, `first_seen_at`, `resolved_at`).

## Cas d'usage

Génération et résolution des alertes (ouverture / résolution), consultation de l'historique (`/api/v1/direction/alerts/history`).

## Entrées legacy

`DirectionAlertPolicy`, `DirectionAlertHistoryService` ; `DirectionDashboardRestController` (`/alerts/history`). Tests classiques : `DirectionAlertPolicyTest`, `DirectionAlertHistoryServiceTest`.

## Questions ouvertes

- **Règle R2** : le module gelé imposait « une alerte par centre et par jour, tous types confondus » (`uq_alerte_centre_jour`). Le legacy identifie une alerte ouverte par centre et code. **À trancher à la reprise**, sur la base du comportement caractérisé (centre seul, centre et jour, ou centre, code et jour) ; non tranchée au gel.
- Types d'alerte du glossaire (`proposed`) à confronter aux codes réels de `DirectionAlertPolicy`.

## Données

Aucune migration. Unicité traduite en code métier (jamais en 500). Date d'alerte dans le fuseau du centre.
