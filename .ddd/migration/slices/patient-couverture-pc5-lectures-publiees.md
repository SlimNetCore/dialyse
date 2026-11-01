---
slice: patient-couverture-pc5-lectures-publiees
status: built
date: 2026-10-29
order: 5
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC5 — Lectures publiées et retrait du legacy Patient & couverture

Tous les consommateurs (Facturation, Comptabilité, Seance, Planification, Absences, Dossier médical, Pilotage) passent
par patientcouverture.v1 et ports publiés. Retrait de bc-patient/bc-assure/bc-insurance/bc-pec. Garde « pas de SQL sur
les tables Patient & couverture hors contexte ».