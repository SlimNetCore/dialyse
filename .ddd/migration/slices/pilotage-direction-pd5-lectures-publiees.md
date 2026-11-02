---
slice: pilotage-direction-pd5-lectures-publiees
status: planned
date: 2026-11-02
order: 5
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066, 0075, backend/contexts/pilotage-direction/GEL.md]
---

# PD5 — Lectures publiées et retrait du legacy Pilotage

> **GEL (2026-11-02, ADR 0075)** : module gelé, statut réel `planned`. Aucun retrait n'a eu lieu : `platform/application/direction` (19 fichiers) reste intact et servi.

Tous les consommateurs passent par un langage publié ; retrait de `platform/application/direction` après parité ; garde « pas de SQL cross-context hors langage publié » à réécrire sur `direction_snapshot` et `direction_alert_history` avec une liste blanche nominative des lecteurs. Le pilotage ne produit aucune écriture dans les autres contextes (R4).
