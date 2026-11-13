---
status: done (en attente de validation)
date: 2026-11-13
sources: ADR 0167, KB-PLN-51, doc2-realisation.md, doc4c-realisation.md, inventaire-platform-restant-v2.md (ligne 6)
---

# PLN-DOC - réalisation : les impressions de la Planification passent par le port publié de Documents

## État d'avancement
| Étape | État |
|---|---|
| Filet existant relu (présence : formats, texte, désactivé, non-provisionnement, rôles ; calendrier : PDF, refus, rôles) | fait, vert avant et après |
| Filet complété : `impression_selon_le_modele` (calendrier : format EXCEL/HTML/CSV du modèle, modèle désactivé 422) ; approuvé seul depuis `.received` | fait |
| `documents-api` : `RenduDeDocument.formatDImpression` + `rendreSansProvisionner` (défaut « non fourni ») ; `RenduRefuse` déplacé dans documents-api (même paquet) | fait |
| Documents : `MoteurDeRendu`/`MoteurDeRenduJasper`/`RendreUnDocument`/`RenduDeDocumentPublie` (SQL de lecture des modèles porté par le moteur) | fait |
| Planification : `infrastructure/acl/documents/{PresenceImprimeeParDocuments,CalendrierDeLaPropositionImprimeParDocuments}` + `ImpressionsDeLaPlanificationConfiguration` ; test unitaire `ImpressionsParDocumentsTest` (6) | fait |
| Suppression plateforme : `ImpressionDeLaPresenceLegacy`, `ImpressionDuCalendrierDeLaPropositionLegacy` ; retirées de `.ddd/safety-net/covered.json` | fait |
| `PapierEnTeteDuCentreParLaPlateforme` | conservé (ACL du Dossier médical, pas de la Planification) : ADR 0167 |
| ADR 0167, RG-INF-061, RG-PLN (impression), glossaires planification et documents | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées + build complet | voir la réponse finale |

## Parité
Tous les goldens existants de la présence et du calendrier sont verts sans modification (texte du PDF, HTML, xlsx, statuts, en-têtes). Aucun `*_nouveau_code.approved.json` ajouté, aucune archive `_ecarts-legacy` : aucun comportement n'a changé. Le golden ajouté `impression_selon_le_modele` a été écrit sur le nouveau code ; son contenu est celui qu'impose le contrôleur et le moteur inchangés (contrôle par lecture, non rejoué sur l'ancien code).

## KB-PLN-51
L'impression de la présence ne provisionnait déjà plus (corrigé 2026-10-27). Reproduit à l'identique par le port en « lecture sans écriture » ; rien de nouveau à confirmer. Le calendrier de la proposition provisionne toujours le modèle `PLANNING_OPTIMISE` à la première impression (comportement du moteur par type, constaté par le nouveau golden).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` non touché ; rien de commité ; aucun butoir reporté, aucune nouvelle date.
- Échecs connus non touchés : `InventairePl7...feuille_de_comptage` (date), `LancementEtSuiviDesOptimisationsCharacterizationIntegrationTest` (course temporelle, parfois).
- Documentation fonctionnelle (maquette) non régénérée ici.
