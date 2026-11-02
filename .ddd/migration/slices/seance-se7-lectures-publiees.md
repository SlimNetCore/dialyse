---
slice: seance-se7-lectures-publiees
status: planned
date: 2026-10-29
order: 7
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE7 — Lectures publiées et retrait du legacy Seance

> Statut vrai au 2026-11-02 : `planned` (`built` était faux : aucun port publié). Module gelé (GEL.md, ADR 0074) ; `TableDesSeancesTest` désactivé (inerte : 18 lecteurs SQL hors contexte non vus). Jalons de dette hérités : port `attenduLe` de Planification, règle de présence recopiée dans `SeancePlanningJdbcAdapter`, `SeanceRestController` (1 315 l.) à découper, colonnes de place de `patients`. Reprise : dernière.

Tous les consommateurs (Facturation, Stock, Absences, Dossier médical, Pilotage, Planification) passent par seance.v1.
Retrait de bc-seance. Garde « pas de SQL sur les tables de Séance hors contexte ». Note : contexte le plus couplé, en
dernier.