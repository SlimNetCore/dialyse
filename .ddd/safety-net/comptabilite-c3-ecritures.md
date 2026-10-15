---
status: proposed
date: 2026-10-15
slice: comptabilite-c3-ecritures
sources: [.ddd/tactical/comptabilite.md §2 §4 §8 §10 §11 §12 (C3, B1..B17, décisions du 2026-10-14), ADR 0024, .ddd/safety-net/comptabilite-c1-referentiel.md et comptabilite-c2-mapping-tva.md (méthode), .ddd/safety-net/known-bugs.md (KB-CPT-24 à 34)]
---

# Filet de caractérisation — Comptabilité C3 « Écritures, validation, export, clôture de période »

Refactoring **sur place** (ADR 0024) : mêmes tables, mêmes URLs, livraison en une fois. Aucun code de production
modifié, aucun seam appliqué (voir `.ddd/safety-net/seams.md`). Slice la plus risquée de l'argent : le filet fige ce que
C3 déplace (consultation, validation, export, clôture, numéro de pièce, équilibre, immutabilité) et utilise les
générateurs existants (C5 ventes et règlements, C6 stock, C4 pièces saisies) **seulement comme fabrique de données**.

## Périmètre exact

Préfixe `/api/v1/comptabilite`, contrôleur legacy `ComptabiliteRestController` (partagé avec C2, C5, C6). Tables :
`ecritures_comptables` + `lignes_ecriture` (statut), `periodes_comptables`.

