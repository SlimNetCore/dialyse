---
status: proposed
date: 2026-10-15
slice: comptabilite-c2-mapping-tva
sources: [.ddd/tactical/comptabilite.md §8 §10 (C2), ADR 0024, .ddd/safety-net/comptabilite-c1-referentiel.md (méthode), .ddd/migration/slices/comptabilite-c1-referentiel.md, .ddd/safety-net/known-bugs.md (KB-CPT-14 à 23)]
---

# Filet de caractérisation — Comptabilité C2 « Mapping comptable et règles de TVA »

Refactoring **sur place** (ADR 0024) : mêmes tables, mêmes URLs, livraison en une fois. Aucun code de production
modifié, aucun seam appliqué (voir `.ddd/safety-net/seams.md`).

## Périmètre exact

Préfixe `/api/v1/comptabilite`, contrôleur legacy `ComptabiliteRestController` (partagé avec C3, C5, C6). Tables
écrites : `mapping_comptable` (une ligne par centre, `uq_mapping_center`), `tva_types` (**partagée** avec la
Facturation ; propriétaire à trancher à la modélisation de la Facturation, Q1 : la Comptabilité y écrit et lit via
`/regles-tva`, même URL en attendant). Lectures de contrôle : plan comptable et journaux du centre via les ports publiés
de C1 (`PlanComptableDuContexte`, `JournauxDuContexte`).

