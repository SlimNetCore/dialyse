---
status: proposed
date: 2026-10-26
sources: check-arch, state.ts show, scan --incremental, tactical/stock.md, glossaries/stock.md, ADR 0045/0046 (+0013/0019), fiches slices S1–S8, known-bugs KB-STK-01..57, seams.md, covered.json, roadmap.md, verifications-production.md, RG 08-stock.md, code backend/contexts/stock + lecteurs SQL de la plateforme (commits b04f76f6, 0228109c)
---
# Vérification globale — contexte Stock (S1–S8, refactoring sur place, ADR 0024)

## Contrôles automatiques
- `check-arch` : **0 violation** (0 baseline, 0 nouvelle). `scan --incremental` : 0 cycle entre modules, aucun hub. Le scan raisonne par module : un seul module backend, donc il ne voit pas les tables partagées (voir la ligne M3).
- Domaine et application de `contexts/stock` (150 fichiers) : **aucun import Spring, JPA, `java.sql` ni Hibernate**, aucun setter public dans le domaine. Aucun `now()` ni `randomUUID` dans le domaine : l'horloge est injectée dans l'application.
- ArchUnit `FrontieresDesContextesTest` : interdit `domain.stock..` et `domain.article..` dans tout le dépôt (`:73-77`). La Séance passe uniquement par le langage publié (`:83`). Les tests n'ont pas été lancés ici, conformément à la consigne.
- Les correctifs de la revue S8 sont faits : 400 pour un article inactif à la validation (KB-STK-57, `SeanceDomainServiceTest.java:192`), clé dupliquée tolérée (`NotificationsDesPeremptionsLegacy.java:46-50`), horloge fixe (`PeremptionsPostgresContractIT.java:61`, `ControleDesPeremptionsCharacterizationIntegrationTest.java:76`).
- `seamAllowed: []` : aucun seam ouvert (`seams.md:31-35` : seam S8 fermé).

## Constats

| # | Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| M1 | major | tactique | **Violation.** L'agrégat `StockArticle`, présenté dans le modèle approuvé comme « cœur monétaire », **n'existe pas**. Les invariants T5 à T8 (restant ≥ 0, stock = Σ lots, FEFO, article inactif) sont codés dans les cas d'usage, et la boucle FEFO est dupliquée deux fois dans `GestionDesSortiesPourSeances`. Aucun ADR ni aucune fiche ne trace cet écart : les fiches S5, S6 et S6b citent encore `StockArticle`. | `GestionDesSorties.java:198-201` ; `GestionDesSortiesPourSeances.java:115-134` et `:222-235` ; `GestionDesReceptions.java:192-201` ; `domain/` sans `StockArticle` ; `tactical/stock.md:139`, `:428-431` ; `stock-s5-receptions-pmp.md:18` | Extraire `StockArticle` (ou au minimum un service de domaine `SortieFefo` pur avec ses tests T5 à T8) et faire déléguer les trois cas d'usage. Sinon, ADR « modèle transactionnel par script assumé » et mise à jour de la matrice T5 à T8 (tests réellement existants). |
| M2 | major | legacy | **Violation.** Le mode historique de la Séance (sortie immédiate) et l'aiguillage par centre ont été **retirés le 2026-10-24** sans décision tracée. L'ADR 0045 laisse « paramètre de bascule par centre » à trancher, la question Q1 du modèle est toujours ouverte, et la roadmap annonce encore « sauf le paramètre par centre ». Conséquence en production : sans pièce « sortie pour séances » préparée, les consommations deviennent des alertes `AUCUNE_PIECE` et le stock ne baisse plus, dans tous les centres le jour J. | `stock-s6b-sortie-pour-seances.md:159-164` ; commit `eaa76b31` ; `ADR 0045:59` ; `tactical/stock.md:526-528` ; `roadmap.md:116-117` | Faire confirmer la décision par l'utilisateur, puis l'inscrire dans l'ADR 0045 ou 0046, clore Q1 et corriger la roadmap. Ajouter le prérequis opérationnel à `verifications-production` (voir V5). |
| M3 | major | stratégique | **Violation (propriété des données).** La Séance lit en SQL les **nouvelles** tables du Stock (`consommations_de_seance*`, `sorties_pour_seances_lignes`) et les anciennes (`stock_movements`, `bons_sortie*`, `articles`). La Direction et le référentiel lisent `articles` et `lots`. L'indicateur de la roadmap « lectures SQL des tables du stock par d'autres contextes : cible 0 » n'est pas atteint et n'est mentionné nulle part. | `SeanceRestController.java:309-312`, `:343-349`, `:655-657` ; `DirectionIndicatorsQueryService.java:218`, `:234` ; `ReferentialRepositoryAdapter.java:97` ; `roadmap.md:108-109` | Recenser ces lecteurs dans la fiche S8 ou dans une slice S9. Publier une lecture `ConsommationsDeSeance.resume(seanceId)` et des indicateurs du stock en langage publié, puis ajouter une règle ArchUnit ou un grep de garde. |
| M4 | major | legacy | **Violation (filet / known-bugs).** KB-STK-46 à 49 sont cités par la fiche S6 (« corrections … à valider ») mais **absents de `known-bugs.md`**. KB-STK-51 est encore « À TRANCHER (S7) ». KB-STK-32 est « REPORTE (S7) », alors que S7 est `built` et que sa fiche n'en parle pas. KB-STK-50 est encore « à reproduire en S7 » alors que la décision est prise et construite. La fiche S6 garde aussi « création sans contrôle de facturation de la séance (reproduit, à trancher) ». | `known-bugs.md` (0 occurrence de KB-STK-4[6-9]) ; `known-bugs.md:908`, `:932-934` ; `stock-s6-sorties-manuelles.md:66-71` ; `stock-s7-inventaire.md:85-102` | Ajouter KB-STK-46 à 49 (décision et golden). Faire trancher KB-STK-51, KB-STK-32 et le contrôle de facturation à la création d'un bon manuel. Mettre à jour les statuts KB-STK-50 et 52 (CORRIGÉ ou CONFORME en S7). |
| M5 | major | legacy | `verifications-production` ne compte **que deux points Stock** (§ 29 et § 30). Il manque : les migrations **V38, V45 et V46**, le rôle **`DIRECTEUR_ADMINISTRATIF`** (créé par aucune migration ni aucun seed, `seed-production.sql` compris), la cohérence des chiffres (stock article = Σ lots, PMP, lots négatifs), l'absence d'inventaire en cours ou de recalcul pendant le déploiement, et la préparation des pièces (M2). La conclusion « Que nous rapporter » ne cite que le § 30. | `verifications-production.md:979-1063` ; `db/migration/V45__…sql`, `V46__…sql` ; grep `DIRECTEUR_ADMINISTRATIF` absent de `db/*.sql` ; `stock-s6b…md:124-125` | Ajouter un § 31 « Stock : bascule S1–S8 » selon la liste ordonnée ci-dessous, et le citer dans « Que nous rapporter ». |
| m1 | minor | tactique | Le modèle tactique n'est pas à jour : ligne 13 « journal technique, H10 / cible à valider » ; § 7.3 décrit encore les 4 appels directs `BonSortieUseCase` et `OperationsStockJdbcAdapter` comme « aujourd'hui » ; § 12 « à confirmer par caractérisation » ; questions ouvertes 1, 3 et 4 non closes ; § 7.2 « Notifications » donné comme consommateur de `LotProcheExpiration` ; 15 lignes vides. | `tactical/stock.md:40`, `:372-383`, `:496-510`, `:524-552`, `:366` | Passe de mise à jour « état construit » : clore les questions, corriger le § 7.3 et le catalogue, et renvoyer pour § 12 vers les KB. |
| m2 | minor | archi | La roadmap est incohérente avec l'état : S3 à S7 sont encore marquées `planned` dans le tableau, alors que la ligne 111 et `state.json` les donnent `built`. La ligne 130 est hors tableau et dit le modèle « proposé ». | `roadmap.md:91-96`, `:130` | Mettre à jour le statut et la fiche de chaque ligne. |
| m3 | minor | legacy | Il reste 11 entités JPA du stock et 10 `JpaRepository` **sans aucun usage** (`Article`, `Lot`, `StockMovement`, `BonCommande`, `BonReception`, `BonSortie`, leurs lignes, `Fournisseur`, `Emplacement`), gardées pour la H2 et pour `ddl-auto validate`. Seule `ArticleJpaEntity` a une date de retrait. | `ArticleJpaEntity.java:12-18` ; `persistence/repository/*JpaRepository` (0 référence) ; `application-prod.yml:11` | Une seule tâche datée (avant le 2026-12-31) : tables du stock dans `db/schema.sql` pour la H2, puis suppression des entités et repositories. |
| m4 | minor | archi | Des adapters du Stock vivent dans `platform` et importent l'intérieur du contexte (`domain.inventaire`, `application.sortie`). Aucune règle ArchUnit ne limite la plateforme au langage publié du Stock, comme c'est déjà fait pour la Séance. | `FeuilleComptageExcel.java`, `InventaireReportService.java`, `StockDemoDataSeeder.java` (imports `contexts.stock.domain/application`) ; `FrontieresDesContextesTest.java:83` | **Suggestion** : déplacer ces adapters dans `contexts/stock/infrastructure`, ou ajouter une règle « hors `contexts.stock` : seulement `publishedlanguage..` » avec une liste d'exceptions explicite. |
| m5 | minor | stratégique | Événements : `LotProcheExpiration` v1 n'a **aucun consommateur** (YAGNI documenté). Les 5 contrats d'intégration sont différés : la Comptabilité reste en **requête planifiée** sur `OperationsDeStock` (filtre sur `created_at`, différentiel 35 j), qui gère bien `RETOUR` comme une sortie négative. Ce choix est cohérent, mais le catalogue § 7.2 promet encore ces 5 contrats. | `OperationsDeStockJdbc.java:43-47` ; `tactical/stock.md:358-366`, `:557-562` | Indiquer « différé, pas de consommateur » sur chaque ligne du § 7.2. |
| m6 | minor | stratégique | Glossaire : 4 termes en `proposed` (Contrôle des péremptions, Alerte de péremption, Rappel hebdomadaire, Jour du centre). « Gestionnaire de stock = `PHARMACIEN` (hypothèse, à confirmer) » est marqué `validated`. | `glossaries/stock.md:37`, `:39-42` | Faire valider les 4 termes et lever l'hypothèse sur le gestionnaire de stock. |
| n1 | nit | legacy | `covered.json` : 228 des 284 entrées désignent des fichiers supprimés (GMAO, Comptabilité, Facturation, `bc-stock`). C'est sans effet sur le garde, mais cela brouille la lecture. | `covered.json` (vérifié par `statSync`) | Purger les entrées mortes. |
| n2 | nit | legacy | Restes : `stock_parametres` (V45) est orpheline depuis le retrait de l'aiguillage. `pit-c3-ecritures.sh:16` et `pit-f3-validation.sh:15` citent `bc-article`. Deux commentaires citent bc-stock et bc-article (`OrdonnanceNumeroGeneratorAdapter.java:14`, `EtapeBilanPreGreffe.java:18`). `.idea/compiler.xml` et `encodings.xml` déclarent encore `bc-stock` et `bc-article` (fichiers ignorés par git : recharger Maven). La fiche S6b contient un `` `r`n`r`n `` littéral et deux versions contradictoires de Q2. | fichiers cités ; `stock-s6b…md:77-84`, `:111` | Nettoyage groupé. Ne retirer la table `stock_parametres` que par une migration validée (aucun `DROP` sans accord écrit). |
| n3 | nit | tests | Les filets S6, S6b et S7 n'ont pas de fiche dans `.ddd/safety-net/` : ils sont décrits dans les fiches de slice. Les 17 suites `characterization/stock/referentiel` ne mentionnent les classes legacy qu'en Javadoc. | `ls .ddd/safety-net/stock-*` ; `*CharacterizationIntegrationTest.java` (en-têtes) | **Conserver les filets comme non-régression** (goldens `_nouveau_code`), garder `_ecarts-legacy/*.legacy.json` comme archive des écarts décidés, et mettre à jour les Javadoc (« écrit sur le legacy, rejoué sur contexts/stock »). |

