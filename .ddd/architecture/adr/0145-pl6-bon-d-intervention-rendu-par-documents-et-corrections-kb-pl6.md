---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: gmao, documents, platform
related: ADR 0024, 0140, 0142, 0144 ; `.ddd/migration/reviews/pl6-filet.md`, `pl6-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-PL6-1 à 6
---

# 0145 - PL6 : le bon d'intervention est rendu par Documents depuis une ACL de la GMAO ; corrections KB-PL6-1, 2, 3, 5, 6

Refactoring sur place (ADR 0024) : mêmes tables, même URL `GET /api/v1/gmao/interventions/{id}/bon`, mêmes rôles (`ADMIN`), mêmes en-têtes. Pas de flag ; retour arrière = revert du commit.

## Placement
| Élément | Où |
|---|---|
| `BonInterventionReportService` (plateforme) | supprimé ; retour interdit par ArchUnit (`FrontieresDesContextesTest`) |
| ACL `BonRenduParDocuments` (implémente `ImpressionDuBon`, appelle `RenduDeDocument`, type `BON_INTERVENTION`) | `contexts/gmao/infrastructure/acl/documents` |
| Gabarit `reports/bon_intervention.jrxml`, moteur Jasper, catalogue | restent en plateforme jusqu'à DOC2 |

## Décision de build : module `contexts-documents-api`
`contexts-documents` dépend de `contexts-organisation` (papier en-tête), qui dépend de `contexts-gmao` : GMAO ne peut pas dépendre de `contexts-documents` (cycle Maven). `RenduDeDocument` et `DocumentRendu` (pur Java, même paquet `...documents.publishedlanguage`) sont déplacés dans un module `contexts-documents-api` dont dépendent `contexts-documents` et `contexts-gmao`. Aucun changement de paquet ni de contrat.

## Écarts décidés (2026-11-12)
| KB | Décision |
|---|---|
| 1 | Jeton sans centre : 403 `ACCESS_DENIED` ; centre illisible ou vide : 400 « Requête illisible » (`SessionCourante.centrePourImpression`, limité à l'impression : les autres routes GMAO gardent leur comportement verrouillé) |
| 2 | Identifiant d'intervention mal formé : 400 (analysé dans le contrôleur de l'impression). `.../bon.pdf` et `.../bon/` : 500 reproduit (route inconnue, transverse) |
| 3 | Champs de montant élargis (récapitulatif et lignes). Négatifs : déjà refusés à la saisie (`LigneDeCout`, 400) ; lignes hors API imprimées telles quelles |
| 5 | Désignation d'équipement sur plusieurs lignes (champ extensible, blocs suivants `positionType="Float"`) |
| 6 | Action de lecture `BON_INTERVENTION_IMPRESSION` (aussi pour les réponses 400/403) ; clôture rejouée (même instant, même auteur) imprimée une fois ; motif blanc non imprimé |
| 4 | REPRODUIT : caractères hors police perdus, dette |

## Questions ouvertes
- Chronologie : seules les clôtures strictement identiques sont dédoublonnées ; les clôtures successives d'heures différentes restent (historique réel d'une réouverture). À confirmer avec les métiers.
- Le gabarit modifié concerne aussi les modèles déjà téléversés par un centre : ils gardent l'ancienne mise en page (champs rognés).
- La ligne « Heures exprimées dans le fuseau » tient désormais en page 1 sur le cas de test (effet de bord de la suppression de lignes).