| Route                                      | Rôle  | Comportement résumé                                                                                                                                                                                                                                                                                                                             |
|--------------------------------------------|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /mapping` (`centerId?`)               | ADMIN | mapping du centre ; **sans ligne** : valeurs par défaut (706, 411100, 411500, 512, 530, 44571, stock 322/602/408/757/657, journaux VE/BQ/CA/AC/ST/ST) sans rien enregistrer ; colonnes vides = défaut ; TVA vide en base lue `44571` (KB-CPT-14)                                                                                                |
| `PUT /mapping` (`UpdateMappingRequest`)    | ADMIN | 5 comptes obligatoires (≤ 20), TVA et 5 comptes du stock facultatifs (absent ou blanc = valeur actuelle, KB-CPT-15), `journaux` {opération: code} (absent = journal actuel) ; contrôle **journaux d'abord** (`JOURNAL_INCONNU`) puis **comptes du plan** (`COMPTE_INCONNU`), tous revalidés ; upsert de la ligne ; réponse = mapping enregistré |
| `GET /regles-tva` (`centerId?`)            | ADMIN | toutes les règles du centre (actives et désactivées, tous types), de la plus récente à la plus ancienne ; champs `typePrestation`, `tauxApplique`, `exonere`, `dateDebutValidite`, `dateFinValidite` (`""` si ouverte)                                                                                                                          |
| `POST /regles-tva` (`SaveRegleTVARequest`) | ADMIN | insère une ligne `tva_types` (actif, `created_by` `comptabilite`) ; `{"saved":true}` en 200 ; ni modification ni suppression (KB-CPT-16)                                                                                                                                                                                                        |

Routes **voisines non caractérisées ici** : écritures, validation, export, clôture (C3), `/rebuild` (C5),
`/stock/synchroniser` (C6), `/modeles` et `/pieces` (C4), comptes, journaux, payeurs (C1). Les lectures de `/mapping`
par les cas d'usage de ventes, règlements et stock ne sont observées que par leur effet sur l'écriture générée.

## Point d'observation et harnais

HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle +
`RoleScopeFilter` ajouté à la chaîne comme en C1, JPA/JDBC réels sur **H2**), plus lecture SQL de `mapping_comptable`,
`tva_types`, `lignes_ecriture` pour les effets. Aucun mock. Les consommateurs hors HTTP (écriture de ventes et de
règlements : `ComptabiliteUseCase`; lecteurs de `tva_types` : `ParametrageFiscalPort`, `TypeTvaRepositoryPort`,
`ForfaitAbsencePort`) sont appelés comme **vrais beans Spring** (même point d'entrée que les écouteurs et services de
production).

- Harnais :
  `backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/mappingtva/MappingTvaCharacterizationSupport.java`
  (fixtures `99996200-…`, trois centres, deux payeurs, nettoyage de toutes les tables touchées avant/après chaque test ;
  goldens à **clés d'objet triées** car `/regles-tva` répond par `Map.of`).
- Tests (84) : `MappingComptableCharacterizationIntegrationTest` (35), `ReglesTvaCharacterizationIntegrationTest` (28),
  `MappingTvaConsommateursCharacterizationIntegrationTest` (21).
- Goldens (259 fichiers `*.approved.json`, un par scénario observable, tous < 200 lignes) :
  `backend/platform/src/test/resources/characterization/comptabilite/mapping-tva/{mapping,regles-tva,consommateurs}/`.
  Approuver : `-DargLine=-Dcharacterization.approve=true` avec `-Dtest=<les 3 classes>` **explicitement** (jamais un
  motif large : cela approuverait aussi les goldens GMAO et C1) ; revue humaine du diff obligatoire.

## Scénarios

| Domaine                                                                        | Scénarios verrouillés                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Lecture et valeurs par défaut                                                  | sans ligne : défauts et aucune ligne créée ; centre vide ; ligne héritée (stock et journaux `NULL`) complétée ; colonnes blanches = défaut ; TVA `NULL`/blanche relue `44571` ; compte de ventes blanc en base = 400 ; journal en minuscules normalisé, hors format = 400                                                                                                                                                                                                                                                                   |
| Enregistrement                                                                 | création, remplacement (une seule ligne), idempotence, mise à jour d'une ligne héritée ; comptes rognés ; mêmes comptes pour tous les usages ; plan de départ et journaux par défaut **non enregistrés** par le mapping                                                                                                                                                                                                                                                                                                                     |
| Comptes du stock                                                               | personnalisés, absents/blancs/nuls = valeurs actuelles, remplacement partiel, pas de retour au défaut, plan enregistré sans comptes du stock = refus                                                                                                                                                                                                                                                                                                                                                                                        |
| Plan (ports C1)                                                                | plan de départ, plan enregistré, compte désactivé, casse (`abc` refusé), compte d'un autre centre, deux comptes fautifs (le premier cité), TVA renseignée contrôlée / TVA vide non contrôlée                                                                                                                                                                                                                                                                                                                                                |
| Journaux (ports C1)                                                            | défauts acceptés tant que rien d'enregistré, journal inconnu, désactivé, absent des journaux enregistrés, codes normalisés (minuscules, espaces), 10/11 caractères, tiret, accent, nul, vide, blanc, opération inconnue ou en minuscules (400), six opérations dans le même journal, journaux absents = actuels, journal contrôlé avant compte                                                                                                                                                                                              |
| Validation                                                                     | 5 comptes obligatoires × absent/vide/blanc/nul (400) ; 20/21 caractères ; propriétés inconnues ignorées ; corps absent/illisible/tableau/`null`/`text/plain` (500), `{}` (400), type nombre coercé, `journaux` texte (500), `centerId` illisible (500)                                                                                                                                                                                                                                                                                      |
| Règles de TVA — enregistrement                                                 | nominal, complet (fin, texte, exonération), ajouts successifs sans fermeture, libellé composé, taux 0 / 0.00 / −0.01 / −1 / 100 / 150 / 999.99 / 1000 / 19.999 / 19.005 / texte, type absent/nul/vide/blanc/espaces/minuscules/accents/120/121, texte 500/501, début absent/nul/mal formé/29 février, fin avant début/égale/nulle, dates extrêmes, chevauchements et doublons, `exonere` absent/nul/`"oui"`/`"true"`/`1`, propriétés inconnues, corps illisibles, validations multiples                                                     |
| Règles de TVA — lecture                                                        | liste vide, tri par début décroissant, règles désactivées et autres types listés, ni pagination ni filtre, date de fin vide, champs exposés                                                                                                                                                                                                                                                                                                                                                                                                 |
| Règle applicable à une date (3 lecteurs : Comptabilité, Facturation, Absences) | dernière règle commencée ≤ date, bornes de début et de fin incluses, règle terminée = aucune (Absences : 0), règle récente terminée laisse revenir l'ancienne ouverte, exonérée (Absences 0, Facturation taux saisi), type `HEMODIALYSE` exact, autre centre, règle désactivée par `DELETE /tva-types/{id}` ignorée mais toujours listée, taux > 100 (Facturation en erreur, Absences et Comptabilité lisent)                                                                                                                               |
| Effet sur les écritures                                                        | **ventes** : défauts, comptes personnalisés (ventes, client patient/défaut, TVA), TVA vide = 44571, vente sans TVA sans ligne de TVA, journal OD et numérotation, changement de mapping sans effet rétroactif ni régénération ; **règlements** : banque/caisse et journaux du mapping, comptes personnalisés, règlement qui solde le compte débité par la facture même après changement du mapping ; **stock** (réception, sorties, inventaire) : défauts, puis comptes et journaux personnalisés, comptes propres à l'article prioritaires |
| Isolation de centre                                                            | un centre ne voit que son mapping et ses règles ; `centerId` d'un autre centre = 403 `ACCESS_DENIED` (lecture et écriture, les 4 routes) ; `centerId` propre accepté ; illisible = 500 ; `centerId` du corps de `/regles-tva` ignoré                                                                                                                                                                                                                                                                                                        |
| Autorisations                                                                  | ADMIN seul ; SECRETAIRE et INFIRMIER 403 `ACCESS_DENIED` ; MEDECIN seul, DIRECTION, SUPERADMIN 403 `ROLE_SCOPE` ; ADMIN+SECRETAIRE lecture permise ; anonyme 403 sans corps                                                                                                                                                                                                                                                                                                                                                                 |
| Routes absentes                                                                | `POST/DELETE/PATCH /mapping`, `GET/PUT /mapping/{id}`, `PUT/DELETE/PATCH /regles-tva`, `GET/PUT/DELETE /regles-tva/{id}` : 500 `INTERNAL_ERROR` (KB-CPT-02)                                                                                                                                                                                                                                                                                                                                                                                 |
| Parcours                                                                       | mapping : défauts, personnalisation, relecture, refus, relecture inchangée, protection du compte par C1 ; règles : vide, première règle, successeuse, relecture, refus, relecture inchangée                                                                                                                                                                                                                                                                                                                                                 |
| Protections C1 par le mapping                                                  | un compte ou un journal choisi par le mapping ne se désactive plus (`COMPTE_UTILISE`, `JOURNAL_UTILISE`), se désactive une fois libéré                                                                                                                                                                                                                                                                                                                                                                                                      |

## Non-déterminisme

Aucune horloge ni aléa dans les réponses (dates de fixtures fixes ; `created_at` de `tva_types` jamais exposé ni
comparé ; numéros de pièce `VE-2026-000001` déterministes après nettoyage). Quatre contrôles sans seam : tri des clés
JSON (`Map.of`), assertion par motif de l'opération citée par `JOURNAL_INCONNU` (`Map.copyOf`, KB-CPT-15), vidage du
cache `tva-types-list` avant chaque test, `GET /tva-types` observé seulement pour la liste (pas de golden contenant
`createdAt`). Messages Spring/Jackson/SGBD des 500 masqués.

## Lecteurs de `tva_types` hors Comptabilité

| Lecteur                    | Comment                                                                                                                                                                                                                                                                                                                                                               | Observé par                                                         |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| **Facturation**            | `TypeTvaJdbcAdapter.findActiveAt` (`FacturationDomainService.resoudreTauxTva`, prestation `HEMODIALYSE`, date de facturation) ; `TypeTvaApplicationService.listerTypesTva` (`GET /tva-types`, **cache 6 h**) ; écrit aussi (`POST/PUT/DELETE /tva-types`, désactivation = `actif = FALSE`) ; lit le **taux** sans regarder `exonere` ; `TypeTVA` refuse un taux > 100 | `regle_*_lectures`, `taux_superieur_a_100_*`, `la_liste_en_cache_*` |
| **Absences**               | `ForfaitAbsenceJdbcAdapter.tauxTva` (`ValorisationAbsenceService`) via `ParametrageFiscalPort` **de la Comptabilité** (`ParametrageFiscalJdbcAdapter.findActiveAt`) : prestation `HEMODIALYSE`, 0 si aucune règle ou exonérée                                                                                                                                         | idem (colonne `absences_taux`)                                      |
| **Comptabilité elle-même** | n'utilise **aucune** règle pour ses écritures (la TVA vient de `total_tva` de la facture) ; `isActiveAt` de `RegleTVA` est du code mort                                                                                                                                                                                                                               | KB-CPT-18                                                           |

Le propriétaire de `tva_types` est à trancher à la modélisation de la Facturation ; tant qu'il ne l'est pas, C2 conserve
`/regles-tva` et `ParametrageFiscalPort` **tels quels** (l'Absence en dépend ; C7 prévoit de la re-pointer vers une
lecture publiée de la TVA).

## Mesures

- Suite (84 tests) : **3 exécutions vertes consécutives en ordre aléatoire**
  (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`), 0 flaky. Les deux seules
  instabilités rencontrées (opération citée par `JOURNAL_INCONNU`, cache `tva-types-list`) sont des non-déterminismes
  **legacy** maintenant contrôlés dans les tests (voir ci-dessus).
