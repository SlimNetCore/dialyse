---
status: accepted (placement, ports publiés, déclaration des 14 types, aucun changement de comportement) ; proposed (points reproduits à confirmer métiers/DPO avant le 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: documents, documents-api, facturation, dossier-medical, stock, gmao, planification, seance, pilotage-direction, platform
related: ADR 0024, 0034, 0081, 0140, 0142, 0162, 0163, 0164, 0165 ; `.ddd/safety-net/known-bugs.md` KB-DOC1-3, KB-MED-18, KB-FAC-52, KB-FAC-60, KB-DOC4C-1 à 3 ; `.ddd/migration/reviews/doc4c-realisation.md`
---

# 0166 - DOC4c : les règles d'impression des types sensibles sont publiées par leurs contextes ; `DocumentRestController` disparaît

Refactoring sur place (ADR 0024) : mêmes URL (`POST /api/v1/documents/print`, `POST /api/v1/documents/print/{modeleId}`), mêmes rôles, mêmes codes, mêmes corps de refus (403 `ACCESS_DENIED`, mêmes messages) qu'avant. Aucun comportement n'a changé : le filet (matrice rôle x type x voie, garde de centre, ordre des gardes, totaux du serveur, audit) est identique avant et après.

## Placement
| Avant (plateforme) | Après |
|---|---|
| `DocumentRestController implements ReglesDImpressionDesTypesSensibles` : garde ordonnance, garde synthèse, totaux du serveur, lus par Documents | **supprimé**. Port publié `RegleDImpressionDUnType` (documents-api, documents.v1) : `type()`, `garder(centre)`, `gardeAussiParLeModele()`, `donneesDuServeur(centre, paramètres)` |
| port de Documents `publishedlanguage.ReglesDImpressionDesTypesSensibles` (fourni par la plateforme) | supprimé ; port sortant `application.ReglesDImpression` de Documents, implémenté par `ReglesDesTypesDeclares` (retrouve la règle par le code du type, sans casse ni espaces ; deux règles pour un type, ou une règle sans type = démarrage refusé) |
| garde de la synthèse (`ProfilDeLaSynthese` + `CenterAccessGuard`) et totaux (`SyntheseDeFacturation`) dans la plateforme | **Facturation** : `interfaces.publie.RegleDImpressionDeLaSyntheseMensuelle` (profil ADMIN ou SECRETAIRE, centre de la session, totaux enregistrés de la période) ; `SessionCourante.roles()` |
| garde de l'ordonnance dans la plateforme | **Dossier médical** : `application.GarderLImpressionDeLOrdonnance` (profil de lecture MEDECIN ou ADMIN, domaine `ProfilDeLectureDesOrdonnances`, puis centre) ; adaptateur `interfaces.publie.RegleDImpressionDeLOrdonnance` ; port `ProfilDeLaSession` (ACL `ProfilLegacy`) |
| `ModeleDocumentCatalog` (14 entrées statiques) lu par le moteur, le provisionnement et deux ACL de planification | supprimé ; `application.CatalogueDesTypesLivres` réunit les déclarations `DeclarationDesTypesDeDocument` (ordre `@Order` des beans, doublon = démarrage refusé) ; `TypeDeDocumentDeclare` gagne `description` (le libellé si absente) |

L'orchestration reste celle de DOC1 : `ImprimerUnDocument` appelle la garde du type AVANT toute validation de la demande et toute lecture de données (403 avant 400, y compris pour un corps sans centre ou des paramètres illisibles quand le type est lisible), puis la garde de centre de la session, puis lit le modèle ; les paramètres du serveur l'emportent toujours sur ceux du client (KB-DOC1-2).

## Déclaration des 14 types : où va chaque entrée
| Contexte propriétaire | Types | Ordre (`@Order`) |
|---|---|---|
| Documents | FICHE_PATIENT, ATTESTATION, PEC, LISTE_PATIENTS, LISTE_PEC, LISTE_ATTESTATIONS | 10 |
| Facturation | SYNTHESE_FACTURATION_MENSUELLE | 20 |
| Dossier médical | ORDONNANCE | 30 |
| Stock | INVENTAIRE_STOCK | 40 |
| GMAO | BON_INTERVENTION | 50 |
| Planification | PLANNING_PRESENCE_INFIRMIERS, PLANNING_OPTIMISE (constantes publiées : les ACL d'impression de la plateforme les lisent) | 60 |
| Séance | CAHIER_DIALYSE | 70 |
| Pilotage de la direction | RAPPORT_DIRECTION | 80 |

Moteur de rendu, provisionnement et ACL de planification lisent maintenant le même catalogue construit des déclarations : une seule source de vérité par type, l'ordre de la liste (`GET /types`) est inchangé (golden `doc4b/types`, test `CatalogueDesTypesLivresIntegrationTest`).

## Ce qui reste, et pourquoi
- **Les six documents du patient et de ses listes restent déclarés par Documents** : leurs gabarits lisent le patient par la source de données du moteur et aucun autre contexte n'en fournit de paramètres ; les déplacer chez Patient et couverture sans leur donner de paramètres serait un changement de nom, pas de propriétaire. À reprendre quand l'impression des listes patient sera décidée avec ce contexte.
- **Les gabarits `.jrxml` restent dans les ressources de Documents** (comme en DOC2) ; chaque contexte ne déclare que le chemin livré. Un test vérifie que tout gabarit livré est déclaré.
- **`parametresAttendus` reste vide** pour les 14 types : le contrat de paramètres de chaque type n'est pas encore écrit ; il l'est avec le premier gabarit personnalisé qui en dépend. Aucune date n'est reportée : ce n'est pas un butoir, aucun comportement n'en dépend.
- **L'impression par modèle d'une ordonnance reste non gardée par profil** (voir KB-DOC4C-1).

## Décisions reproduites, à confirmer métiers/DPO avant le 2026-12-15
- KB-DOC1-3 : tout rôle authentifié imprime un type sans règle (LISTE_PATIENTS, FICHE_PATIENT, ATTESTATION, PEC, listes, cahier hors route propre...). La matrice rôle x type de document est fixée par le filet (`doc4c/matrice`) ; elle ne change qu'après décision des métiers.
- KB-DOC4C-1 : `POST /print/{modeleId}` d'un modèle de type ORDONNANCE n'est pas gardé par profil (tous les profils authentifiés obtiennent 200 avec l'ordonnanceId qu'ils fournissent) ; seul le centre du corps est confronté à la session.
- KB-DOC4C-2 : sur la voie par type, un SUPERADMIN seul est refusé pour l'ordonnance et la synthèse (rôles exigés explicites), mais imprime les autres types.

## Conséquences
- Filet : 5 scénarios MockMvc/H2 (`documents/doc4c`), approuvés un par un depuis `.received`, tous identiques à l'existant (aucun `*_nouveau_code`, aucune archive `_ecarts-legacy`).
- Garde permanente (`FrontieresDesContextesTest`) : `DocumentRestController` ne doit plus exister ; aucune classe de la plateforme n'implémente `RegleDImpressionDUnType`.
- `DocumentRestController` retiré de `.ddd/safety-net/covered.json` ; `seamAllowed` reste `[]` ; aucun report de butoir, aucune nouvelle date.
