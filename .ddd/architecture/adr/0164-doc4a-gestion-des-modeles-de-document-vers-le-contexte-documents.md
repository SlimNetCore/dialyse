---
status: accepted (placement, corrections de forme, audit) ; proposed (points reproduits à confirmer métiers/DPO avant le 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: documents, platform
related: ADR 0024, 0142, 0162, 0163 ; `.ddd/safety-net/known-bugs.md` KB-DOC4A-1 à 8 ; `.ddd/migration/reviews/doc4a-realisation.md`
---

# 0164 - DOC4a : la gestion des modèles de document passe au contexte Documents

Refactoring sur place (ADR 0024) : mêmes URL (`/api/v1/documents/modeles`, `/modeles/search`, `/modeles/{id}`), mêmes rôles (création, modification, suppression : `ADMIN`, `SUPERADMIN` ; liste et recherche : toute session), mêmes codes de succès (200), mêmes champs, même table `modele_document`.

## Placement
| Avant (plateforme) | Après (`contexts/documents`) |
|---|---|
| `DocumentRestController` : 5 routes en SQL direct | `interfaces.http.ModelesControleur` + `ErreursDesModelesAdvice` (+ `CorpsJson`, `CorpsIllisible`) |
| regex `SAFE_TEMPLATE_PATH` dans le contrôleur | `domain.CheminDeGabarit` (objet-valeur : uniquement `reports/<nom>.jrxml` livré) |
| contraintes des colonnes (échec en base, 500) | `domain.DefinitionDeModele` (code, libellé, type, format, description : obligatoire et borné) ; `domain.RechercheDeModeles` (page 0 ou plus, taille 1 à 200, jamais ramenée) ; `domain.ModeleDuCatalogue` (ligne de la liste) ; `domain.ModeleRefuse` |
| SQL du contrôleur | port `application.ModelesDeDocument` étendu en écriture (`lister`, `rechercher`, `creer`, `modifier`, `supprimer`) ; adapter `ModelesDeDocumentJdbc` ; doublon = `application.ModeleEnDoublon` |
| suppression : `VersionsDeModele.retirerToutes` puis `DELETE` (deux transactions) | `application.GererLesModeles.supprimer` : `GererLesGabarits.retirerLHistorique` + suppression dans une seule unité de travail |
| `ModeleDocumentDto`, `DocumentSearchRequest` | supprimés : corps lus en JSON dans l'adapter entrant (`ModeleRequete`, `RechercheRequete`, privés au paquetage `interfaces.http`) |
| port publié `VersionsDeModele` (DOC3) | retiré : plus aucun appelant hors du contexte |

`DocumentRestController` reste à la plateforme pour `GET /types` (DOC4b) et les règles des types sensibles (DOC4c), sans SQL ni JDBC.

## Décisions
- Formes illisibles : 400 / 415 / 405 (+ `Allow`) / 406 au lieu de 500 ; 403 avant 400 (le profil est contrôlé par la garde de méthode avant la lecture du corps, lu dans la méthode et non par le cadre ; le centre est contrôlé avant la page et la taille) ; le corps n'est lu que si le `Content-Type` est JSON (le corps XML lu par erreur est refusé en 415) (KB-DOC4A-1 à 3).
- Doublon de code ou d'identifiant : 409 `CONFLICT` ; champ obligatoire absent ou trop long : 400, avec un message lisible au lieu du message brut du SGBD (KB-DOC4A-4). Aucune règle de gestion n'est ajoutée : les bornes sont celles des colonnes.
- Audit : `MODELE_CREE`, `MODELE_MODIFIE`, `MODELE_SUPPRIME`, `MODELE_RECHERCHE` (la recherche n'est plus une « création », KB-DOC4A-5).
- Noms de champs de la liste fixés en minuscules (`center_id`, `chemin_jrxml`...), identiques à la production sous PostgreSQL ; le filet normalise la casse des clés de H2 (KB-DOC4A-8).
- 406 : traité par le gestionnaire commun de la plateforme (`DatesIllisiblesDeLaDirectionAdvice`, précédent PL9/PL11) pour ces deux routes.
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : KB-DOC4A-6 (modifier ou supprimer un modèle inexistant ou étranger = 200) et KB-DOC4A-7 (code ou libellé blancs, type et format libres, filtre de type sensible à la casse).

## Conséquences
- Filet : 10 scénarios MockMvc/H2 ; 2 identiques à l'existant (profils et session, impression après suppression ou désactivation), 8 rebasés en `*_nouveau_code.approved.json` (anciens dans `_ecarts-legacy`).
- `seamAllowed` reste `[]` ; aucun report de butoir, aucune nouvelle date.