- **PIT 1.30.0** (+ junit5-plugin 1.2.3, `backend/scripts/pit-c2-mapping-tva.sh`, tests = les 3 classes HTTP) sur
  `MappingComptable`, `RegleTVA`, `ComptesStock`, `OperationComptable`, `ComptabiliteService` (hors génération
  d'écritures et autres cas d'usage), `MappingComptableJpaAdapter`, `ParametrageFiscalJdbcAdapter`,
  `ComptabiliteRestController` (hors autres routes) : **114 mutants, 90 tués (79 %)**, force des tests 88 %, couverture
  de lignes des classes mutées 237/364 (65 % ; le reste est la génération d'écritures, l'export, la validation,
  `/rebuild`, `/stock`, hors C2). Cible ≥ 70 % atteinte. Rapport : `backend/platform/target/pit-reports-c2/`. Les 12
  sans couverture : 6 lambdas des routes voisines (`rebuild`, `valider`, `exporter`) et 6 de `RegleTVA.isActiveAt` (code
  mort). Les 12 survivants : 8 gardes de construction (`RegleTVA`, `MappingComptable`, `ComptesStock.exiger`) que les
  DTO arrêtent avant d'atteindre le domaine (seul un test unitaire du nouveau domaine peut les tuer) ; 2 branches «
  article sans compte » de `ComptesStock.stockDe/consommationDe` (lues par C6 ; un compte d'article vide donne le compte
  du centre : couvert par le golden stock mais pas par ce mutant) ; 1 équivalent (`ComptabiliteService:160`, le port des
  journaux de C1 renvoie déjà les défauts) ; 1 de C5 (`ComptabiliteService:103`, compte débité par la facture).
