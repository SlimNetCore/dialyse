---
status: done (en attente de validation)
date: 2026-11-13
sources: ADR 0166, KB-DOC4C-1 à 3, KB-DOC1-3, KB-MED-18, KB-FAC-52/60, doc4b-realisation.md, inventaire-platform-restant-v2.md (ligne 5)
---

# DOC4c - réalisation : les règles d'impression des types sensibles sont publiées par leurs contextes ; `DocumentRestController` disparaît

## État d'avancement
| Étape | État |
|---|---|
| Filet `documents/doc4c` (5 scénarios MockMvc/H2 : matrice rôle x type x voie, garde de centre et ordre des gardes, totaux du serveur, audit), approuvés un par un depuis `.received`, jamais `-Dcharacterization.approve=true`, écrits et verts sur l'ancien code | fait |
| Langage publié : `RegleDImpressionDUnType` (documents-api) ; `TypeDeDocumentDeclare` gagne `description` | fait |
| Documents : port `ReglesDImpression`, `ReglesDesTypesDeclares` (orchestration par code de type), `CatalogueDesTypesLivres` (moteur, provisionnement, `ListerLesTypesDeDocument` sur les mêmes déclarations) | fait |
| Facturation : `RegleDImpressionDeLaSyntheseMensuelle` (profil, centre, totaux) + `SessionCourante.roles()` + déclaration du type | fait |
| Dossier médical : `GarderLImpressionDeLOrdonnance`, `ProfilDeLectureDesOrdonnances` (domaine), `ProfilDeLaSession`/`ProfilLegacy`, `RegleDImpressionDeLOrdonnance` + déclaration du type | fait |
| Déclaration des 12 autres types : Documents (6), Stock, GMAO, Planification (2, constantes publiées pour les ACL), Séance, Pilotage de la direction | fait |
| Suppression : `DocumentRestController`, `ReglesDImpressionDesTypesSensibles`, `ModeleDocumentCatalog` (+ son test, remplacé) ; entrée retirée de `.ddd/safety-net/covered.json` | fait |
| Gardes : `FrontieresDesContextesTest` (contrôleur disparu, aucune règle d'impression à la plateforme), `CatalogueDesTypesLivresIntegrationTest` (14 types, ordre, tout gabarit livré déclaré) | fait |
| KB-DOC4C-1 à 3, ADR 0166, référentiel RG-DOC-012 (+ RG-DOC-007, 011, annexe), glossaire Documents | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées + build complet | voir la réponse finale |

## Parité
5/5 scénarios identiques à l'existant ; aucun `*_nouveau_code.approved.json`, aucune archive `_ecarts-legacy` : aucun comportement n'a changé. Les goldens existants de DOC1, DOC3, DOC4a, DOC4b, DM2 (impression de l'ordonnance), facturation (synthèse par modèle) et planification (présence) restent verts sans modification, dont l'ordre de `GET /types`.

## Décisions
- L'ordre de la liste des types est porté par `@Order` sur chaque bean de déclaration (10 Documents, 20 Facturation, 30 Dossier médical, 40 Stock, 50 GMAO, 60 Planification, 70 Séance, 80 Pilotage) ; le test d'intégration le verrouille.
- Les six documents du patient et de ses listes restent déclarés par Documents (leurs gabarits lisent le patient par la source de données du moteur) ; `parametresAttendus` reste vide pour les 14 types : motifs dans l'ADR 0166. Ce ne sont pas des butoirs : aucune date reportée.
- Les refus restent des `AccessDeniedException` de la plateforme (403 `ACCESS_DENIED`, mêmes messages) ; le Dossier médical convertit son `AccesRefuse` à la frontière publiée.

## Reproduits, à confirmer métiers/DPO avant le 2026-12-15
KB-DOC4C-1 (ordonnance imprimée par son modèle non gardée), KB-DOC4C-2 (SUPERADMIN seul refusé pour l'ordonnance et la synthèse), KB-DOC4C-3 / KB-DOC1-3 (tout authentifié imprime un type sans règle ; refus journalisés comme une impression).

## Notes
- `seamAllowed` = `[]` ; `.ddd/state.json` non touché ; rien de commité.
- Échecs déjà connus, non touchés : `InventairePl7...feuille_de_comptage` (date) et `LancementEtSuiviDesOptimisationsCharacterizationIntegrationTest` (calcul trop rapide), sans lien avec Documents.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
