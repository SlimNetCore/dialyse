---
status: done (en attente de validation)
date: 2026-11-13
sources: ADR 0163, KB-DOC3-1 à 6, doc2-realisation.md
---

# DOC3 - réalisation : versions de gabarit par centre vers `contexts/documents`

## État d'avancement
| Étape | État |
|---|---|
| Filet (15 scénarios MockMvc/H2, approuvés un par un) | fait |
| `modele_document_version` dans `schema.sql` | fait |
| Domaine, application, JDBC, contrôleur, advice, port publié | fait (module : 164 tests verts, ArchUnit 9 verts) |
| Suppression côté plateforme + `covered.json` (`seamAllowed` = `[]`) | fait |
| Audit dédié (`AuditActionResolver`) | fait |
| Gardes plateforme (`FrontieresDesContextesTest`, cache) | fait |
| KB, ADR 0163, référentiel RG-DOC-009, glossaire | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées + build complet | voir résultats dans la réponse finale |

## Parité
8/15 scénarios identiques au legacy (cycle complet, borne 512 Ko incluse, refus de contenu, isolation par centre, profils, réinitialisation, commentaire, suppression avec le modèle). 7/15 écarts voulus (500 vers 400/405/415, 403 avant 400, actions d'audit) : `*_nouveau_code.approved.json`, anciens dans `doc3/_ecarts-legacy/**/*_avant_doc3.legacy.json`.

## Écarts et reports
- `ModeleDocumentDto` reste à la plateforme (corps de `POST/PUT /modeles`, DOC4a).
- `CacheConfig` garde le cache `reporting.customTemplates`.
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : 204 sur retour au modèle d'origine répété, `Accept` XML servi, 500 sur sous-chemin non routé et sur collision de dépôts simultanés, taille multipart globale non testable sous MockMvc.
- `ModeleDocumentTemplateIntegrationTest` (plateforme) conservé : mêmes URL, toujours vert.
- Tests sensibles à la date déjà rouges non touchés.
