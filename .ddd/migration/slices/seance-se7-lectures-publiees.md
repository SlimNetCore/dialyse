---
slice: seance-se7-lectures-publiees
status: built
date: 2026-10-29
order: 7
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE7 — Lectures publiées et retrait du legacy Seance

Tous les consommateurs (Facturation, Stock, Absences, Dossier médical, Pilotage, Planification) passent par seance.v1.
Retrait de bc-seance. Garde « pas de SQL sur les tables de Séance hors contexte ». Note : contexte le plus couplé, en
dernier.