- PostgreSQL 16 : `backend/scripts/comptabilite-c2-pg16-constat.sh 55442` (cluster jetable, port 55442, jamais 5432,
  détruit en sortie, schéma Flyway V1..Vn) : `INSERT` de `POST /regles-tva` en **erreur** (`random_uuid()` n'existe
  pas : KB-CPT-21) ; `19.999` devient `20.00` et `19.005` devient `19.01` ; `1000` dépasse `numeric(5,2)` ; contraintes
  `uq_mapping_center` et clés primaires constatées. La suite HTTP reste sur H2.

## Limites : ce qui N'EST PAS verrouillé

- Contraintes et fonctions PostgreSQL réelles pour les routes (la suite est sur H2) : KB-CPT-21 constaté **à part**, pas
  par la suite ; concurrence (deux premiers `PUT /mapping`, deux règles de même début) : lue dans le code, non exécutée.
- Choix entre deux règles de même type et même date de début (`LIMIT 1` sur égalité) : indéterminé, volontairement non
  verrouillé.
- Opération citée par `JOURNAL_INCONNU` quand plusieurs sont fautives (motif seulement) ; texte exact des 500
  (Spring/Jackson/SGBD) ; en-têtes ; audit des écritures (filtre d'audit hors chaîne MockMvc) ; performances.
- Comportement propre des écritures de ventes (arrondis, équilibre, période clôturée : C5), des règlements (C5), du
  stock (différentiel, révision, compléments : C6), des modèles et pièces (C4), de l'export et des périodes (C3) : seul
  l' **effet du mapping** sur l'écriture générée est verrouillé.
- Lecture de `tva_types` par la préparation de facturation (aperçu, génération) et par l'écran d'absences en HTTP
  complet : observée par les ports (mêmes adaptateurs), pas par ces routes ; `GET /tva-types` seulement pour la liste et
  le cache.
- Écran Angular (écrans mapping et règles de TVA) : non rejoué.

## Fichiers legacy de C2

**Propres à C2 (ajoutés à `covered.json`)** : `MappingComptable`, `RegleTVA`, `ComptesStock`, `OperationComptable` ;
ports `MappingComptablePort`, `ParametrageFiscalPort` ; `MappingComptableJpaAdapter`, `MappingComptableJpaEntity`,
`MappingComptableJpaRepository`, `ParametrageFiscalJdbcAdapter`.

**Partagés avec C3 à C7, ajoutés à `covered.json` parce que C2 devra les modifier** (accord de l'utilisateur déjà donné
pour C1 au même titre ; à reconfirmer s'il le souhaite) : `ComptabiliteService` (mapping et règles, mais aussi
génération d'écritures et clôture), `ComptabiliteUseCase`, `ComptabiliteApplicationService` (transactions du cas
d'usage). **Déjà couverts par C1** : `ComptabiliteRestController` (4 routes C2 y restent, sans autre route de C1),
`infrastructure/acl/comptabilite/**`.

**Partagés, NON ajoutés (non nécessaires à C2)** : `GenerateurEcritureComptable`, `ComptabiliteStockService`/
`ComptabiliteStockApplicationService` (C5, C6, lisent le mapping), `ComptabiliteParametrageRestController` (C4),
`TypeTvaJdbcAdapter`, `TypeTvaApplicationService`, `TvaTypesRestController`, `FacturationDomainService`,
`ForfaitAbsenceJdbcAdapter` (Facturation et Absences : lecteurs observés, jamais modifiés par C2 ; `/tva-types` reste à
la Facturation).
Tests existants conservés : `ComptabiliteRestControllerIntegrationTest` (mapping, centre, rôles),
`ComptabiliteStockIntegrationTest`, `JournauxDuMappingTest`, `ParametrageSoupleTest`, `GenerateurEcritureComptableTest`.

## Bugs connus

`.ddd/safety-net/known-bugs.md`, section « Contexte Comptabilité — slice C2 » : KB-CPT-14 à KB-CPT-23 (dont **KB-CPT-21,
`POST /regles-tva` en échec sur PostgreSQL, à corriger**).

## Lancer

Depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest='MappingComptableCharacterizationIntegrationTest,ReglesTvaCharacterizationIntegrationTest,MappingTvaConsommateursCharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(ordre aléatoire : ajouter
`'-DargLine=-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`). Mutation :
`CPFILE=<classpath> bash backend/scripts/pit-c2-mapping-tva.sh` (prérequis dans l'en-tête). Constat PostgreSQL 16 :
`bash backend/scripts/comptabilite-c2-pg16-constat.sh 55442 > constat.log 2>&1 &`.

## Questions ouvertes (décisions pour la conception sur place de C2)

1. **KB-CPT-21** : corriger l'identifiant de `POST /regles-tva` (généré en Java) dès C2 ? Et vérifier en production si
   une fonction `random_uuid()` existe (sinon la route n'a jamais fonctionné hors H2 : les règles viennent de
   `/tva-types`).
2. **Propriétaire de `tva_types`** : C2 garde `/regles-tva` et `ParametrageFiscalPort` à l'identique en attendant la
   modélisation de la Facturation (Q1) ; ou publier dès C2 une lecture publiée « règle applicable à une date » consommée
   par la Facturation et les Absences (aujourd'hui trois lecteurs, deux modèles `RegleTVA` / `TypeTVA`) ?
3. **Taux** (KB-CPT-17) : borner 0 à 100 comme la Facturation, et que faire des règles > 100 éventuellement déjà en base
   (contrôle de production) ? **Exonération** (KB-CPT-19) : la Facturation ignore `exonere`, les Absences non ; aligner
   sur laquelle ?
4. **TVA collectée facultative** (KB-CPT-14) : vraie absence de TVA (pas de ligne) ou compte 44571 obligatoire ; et la
   réponse du PUT doit-elle refléter la relecture ?
5. **Règles de TVA versionnées** (KB-CPT-16, 22) : fermer la règle précédente à l'ajout, refuser chevauchements,
   doublons et fin < début ; exposer `id`/`actif` ; ajouter la modification et la désactivation ?
6. **PUT du mapping** (KB-CPT-15) : ne contrôler que ce qui change ? permettre le retour aux défauts du stock ? ordre
   stable des opérations ?
7. **Cache** (KB-CPT-20) : invalider `tva-types-list` à l'écriture de `/regles-tva`, ou supprimer le cache de lecture ?
8. **Autorisations** (KB-CPT-23) : la secrétaire lit comptes et journaux mais pas le mapping : à conserver ?
   **SUPERADMIN** bloqué par `ROLE_SCOPE` (KB-CPT-10) : à conserver ?
9. Compte d'article « compte du mapping » : le contrôle d'usage de C1 voit déjà `articles.compte_stock/compte_charge`
   (KB-CPT-13a) ; le mapping, lui, ne vérifie pas que le compte d'un article reste dans le plan : à ajouter à C2 ou à
   C6 ?
