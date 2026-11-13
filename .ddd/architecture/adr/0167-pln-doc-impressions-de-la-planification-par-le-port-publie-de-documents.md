---
status: accepted (placement, port « lecture sans écriture », aucun changement de comportement)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: planification, documents, documents-api, platform
related: ADR 0024, 0053, 0142, 0162 à 0166 ; `.ddd/safety-net/known-bugs.md` KB-PLN-51 ; `.ddd/migration/reviews/pln-doc-realisation.md`
---

# 0167 - PLN-DOC : les impressions de la Planification passent par `RenduDeDocument` ; plus aucun SQL `modele_document` côté Planification

Refactoring sur place (ADR 0024) : mêmes URL (`GET /api/v1/infirmiers/presence/imprimer`, `GET /api/v1/planning/optimisations/{id}/impression`), mêmes rôles, mêmes statuts, mêmes corps de refus, mêmes documents (texte extrait identique). Le filet existant (formats, texte, modèle désactivé, non-provisionnement, rôles, centre) est inchangé et vert ; un scénario est ajouté (calendrier : format du modèle, modèle désactivé).

## Placement
| Avant (plateforme) | Après |
|---|---|
| `ImpressionDeLaPresenceLegacy` (110 l.) : deux requêtes SQL sur `modele_document`, appel direct du moteur et du papier en-tête | supprimé. `contexts/planification/infrastructure/acl/documents/PresenceImprimeeParDocuments` : paramètres du planning (sept dates, libellé de semaine) + deux appels du port publié |
| `ImpressionDuCalendrierDeLaPropositionLegacy` (49 l.) via `MoteurDeRendu` | supprimé. `CalendrierDeLaPropositionImprimeParDocuments` via `RenduDeDocument.rendre` ; type MIME rendu traduit en format du contexte (EXCEL, HTML, sinon PDF : le contrôleur servait déjà tout format inconnu en PDF) |
| beans `@Component` de la plateforme | `configuration/ImpressionsDeLaPlanificationConfiguration` (aucun cycle Maven : la Planification dépendait déjà de `contexts-documents-api`) |
| `RenduRefuse` dans `contexts-documents` | déplacé dans `contexts-documents-api` (même paquet `documents.publishedlanguage`, aucun import à changer) pour que la Planification traduise les refus du rendu |

## Option « lecture sans écriture » (KB-PLN-51)
`RenduDeDocument` (documents.v1) gagne deux méthodes, à implémentation par défaut « non fournie » :
- `formatDImpression(centre, type)` : format du modèle actif du centre ; `PDF` sans modèle ; modèle présent mais désactivé (par type ou par code) = `RenduRefuse DOCUMENT_MODEL_INACTIVE` ;
- `rendreSansProvisionner(centre, type, données, format)` : comme `rendre` mais sans créer le modèle du centre : sans modèle, gabarit livré en mémoire ; identité du centre posée par Documents.
Le moteur (`MoteurDeRenduJasper`) possède désormais le SQL de lecture des modèles pour cette voie ; la Planification n'accède plus à `modele_document`. Les deux méthodes sont en deux temps parce que la Planification refuse (400) un format de modèle qu'elle ne sert pas AVANT de générer.

## KB-PLN-51 : effet constaté et décision
Constaté dans le filet (`provisionnement_du_modele_nouveau_code.approved.json`) : l'impression de la présence ne crée déjà aucun modèle ni ne touche aux tables de la présence (corrigé le 27/10/2026, décision de l'utilisateur). Le rendu par le port ne change rien : rien à corriger, rien à reproduire. Aucun `*_nouveau_code`, aucune archive `_ecarts-legacy`, aucun point « à confirmer » nouveau. La **calendrier** de la proposition continue, lui, de provisionner le modèle du type `PLANNING_OPTIMISE` à la première impression (comportement du moteur par type, filet `impression_selon_le_modele`) ; le rendre en lecture sans écriture n'a pas été demandé.

## `PapierEnTeteDuCentreParLaPlateforme` : jugé et conservé
C'est l'ACL du **Dossier médical** (et non de la Planification) : le port `dossiermedical.publishedlanguage.PapierEnTeteDuCentre` est fourni par la plateforme vers `documents.publishedlanguage.PapierEnTeteDuCentre`, qui est dans le module `contexts-documents` (pas dans `documents-api`). Le Dossier médical ne peut pas dépendre de ce module sans cycle (Documents lit Organisation, qui dépend de GMAO). Hors périmètre de PLN-DOC ; il disparaît quand ce port sera déplacé dans `documents-api` (slice Dossier médical, pas de date reportée ici).
