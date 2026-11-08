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

## Slice-parapluie : découpage (2026-11-08)

Cette slice reste `planned` : elle n'est caractérisée qu'au travers de ses sous-slices, une seule l'est à ce jour.

| Ordre | Sous-slice | État | Contenu |
|---|---|---|---|
| 7.1 | `seance-se7a-routes-residuelles` | characterized | journal, calendrier, volet médical, séances récentes |
| 7.2 | `seance-se7b-tableau-de-bord-mensuel` | planned | dashboard, détails, exports |
| 7.3 | `seance-se7c-lectures-facturation-stock` | planned | 7 fichiers dont 5 UPDATE sur `seances` (argent) |
| 7.4 | `seance-se7d-lectures-absences-planification-dossier` | planned | 5 fichiers |
| 7.5 | `seance-se7e-lectures-pilotage-et-rapports` | planned | 8 fichiers dont migration |
| 7.6 | `seance-se7f-retrait-bc-seance-et-garde` | planned | retrait bc-seance, `TableDesSeancesTest` |