| Route                                                                                                      | Rôle              | Comportement résumé                                                                                                                                                                                                                                                                  |
|------------------------------------------------------------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /ecritures` (`from`, `to` obligatoires ; `journalCode?`, `statut?`, `page=0`, `size=20`, `centerId?`) | ADMIN, SECRETAIRE | liste paginée sur la **date d'écriture** (bornes incluses), tri date décroissante puis numéro décroissant, `items/total/page/size`, une écriture = id, dates, numéro, libellé, statut, `totalDebit`, `saisie`, lignes (`compteSCF`, `libelleLigne`, `montantDebit`, `montantCredit`) |
| `POST /ecritures/{id}/valider` (`centerId?`)                                                               | ADMIN             | BROUILLON → VALIDEE ; VALIDEE → VALIDEE 200 sans effet ; EXPORTEE 422 `ECRITURE_EXPORTEE_IMMUABLE` ; inconnue ou d'un autre centre 422 `ECRITURE_INTROUVABLE` ; aucun contrôle de période ni de journal                                                                              |
| `GET /ecritures/export` (`from`, `to`, `journalCode=VE`, `format=SAGE100`, `centerId?`)                    | ADMIN             | CSV Sage 100 (`;`, CRLF, `ddMMyyyy` = date de pièce) de **toutes** les écritures du journal sur la période (brouillons et déjà exportées comprises) ; marque EXPORTEE les seules VALIDEE (effet de bord d'un `GET`) ; `format` ignoré                                                |
| `POST /periodes/cloturer` (`{centerId?, annee, mois, userId}`)                                             | ADMIN             | clôture irréversible une seule fois (422 `PERIODE_DEJA_CLOTUREE`) ; auteur = `userId` du corps ; réponse `{closed:true, periode:"2026-3"}`                                                                                                                                           |

Routes **voisines non caractérisées ici** : `/rebuild` (C5), `/stock/synchroniser` (C6, seulement son effet sur
l'immutabilité), `/modeles` et `/pieces` (C4, seulement comme fabrique de données), comptes, journaux, payeurs (C1),
`/mapping` et `/regles-tva` (C2). Écritures générées : C5 (ventes, règlements), C6 (stock) ; saisies et extournes : C4.

## Point d'observation et harnais

HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle +
`RoleScopeFilter` comme en C1/C2, JPA/JDBC réels sur **H2**), plus lecture SQL de `ecritures_comptables`,
`lignes_ecriture`, `periodes_comptables` pour les effets. Aucun mock. Les générateurs de production sont appelés comme
**vrais beans** (`ComptabiliteUseCase.genererEcritureFacturation/Reglement`, `/stock/synchroniser`, `/modeles` +
`/pieces`) ; les écritures « de cas limite » (brouillon, exportée, désordonnée, corrompue) sont insérées en SQL à
identifiants fixes.

- Harnais :
  `backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/ecritures/EcrituresCharacterizationSupport.java`
  (fixtures `99996300-…`, trois centres, un payeur, nettoyage de toutes les tables touchées avant/après chaque test ;
  normalisation des identifiants : `<ECRITURE_n>`, `<LIGNE_n>`, `<FACTURE_n>`, `<REGLEMENT_n>`, `<MODELE_n>`,
  `<UUID_GENERE_n>` ; lignes d'écriture triées par compte et montants car `@OrderBy("id")` suit des UUID aléatoires,
  sauf `goldenOrdreDesLignes` pour les fixtures à ids fixes ; fichiers d'export rendus ligne par ligne, avec tri des
  lignes pour les fichiers multi-écritures dont l'ordre n'est pas défini).
- Tests (102) : `ConsultationDesEcrituresCharacterizationIntegrationTest` (20),
  `ValidationDesEcrituresCharacterizationIntegrationTest` (13), `ExportComptableCharacterizationIntegrationTest` (26),
  `ClotureDePeriodeCharacterizationIntegrationTest` (19), `NumerotationDesPiecesCharacterizationIntegrationTest` (17),
  `EquilibreEtImmutabiliteCharacterizationIntegrationTest` (5), `ParcoursDesEcrituresCharacterizationIntegrationTest`
  (2).
- Goldens (335 fichiers `*.approved.json`, un par scénario observable, **tous < 200 lignes sauf
  `consultation/pagination_page_0_taille_par_defaut_200` (490 lignes : 20 écritures, taille par défaut)**) :
  `backend/platform/src/test/resources/characterization/comptabilite/ecritures/{consultation,validation,export,cloture,numerotation,equilibre-immutabilite,parcours}/`.
  Approuver : `-DargLine=-Dcharacterization.approve=true` avec `-Dtest=<les 7 classes>` **explicitement** (jamais un
  motif large : cela approuverait aussi les goldens GMAO, C1 et C2) ; revue humaine du diff obligatoire.

## Scénarios

| Thème                     | Scénarios verrouillés                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Consultation              | tri date puis numéro décroissants ; forme complète d'une écriture (lignes dans l'ordre de leur id, tiers) ; période sur la date d'écriture (et non la date de pièce), bornes incluses, inversée = vide ; pagination (taille par défaut 20, total, page au-delà, `size` 1000, `size`/`page` 0, négatifs, non numériques) ; journal normalisé (minuscules, espaces), vide/blanc ignoré, hors format 400, inconnu = liste vide ; statut exact (casse, inconnu, vide = 400) ; journal + statut ; paramètres absents/mal formés/inexistants ; isolation de centre (A, B, vide, `centerId` autre 403, propre, illisible, vide) ; rôles (ADMIN, SECRETAIRE, INFIRMIER, MEDECIN seul, DIRECTION, SUPERADMIN, anonyme) ; pièce saisie, extourne et écriture du système (`saisie`) ; journal et numéro en minuscules en base ; **lectures impossibles** (déséquilibre, une ligne, montant NULL, débit/crédit négatif, compte blanc, numéro blanc, statut inconnu, journal hors format, axe analytique sans code ou libellé) ; libellés nuls ou à rogner ; routes absentes                                             |
| Validation                | BROUILLON → VALIDEE (numéro, dates, lignes intacts) ; VALIDEE → VALIDEE ; EXPORTEE refusée et inchangée ; ni période ni journal contrôlés ; id inconnu, d'un autre centre, mal formé, nul ; `centerId` ; admin d'un autre centre ; rôles ; corps ignoré (JSON, texte) ; écriture déséquilibrée ou à une ligne en base ; pièce saisie ; relecture/réécriture des **axes analytiques** (normalisation code/libellé, axe sans deux-points, blanc → `NULL`) ; **B11 : aucune écriture générée (ventes, règlement caisse, stock, pièce saisie) n'est un BROUILLON**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| Export                    | fichier exact (en-tête, séparateur, CRLF final, octets, nom `journal-<CODE>-<from>.csv`) ; date de pièce vs date d'écriture ; bornes ; montants (virgule, grands, 0,01, entiers, zéro vide) ; échappement (`;`, `"`, retour à la ligne, accents), lettrage (8 premiers caractères du tiers, majuscules), libellé de ligne nul ; ordre des lignes d'une écriture ; **marquage** VALIDEE → EXPORTEE (état avant/après, lignes intactes) ; second export identique ; brouillons et exportées dans le fichier ; brouillon validé puis exporté ; valider après export (422) ; journal/période/centre ciblés ; période close ; 60 écritures sans pagination ; journal par défaut `VE`, minuscules, espaces, inconnu (fichier vide), blanc/tiret/11 caractères (400), vide = `VE` ; `format` ignoré ; dates absentes/mal formées/inexistantes, période inversée ; `centerId` autre 403 sans effet ; centres A, B, vide ; rôles ; une écriture illisible (déséquilibre, statut inconnu) fait échouer **tout** l'export sans rien marquer                                                                            |
| Clôture                   | nominal (réponse `2026-3`, ligne enregistrée, auteur, horodatage renseigné) ; re-clôture 422 sans changement ; mois futur 2030, ancien 2000, sans écriture ; mois et centres indépendants ; ligne ouverte existante mise à jour (pas de doublon) ; ligne close héritée sans auteur ; auteur libre non rogné, nombre coercé, 120/121 caractères ; `centerId` du corps autre 403 / propre / nul / illisible / en paramètre ignoré ; bornes d'année (1999, 2000, 3000, 3001) et de mois (0, 1, 12, 13, négatif) ; `userId` absent, nul, vide, blanc ; champs absents ou nuls (500), texte numérique coercé, décimale tronquée, propriétés inconnues, corps JSON illisible, tableau, `null`, absent, `text/plain` ; rôles ; routes de réouverture et de liste absentes ; **effets** : ventes et règlements du mois clos refusés (`PERIODE_CLOTUREE`, rien créé), veille et lendemain ouverts, facture et règlement déjà comptabilisés rendus tels quels, autre centre libre, validation et export du mois clos possibles, saisie de pièce refusée puis acceptée le mois suivant, réception de stock « ignorée » |
| Numérotation (RG-CPT-010) | `<JOURNAL>-<année>-<6 chiffres>` à la suite à partir de 000001 ; année de la date d'écriture ; séquence par journal, centre, année ; plus grand + 1 avec trous ; numéro supprimé réutilisé ; **passé 999999 : doublons `VE-2026-1000000`** ; suffixe non numérique : tout repart à 000001 ; numéro hors motif ignoré ; **base qui accepte deux écritures de même numéro et de même source** ; génération sur source en double sans création ; idempotence par source quel que soit le journal ; facture déjà comptabilisée rendue telle quelle (montants ignorés) ; règlement identifié par son id ; refus (période close, déséquilibre) sans numéro consommé ; pièce saisie qui partage la séquence du journal                                                                                                                                                                                                                                                                                                                                                                                             |
| Équilibre (RG-CPT-003)    | 16 cas d'écritures de ventes (arrondi HALF_UP par ligne avant somme, demi-centime, écart d'un centime, TVA dérivée/négative/à 3 décimales, trois tiers refusé, montants nuls, avoir négatif, TTC < HT, grands montants) ; règlements (valeur absolue, zéro, demi-centime)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Immutabilité (RG-CPT-004) | exportée inchangée après validation refusée, réexport, clôture, régénération (comparaison SQL complète y compris `updated_at`) ; aucune route PUT/PATCH/DELETE ; **sorties de stock validées réécrites en place (B9), complément après export**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| Parcours                  | génération (2 ventes, règlement banque, règlement caisse) → consultation → validation sans effet → export ventes → statuts → réexport identique → export banque et caisse → validation refusée → clôture → seconde clôture refusée → ventes et règlements du mois clos refusés → avril ouvert → réexport du mois clos → état final ; second centre indépendant                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

