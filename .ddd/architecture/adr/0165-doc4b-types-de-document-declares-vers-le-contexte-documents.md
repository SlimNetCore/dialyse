---
status: accepted (placement, port de déclaration, corrections de forme, audit) ; proposed (points reproduits à confirmer métiers/DPO avant le 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: documents, platform
related: ADR 0024, 0140, 0142, 0162, 0163, 0164 ; `.ddd/safety-net/known-bugs.md` KB-DOC4B-1 à 3 ; `.ddd/migration/reviews/doc4b-realisation.md`
---

# 0165 - DOC4b : la liste des types de document passe au contexte Documents, sur des types déclarés

Refactoring sur place (ADR 0024) : même URL `GET /api/v1/documents/types`, même rôle (toute session), même code de succès (200), mêmes champs (`code`, `label`), mêmes valeurs et même ordre qu'avant.

## Placement
| Avant (plateforme) | Après (`contexts/documents`, `contexts/documents-api`) |
|---|---|
| `DocumentRestController.availableTypes` lisait le catalogue statique `ModeleDocumentCatalog` et ajoutait `CUSTOM` | `interfaces.http.TypesControleur` -> `application.ListerLesTypesDeDocument` (agrège les déclarations, ajoute `CUSTOM`) -> `domain.TypeDisponible` |
| aucun port | port publié `DeclarationDesTypesDeDocument` (documents-api, documents.v1) : `List<TypeDeDocumentDeclare> types()` |
| `TypeDeDocumentDeclare` dans `contexts/documents` | déplacé dans `contexts/documents-api` (même paquet publié, aucun import à changer) : les contextes déjà liés à documents-api (facturation, GMAO, stock, pilotage-direction) peuvent déclarer sans cycle Maven |
| catalogue statique lu en direct | `infrastructure.declaration.TypesLivresDeDocuments` : déclare, par ce port, les types livrés |

`DocumentRestController` ne garde que les règles des types sensibles (DOC4c) ; plus aucune route.

## Décision sur la déclaration par les contextes propriétaires
Le port existe et Documents n'agrège plus que des déclarations (un code déclaré deux fois, ou `CUSTOM` réservé, empêche le démarrage). **Le catalogue des types livrés reste dans Documents, derrière ce port** : ce n'est pas un cycle Maven (facturation, GMAO, stock et pilotage-direction dépendent déjà de documents-api, qui est pur Java) mais une dépendance de contenu : le moteur de rendu, le provisionnement des centres et les ACL de planification lisent encore `ModeleDocumentCatalog` (description, gabarit, constantes), et le contrat de paramètres attendus de chaque type est décidé avec ses règles en DOC4c. Déplacer les 14 entrées dans leurs contextes maintenant dédoublerait la source de vérité sans bénéfice ; le déplacement type par type se fait en DOC4c, quand chaque contexte fournit déjà sa règle.

## Décisions
- Verbe d'écriture : 405 avec `Allow: GET, HEAD, OPTIONS` (403 sans session, avant) ; `Accept` indisponible : 406 (gestionnaire commun `DatesIllisiblesDeLaDirectionAdvice`) ; c'étaient des 500 (KB-DOC4B-1).
- Audit : une écriture tentée est journalisée `TYPE_DE_DOCUMENT_ECRITURE_REFUSEE` (statut 405), et non plus « DOCUMENTS_CREATION » ; la lecture n'est pas journalisée (inchangé) (KB-DOC4B-2).
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : liste identique pour tout profil et tout centre ; `CUSTOM` toujours en dernier ; chemins voisins (`/types/`, `/types/x`) en 500 comme toute route inconnue de la plateforme (KB-DOC4B-3).
- Clés JSON : `code`, `label` (l'ordre des champs d'une entrée était aléatoire avec `Map.of`, il est maintenant fixe ; le filet l'ignore).

## Conséquences
- Filet : 3 scénarios MockMvc/H2 (profils et session, formes de requête, audit) ; 1 identique, 2 rebasés en `*_nouveau_code.approved.json` (anciens dans `doc4b/_ecarts-legacy`).
- `seamAllowed` reste `[]` ; aucun report de butoir, aucune nouvelle date.