## Cohérence vérifiée conforme
- ADR : 0045 `accepted`, 0006 et 0024 `approved`, 0029 et 0043 `accepted`. **À l'état `proposed`** : **0046** (points à valider : texte et destinataires de la notification, en particulier en kabyle, et § 30), ainsi que **0013** (Outbox) et **0019** (bugs legacy), qui sont transverses.
- Les références à `domain.stock`, `domain.article`, `BonSortieUseCase` et `ConsommationsDeSeanceLegacy` dans le code de production valent zéro (il ne reste que des commentaires, des tests et `.idea`). RG `08-stock.md` (49 RG-STK) ne cite aucune classe retirée.
- Séance → Stock : `ConsommationsDeSeance` et `CatalogueDArticles` (langage publié) uniquement. Comptabilité → Stock : `OperationsDeStockDuStock` (ACL) sur `publishedlanguage.OperationsDeStock` uniquement.

## Liste ordonnée des contrôles à exécuter AVANT la mise en production du Stock (à porter dans `verifications-production` § 31)
1. **§ 7** : `ddl-auto` de la production en `validate` ; les entités résiduelles (m3) doivent rester compatibles.
2. **§ 8 étendu** : Flyway appliqué jusqu'à **V46** ; présence de `stock_movements.saisi_le` (V38), des 5 tables V45 (`stock_parametres`, `sorties_pour_seances`, `sorties_pour_seances_lignes`, `consommations_de_seance`, `consommations_de_seance_imputations`) et de `centers.fuseau_horaire` (V46).
3. **Rôle `DIRECTEUR_ADMINISTRATIF`** : une ligne dans `app_role` (aucune migration ne la crée), avec la liste des comptes à qui l'attribuer.
4. **État au moment de la bascule** (comptage par centre) : aucun inventaire `EN_COURS`, aucun recalcul de PMP en cours (tâche en mémoire perdue au redémarrage, KB-STK-43). Déployer hors des heures de saisie.
5. **Prérequis S6b (M2)** : séances du jour J déjà validées sous l'ancien mode (sorties `bons_sortie.seance_id`) ou non ; une pièce « sortie pour séances » préparée par centre et par périmètre **avant la première séance** suivant le déploiement ; profils `PHARMACIEN`/`ADMIN` présents dans chaque centre.
6. **Point d'argent : stock article = Σ lots** (`articles.stock_quantity` contre `SUM(lots.quantite_restante)` par article et par centre). Écarts hérités (KB-STK-50, scénario « 48 au lieu de 46 » de S6) : à rapporter avant toute clôture d'inventaire.
7. **Point d'argent : lots** à `quantite_restante < 0` ou supérieure à la quantité initiale ; lots gérés sans numéro ni péremption.
8. **Point d'argent : PMP**. Articles dont `pmp_courant` diffère du dernier `stock_movements.pmp_apres` dans l'ordre `CHRONOLOGIE` (date de pièce, `saisi_le`, entrées avant sorties). Sorties à `pmp_apres` ou `prix_unitaire` nuls (`SANS_VALEUR`, KB-STK-01, KB-CPT-42/44). Rejeu `stock-pg16-rejeu.sh` sur une copie.
9. **Point d'argent : valorisation et Comptabilité**. Pour un centre et un mois clos, valorisation de période (début, entrées, sorties, fin) comparée aux écritures C6. Liste des `echecs` C6 avant bascule. Mouvements `RETOUR` à 0 au départ.
10. **§ 29** (S5) : bons de réception validés sans `lot_id`, cas ambigus, lots ≠ lignes.
11. **§ 30** (S8) : volume de la première alerte de péremption, fuseaux, destinataires.
12. **§ 9** : `event_publication` non acquittées (aucune attendue pour `LotProcheExpiration`, qui n'a pas d'écouteur).
13. Rejeux PostgreSQL 16 jetables : `stock-pg16-rejeu.sh`, `stock-s8-pg16-rejeu.sh <port≠5432>`.

