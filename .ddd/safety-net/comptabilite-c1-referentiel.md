---
status: proposed
date: 2026-10-14
slice: comptabilite-c1-referentiel
sources: [.ddd/tactical/comptabilite.md §8 §10, ADR 0024, .ddd/safety-net/gmao-g2-intervenants.md (méthode), .ddd/safety-net/known-bugs.md (KB-CPT-01 à 13), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-CPT-009, 018, 019)]
---

# Filet de caractérisation — Comptabilité C1 « Référentiel comptable »

Refactoring **sur place** (ADR 0024) : mêmes tables, mêmes URLs, livraison en une fois. Aucun code de production
modifié, aucun seam appliqué.

## Périmètre exact

Préfixe `/api/v1/comptabilite`. Tables écrites : `comptes_comptables`, `journaux_comptables`, `comptes_payeurs` ; lue :
`centre_payeur`. Contrôles d'usage lus sur `mapping_comptable`, `modeles_piece`/`modeles_piece_lignes`,
`ecritures_comptables`/`lignes_ecriture`.

| Route                                                  | Rôles             | Contrôleur legacy                       | Comportement résumé                                                                                                                                                     |
|--------------------------------------------------------|-------------------|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /comptes` (`recherche`, `actifs`, `page`, `size`) | ADMIN, SECRETAIRE | `ComptabiliteParametrageRestController` | plan SCF de départ (20 comptes, complété des comptes du mapping) tant que le centre n'a rien enregistré ; sinon `comptes_comptables` trié par `numero` ; `size` 1 à 500 |
| `PUT /comptes/{numero}` (`{libelle, actif}`)           | ADMIN             | idem                                    | upsert ; enregistre tout le plan de départ à la 1re modification ; refus `COMPTE_UTILISE` si désactivation d'un compte utilisé                                          |
| `DELETE /comptes/{numero}`                             | ADMIN             | idem                                    | suppression physique ; `COMPTE_INTROUVABLE` > `COMPTE_UTILISE` > `COMPTE_AVEC_ECRITURES`                                                                                |
| `GET /journaux` (`page`, `size` ≤ 100)                 | ADMIN, SECRETAIRE | `ComptabiliteRestController`            | 5 journaux par défaut (VE, BQ, CA, AC, ST) tant que rien n'est enregistré ; découpage en mémoire                                                                        |
| `PUT /journaux/{code}` (`{libelle, actif}`)            | ADMIN             | idem                                    | upsert ; code normalisé en majuscules ; `JOURNAL_UTILISE` si désactivation d'un journal choisi pour une opération ou un modèle                                          |
| `DELETE /journaux/{code}`                              | ADMIN             | idem                                    | suppression physique ; `JOURNAL_INTROUVABLE` > `JOURNAL_UTILISE` > `JOURNAL_AVEC_ECRITURES`                                                                             |
| `GET /payeurs` (`recherche`, `page`, `size` ≤ 100)     | ADMIN             | `ComptabiliteParametrageRestController` | payeurs du centre par `nom, code` avec leur compte client (ou `null`)                                                                                                   |
| `PUT /payeurs/{payeurId}/compte` (`{compte}`)          | ADMIN             | idem                                    | compte du plan (actif) ; vide/blanc/null/absent = retrait (retour au compte client par défaut du centre) ; `PAYEUR_INTROUVABLE` avant tout                              |

Hors C1 (routes voisines, seulement utilisées comme points d'entrée ou de preuve) : `/mapping` (C2), `/modeles`,
`/pieces` (C4), écritures de ventes via `ComptabiliteUseCase.genererEcritureFacturation` (C5).

## Point d'observation et harnais

HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle +
`RoleScopeFilter` ajouté à la chaîne comme dans le filet G6, JPA/JDBC réels sur **H2**), plus lecture SQL des trois
tables pour les effets de bord. Aucun mock. Réutilise `GoldenMaster.verify(Path, …)` de `characterization.gmao.plans` ;
goldens JSON en sous-dossiers par classe.

- Harnais :
  `backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/referentiel/ReferentielCharacterizationSupport.java`
  (identités ADMIN/SECRETAIRE/autres rôles, deux centres + un centre sans donnée, 4 payeurs, scrubbing des UUID en
  alias).
- Tests (76) : `PlanComptableCharacterizationIntegrationTest` (28), `JournauxCharacterizationIntegrationTest` (20),
  `ComptesPayeursCharacterizationIntegrationTest` (17), `ReferentielConsommateursCharacterizationIntegrationTest` (11).
- Goldens (355 fichiers, ~1 par scénario observable, tous < 200 lignes) :
  `backend/platform/src/test/resources/characterization/comptabilite/referentiel/{plan-comptable,journaux,comptes-payeurs,consommateurs}/*.approved.json`.
  Approuver : `-DargLine=-Dcharacterization.approve=true` avec `-Dtest=<les 4 classes>` **explicitement** (jamais
  `*CharacterizationIntegrationTest` : cela approuverait aussi les goldens GMAO) ; revue humaine du diff obligatoire.
- Isolation : fixtures `99996100-…` ; nettoyage de toutes les tables du référentiel (et de leurs consommateurs) pour 3
  centres avant/après chaque test.

## Scénarios

| Domaine                                 | Scénarios verrouillés                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Plan SCF de départ (RG-CPT-018)         | 20 comptes triés ; page/taille bornées (0, négatif, 500+, hors limites) ; recherche numéro/libellé, casse, espaces, jokers littéraux ; `actifs` sans effet ; complément par les comptes du mapping hérité (`Compte 7061`) ; rien enregistré en lecture ; enregistrement complet à la 1re modification ; annulation du plan par un refus                                                                                                                                                          |
| Comptes — validations                   | numéro 1/20/21 caractères, minuscules/majuscules (deux comptes), tiret, point, soulignement, accent, espaces de bord rognés, espace interne ; libellé absent/vide/blanc/null, rognage, 150/151 caractères ; `actif` absent/null (500), chaîne et nombre coercés ; corps absent/illisible/tableau/vide/`text/plain` ; propriétés inconnues ignorées                                                                                                                                               |
| Comptes — usage                         | désactivation refusée si mapping (les 10 comptes par défaut), payeur, modèle ; désactivation permise si libre ou avec écritures ; suppression refusée si utilisé (prioritaire), avec écritures (même désactivé) ; libération du payeur lève la protection ; écritures d'un autre centre sans effet ; suppression physique, inconnu, déjà supprimé, mal formé, numéro rogné                                                                                                                       |
| Plan enregistré                         | liste sans le plan de départ, tri par numéro (alphabétique : `10` avant `5121`), actifs seulement, jokers `%` et `_` non échappés (KB-CPT-05), ligne hors format casse la liste (KB-CPT-06)                                                                                                                                                                                                                                                                                                      |
| Journaux (RG-CPT-009)                   | 5 par défaut dans l'ordre de déclaration ; pagination en mémoire (≤ 100) ; `recherche`/`actifs` ignorés ; enregistrement des défauts à la 1re modification ; code normalisé (minuscules, mixte, espaces), bornes `A`,`Z`,`0`,`9`, 10/11 caractères, caractères voisins (`@`, `[`, `:`) ; libellé 100/101 ; `actif` absent ; les 5 défauts protégés par le mapping ; protection levée quand le mapping change ; modèle ; écritures ; dernier journal supprimé = défauts réaffichés (KB-CPT-07)    |
| Payeurs (RG-CPT-019)                    | liste par nom/code avec compte ; pagination, taille 1 à 100 ; recherche code/nom (jokers non échappés) ; affecter / remplacer / partager un compte ; retrait par vide/blanc/null/`{}` (idempotent) ; compte hors plan de départ, désactivé, casse différente, compte du mapping hérité ; format (400) vs longueur 21 (400 `VALIDATION_ERROR`) ; `PAYEUR_INTROUVABLE` (inconnu, autre centre, passe avant la validation du compte) ; id illisible (500) ; compte hors plan en base listé tel quel |
| Isolation de centre                     | un centre ne voit que ses lignes (plan, journaux, payeurs) ; même numéro/code dans deux centres ; `centerId` ≠ session = 403 `ACCESS_DENIED` sur les 8 routes ; `centerId` propre accepté                                                                                                                                                                                                                                                                                                        |
| Autorisations                           | lectures ADMIN+SECRETAIRE (comptes, journaux), ADMIN seul (payeurs), écritures ADMIN ; INFIRMIER 403 `ACCESS_DENIED` ; MEDECIN seul, DIRECTION, SUPERADMIN 403 `ROLE_SCOPE` ; MEDECIN+SECRETAIRE lecture permise ; anonyme 403 sans corps                                                                                                                                                                                                                                                        |
| Routes absentes                         | GET d'un élément, POST, PATCH, DELETE de collection, PUT sans `/compte` : 500 `INTERNAL_ERROR` (KB-CPT-02)                                                                                                                                                                                                                                                                                                                                                                                       |
| Consommateurs (lectures du référentiel) | mapping : comptes actifs du plan de départ ou enregistré, journal actif (défauts tant que rien d'enregistré), ordre JOURNAL_INCONNU avant COMPTE_INCONNU ; modèle de pièce : journal avant compte ; saisie de pièce : plan et journal relus à la saisie ; écriture de ventes : compte du payeur, sinon compte par défaut, sinon compte patient, journal des ventes du mapping, compte du payeur lu sans contrôle du plan (KB-CPT-09)                                                             |
| Parcours                                | créer, modifier, désactiver, lister, supprimer pour comptes et journaux ; ajouter un payeur, lui affecter un compte (refus puis création du compte), le retirer                                                                                                                                                                                                                                                                                                                                  |

## Non-déterminisme

Aucune horloge ni aléa dans les réponses. `UUID.randomUUID()` n'alimente que `comptes_comptables.id`/
`journaux_comptables.id`, jamais lus ; les identifiants de fixtures sont fixes. Listes triées côté SQL ou dans l'ordre
de déclaration. Messages Spring/Jackson/SGBD des 500 masqués (`<message technique … non verrouille>`). Aucun seam (voir
`.ddd/safety-net/seams.md`).

## Mesures

- Suite (76 tests) : **3 exécutions vertes consécutives en ordre aléatoire**
  (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`), 0 flaky.
- PIT 1.30.0 (+ junit5-plugin 1.2.3, `backend/scripts/pit-c1-referentiel.sh`, tests = les 4 classes HTTP ci-dessus) sur
  `PlanComptableService`, `JournauxService`, `ComptesPayeursService`, `CompteComptable`, `Journal`, `JournalCode` :
  **103 mutants, 94 tués (91 %)**, couverture de lignes 160/165 (97 %), force des tests 91 %. Cible ≥ 70 % atteinte.
  Rapport : `backend/platform/target/pit-reports-c1/`. Les 9 survivants sont : 7 gardes de construction inatteignables
  par HTTP (code ou libellé `null`/trop long de `Journal` et `CompteComptable`, `JournalCode` `null`, sauts de
  `exigerActifs` pour numéro vide : les DTO les arrêtent avant ; seul un test unitaire de domaine, à écrire avec le
  nouveau code, peut les tuer) ; 2 équivalents (`planDeDepart` retrie une `TreeMap` déjà triée ;
  `enregistre ? List.of() : planDeDepart` sans effet observable).
- Couverture de branches par outil : non mesurée (pas de JaCoCo configuré), couverture de lignes PIT ci-dessus.

## Limites : ce qui N'EST PAS verrouillé

- Contraintes PostgreSQL réelles et Flyway (V32/V33) : H2 uniquement ; collation du tri `numero` (`10` < `4456` < `9` en
  ordre alphabétique) et `nom, code`, `ILIKE`/`LOWER` sur accents, contrainte `uq_compte_comptable_centre_numero`,
  longueur de colonnes, concurrence (deux PUT du même numéro, KB-CPT-13) ; rejeu PG16
  (`backend/scripts/gmao-pg16-rejeu.sh` adapté) à prévoir si l'on veut les figer.
- Comptes des articles (`articles`, RG-CPT-023) et leur absence dans la règle « utilisé » ; suppression d'un payeur
  laissant `comptes_payeurs` orphelin : lus dans le code, pas exécutés (KB-CPT-13).
- Texte exact des messages 500 (Spring/Jackson), en-têtes, performances, audit des écritures (le filtre d'audit n'est
  pas dans la chaîne MockMvc).
- Consommateurs : seul le **contrat de lecture** du référentiel est verrouillé (qui refuse quoi) ; le comportement
  propre du mapping (C2), des modèles/pièces (C4), des écritures de ventes et de règlements (C5), de la comptabilisation
  du stock (C6, lit les comptes du mapping et des articles), de l'export et des périodes (C3), des règles de TVA (C2)
  relève de leurs filets.
- Absences et Facturation : aucune lecture SQL directe des trois tables n'existe dans le code (grep) ; la seule lecture
  externe passe par `ComptabiliteService` (compte client du payeur) et `PiecesComptablesService`.
- Écran Angular et ses sélecteurs de comptes/journaux/payeurs : non rejoués.

## Fichiers legacy de C1

**Propres à C1 (ajoutés à `covered.json`)** : services de domaine `PlanComptableService`, `JournauxService`,
`ComptesPayeursService` ; objets-valeur `CompteComptable`, `Journal` ; ports `PlanComptableUseCase`, `JournauxUseCase`,
`ComptesPayeursUseCase`, `CompteRepositoryPort`, `JournalRepositoryPort`, `ComptePayeurRepositoryPort`, `PayeursPort`
(dans `bc-comptabilite`) ; `PlanComptableApplicationService`, `JournauxApplicationService`,
`ComptesPayeursApplicationService` ; `PlanComptableJdbcAdapter`, `ComptePayeurJdbcAdapter`,
`JournalComptableJpaAdapter`, `JournalComptableJpaEntity`, `JournalComptableJpaRepository`.

**Partagés avec C2 à C7 (NON ajoutés, accord à donner avant d'y toucher)** :

- `ComptabiliteParametrageRestController` : C1 (comptes, payeurs) avec C4 (modèles, pièces) ; C1 en retire 3 routes.
- `ComptabiliteRestController` : C1 (journaux) avec C2 (mapping, règles de TVA), C3 (écritures, clôture), C5 (rebuild),
  C6 (stock) ; C1 en retire 3 routes.
- `JournalCode` (VO, partagé avec écritures, mapping, modèles, opérations), `ComptabiliteService` et
  `PiecesComptablesService` (consomment `PlanComptableUseCase.exigerActifs`, `JournalRepositoryPort`,
  `ComptePayeurRepositoryPort` : à re-brancher sur les nouveaux ports de C1), `MappingComptableJpaAdapter`,
  `ModelePieceJdbcAdapter`, `EcritureComptableJpaAdapter` (`existsByCompte`, `existsByJournal` : lus par le contrôle
  d'usage), `bc-comptabilite/.../MappingComptablePort`, `ModelePieceRepositoryPort`, `EcritureComptableRepositoryPort`.
- Tests existants conservés : `ComptabiliteParametrageIntegrationTest`, `ComptabiliteRestControllerIntegrationTest`,
  `JournauxServiceTest`, `ParametrageSoupleTest`.

## Bugs connus

`.ddd/safety-net/known-bugs.md`, section « Contexte Comptabilité — slice C1 » : KB-CPT-01 à KB-CPT-13.

## Lancer

Depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest='PlanComptableCharacterizationIntegrationTest,JournauxCharacterizationIntegrationTest,ComptesPayeursCharacterizationIntegrationTest,ReferentielConsommateursCharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(ordre aléatoire : ajouter
`'-DargLine=-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`). Mutation :
`CPFILE=<classpath> bash backend/scripts/pit-c1-referentiel.sh` (prérequis dans l'en-tête du script).
