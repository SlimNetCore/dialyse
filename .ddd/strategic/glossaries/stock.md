---
status: validated
date: 2026-10-09
context: Stock (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §8]
---
# Glossaire — Stock

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Article | Produit référencé (consommable, médicament) avec unités et conservation | `Article` | `bc-article` | produit | catalogue exposé en OHS (ADR 0006) | proposed |
| Compte d'article | Compte comptable propre à l'article, transmis à la Comptabilité | `Article.compteComptable` | — | compte (seul) | RG-CPT-011 | proposed |
| Lot | Quantité d'un article avec numéro et date d'expiration | `Lot` | `Lot` | — | consommation FEFO | proposed |
| Bon de commande / de réception | Documents d'approvisionnement | `BonCommande`, `BonReception` | idem | bon (seul) | `BROUILLON → VALIDE → RECU / ANNULE` | proposed |
| Sortie de séance | Sortie de lots justifiée par une séance (motif) | `SortieDeSeance` | `BonSortie.seanceId` | consommation | non annulable si séance facturée (RG-SEA-002) | proposed |
| Séance (motif de sortie) | Simple référence `seanceId` justifiant une sortie | `MotifSortie.seance(id)` | `seanceId` | séance (objet) | aucun autre attribut de séance | proposed |
| PMP | Prix moyen pondéré d'un article | `Pmp` | `PmpRecalculationCoordinator` | coût moyen | recalcul à la réception (RG-STK-024) | proposed |
| Inventaire | Comptage physique, gèle le stock pendant le comptage | `Inventaire` | `Inventaire` | — | RG-STK-052 | proposed |
| Clôture d'inventaire | Fige les mouvements antérieurs ; clôture de stock du mois | `Inventaire.cloturer()` / `InventaireCloture` | `CLOTURE` | clôture (seule) | RG-STK-056 ; ADR 0009 | proposed |
| Lot proche de l'expiration | Lot dont l'expiration approche | `LotProcheExpiration` | `ExpirationAlertScheduler` | — | pas de notification aujourd'hui (H10, à valider) | proposed |
