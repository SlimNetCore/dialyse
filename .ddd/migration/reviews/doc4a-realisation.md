---
status: done (en attente de validation)
date: 2026-11-13
sources: ADR 0164, KB-DOC4A-1 à 8, doc3-realisation.md, inventaire-platform-restant-v2.md (ligne 3)
---

# DOC4a - réalisation : gestion des modèles de document vers `contexts/documents`

## État d'avancement
| Étape | État |
|---|---|
| Filet (10 scénarios MockMvc/H2, approuvés un par un depuis `.received`, jamais `-Dcharacterization.approve=true`) écrit et vert sur l'ancien code | fait |
| Domaine (`CheminDeGabarit`, `DefinitionDeModele`, `RechercheDeModeles`, `ModeleDuCatalogue`, `ModeleRefuse`) | fait |
| Application (`GererLesModeles`, port `ModelesDeDocument` étendu en écriture, `ModeleEnDoublon`) | fait |
| Adapters : `ModelesDeDocumentJdbc` (même contrat que le double en mémoire), `ModelesControleur`, `ErreursDesModelesAdvice` | fait |
| Suppression côté plateforme : SQL, regex, `ModeleDocumentDto`, `DocumentSearchRequest`, port publié `VersionsDeModele` | fait |
| Audit dédié (`AuditActionResolver`) ; 406 (`DatesIllisiblesDeLaDirectionAdvice`) | fait |
| KB-DOC4A-1 à 8, ADR 0164, référentiel RG-DOC-010, annexe, glossaire | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées + build complet | voir la réponse finale |

## Parité
2/10 scénarios identiques à l'existant (profils et session, impression après suppression ou désactivation). 8/10 avec écarts voulus : `*_nouveau_code.approved.json`, anciens dans `doc4a/_ecarts-legacy/**/*_avant_doc4a.legacy.json`. La casse des clés de colonnes (capitales sous H2, minuscules en production) est normalisée par le filet : aucun écart de parité en production sur la forme de la liste.

## Écarts (corrigés) et reports
- 500 vers 400/415/405/406 (formes illisibles), 403 avant 400, 500 `DATA_ACCESS_ERROR` vers 409 (doublon) et 400 (obligatoire, trop long), actions d'audit dédiées.
- `seamAllowed` = `[]` ; `DocumentRestController` reste (`/types`, règles des types sensibles : DOC4b, DOC4c) et reste dans `covered.json` ; aucun autre fichier de `covered.json` concerné.
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : modifier ou supprimer un modèle inexistant ou étranger = 200 ; code et libellé blancs ; type et format libres ; filtre de type sensible à la casse.
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
- Tests sensibles à la date déjà rouges non touchés.