## Sort des filets
Les conserver tous comme **non-régression** (`characterization/stock/referentiel`, 17 suites ; goldens `_nouveau_code` faisant référence) et les Javadoc doivent pointer vers `contexts/stock` (n3). Les `_ecarts-legacy` servent d'archive et ne sont pas rejoués.

## Corrections appliquées (2026-10-26, documentation seulement : aucun code de production touché, aucun build, rien de commité)

| Constat | Traitement | Où |
|---|---|---|
| M1 | ADR d'écart `StockArticle` rédigé, dette chiffrée (3 cas d'usage, 4 sites), critère de reprise ; **non refactoré** ; `proposed` | ADR 0048 ; renvoi en tête de § 9 du modèle |
| M2 | Décision utilisateur du 2026-10-25 tracée (pièce impérative, retrait du mode historique confirmé) ; ADR 0045 (points restants, décision 10), question 1 du modèle, roadmap, fiches S6b et S6 alignés ; prérequis ajouté au § 31 | ADR 0047 `accepted` ; `verifications-production.md` § 31.5 |
| M3 | Dette consignée avec fichier:ligne et solution (ports publiés, slice S9 proposée) ; **non codée** | `roadmap.md` § 1, « Dette : lectures SQL hors contexte » (7 lecteurs, dont `UsagesDuParametrageJdbc.java:71` de la Comptabilité, trouvé en plus) |
| M4 | KB-STK-46 à 49 ajoutés (reconstitués de la fiche S6, golden exact de 49 « à documenter ») ; statuts de 32, 50, 51, 52 corrigés d'après le code ; section « Décisions à prendre » D1 à D5 | `known-bugs.md` |
| M5 | § 31 : 13 contrôles ordonnés, `SELECT` seul ; rôle `DIRECTEUR_ADMINISTRATIF` : aucune migration ni seed ne le crée (vérifié) ; cité dans « Que nous rapporter » | `verifications-production.md` § 31 |
| m1, m2, m5 | Modèle tactique et roadmap alignés sur l'état construit (S3 à S7 `built`, questions closes, § 7.2 : 5 contrats « différé », § 7.3 réécrit, lignes vides supprimées) | `tactical/stock.md`, `roadmap.md` |
| m3, m4, m6, n1, n2, n3 | **Non traités** (hors périmètre de cette passe : code, migration ou validation métier) | voir « Suites » |

