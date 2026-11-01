---
slice: pilotage-direction-pd4-export-synthese
status: planned
date: 2026-10-29
order: 4
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD4 — Export synthèse (reporting)

Entité `ExportSynthese`. Impression via `ModeleDocumentPrinter` (modèle de document, §11.1).

## Cas d'usage

`GenererExportSynthese`, `TelechargerExport`.

## Entrées legacy

`Direction*QueryService` + reporting.

## Données

Export on-demand ; pas de table dédiée (ou table tracking). Modèle de document à déclarer dans `ModeleDocumentCatalog`.