## Non-déterminisme

`UUID.randomUUID()` des écritures et lignes générées (normalisés), `OffsetDateTime.now()` de `cloturee_at`/`created_at`/
`updated_at` (jamais exposés ni comparés : seul « renseigné ou non » est testé), clés d'objet triées (`Map.of`), lignes
triées par compte et montants dans les réponses d'écritures générées (ordre `@OrderBy id` aléatoire), lignes des
fichiers multi-écritures triées (aucun `ORDER BY` dans `findForExport`). Aucune fixture ne dépend du jour d'exécution
(dates fixes 2025-12-31, mars/avril/septembre 2026, 2000-01, 2030-12, 3000-12 ; « aujourd'hui » n'intervient dans aucune
assertion). Messages Spring/Jackson/SGBD des 500 masqués.

## Rapprochement avec B1..B17 (tactical §12)

| B                                                                             | Verdict                                                                                                                                                 | Verrouillé par                                          |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| B1 numéro sans verrou                                                         | **CONFIRMÉ** (absence de contrainte et doublons déterministes : > 999999, suffixe non numérique, numéro réutilisé) ; la **course** n'est pas déclenchée | `numerotation/*` (KB-CPT-24)                            |
| B2 idempotence sans contrainte unique                                         | **CONFIRMÉ** (la base accepte deux écritures de même source ; la génération n'en crée pas de troisième) ; course non déclenchée                         | `numerotation/doublons_*`, `idempotence_*`              |
| B3 P1 une erreur arrête la boucle                                             | non propre à C3 (C5)                                                                                                                                    | — non verrouillé                                        |
| B4 TTC ≠ HT + TVA                                                             | **CONFIRMÉ** au niveau du cas d'usage (422 `ECRITURE_DESEQUILIBREE`, rien créé, numéro non consommé) ; l'avertissement de l'écouteur n'est pas observé  | `equilibre-immutabilite/equilibre_ventes_*` (KB-CPT-32) |
| B5 TVA facultative                                                            | non propre à C3 (C2/C5, KB-CPT-14)                                                                                                                      | —                                                       |
| B6 règlement négatif = valeur absolue                                         | **CONFIRMÉ** (cas d'usage)                                                                                                                              | `equilibre_reglements_*`                                |
| B7 export `GET` qui modifie, reprend exportées et brouillons, `format` ignoré | **CONFIRMÉ**                                                                                                                                            | `export/*` (KB-CPT-25)                                  |
| B8 date de pièce dans le fichier                                              | **CONFIRMÉ** pour l'export (le filtre porte sur la date d'écriture) ; extourne (C4) non observée ; complément de stock daté du jour d'origine observé   | `export/export_periode_*`, `stock_4_*` (KB-CPT-26)      |
| B9 réécriture d'une écriture validée de sorties                               | **CONFIRMÉ** (propriété de C6)                                                                                                                          | `equilibre-immutabilite/stock_*` (KB-CPT-33)            |
| B10 `/rebuild`                                                                | non propre à C3                                                                                                                                         | — non verrouillé                                        |
| B11 `valider` sans effet                                                      | **CONFIRMÉ** (aucun brouillon généré)                                                                                                                   | `validation/*` (KB-CPT-27)                              |
| B12 auteur de clôture = corps                                                 | **CONFIRMÉ**                                                                                                                                            | `cloture/auteur_*` (KB-CPT-30)                          |
| B13 id du règlement deviné, mode toujours BANQUE                              | non propre à C3 (C5)                                                                                                                                    | —                                                       |
| B14 factures sans filtre de statut                                            | non propre à C3 (C5)                                                                                                                                    | —                                                       |
| B15 « aujourd'hui » en UTC                                                    | non propre à C3 (C6, `LocalDate.now(UTC)` du stock) ; non verrouillé                                                                                    | —                                                       |
| B16 code de modèle `_`, 367 jours                                             | non propre à C3 (C4, C6)                                                                                                                                | —                                                       |
| B17 écouteurs synchrones dans la transaction                                  | non propre à C3 (C5) ; **non verrouillé**                                                                                                               | —                                                       |

## Mesures

- Suite (102 tests) : **3 exécutions vertes consécutives en ordre aléatoire**
  (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`), 0 flaky.
- **PIT 1.30.0** (+ junit5-plugin 1.2.3, `backend/scripts/pit-c3-ecritures.sh`, tests = les 7 classes HTTP) sur
  `EcritureComptable`, `LigneEcriture`, `ComptabiliteService` (hors génération d'écritures),
  `EcritureComptableJpaAdapter`, `PeriodeComptableJpaAdapter`, `Sage100ExportAdapter`, `ComptabiliteRestController`
  (hors `/rebuild` et `/stock/synchroniser`), `ComptabiliteApplicationService` : **162 mutants, 145 tués (90 %)**, force
  des tests 92 %, couverture de lignes des classes mutées 334/395 (85 %). Cible ≥ 70 % atteinte. Rapport :
  `backend/platform/target/pit-reports-c3/`. Une première passe (127 tués, 78 %) a révélé les **axes analytiques**
  (écriture et relecture de `axes_analytiques`), les gardes de lecture de `LigneEcriture`/`EcritureComptable` et le
  libellé nul non verrouillés : ajoutés (`axes_*`, `lecture_*_en_base`, `libelles_nuls_*`). **17 non tués, tous
  justifiés** : (a) 5 équivalents ou doublons de garde : `ComptabiliteService:135` et `PeriodeComptableJpaAdapter:34`
  (le service **et** l'adaptateur contrôlent « déjà clôturée » : seule une course les distinguerait),
  `EcritureComptableJpaAdapter:88` (`code == null` jamais nul : `JournalCode` est non nul), `Sage100ExportAdapter:70`
  (libellé jamais nul : `LigneEcriture` le convertit en vide) ; (b) 4 gardes du constructeur inatteignables par l'API ou
  la base (`EcritureComptable:60/63/64` identifiant, centre, journal, dates non nuls par colonnes `NOT NULL` ;
  `LigneEcriture:29` identifiant non nul) et `EcritureComptable:108` (garde `marquerExportee` d'une écriture non
  validée : le service ne marque que les VALIDEE ; seul un test unitaire du nouveau domaine la tue) ; (c) 5 sans
  couverture : `existsByJournal`/`existsByCompte` (code mort depuis C1) et `Sage100ExportAdapter.formatId` (jamais lu) ;
  (d) 2 de C5 hors `/rebuild` : `ComptabiliteService:93-94` (compte débité par la facture pour un règlement, verrouillé
  par les goldens de C2 `reglement_*` mais hors des 7 classes mesurées ici).

## Lecteurs SQL externes des écritures (état au 2026-10-15)

| Lecteur                                                                                                    | Comment                                                                                                                                                                                                                           | Statut                                                                                       |
|------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **Comptabilité C1** (`contexts/comptabilite`, `UsagesDuParametrageJdbc`)                                   | `SELECT COUNT(*) FROM lignes_ecriture l JOIN ecritures_comptables …` (compte porté par une ligne) et `… FROM ecritures_comptables WHERE … journal_code` (journal porté) : refus `COMPTE_AVEC_ECRITURES`, `JOURNAL_AVEC_ECRITURES` | lecture seule, **à conserver tel quel** (colonnes `compte_scf`, `journal_code`, `center_id`) |
| **Facturation**                                                                                            | aucun : ne lit ni `ecritures_comptables` ni `periodes_comptables` (ne refuse pas une facturation sur mois clos : ADR 0009 ⚠, ouvert) ; elle **publie** `FacturationValideeEvent`, consommé par l'écouteur de C5                  | aucun lecteur SQL                                                                            |
| **Stock**                                                                                                  | aucun : `OperationsStockJdbcAdapter` lit les tables de stock pour la Comptabilité (sens inverse)                                                                                                                                  | aucun lecteur SQL                                                                            |
| **Absences**                                                                                               | aucun (ne lit que `tva_types`, voir C2)                                                                                                                                                                                           | aucun lecteur SQL                                                                            |
| **Direction / dashboard**                                                                                  | aucun (`DirectionAbsencesQueryService` : « comptabilisées » = absences, sans rapport) ; le pilotage direction de l'état des clôtures (ADR 0009) n'existe pas encore en SQL                                                        | aucun lecteur SQL                                                                            |
| **Angular** (`comptabilite-api.service.ts`, `comptabilite.store.ts`, `comptabilite-dashboard.component.*`) | 4 routes : `searchEcritures`, `validerEcriture`, `exporterJournal` (blob CSV), `cloturerPeriode` (envoie `userId`)                                                                                                                | contrat HTTP figé par les goldens ; écran non rejoué                                         |

Aucune vue ni procédure stockée ni job externe n'a été trouvé (recherche dans `backend/` et `frontend/src`) ; une
lecture hors dépôt (outil de BI, requêtes manuelles, import du logiciel comptable) reste **à confirmer avec
l'exploitation** avant de modifier les colonnes.

## Limites : ce qui N'EST PAS verrouillé

- **Course de numérotation et d'idempotence** (B1/B2) : deux générations ou deux clôtures simultanées ne sont pas
  déclenchées (non déterministe sans seam) ; seules l'absence de contrainte en base et les doublons déterministes sont
  verrouillés. Contraintes et fonctions PostgreSQL réelles (la suite est sur H2 ; le script
  `backend/scripts/comptabilite-pg16-rejeu.sh` existe pour un rejeu sur PostgreSQL 16 jetable, port ≠ 5432, **non lancé
  pour C3**).
- Ordre des écritures dans le fichier d'export (aucun `ORDER BY`) ; ordre PostgreSQL des listes à égalité de date et de
  numéro ; collation.
- Format exact attendu par le logiciel comptable (le code dit « indicatif ») ; codage d'ouverture du fichier (UTF-8 sans
  BOM constaté).
- `/rebuild` (B10), écouteurs P1/P2 (B3, B13, B14, B17), `LocalDate.now(UTC)` du stock (B15), extourne (B8 côté pièce),
  code de modèle (B16) : propriétés de C4, C5, C6.
- Texte exact des 500 (Spring/Jackson/SGBD), en-têtes autres que `Content-Disposition` et `Content-Type`, audit des
  écritures (filtre d'audit hors chaîne MockMvc), performances (export de plusieurs milliers d'écritures).
- Code mort constaté : `EcritureComptableJpaAdapter.existsByJournal/existsByCompte` (remplacés par
  `UsagesDuParametrageJdbc` en C1), `Sage100ExportAdapter.formatId`, `EcritureComptable.estSaisie` n'est lu que par la
  réponse.
- Écran Angular : non rejoué (pas de Playwright).

## Fichiers legacy de C3

**Propres à C3 (ajoutés à `covered.json`)** : `EcritureComptable`, `LigneEcriture`, `StatutEcriture` ; ports
`EcritureComptableRepositoryPort`, `PeriodeComptableRepositoryPort`, `ExportComptablePort` ;
`EcritureComptableJpaAdapter`, `PeriodeComptableJpaAdapter`, `Sage100ExportAdapter` ; entités
`EcritureComptableJpaEntity`, `LigneEcritureJpaEntity`, `PeriodeComptableJpaEntity` ; dépôts
`EcritureComptableJpaRepository`, `PeriodeComptableJpaRepository`.

**Déjà couverts par C1/C2 et réutilisés** : `ComptabiliteRestController` (4 routes C3 y restent avec `/rebuild` et
`/stock/synchroniser`), `ComptabiliteService` (valider, exporter, clôturer + génération), `ComptabiliteUseCase`,
`ComptabiliteApplicationService`.

**Partagés avec C4 à C6, NON ajoutés** (C3 ne les modifie pas ; à ajouter par la slice qui les touche, avec l'accord de
l'utilisateur identique à C1/C2) : `GenerateurEcritureComptable` (C5), `GenerateurEcritureStock`,
`ComptabiliteStockService`, `ComptabiliteStockApplicationService` (C6), `PiecesComptablesService`,
`PiecesComptablesApplicationService`, `PiecesComptablesUseCase`, `ComptabiliteParametrageRestController` (C4),
`OperationsStockJdbcAdapter`, écouteurs `FacturationValideeComptabiliteListener` et
`ReglementEncaisseComptabiliteListener` (C5). **Attention** : `EcritureComptableRepositoryPort`/
`EcritureComptableJpaAdapter` (numérotation `nextNumeroPiece`, `findBySourceId`) et `LigneEcriture` sont **lus par C4,
C5, C6** : leur modification par C3 doit rester compatible ou être suivie des slices voisines.
Tests existants conservés : `ComptabiliteRestControllerIntegrationTest`, `GenerateurEcritureComptableTest`,
`ComptabiliteStockIntegrationTest`, `ParametrageSoupleTest`.

## Bugs connus

`.ddd/safety-net/known-bugs.md`, section « Contexte Comptabilité — slice C3 » : KB-CPT-24 à KB-CPT-34.

## Lancer

Depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest='ConsultationDesEcrituresCharacterizationIntegrationTest,ValidationDesEcrituresCharacterizationIntegrationTest,ExportComptableCharacterizationIntegrationTest,ClotureDePeriodeCharacterizationIntegrationTest,NumerotationDesPiecesCharacterizationIntegrationTest,EquilibreEtImmutabiliteCharacterizationIntegrationTest,ParcoursDesEcrituresCharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(ordre aléatoire : ajouter
`'-DargLine=-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`). Mutation :
`CPFILE=<classpath> bash backend/scripts/pit-c3-ecritures.sh` (prérequis dans l'en-tête ; rapport
`backend/platform/target/pit-reports-c3/`).

## Questions ouvertes (décisions pour la conception sur place de C3)

1. **Export** (décision 3 déjà prise : exclure les exportées, dater l'export) : où enregistrer la date et l'auteur de
   l'export (colonne `exportee_le`, migration) ? un export sans rien de nouveau répond-il 200 + fichier réduit à
   l'en-tête ou 204/422 ? les **brouillons** restent-ils dans le fichier ? l'URL reste un `GET` à effet de bord (parité)
   ou un `POST` (écart d'URL, à éviter) ? le fichier doit-il garder la **date de pièce** ou passer à la date d'écriture
   (B8) ? un journal vide (`journalCode=`) doit-il continuer à prendre `VE` ?
2. **Numérotation** (décision 1 : verrou) : porté par C3 ou par le port `NumerotationDesPieces` de C4 ? comparer le
   suffixe **numériquement** (corrige le passage à 7 chiffres et la reprise à 000001 après un numéro non numérique :
   écart de comportement à valider) ? que faire des **doublons déjà en production** avant toute contrainte unique
   (requête SELECT à fournir dans `verifications-production.md` :
   `GROUP BY center_id, journal_code, numero_piece HAVING COUNT(*) > 1`, idem `(center_id, source_id)`) ?
3. **Validation** : garder VALIDEE → VALIDEE (200) et le 422 `ECRITURE_INTROUVABLE` pour une écriture d'un autre centre
   (pas 404/403) ? la validation doit-elle désormais refuser un **mois clos** ou un journal inconnu (aujourd'hui non) ?
   le brouillon existera-t-il un jour (saisie différée) ou la route reste-t-elle un no-op ?
4. **Clôture** : auteur = utilisateur de la session et `userId` du corps toléré mais ignoré (décision 12) ? réponse
   `periode:"2026-3"` à conserver à l'identique ? interdire les mois **futurs** et exiger la clôture des mois
   précédents ? exiger Facturation et Stock clos avant (ADR 0009, Q9) ? publier `PériodeComptableClôturée` maintenant ?
   les champs absents (`annee`, `mois`) répondent 500 aujourd'hui : 400 à la livraison ?
5. **Erreurs de paramètres** : `from`/`to` absents ou mal formés et `size` non numérique répondent 500 : passer à 400
   (écart) ? plafonner `size` (aujourd'hui illimité) ? statut vide ou en minuscules : 400 inchangé ?
6. **Données illisibles** : une écriture déséquilibrée, à une ligne, à statut ou axe invalide fait échouer toute la
   page / tout l'export : faut-il un contrôle de production préalable et/ou une lecture tolérante ? (requêtes à
   fournir : équilibre, nombre de lignes, statuts, minuscules en base, axes sans libellé.)
7. **Écritures à zéro et avoirs** (C5) : une facture à 0 crée aujourd'hui une écriture à deux lignes à 0,00 ; un avoir
   (montants négatifs) n'est jamais comptabilisé : à trancher à la modélisation de C5 ; le filet C3 verrouille l'état
   actuel.
8. **Immutabilité** : la réécriture d'une écriture **validée** de sorties de stock (B9) reste-t-elle permise tant que le
   mois est ouvert et que l'écriture n'est pas exportée ? (décision du 2026-10-09 : oui ; à reconfirmer maintenant que
   l'export va dater l'écriture.)
9. **Modèle** : `EcritureComptable` est partagée entre C3 (statuts, export), C4 (saisie, extourne), C5 (ventes,
   règlements) et C6 (stock, révision). Ordre de construction : C3 introduit l'agrégat complet avec ses seules commandes
   `valider`/`marquerExportee` et un **constructeur de reconstruction** pour que C4..C6 le réutilisent, ou C3 est-elle
   livrée avec un modèle de lecture seul + statut ? Les deux modèles (legacy et nouveau) coexistent-ils jusqu'à C6 sur
   les mêmes tables ?
10. **Propriétaire de `periodes_comptables`** en lecture par la Facturation (ADR 0009 : refuser une facturation sur mois
    clos) : port publié dès C3 ou plus tard ?
11. **Format Sage 100** : à confirmer avec la personne qui importe le fichier (en-tête, `;`, date, virgule, lettrage de
    8 caractères) avant de figer l'écart de la décision 3.
12. **Avertissement d'accès** : un lecteur hors dépôt (BI, requêtes manuelles, import du logiciel comptable) des tables
    d'écritures est-il en service ? (voir « Lecteurs SQL externes »).
