---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de réaliser O4, 2026-11-09 ; décisions du lot E déléguées), architecte]
context-scope: organisation (publishedlanguage), plateforme (documents)
related: ADR 0107 (lot A, plan en lots), 0108, 0109, 0110 (lots B à D), 0070 (admin générique hébergé en plateforme), 0071, 0024 et 0075 (refactoring sur place), 0063 (Organisation) ; `.ddd/migration/slices/organisation-o4-lectures-publiees.md`
---

# 0111 - Organisation O4, lot E : identité imprimée publiée, admin générique évalué, état final de O4

Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. Les points « à confirmer » le sont par les métiers / DPO au plus tard le **2026-12-15**.

## Contexte
Le plan (ADR 0107) prévoyait pour le lot E deux lecteurs : `DocumentIdentityProvider` (identité imprimée) et `ReferentialAdminJdbcAdapter` (admin
générique, ADR 0070), liste blanche 9 -> 7. La lecture du code montre que `ModeleDocumentProvisioner`, classé « provisionnement » parmi les résiduels,
ne lit des tables d'Organisation que pour une chose : `SELECT id FROM centers` (tous les centres), déjà publiée par `AnnuaireDesCentres.tous()` (lot A, SQL
identique). Il peut donc migrer sans nouveau langage.

## Décisions
1. **Lecture : `IdentiteImprimee`** (organisation.v1) : `duCentre(UUID)` et `deLaSociete(UUID)`, rendant `IdentiteDuCentre` (nom, adresse, ville, wilaya,
   téléphone, email, site web, et la société) et `IdentiteDeLaSociete` (raison sociale, adresse, ville, wilaya, téléphone, email, site web, NIF, NIS, RC, pied de
   page, logo). Ne publie que ce que le SQL lisait. Les textes sont rognés et jamais nuls (absent = chaîne vide), le logo est nul s'il est absent ou vide,
   aucun filtre d'activité (une société désactivée est imprimée comme une autre : comportement historique, pas d'état d'activité publié), centre ou société
   inconnus (ou nuls) = vide. Implémentation `IdentiteImprimeeJdbc` : les deux requêtes sont celles de `DocumentIdentityProvider`, mot pour mot (colonnes, `LEFT JOIN
   societes`, `WHERE c.id = ?`).
2. **Mise en forme laissée à la plateforme** : `DocumentIdentityProvider` garde son API publique (`paramsFor`, `societeIdentity`, `decorateHtml`, `stripReservedParams`)
   et toute la mise en forme (adresse « ville – wilaya » ou ville seule si identiques sans tenir compte de la casse, ligne de contact, mentions légales, flux Jasper neuf à
   chaque impression, `data:` URI, échappement). Seul le constructeur change : il reçoit `IdentiteImprimee` au lieu de `JdbcTemplate`. Ce n'est pas un concept d'Organisation.
3. **`ModeleDocumentProvisioner`** reçoit `AnnuaireDesCentres` et appelle `tous()` : même requête, même exception (`DataAccessException`, toujours captée pour une base non
   initialisée). Les modèles de documents (`modele_document`) restent à la plateforme, qui les possède.
4. **Admin générique : évalué, NON migré (O4-R13).** Il est générique par nature (ADR 0070) : un seul adaptateur construit du SQL dynamique à partir de `ReferentialAdminTables`
   pour `salle`, `position_creneau`, `medecin` ET les référentiels de facturation (propriétaires différents). Faire passer la partie Organisation par des commandes d'Organisation
   n'est pas strictement équivalent : l'adaptateur écrit sans aucun des invariants des services d'Organisation (validation, unicité, effets de bord), insère avec ses propres
   identifiants, évince les caches `ref.*` après commit selon une table de caches par référentiel, alimente l'import CSV (ports génériques) et son comportement est fixé par
   31 goldens `o2-administration` plus l'import. Remplacer seulement `UPDATE medecin SET actif = FALSE` par une commande ferait disparaître le littéral que la garde détecte
   tout en laissant l'insertion, la modification et les lectures dynamiques sur les trois tables : ce serait cacher la dette, pas la retirer. Le chemin de sortie reste l'option 2
   de l'ADR 0070 (administration par type de référentiel, use cases d'Organisation), avec décision explicite sur chaque invariant qui diffère. Propriétaire cible Organisation,
   jalon : revue de l'ADR 0070 avec la slice qui découpera l'admin générique, butoir **2027-03-31**.
