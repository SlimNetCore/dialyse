---
slice: pilotage-direction-pd4-export-synthese
status: planned
date: 2026-11-02
order: 4
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066, 0075, backend/contexts/pilotage-direction/GEL.md]
---

# PD4 — Export synthèse (rapport)

> **GEL (2026-11-02, ADR 0075)** : module gelé, statut réel `planned`. La table parallèle `exports_synthese` et le JSON concaténé à la main sont abandonnés.

## Cas d'usage

Rapport de synthèse PDF de la Direction (`/api/v1/direction/report`, `/snapshots/{mois}/report`).

## Entrées legacy

`DirectionReportPdfService`, `SocieteLetterheadPort` ; tests classiques : `DirectionReportIntegrationTest`.

## Filet existant et manques

Aucun golden dédié. À caractériser : contenu du rapport, en-tête de société, masquage.

## Données

Génération à la demande, aucune table, aucune migration. Impression via le modèle de document (§11.1) lors de la reprise.
