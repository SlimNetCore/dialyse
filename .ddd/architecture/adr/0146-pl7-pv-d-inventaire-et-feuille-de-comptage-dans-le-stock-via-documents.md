---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: stock, documents, platform
related: ADR 0024, 0140, 0144, 0145 ; `.ddd/migration/reviews/pl7-filet.md`, `pl7-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-PL7-1 à 5
---

# 0146 - PL7 : le PV d'inventaire est rendu par Documents depuis une ACL du Stock, la feuille de comptage vit dans le Stock ; corrections KB-PL7-1, 2, 4, 5

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL (`GET /api/v1/stock/inventaires/{id}/rapport`, `GET|POST .../{id}/feuille-comptage`), mêmes rôles. Pas de flag ; retour arrière = revert du commit.

## Placement
| Élément | Où |
|---|---|
| `InventaireReportService`, `FeuilleComptageExcel` (plateforme) | supprimés ; retour interdit par ArchUnit (`FrontieresDesContextesTest`) |
| ACL `ProcesVerbalRenduParDocuments` (implémente `ProcesVerbalDInventaire`, appelle `RenduDeDocument`, type `INVENTAIRE_STOCK`) | `contexts/stock/infrastructure/acl/documents` |
| `FeuilleDeComptageExcel` (POI, implémente `FeuilleDeComptage`) | `contexts/stock/infrastructure/classeur` ; dépendance `poi-ooxml` 5.2.5 (comme Facturation) |
| `reports/inventaire_stock.jrxml`, moteur Jasper, catalogue | restent en plateforme jusqu'à DOC2 |
| Dépendance Stock → `contexts-documents-api` (module créé par PL6) | pas de dépendance vers `contexts-documents` |

## Écarts décidés (2026-11-12)
| KB | Décision |
|---|---|
| 1 | Paramètres illisibles (id, `centerId`, `aveugle`, import sans partie `file`) : 400 propre ; autre verbe sur `/rapport` (POST, PUT, PATCH, DELETE) ou la feuille (PUT, PATCH, DELETE) : 405. Lecture à la main dans le contrôleur : les autres routes d'inventaire gardent leur comportement verrouillé. Barre finale et `.pdf` : 500 reproduit (route inconnue, transverse) |
| 2 | PV sans ligne : « Aucune ligne » ; pied d'article imprimé seulement s'il y a une ligne (gabarit) |
| 4 | Titre « Inventaire INV-… du … » exigé (400), contrôlé après la recherche des colonnes (une feuille sans colonnes reste 422) ; motif sur un lot compté égal au théorique : 400 (toute la feuille est refusée, rien n'est enregistré) |
| 5 | Journalisation : actions de lecture `INVENTAIRE_PV_IMPRESSION`, `INVENTAIRE_FEUILLE_TELECHARGEMENT` |
| 3, 5 (partie à confirmer) | REPRODUIT |

## Questions ouvertes (métiers, avant le 2026-12-15)
- Lot « sans lot » après clôture (affiché avec la référence d'inventaire) ; lots négatifs ou arrondis à 0 absents de l'inventaire ; quantité théorique de la feuille arrondie à 2 décimales ; caractères hors police (avec DOC2).
- Le refus d'un motif sans écart rejette toute la feuille ; un refus ligne à ligne (anomalie) serait plus tolérant : à confirmer.
- Modèles de PV déjà personnalisés par un centre : ancienne mise en page (pas de pied fantôme corrigé).
