---
slice: pilotage-direction-pd2-instantanes-mensuels
status: planned
date: 2026-11-02
order: 2
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0009, 0066, 0075, backend/contexts/pilotage-direction/GEL.md]
---

# PD2 — Instantanés mensuels figés

> **GEL (2026-11-02, ADR 0075)** : module gelé, statut réel `planned`. Le module avait créé une table parallèle `instantanes_mensuels` : abandonnée. Reprise sur la table legacy **`direction_snapshot`** (V1 : `societe_id`, `mois`, `payload`, `generated_at`, unicité `uq_direction_snapshot`).

## Cas d'usage

`CapturerInstantaneMensuel`, `ConsulterInstantane` (immuable une fois créé ; mois clôturé non recalculé).

## Entrées legacy

`DirectionSnapshotService` ; `DirectionSnapshotRestController` (`/api/v1/direction/snapshots/{mois}`, `/{mois}/report`). Tests classiques : `DirectionSnapshotIntegrationTest`.

## Filet existant et manques

Aucun golden dédié. À caractériser : lecture, création, immuabilité, liste des mois, rapport.

## Données

Aucune migration. Fuseau du centre (`centers.fuseau_horaire`) pour la date de capture.
