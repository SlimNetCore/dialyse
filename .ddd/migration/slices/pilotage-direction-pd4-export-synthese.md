---
slice: pilotage-direction-pd4-export-synthese
status: built
date: 2026-10-29
order: 4
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD4 â€” Export synthÃ¨se (reporting)

EntitÃ© `ExportSynthese`. Impression via `ModeleDocumentPrinter` (modÃ¨le de document, Â§11.1).

## Cas d'usage

`GenererExportSynthese`, `TelechargerExport`.

## EntrÃ©es legacy

`Direction*QueryService` + reporting.

## DonnÃ©es

Export on-demand ; pas de table dÃ©diÃ©e (ou table tracking). ModÃ¨le de document Ã  dÃ©clarer dans
`ModeleDocumentCatalog`.