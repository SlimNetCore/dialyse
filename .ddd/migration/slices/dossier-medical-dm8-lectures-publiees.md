---
slice: dossier-medical-dm8-lectures-publiees
status: planned
date: 2026-10-29
order: 8
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

# DM8 — Lectures publiées et retrait du legacy Dossier médical

Tous les consommateurs (Seance, Facturation, Planification, Pilotage, Patient & couverture) passent par
dossiermedical.v1 et ports publiés (`RisquesInfectieux`, `AbordsVasculaires`). Retrait de bc-medical. Garde « pas de SQL
sur les tables du Dossier médical hors contexte ». Données de santé : cloisonnement strict.