## État posé via `state.ts` (2026-10-26)
- `contexts.stock` : `approved` → **`architected`**. Il passera à `verified` après les décisions D1 à D5 et la validation des ADR 0046 et 0048.
- `slices.stock-s1…s5` et `stock-s8` : **`verified`**. `stock-s6b` : **`verified`** (M2 clos par l'ADR 0047).
- `stock-s6` et `stock-s7` restent **`built`** : D1 (contrôle de facturation d'un bon manuel) et D4 (KB-STK-32, règle d'inventaire du PMP) sont des décisions métier non prises ; elles ne pouvaient pas être tranchées ici.
- `phases.tactical` et `phases.verify` restent `in-progress` (autres contextes à modéliser).

## Verdict : **pass-with-actions**
Top 3 actions :
1. Faire trancher par l'utilisateur et tracer : retrait du mode historique et de la bascule par centre (M2), KB-STK-46 à 49, KB-STK-51, KB-STK-32 et le contrôle de facturation d'un bon manuel (M4) ; puis passer l'ADR 0046 en `accepted`.
2. Écrire le § 31 de `verifications-production` (liste ci-dessus, dont le rôle `DIRECTEUR_ADMINISTRATIF` et les points d'argent 6 à 9) et le citer dans « Que nous rapporter ».
3. Clore la dette de modèle : `StockArticle` / FEFO dans le domaine ou ADR d'écart (M1) ; lecteurs SQL des tables du stock hors contexte (M3) ; mise à jour de `tactical/stock.md` et de la roadmap (m1, m2).

Prochaine commande du pipeline (roadmap § 2, `state.ts` → `/ddd-tactical <context>`) : `/ddd-tactical planification`, qui consommera les événements GMAO. Patient et couverture, puis Séance, viendront en dernier.

## Suites (ce qui reste à l'utilisateur)

1. **ADR 0046** (S8) : valider le texte et les destinataires de la notification de péremption, **en particulier la traduction
   kabyle** (arabe et kabyle à faire relire par un locuteur), puis le passer en `accepted`.
2. **Glossaire du Stock** (`.ddd/strategic/glossaries/stock.md`) : valider les 4 termes `proposed` (Contrôle des péremptions,
   Alerte de péremption, Rappel hebdomadaire, Jour du centre) et **lever l'hypothèse « gestionnaire de stock = `PHARMACIEN` »**
   (aujourd'hui marquée `validated` à tort).
3. **Décisions de `known-bugs.md` (« Stock — Décisions à prendre »)** :
   - **D1** contrôle de facturation à la création d'un bon de sortie manuel sur une séance (reproduire ou refuser) ;
   - **D2** confirmer les corrections KB-STK-46 à 49 (et leur numérotation, reconstituée de la fiche S6) ;
   - **D3** code d'erreur d'un comptage négatif (KB-STK-51, facultatif) ;
   - **D4** règle d'inventaire du PMP (KB-STK-32), avec le comptable ;
   - **D5** confirmer la sortie supplémentaire (lots de la pièce puis FEFO) et les consommations imputées comme base du coût par séance.
4. **Valider l'ADR 0048** (écart `StockArticle`, `proposed`) : assumer les règles dans les cas d'usage et la boucle FEFO dupliquée,
   ou ouvrir la slice de reprise. Valider aussi l'ADR 0013 (Outbox) et l'ADR 0019 (bugs legacy), transverses, toujours `proposed`.
5. **Exécuter les contrôles de production** : `verifications-production.md` § 29 (S5), § 30 (S8) et **§ 31** (liste ordonnée,
   dont la création du rôle `DIRECTEUR_ADMINISTRATIF` et une pièce « sortie pour séances » préparée par centre avant la première
   séance), puis rapporter les résultats.
6. **À planifier (non traité ici)** : slice S9 « lectures publiées hors contexte » (`roadmap.md`) ; nettoyage m3 (entités JPA
   résiduelles du stock, avant le 2026-12-31) ; m4 (règle ArchUnit sur les adapters du Stock dans `platform`) ; n1 (purge de
   `covered.json`) ; n2 (restes `stock_parametres`, scripts `pit-*`, commentaires, `.idea`) ; n3 (Javadoc des suites de caractérisation).
7. Après ces décisions : repasser `stock-s6` et `stock-s7` à `verified` et `contexts.stock` à `verified` (`state.ts`), puis
   `/ddd-tactical planification`.
