---
slice: pilotage-direction-pd4-export-synthese
status: built
date: 2026-11-09
order: 4
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0075, ADR 0102, .ddd/safety-net/pilotage-direction.md]
---

# PD4 — Export synthèse (rapport imprimable)

Refactoring SUR PLACE (ADR 0102) : mêmes URL `/api/v1/direction/report` et `/api/v1/direction/snapshots/{mois}/report`, mêmes rôles et codes ; la table parallèle `exports_synthese` est abandonnée. Aucun flag ni bascule ; rollback = revert du lot.

## Fait (lot A, 2026-11-09)
- Langage publié `RapportDeSyntheseDeLaDirection` : `enDirect(société, from, to)` et `constituer(SourceDuRapport)` ; modèle `Rapport` (synthèse, alertes, finances, évolution, qualité des soins, stock, répartitions).
- Application `ConstituerLeRapportDeSynthese` ; domaine `LibelleDAlerte`, `EvolutionMensuelle` (sommes en double comme le legacy), `MatriceDesCaisses` (zéros et masques conservés) ; publication `RapportDeSynthesePublie`.
- Plateforme : `DirectionReportPdfService` réduit au RENDU (HTML vers PDF openhtmltopdf 1.0.10, papier à en-tête, pied de page, `DocumentIdentityProvider`) ; traducteurs `RapportDUnInstantane` (payload figé JSON vers `SourceDuRapport`, lecture tolérante) et `IndicateursDuRapportParLaPlateforme` (`infrastructure/acl/pilotagedirection/`). `DirectionSnapshotRestController` inchangé.

## Parité
Goldens PD4 (3 tests, texte du PDF), PD2 (instantanés) : inchangés. Aucun écart assumé. Texte du rapport mensuel identique entre le rendu en direct et le rendu d'un instantané figé (représentation des décimaux conservée).

## Reste à faire
- Instantanés mensuels (PD2) et alertes temps réel (PD3) : `DirectionSnapshotService`, `DirectionAlertHistoryService` restent en plateforme (garde nominative `TableDuPilotageTest`).
- Question ouverte : KB-DIR-01 (les instantanés déjà figés portent les totaux bruts : détection §69.1 de `verifications-production.md`).
