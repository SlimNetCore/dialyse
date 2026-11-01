---
slice: pilotage-direction-pd5-lectures-publiees
status: planned
date: 2026-10-29
order: 5
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD5 — Lectures publiées et retrait du legacy Pilotage

Tous les consommateurs passent par pilotagedirection.v1. Retrait de `platform/application/direction`. Garde « pas de SQL
cross-context hors langage publié ». Le pilotage ne produit aucune écriture dans les autres contextes (R4).