---
slice: patient-couverture-pc5-lectures-publiees
status: planned
date: 2026-10-29
order: 5
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC5 — Lectures publiées et retrait du legacy Patient & couverture

> Statut vrai au 2026-11-02 : `planned` (module gelé, `GEL.md`, ADR 0073). Aucun port publié, aucun legacy retiré. Le langage publié est défini à la reprise de cette slice (rien avant). Les 25 lectures SQL hors contexte (voir PC1) en sont le périmètre.

Tous les consommateurs (Facturation, Comptabilité, Seance, Planification, Absences, Dossier médical, Pilotage) passent
par patientcouverture.v1 et ports publiés. Retrait de bc-patient/bc-assure/bc-insurance/bc-pec. Garde « pas de SQL sur
les tables Patient & couverture hors contexte ».