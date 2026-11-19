---
status: proposed
date: 2026-11-19
sources: demande utilisateur 2026-11-19 (pagination serveur hors résultat du calcul de facturation + erreur RECU sur le bon de réception) ; commits 8a15ed6a..2ece31f2, c47f4e92
---

# Slice `pagination-serveur` — contrat

Refactoring sur place (mêmes tables, mêmes URLs existantes). Les listes passées en ng-table aujourd'hui paginent côté
serveur, avec tri et filtres de colonne appliqués côté serveur. **Exclu** : résultat du calcul de facturation (reste local).
Déjà distants et sans tri/filtre de colonne dans la table : Articles (`/referentiel/articles/page`) et Historique des
inventaires (`GET /inventaires`) — **inchangés**.

## 1. Convention unique des listes paginées

| Élément | Règle |
|---|---|
| URL | **`GET <URL de la liste existante>/page`** (comme `/referentiel/articles/page`). La route existante (`GET <liste>`) est **inchangée** (réponse, ordre, absence de plafond) : les golden masters restent verts sans modification. |
| Centre / rôles | `centerId` optionnel comme la liste existante, résolu par `session.centre(centerId)` (isolation inchangée) ; **même `@PreAuthorize`** que la route de liste existante. |
| `page` | index base 0, défaut `0` ; `< 0` ⇒ 400 `PAGE_INVALIDE`. |
| `size` | défaut `20`, borné **1..100** ; hors bornes ⇒ 400 `TAILLE_INVALIDE`. |
| `sort` | `sort=champ[,asc\|desc]` (direction absente = `asc`, insensible à la casse). Un seul niveau. `champ` ∈ liste blanche de la liste, sinon 400 `TRI_INVALIDE` ; plus de 2 parties ou direction inconnue ⇒ `TRI_INVALIDE`. Absent ⇒ tri par défaut de la liste. Un départage stable (`id`) est toujours ajouté. L'adapter traduit `champ` en colonne par une **table fixe** (jamais le texte reçu dans le SQL). |
| Filtres | un paramètre par filtre de colonne, nom = nom de champ d'API. **texte** : « contient », insensible à la casse, trim ; vide = ignoré. **numérique** : `[op]nombre`, `op` ∈ `= > >= < <=` (défaut `=`), virgule ou point ; illisible ⇒ 400 `FILTRE_INVALIDE`. **énumération** : une ou plusieurs valeurs séparées par `,` (OU), sensibles à la casse ; inconnue ⇒ 400 `FILTRE_INVALIDE`. **recherche** `q` : texte sur plusieurs colonnes (dite par liste). Les filtres se combinent en ET. |
| Paramètre illisible | UUID / nombre illisible ou obligatoire absent ⇒ 400 `PARAMETRE_INVALIDE` (avant ou après le 403 : même ordre que Factures — un appelant sans rôle reçoit 403). |
| Réponse 200 | `{ "items": [...], "total": <long filtré>, "page": <int>, "size": <int> }` ; champs supplémentaires autorisés (PMP : résumé). `items` = même forme JSON que les éléments de la liste existante sauf mention. Page au-delà de la fin ⇒ `items: []`, `total` réel. |
| Erreurs 400 | `ProblemDetail` `title: "Requete invalide"`, `detail` lisible, propriété **`code`** ∈ `PAGE_INVALIDE`, `TAILLE_INVALIDE`, `TRI_INVALIDE`, `FILTRE_INVALIDE`, `PARAMETRE_INVALIDE`. |
| Exécution | adapter SQL : `COUNT(*)` + `ORDER BY … LIMIT ? OFFSET ?` avec les mêmes `WHERE`. **En mémoire** seulement pour une donnée calculée (PMP, situations de règlement) — dit dans la Javadoc et la RG. |
| Front | ng-table en mode distant (`NgTableRemoteQuery`) ; `core/api/page-serveur.ts` → `parametresDePage(requete, {tris, filtres, recherche})` (colonne → champ d'API ; une colonne sans correspondance n'envoie ni tri ni filtre : la rendre `sortable: false`/sans `filter`). Une colonne filtrée par libellé traduit (statut, état, solde) devient un filtre `enum` dont la valeur est le nom d'énumération. Après une écriture (validation, motif, comptage…), **recharger la page courante**. |

