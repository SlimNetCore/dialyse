---
slice: organisation-o4-lectures-publiees
status: built
date: 2026-10-29
order: 4
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O4 — Lectures publiées et retrait du legacy Organisation

Tous les consommateurs (Planification, Stock, Seance, Facturation, GMAO, Pilotage) passent par organisation.v1. Retrait
de bc-referential pour l'organisation. Garde « pas de SQL sur les tables d'Organisation hors contexte » (modèle
TableDuStockTest).