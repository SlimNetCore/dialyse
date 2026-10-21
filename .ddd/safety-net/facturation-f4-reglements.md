---
status: done
date: 2026-10-21
slice: facturation-f4-reglements
sources: roadmap § 1 ter (F4), .ddd/tactical/facturation.md (F12-F13, agrégat Règlement), RG-REG-001 à 004, comptabilite-c5-ventes-reglements.md
---

# Filet de caractérisation — F4 Règlements

**Où** : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/reglements/`
(6 classes + harnais `ReglementsCharacterizationSupport` + observateur d'événements `ReglementsEnregistresObserves`) ;
goldens `backend/platform/src/test/resources/characterization/facturation/reglements/{enregistrement,situation,recherche,tableau-de-bord-export,evenement,autorisations}/`.
**94 scénarios**, 0 flaky (3 exécutions complètes, ordre des classes et des méthodes aléatoire).
Mutation : `backend/scripts/pit-f4-reglements.sh` (résultat plus bas). Constats : `known-bugs.md` § « slice F4 »,
KB-FAC-37 à 51. Les tests préexistants (`ReglementRestControllerIntegrationTest`, `ReglementServiceTest`) restent en place.

## Périmètre exact

Un seul contrôleur, `ReglementRestController` (`/api/v1/reglements`, `@PreAuthorize ADMIN, SECRETAIRE` partout) :

| Route | Rôle | Chemin legacy |
|---|---|---|
| `POST /{factureId}/paiements` | enregistrer un règlement (corps `RegisterFacturePaymentRequest` : `centerId` et `montant` requis, `dateReglement`, `userId`) ; répond la ligne de situation de la facture | `ReglementApplicationService.registerPayment` (transaction, publie `FactureReglementRecordedEvent`) → `ReglementService` → `FactureReglementAggregate.enregistrerPaiement` → `FactureReglementJdbcAdapter.loadAggregate/savePayment/getFactureItem` |
| `GET /{factureId}/paiements?centerId` | historique des règlements d'une facture (liste, sans total) | `getPaymentHistory` |
| `GET ?centerId&year[&month&caisseId&agenceId&centrePayeurId&page&size]` | recherche paginée des factures avec leur situation | `search` |
| `GET /dashboard?…` | agrégats par situation | `dashboard` |
| `GET /export?…&format=excel\|csv` | CSV (`;`) ou classeur Excel (Apache POI) des mêmes lignes | `exportList` + `buildCsv/buildExcel` du contrôleur |

Table écrite : `facture_reglements` (id, facture_id, center_id, montant `numeric(14,2)`, date_reglement, saisi_par, code_reglement, created_at).
Tables lues : `factures` (`total_ttc`, instantanés payeur/agence), `patients`, `centre_payeur`, `agence`, `caisse_assurance`.
**Aucune route d'annulation, de suppression, de modification ni de lecture d'un seul règlement** (elles répondent 500).
Le seul moyen de corriger est un règlement de montant négatif (remboursement).

### Ce que fait le legacy (réponse aux questions de conception)

| Sujet | Comportement actuel |
|---|---|
| Règlement | rattaché à `factureId` + centre ; montant signé **non nul** (négatif accepté), arrondi HALF_UP à 2 décimales ; un montant qui s'arrondit à 0 est refusé ; date optionnelle (défaut jour d'exécution), sans contrôle ; auteur = `userId` du corps nettoyé, défaut `system` (jamais l'utilisateur de la session) ; code `REG-` + 8 hexadécimaux majuscules de l'UUID. Pas de mode de règlement, pas de référence, pas de reçu (KB-FAC-40) |
| Facture éligible | toute facture du centre (la table `factures` ne contient que des factures émises) ; inconnue ou d'un autre centre : 400 `Facture introuvable pour le centre courant` |
| Dépassement | accepté sans limite ; trop-perçu (KB-FAC-41) |
| Situation | **calculée à chaque lecture** : somme signée des règlements vs `total_ttc` figé ; rien : `NON_REGLEE`/`RESTE` ; somme < TTC : `PARTIELLEMENT_REGLEE`/`RESTE` ; = TTC : `REGLEE`/`REGLE` ; > TTC : `REGLEE`/`TROP_PERCU` (excédent) ; somme nulle après remboursement : `NON_REGLEE` |
| Recherche | filtre sur `date_facturation` (bornes du mois/de l'année incluses), caisse/agence/payeur par **instantané de la facture, sinon payeur courant du patient** ; tri `date_facturation DESC, numero_facture DESC` ; défaut page 0 taille 20 ; libellés « Non renseignee/Non renseigne » |
| Événement | `FactureReglementRecordedEvent(factureId, centerId, montant saisi non arrondi, date effective, auteur effectif)` publié dans la transaction ; consommé après commit par `ReglementEncaisseComptabiliteListener` (jamais bloquant) |
| Comptabilité | une écriture `BQ` (crédit client `411100` ou compte débité par la vente, débit banque `512`) à la date du règlement, en **valeur absolue** ; absente en mois clos ; régénérable par `POST /comptabilite/rebuild` qui relit `facture_reglements` |

## Scénarios (94)

| Classe | n | Scénarios |
|---|---|---|
| `EnregistrementDUnReglement…` | 20 | partiel, soldé ; défauts date/auteur ; auteur nettoyé ; arrondis ±x,xx5 ; montant s'arrondissant à 0 ; nul (`0`, `0.00`) ; négatif ; facture inconnue ; facture d'un autre centre ; centre du corps ≠ session (200) ; dates avant la facture/futures ; double envoi ; code `REG-` ; champs inconnus (mode, référence) ; montant ≥ 10^12 (500) ; facture à total négatif (400) ; 8 corps illisibles/incomplets ; id non UUID ; format JSON des montants (2 décimales) |
| `SituationDeLaFacture…` | 13 | sans règlement ; progression NON_REGLEE → PARTIELLEMENT → REGLEE ; trop-perçu direct, aggravé ; remboursement du trop-perçu ; remboursement total, partiel, sans règlement ; arrondis cumulés ; trois tiers ; facture à 0 ; TTC figé ; règlement posé en base |
| `RechercheEtPaiements…` | 17 | liste complète ; tri ; mois/année/bornes/bissextile ; filtres caisse/agence/payeur (instantané puis patient) ; instantanés absents ; pagination ; total = factures ; bornes d'année/mois/page/taille ; paramètres manquants/illisibles ; autre centre ; dernier code ; historique trié, forme, vide/inconnu/autre centre, erreurs ; cohérence après enregistrement |
| `TableauDeBordEtExport…` | 16 | totaux par situation (mois, année) ; règlements postérieurs au mois ; filtres ; bornes (2000/3000, mois 1/12, mois suivants exclus) et erreurs ; autre centre ; vide ; CSV (contenu, en-têtes, absence d'échappement) ; formats csv/CSV/xlsx/pdf/défaut ; Excel (cellules) ; nom de fichier ; filtres de l'export ; export vide ; erreurs |
| `EvenementEtComptabilite…` | 16 | événement nominal, défauts, montant non arrondi, remboursement ; refus sans événement ; écriture banque ; compte sans/avec vente ; remboursement en valeur absolue ; doublons ; montants/auteurs distincts ; mois clos ; clôture par date de règlement ; mois ouvert ; `/rebuild` rattrape ; règlement posé en base sans événement |
| `AutorisationsEtParcours…` | 12 | ADMIN, SECRETAIRE (5 routes) ; MEDECIN seul (`ROLE_SCOPE`), INFIRMIER seul (`ACCESS_DENIED`) ; MEDECIN+SECRETAIRE ; DIRECTION/SUPERADMIN ; anonyme ; admin A sur B ; centre inconnu ; routes absentes ; parcours complet (recherche, tableau de bord, historique, événements, écritures) ; JSON des montants |

## Point d'observation et non-déterminisme

HTTP (MockMvc, Spring complet, sécurité réelle + `RoleScopeFilter`, H2 uniquement, aucun PostgreSQL, port 5432 non utilisé) + état SQL
(`facture_reglements`) + événement réellement publié (`@EventListener` de test, observation seule) + écritures produites par l'écouteur de
production (Comptabilité) + classeur Excel relu avec POI. Aucun mock du code caractérisé, **aucun seam**, aucun code de production modifié.
Factures et référentiel posés en SQL avec identifiants fixes (`<FACTURE_n>`, `<PAIEMENT_n>`, `<CAISSE_1>`…) et dates fixes de 2026 ; le jour
d'exécution n'apparaît que comme date par défaut d'un règlement sans date (remplacé par `<AUJOURD_HUI>`) ; codes `REG-XXXXXXXX` aléatoires remplacés
par `<CODE_REGLEMENT>` ; les règlements dont l'ordre importe sont posés en SQL (horodatage de création et code fixes). Écritures triées par numéro
de pièce **avant** normalisation des identifiants générés (un premier essai trié après était instable : corrigé, voir « Flakiness »).
Mise à jour d'un golden : `-Dcharacterization.approve=true` après relecture, **avec un nom de classe complet** (le motif court
`EvenementEtComptabilite*` réécrit aussi les goldens de F3 : incident constaté et annulé par `git checkout`).

Lancement : `./mvnw -o -pl platform -am test -Dtest='com/hemodialyse/backend/characterization/facturation/reglements/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`
(ordre aléatoire : `-Dsurefire.runOrder=random -Djunit.jupiter.testmethod.order.default='org.junit.jupiter.api.MethodOrderer$Random' -Djunit.jupiter.testclass.order.default='org.junit.jupiter.api.ClassOrderer$Random'`).