5. **Filet AVANT migration** (vert sur les lecteurs d'origine) : `IdentiteImprimeeCasLimitesCharacterizationIntegrationTest` (7 tests) : société nue (rien d'autre que la raison
   sociale), société inactive imprimée avec textes rognés et logo JPEG (flux neuf à chaque appel), logo vide et pied de page blanc, ville = wilaya à la casse près, identité de la seule
   société (inconnue, nulle, connue), provisionnement de tous les centres même inactifs (idempotent), modèle désactivé conservé. Mêmes assertions après (aucune ligne de test modifiée).
   Nouveau contrat `IdentiteImprimeeContrat` (faux en mémoire et H2, 5 tests chacun). Constat : un centre sans société est impossible (`centers.societe_id NOT NULL`) ; la jointure
   externe est conservée par fidélité, le cas est rendu par une société vide.
6. **Seams** : aucun nouveau fichier de plateforme ; `ModeleDocumentProvisioner` est ajouté à `covered.json` (couvert par le filet ci-dessus) ; `seamAllowed` = `[]`.
7. **Graphe Maven** : la plateforme dépendait déjà d'organisation ; aucun `pom.xml` modifié, aucun cycle.

## Conséquences et risques
- Aucun écart de comportement observable : aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`, aucun `.approved` modifié, aucun KB nouveau. Liste blanche 9 -> 7.
- Le logo est copié (octets) à chaque lecture comme avant ; le tableau publié ne doit pas être modifié par le consommateur (documenté).
- `IdentiteImprimeeJdbc` n'a été exécuté que sur H2 (O4-R7 : variante PostgreSQL 16 non lancée).

## État final de O4 (liste blanche : 7 résiduels nominatifs)
Publié par Organisation : `AnnuaireDesCentres`, `AnnuaireDesSocietes`, `VerrouDuCentre`, `LibellesDeLOrganisation`, `SallesEtCreneauxDuCentre`, `DefinirLesSallesDIsolement`,
`IdentiteImprimee` ; ports `CentresFournis` et `VerrouDuCentreFourni` déclarés par Stock. 25 des 32 lecteurs de départ sont remplacés.

| Résiduel | Fichier | Propriétaire cible | Raison |
|---|---|---|---|
| O4-R9 authentification | `AuthService` | Identité et accès | jointures avec les comptes (`app_user_*`), choix de connexion ; ni le contexte ni la slice d'Organisation ne possède l'authentification |
| O4-R10 comptes | `DirectionAccountService`, `SocieteAdminAccountService`, `UserRestController` | Identité et accès | existence d'une société, centres d'une société ou d'un compte (jointures `app_user_center`) |
| O4-R11 garde d'accès | `DirectionAccessGuard` | Identité et accès | sociétés accessibles à un compte direction (jointure `app_user_societe`) |
| O4-R12 amorçage dev | `DevSuperAdminBootstrapInitializer` | plateforme (outillage de dev) | ne lit que `SELECT id FROM centers` : `AnnuaireDesCentres.tous()` suffirait ; non migré au lot E (hors périmètre délégué, fichier sans filet) |
| O4-R13 admin générique | `ReferentialAdminJdbcAdapter` | Organisation (3 tables) / plateforme (le reste) | décision 4 |

Butoir commun : **2027-03-31**. Restent aussi ouverts hors liste blanche : O4-R5 (calendrier du centre), O4-R6 (événements d'Organisation jamais émis, `CentresActifs` inutilisé),
O4-R7 (contrats sur PostgreSQL 16), O4-R8 (KB-ORG-10). La slice reste `in-progress` : le passage à `built` suit la revue finale.