### Briques communes (stubs commités avec ce contrat, propriétaire `stock-bons`)
- `backend/contexts/stock/.../application/pagination/` : `DemandeDePage.lire(page, size, sort, champsTriables, champParDefaut, descendantParDefaut)`, `PageDeResultats<T>` (`map`, `decouper` = pagination en mémoire), `FiltresDePage` (`texte`, `numerique`, `enumeration`), `PaginationInvalide` (codes). Test : `DemandeDePageTest` (vert).
- `.../infrastructure/persistence/pages/SqlDePage.java` : `ordre(demande, colonnes, departage)`, `limite()`, `contient(terme)` (motif `LIKE … ESCAPE '\'`).
- `.../interfaces/http/pages/PageJson.java` (enveloppe) et `ErreursDesPagesDuStockAdvice` (limité au paquet `interfaces/http/pages` : **les nouveaux contrôleurs Stock vont dans ce paquet**).
- Front : `frontend/src/app/core/api/page-serveur.ts` (+ spec verte).
- Facturation (règlement) : le travailleur `reglement` crée ses équivalents dans le contexte Facturation (pas de dépendance Stock ↔ Facturation), mêmes codes.

Nouveaux contrôleurs : **une classe par liste** (`@RestController`, `@RequestMapping` sur la base existante, `@GetMapping("/page")`) dans le paquet `pages` ; la route littérale `/page` prime sur `/{id}` (UUID) pour Spring. Câblage : une `@Configuration` par travailleur (scannée par `@SpringBootApplication`), `StockContexteConfiguration` **non modifiée**.

## 2. Listes