## Flakiness

3 exécutions complètes en ordre aléatoire : 94/94 vertes. Une instabilité a été trouvée et corrigée **dans le harnais** pendant la construction :
l'alias `<UUID_GENERE_n>` des `source_id` d'écritures dépendait de l'ordre des lignes retournées par H2 (requête sans `ORDER BY`) ; la requête trie
maintenant par pièce avant la normalisation. Risque résiduel connu : l'ordre de l'historique et le « dernier code » à `created_at` égal (KB-FAC-49) ne sont
volontairement observés qu'avec des horodatages distincts.

## Non verrouillé (limites)

- **PostgreSQL** : H2 seulement. `numeric(14,2)` (dépassement ≥ 10^12 en 500 `DATA_ACCESS_ERROR`) et l'ordre à `created_at` égal ne sont pas rejoués sur PostgreSQL 16.
- **Concurrence** : deux enregistrements simultanés sur la même facture (aucun verrou, aucune contrainte d'unicité) ne sont pas rejouables de façon déterministe.
- **Mode de règlement, reçu, rapprochement bancaire** : n'existent pas dans le legacy (KB-FAC-40) ; le tactical ne les demande pas non plus (à décider, Q-F4).
- **Messages techniques** des 500 (`DATA_ACCESS_ERROR`, `INTERNAL_ERROR`) : seul le code est verrouillé.
- **Frontend** (`frontend/src/app/features/reglement`, `backend-api.service.ts` lignes 1280-1312) : non caractérisé.
- Détail du **contenu binaire** du `.xlsx` (styles, largeur de colonnes : `autoSizeColumn`) : seules les cellules sont verrouillées.
- `ReglementServiceTest` (unitaire) reste le filet de l'agrégat pour les cas non atteignables en HTTP (aucun).
- Écritures de règlement : seul l'effet observable (pièces, lignes) est verrouillé ; l'algorithme de la Comptabilité reste protégé par C5.
- Les règlements de **reprise** (`OpeningBalanceJdbcAdapter` : auteur `MIGRATION_AUTHOR`, code `REP-…`, sans événement) sont observés une fois (`un_reglement_pose_en_base_…`) mais le parcours de migration n'est pas rejoué ici.

## Fichiers legacy ajoutés à `covered.json` (propres à F4)

`bc-reglement` entier : `FactureReglementAggregate`, `FacturePayment`, `FactureReglementRecordedEvent`, `ReglementUseCase` (et ses records), `ReglementRepository`,
`ReglementService`, `FactureReglementStatusSpecification` ; `platform` : `ReglementApplicationService`, `ReglementRestController`, `RegisterFacturePaymentRequest`,
`FactureReglementJdbcAdapter`. `ReglementEncaisseComptabiliteListener` était déjà couvert (C5).

### Fichiers partagés avec F5/F6/autres que F4 devra modifier (déjà dans `covered.json` sauf mention)

| Fichier | Pourquoi F4 y touche | État |
|---|---|---|
| `DirectionDashboardQueryService` (lit `facture_reglements` jointe à `factures`, encaissé par `date_facturation`) | si le contrat de lecture des règlements change (table, signe) | non couvert ici (Direction) ; filet Direction existant (`DirectionIntegrationTest`, `DirectionBreakdownIntegrationTest`) |
| `OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter` (écrivent/suppriment `facture_reglements`) | si la table ou ses codes (`REP-`) changent | filet Migration (`MigrationHistoryIntegrationTest`) |
| `ReglementsAComptabiliserJdbc` (Comptabilité, lit `facture_reglements`) | si la table ou le signe changent | déjà couvert C5 |
| `RoleScopeFilter` (liste `/api/v1/reglements` interdite au médecin seul) | si la route change de préfixe | déjà couvert (`RoleScopeFilterTest`) |
| `FacturationValideeComptabiliteListener` | aucun (ventes) | déjà couvert F3 |
| `renadial-seed.sql`, `renadial-purge.sql`, `generate-renadial-seed.cjs` (données de démonstration) | si les colonnes changent | hors production |

**Accord de l'utilisateur identique à C1/C2/F1/F2/F3** (refonte sur place, ADR 0024/0030/0031/0032) ; F4 ne modifie rien en production dans cette étape.

## Consommateurs et lecteurs hors Facturation

| Consommateur | Ce qu'il lit ou reçoit |
|---|---|
| Comptabilité (C5) | événement `FactureReglementRecordedEvent` → `ComptabilisationDesVentesEtReglements.reglementEnregistre(centre, facture, montant, date, auteur)` ; `ReglementsAComptabiliserJdbc` relit `facture_reglements` (id, facture, montant, date) pour `/rebuild` et pour retrouver l'identifiant du règlement ; journal `BQ` en dur |
| Direction | `DirectionDashboardQueryService` : `SUM(facture_reglements.montant)` des factures de la période (encaissé, reste à recouvrer) |
| Migration | `OpeningBalanceJdbcAdapter` insère les règlements de reprise (`saisi_par` = auteur de migration, code `REP-…`) ; `MigrationRollbackJdbcAdapter` les supprime |
| Frontend | module `reglement` : recherche, tableau de bord, saisie, historique, export (`backend-api.service.ts`) |
| Patient / Séance / Stock / Rapports | aucune lecture de `facture_reglements` (vérifié par recherche) |

## Mutation (PIT 1.30.0)

`bash backend/scripts/pit-f4-reglements.sh` (classpath : `./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE`) sur
`ReglementService`, `FactureReglementAggregate`, `FacturePayment`, `FactureReglementStatusSpecification`, `ReglementUseCase*` (records de validation),
`FactureReglementJdbcAdapter`, `ReglementApplicationService`, `ReglementRestController` (construction du classeur Excel exclue : seules ses cellules sont observées).

**169 mutants, 150 tués = 89 %** (cible 70 % atteinte), force des tests 90 %, couverture de lignes de la zone 95 %. 19 non tués, **tous analysés** :

| Mutant | Analyse |
|---|---|
| `ReglementUseCase` 47, 83, 86, 108, 184 (`centerId == null`, `factureId == null`, `montant == null` des constructeurs de requêtes) | gardes défensives inatteignables par HTTP : `@NotNull` du DTO et paramètres obligatoires du contrôleur échouent avant (500/400 verrouillés) ; équivalents |
| `FacturePayment` 17, 23, 27 (identifiants, date, auteur/code par défaut) | gardes du constructeur : la date est toujours effective, l'auteur est déjà normalisé par la commande ; à la relecture d'un règlement sans auteur ni code (`loadAggregate`) les défauts n'ont aucun effet observable (la somme seule est lue) ; équivalents |
| `FactureReglementAggregate` 16, 25 (identifiants nuls, liste nulle) | inatteignables (SQL non nul) ; équivalents |
| `FactureReglementAggregate` 43, 50 (`montantRegle()`, `situation()`) : **sans couverture** | **code mort en production** : la situation est calculée par `FactureReglementJdbcAdapter.mapListItem` + `FactureReglementStatusSpecification`, jamais par l'agrégat ; seul `ReglementServiceTest` (unitaire) les exerce. À trancher à la conception : l'agrégat cible porte-t-il la situation ? |
| `FactureReglementStatusSpecification` 52 (`normalize(null)`) | jamais appelé avec `null` ; équivalent |
| `FactureReglementJdbcAdapter` 157 (liste vide de `getFactureItem`), 274 (`scale(null)`) | `loadAggregate` échoue avant ; `COALESCE` en SQL ; équivalents |
| `ReglementRestController` 186-187 (boucle d'`autoSizeColumn` du classeur) | largeur de colonnes non observée (limite documentée) |

Premier passage : 145/169 = 86 % ; les bornes 2000/3000 et mois 1/12 du tableau de bord, la fin de mois du tableau de bord et la facture à total négatif
(400 `Le montant facture doit etre positif`) ont ensuite été ajoutées (+5 mutants tués, +2 scénarios).

## Questions de décision pour la conception sur place de F4

Voir la réponse de l'agent et `known-bugs.md` (KB-FAC-37 à 51) : isolation de centre, état `TROP_PERCU`, total signé, remboursement comptable, mode/référence,
idempotence, dépassement, date, clôture, erreurs 4xx, export, tableau de bord.
