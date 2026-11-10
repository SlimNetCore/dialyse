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

## Lot L6 (2026-11-10, ADR 0119)

`/report?from=hier` répond 400 (au lieu de 500, KB-DIR-02) : golden `en_tete_et_bornes_nouveau_code` (ancien : `pd4/_ecarts-legacy/rapport/en_tete_et_bornes_avant_l6.legacy.json`). Tranches d'âge du rapport au dernier jour de la période (KB-DIR-08) : aucun golden de texte ne change (les âges des fixtures ne changent pas de tranche entre la fin de période et le jour de l'appel). En-tête PDF « 18- 29 ans » non touché (cosmétique, à regrouper avec une prochaine correction PDF). RG-DIR-112, 113.

## Lot L1 (2026-11-09, ADR 0112)
KB-DIR-01 CORRIGÉ : le rapport imprime « &lt; seuil » pour la synthèse, « Total société » et le total de chaque caisse dès qu'un centre est masqué, y compris pour le rapport d'un instantané ancien (`ConstituerLeRapportDeSynthese` applique la règle à sa source). Golden `rapport_en_direct` : ancien archivé dans `pd4/_ecarts-legacy/rapport/rapport_en_direct_avant_l1.legacy.json`, nouveau `rapport_en_direct_nouveau_code` ; ajouté `rapport_ancien_instantane_nouveau_code`.

## Reste à faire
- Instantanés mensuels (PD2) et alertes temps réel (PD3) : `DirectionSnapshotService`, `DirectionAlertHistoryService` restent en plateforme (garde nominative `TableDuPilotageTest`).
- Question ouverte : KB-DIR-01 (les instantanés déjà figés portent les totaux bruts : détection §69.1 de `verifications-production.md`).