| # | Écran | Endpoint | Exécution | Tri (liste blanche) — défaut | Filtres (param : type) | items |
|---|---|---|---|---|---|---|
| R1 | Règlement saisie | `GET /api/v1/reglements/page` (+ `centerId`, `year` obligatoires, `month`, `caisseId`, `agenceId`, `centrePayeurId` comme `GET /reglements`) | **mémoire** sur `SituationsDesFactures.toutes(criteres)` : fiche patient et chaîne des payeurs viennent des langages publiés (PL8b, ADR 0149/0181), état/solde calculés (F13) | `numeroFacture`, `numeroAssurance`, `patientNom`, `patientPrenom`, `caisse`, `agence`, `centrePayeur`, `montantFacture`, `montantRegle`, `etat` (ordre de l'énum NON_REGLEE<PARTIELLEMENT_REGLEE<REGLEE), `codeReglement`, `dateFacturation` — défaut actuel `dateFacturation desc, numeroFacture desc, factureId` | `numeroFacture`, `numeroAssurance`, `patientNom`, `patientPrenom`, `caisse`, `agence`, `centrePayeur`, `codeReglement` : texte ; `montantFacture`, `montantRegle` : numérique ; `etat` : enum `EtatDeReglement` ; `soldeType` : enum `TypeDeSolde` | `LigneDeSituation` (forme actuelle) |
| B1 | Fournisseurs | `GET /api/v1/stock/referentiel/fournisseurs/page` | SQL (`fournisseurs`, `actif = TRUE` comme la liste) | `code`, `raisonSociale`, `contact` — défaut `raisonSociale asc` | `q` : texte sur code OU raison sociale OU contact (recherche globale de la table) ; `code`, `raisonSociale`, `contact` : texte | `FournisseurReponse` |
| B2 | Bons de commande | `GET /api/v1/stock/bons-commande/page` | SQL (`bons_commande`, `lignes` = sous-requête `COUNT`) | `reference`, `statut`, `lignes`, `createdAt` — défaut `createdAt desc, reference desc` | `reference` : texte ; `statut` : enum `StatutDuBon` (`BROUILLON,VALIDE,RECU`) | `BonReponse` (avec lignes) |
| B3 | Bons de réception | `GET /api/v1/stock/bons-reception/page` | SQL (`bons_reception`) | `reference`, `dateReception` (colonne `date`), `statut`, `lignes`, `createdAt` — défaut `createdAt desc, reference desc` | `reference` : texte ; `statut` : enum `BonDeReception.Statut` (`BROUILLON,VALIDE`) | `BonReponse` (avec lignes) |
| B4 | Bons de sortie | `GET /api/v1/stock/bons-sortie/page` | SQL (`bons_sortie`) | `reference`, `dateSortie` (colonne `date`), `lignes`, `createdAt` — défaut `createdAt desc, reference desc` | `reference` : texte (pas de statut : le bon de sortie n'en a pas) | `BonReponse` (avec lignes) |
| E1 | État du stock | `GET /api/v1/stock/dashboard/stock-valorise/page` | SQL (`articles`, `active = TRUE` ; `valeur` = `qte * pmp` en expression SQL) | `code`, `libelle`, `quantite`, `pmp`, `valeur` — défaut `code asc` | `code`, `libelle` : texte ; `quantite`, `pmp`, `valeur` : numérique | `Valorise` (forme actuelle) |
| E2 | Sorties pour séances — pièces | `GET /api/v1/stock/sorties-pour-seances/page` | SQL (`sorties_pour_seances`) | `reference`, `jour`, `statut`, `lignes`, `enregistreeLe` — défaut `enregistreeLe desc, reference desc` | `reference` : texte ; `statut` : enum `SortiePourSeances.Statut` (`ENREGISTREE,RAPPROCHEE`) | `PieceReponse` **sans `lignes`**, avec `nombreDeLignes` (`PieceResumeReponse`) ; la pièce sélectionnée se lit par `GET /{id}` existant |
| E3 | Sorties pour séances — lignes de la pièce | `GET /api/v1/stock/sorties-pour-seances/{id}/lignes/page` | SQL (lignes de la pièce du centre, jointure `articles` du même contexte pour le libellé) ; pièce inconnue du centre ⇒ même réponse que `GET /{id}` | `article` (libellé), `sortie`, `consomme`, `ecart` — défaut ordre actuel des lignes | `article` : texte sur code OU libellé | `LigneReponse` + `articleCode`, `articleLibelle` |
| E4 | Feuille de comptage (détail d'inventaire) | `GET /api/v1/stock/inventaires/{id}/lignes/page` | SQL (`inventaire_lignes` de l'inventaire du centre) | `article` (code puis libellé), `lot`, `peremption`, `theorique`, `comptee`, `ecart` (`comptee - theorique`, nuls en dernier), `valeurEcart` — défaut `position asc` | `etat` : enum `TOUTES,A_COMPTER,ECARTS,COMPTEES` (une valeur ; `TOUTES` = aucun) — remplace le filtre local `filtrerLignes` ; `q` : texte sur code OU libellé OU n° de lot | ligne d'inventaire (forme actuelle de `details[]`) ; l'en-tête (compteurs, progression, statut) reste `GET /{id}` |
| E5 | Explication du PMP (dialogue) | `GET /api/v1/stock/dashboard/pmp-explain/{articleId}/page` | **mémoire** : le rejeu du PMP est calculé (chaque étape dépend de la précédente) | **aucun** (ordre chronologique du rejeu = le sens de la liste) ; tout `sort` ⇒ 400 `TRI_INVALIDE` | aucun | étapes (forme actuelle) + champs de résumé `articleId, code, libelle, methode, quantiteFinale, valeurFinale, pmpFinal` à côté de l'enveloppe |

Hors périmètre, justifié : Articles et Historique des inventaires (déjà distants, colonnes sans tri ni filtre) ;
résultat du calcul de facturation (demande). Comptage à l'aveugle (E4) : décision du poste (bascule front), le serveur
renvoie déjà le théorique ; le front n'envoie pas de tri `theorique/ecart/valeurEcart` quand ces colonnes sont masquées.

## 3. Bug « No enum constant … BonDeReception.Statut.RECU » (400 sur `GET /api/v1/stock/bons-reception`)

**Diagnostic** : ce n'est pas une requête du front. `GET /bons-reception` n'envoie que `centerId`. Le jeu d'amorçage H2
chargé par défaut (`application.yml:51` → `classpath:db/h2/stock-seed.sql`) insère **120 bons de réception au statut
`'RECU'`** (`backend/contexts/stock/src/main/resources/db/h2/stock-seed.sql:59` et `:77`, centres `1111…` et `2222…`,
copie du vocabulaire des bons de commande ; même contenu dans la référence `platform/src/test/resources/db/seed-reference-avant-decoupe.sql:2030,2048`).
À la lecture, `BonsDeReceptionJdbc.lire` (`infrastructure/persistence/BonsDeReceptionJdbc.java:66`) fait
`BonDeReception.Statut.valueOf("RECU")` alors que l'énum ne connaît que `BROUILLON, VALIDE` ⇒ `IllegalArgumentException`
⇒ `ApiExceptionHandler.java:69` ⇒ 400 `BAD_REQUEST`. Toute la liste du centre tombe (et `GET /{id}` d'un de ces bons).

**Correction (travailleur `stock-bons`)** :
1. Lecture tolérante dans `BonsDeReceptionJdbc` : statut historique `RECU` lu comme **`VALIDE`** (un bon de réception
   « reçu » est entré en stock = validé ; aucune transition possible depuis `VALIDE`, donc aucune réécriture). Tout autre
   statut inconnu ⇒ erreur explicite (`IllegalStateException` avec le statut et l'id), jamais silencieux. **Pas de
   modification des données** (ni seed — `SeedH2DesScriptsParContexteTest` exige l'identité octet pour octet avec la
   référence —, ni migration) ; normaliser les données reste une décision utilisateur (Questions ouvertes).
2. Test de régression adapter (H2) : une ligne `bons_reception.statut = 'RECU'` ⇒ `lister` et `consulter` renvoient
   `VALIDE` ; + un test HTTP ou d'adapter pour `GET /bons-reception/page`.
3. Front aligné sur les statuts réels : `StatutBonReception = 'BROUILLON' | 'VALIDE'`, `StatutBonCommande = 'BROUILLON' |
   'VALIDE' | 'RECU'` (pas d'`ANNULE`, ADR 0045) dans `stock-api.service.ts`, `STATUTS_BON_*`, tons, `editingStatus` et
   specs (régression : options du filtre statut). Bons de sortie : pas de statut (rien à aligner). Sorties pour séances :
   `ENREGISTREE, RAPPROCHEE` (déjà aligné, vérifier).
4. `KB-STK-58` dans `.ddd/safety-net/known-bugs.md` (corrigé, lecture tolérante) et RG dans 8.3.

## 4. Découpage en 3 travailleurs (propriété disjointe : `ownership.json`)

| Travailleur | Écrans | Backend | Front |
|---|---|---|---|
| `reglement` | R1 | `ReglementControleur` (ajout `@GetMapping("/page")`), `ReglementsAdvice`, `application/reglement/**`, `SituationsDesFacturesJdbc`, `FacturationContexteConfiguration`, tests reglement, caractérisation `facturation/reglements/**` | `features/reglement/**`, `core/api/backend-api.service.ts` (+spec) |
| `stock-bons` | B1–B4 + bug RECU + briques communes | `application/pagination/**`, `application/consultation/{bons,fournisseurs}/**`, `persistence/pages/{SqlDePage,Bons*,Fournisseurs*}`, `BonsDeReceptionJdbc`, `interfaces/http/pages/{PageJson,ErreursDesPagesDuStockAdvice,Bons*,Fournisseurs*}`, `configuration/PagesDesBonsConfiguration` | `core/api/stock-api.service.ts` (+spec), `core/api/page-serveur.ts` (+spec), `features/stock/{fournisseurs,bons-commande,bons-reception,bons-sortie}.*`, `stock-ng-table.ts`, `features/stock/state/{bons,fournisseurs}*` |
| `stock-etat` | E1–E5 | `application/consultation/{etat,seances,inventaire,pmp}/**`, `persistence/pages/{EtatDuStock,SortiesPourSeances,LignesDInventaire}*`, `interfaces/http/pages/{EtatDuStock,SortiesPourSeances,LignesDInventaire,ExplicationDuPmp}*`, `configuration/PagesDeLEtatDuStockConfiguration` | **nouveau** `core/api/stock-pages-api.service.ts` (+spec) (ne pas toucher `stock-api.service.ts`), `core/api/inventaire-api.service.ts` (+spec), `features/stock/{etat-du-stock,sorties-pour-seances,pmp-explain-dialog}.*`, `features/stock/inventaire/{inventaire-detail.component,inventaire.util}.*`, `features/stock/inventaire/state/**` |

Fichiers partagés (Edit ciblé dans **sa** section, relire et réessayer en cas de conflit) : `frontend/public/i18n/{fr,en,ar,kab}.json`
(ar/kab = propositions), `docs/reference/regles-de-gestion/16-annexe-tracabilite.md` (une ligne par nouveau contrôleur,
à côté de la ligne du contrôleur existant), `08-stock.md` (stock-bons : 8.1 fournisseurs, 8.2, 8.3, 8.4 ; stock-etat :
8.5, 8.6, 8.9), `12-facturation-reglements-comptabilite.md` (reglement seul). Identifiants réservés : `stock-bons`
RG-STK-087 à 089 ; `stock-etat` RG-STK-090 à 093 ; `reglement` RG-REG-009 (+010 si besoin) ; KB : `KB-STK-58` (stock-bons).
Besoin hors périmètre ⇒ ligne `CONTRAT:` dans le compte rendu (ex. `stock-etat` qui voudrait modifier `SqlDePage` ou `PageJson`).
Tests : `ReglesDeGestionDocumentationTest`, `TableDuStockTest`, `ArchitectureHexagonaleTest`, `check-arch` verts ; golden
masters `characterization/**` inchangés.

## Questions ouvertes
- Données réelles (PostgreSQL) : existe-t-il des `bons_reception.statut = 'RECU'` venant du legacy ? Si oui, normaliser
  en `VALIDE` par une migration (décision utilisateur ; la lecture tolérante couvre en attendant).
- KB-STK-58 / journal comptable : `OperationsDeStockJdbc.java:36` ne journalise que `br.statut = 'VALIDE'` alors que l'écran lit `RECU` comme `VALIDE`. Choisir : normaliser les données (`RECU` → `VALIDE`) ou `br.statut IN ('VALIDE','RECU')` dans le journal (golden masters comptables déplacés, décision dans l'ADR de slice). Non modifié en attendant.
- Seed H2 : corriger `'RECU'` → `'VALIDE'` dans `stock-seed.sql` **et** la référence `seed-reference-avant-decoupe.sql` (même commit) ? Non fait par défaut.
- Règlement : la pagination reste en mémoire (après lecture SQL de la période) tant que la fiche patient et la chaîne des payeurs viennent des langages publiés ; une projection SQL dédiée serait une slice séparée.
- Doc fonctionnelle `docs/client/**` : régénérée par l'orchestrateur après fusion des 3 travailleurs (pas en parallèle).
