---
slice: pilotage-direction-pd5-lectures-publiees
status: built
date: 2026-10-29
order: 5
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD5 â€” Lectures publiÃ©es et retrait du legacy Pilotage

Tous les consommateurs passent par pilotagedirection.v1. Retrait de `platform/application/direction`. Garde Â« pas de
SQL
cross-context hors langage publiÃ© Â». Le pilotage ne produit aucune Ã©criture dans les autres contextes (R4).