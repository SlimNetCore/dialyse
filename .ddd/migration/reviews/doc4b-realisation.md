---
status: done (en attente de validation)
date: 2026-11-13
sources: ADR 0165, KB-DOC4B-1 à 3, doc4a-realisation.md, inventaire-platform-restant-v2.md (ligne 4)
---

# DOC4b - réalisation : la liste des types de document vers `contexts/documents`, sur des types déclarés

## État d'avancement
| Étape | État |
|---|---|
| Filet (3 scénarios MockMvc/H2, approuvés un par un depuis `.received`, jamais `-Dcharacterization.approve=true`) écrit et vert sur l'ancien code | fait |
| Langage publié : `TypeDeDocumentDeclare` déplacé dans `contexts/documents-api` ; nouveau port `DeclarationDesTypesDeDocument` | fait |
| Domaine `TypeDisponible` ; application `ListerLesTypesDeDocument` (agrège, refuse un code en double, réserve `CUSTOM`) | fait |
| Adapters : `TypesControleur` (405), `TypesLivresDeDocuments` (déclaration des types livrés), 406 par le gestionnaire commun, audit dédié | fait |
| Suppression côté plateforme : `GET /types` et l'import du catalogue dans `DocumentRestController` | fait |
| KB-DOC4B-1 à 3, ADR 0165, référentiel RG-DOC-011, annexe, glossaire | fait |
| `check-arch.ts` | 0 violation |
| Trois passes ciblées + build complet | voir la réponse finale |

## Parité
1/3 scénarios identique à l'existant (liste pour chaque profil et sans session). 2/3 avec écarts voulus : `*_nouveau_code.approved.json`, anciens dans `doc4b/_ecarts-legacy/**/*_avant_doc4b.legacy.json`. Le filet trie les clés JSON et normalise l'ordre `code`/`label` du XML (l'ordre venait d'une `Map.of`, aléatoire d'une exécution à l'autre).

## Écarts (corrigés) et reports
- 500 vers 405 (`Allow`) pour POST, PUT, PATCH, DELETE et 500 vers 406 pour un `Accept` indisponible ; action d'audit `TYPE_DE_DOCUMENT_ECRITURE_REFUSEE`.
- Déclaration par les contextes propriétaires : le port est publié et sans cycle Maven possible (facturation, GMAO, stock, pilotage-direction dépendent déjà de documents-api), mais le catalogue des types livrés **reste dans Documents derrière le port** (moteur, provisionnement et ACL le lisent encore ; contrat de paramètres décidé avec les règles en DOC4c). Noté dans l'ADR 0165.
- `seamAllowed` = `[]` ; `DocumentRestController` reste (règles des types sensibles : DOC4c) et reste dans `covered.json` ; aucune entrée retirée (aucun fichier legacy supprimé).
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : liste identique pour tout profil et centre ; `CUSTOM` en dernier ; routes voisines en 500 (comportement général de la plateforme).
- Documentation fonctionnelle (maquette) non régénérée ici : seul le référentiel des règles de gestion est à jour.
- Test sensible à la date (InventairePl7...feuille_de_comptage) déjà rouge, non touché.
