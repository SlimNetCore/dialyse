---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de réaliser O4, 2026-11-09 ; décision O4-R3 déléguée), architecte]
context-scope: organisation (publishedlanguage), stock, plateforme
related: ADR 0107 (lot A, plan en lots), 0098 et 0104 (ports `*Fournis`), 0024 et 0075 (refactoring sur place), 0063 (Organisation) ; `.ddd/migration/slices/organisation-o4-lectures-publiees.md`, `.ddd/safety-net/known-bugs.md` (KB-ORG-09), `.ddd/migration/verifications-production.md` (§72)
---

# 0108 - Organisation O4, lot B : ports fournis au Stock, centre actif au sens strict (O4-R3)

> **AMENDÉ le 2026-11-09 par l'ADR 0115 (KB-ORG-09 corrigé)** : la variante stricte `actifsAuSensStrict()` est retirée, le port du Stock est
> `centresActifs()` (un centre sans état est actif), `centers.actif` devient NOT NULL DEFAULT TRUE (migration V56). Le texte ci-dessous décrit
> la décision d'origine (historique).

Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. Les points « à confirmer » le sont par les métiers / DPO au plus tard le **2026-12-15**.

## Contexte
Quatre lecteurs du Stock lisaient la table des centres en SQL : `CentresActifsLegacy` (centres du contrôle de nuit des péremptions),
`HorlogeDesCentresLegacy` (fuseau du centre), `OperationsDeStockJdbc.centres()` (centres de la comptabilisation de nuit) et
`InventairesJdbc.verrouillerLaPeriode` (verrou de la ligne du centre, clôture de période). Le Stock ne peut pas dépendre de
`contexts-organisation` : Organisation dépend de Stock (cycle Maven, ADR 0107, décision 3).

## Décisions
1. **Ports FOURNIS AU Stock**, déclarés dans le `publishedlanguage` du Stock, en termes du Stock : `CentresFournis` (`centresActifsAuSensStrict`,
   `tousLesCentres`, `fuseauHoraireDe`) et `VerrouDuCentreFourni` (`verrouiller`). Le Stock les consomme par des adapters sans SQL
   (`infrastructure/acl/centres/` : `CentresActifsFournis`, `HorlogeDesCentresFournie`) qui implémentent ses ports applicatifs d'origine
   (`CentresActifs`, `HorlogeDesCentres`, inchangés) ; `OperationsDeStockJdbc` et `InventairesJdbc` reçoivent le port en constructeur. Le défaut de
   fuseau du Stock (UTC) reste dans le Stock.
2. **Traducteurs minces de la plateforme** (`infrastructure/acl/stock/`) : `CentresDuStockParOrganisation` et `VerrouDuCentreDuStockParOrganisation`
   implémentent ces ports en appelant le langage publié d'Organisation. Aucun SQL. La plateforme dépend déjà des deux modules : AUCUN `pom.xml`
   modifié, donc aucun nouveau lien de module (graphe de l'ADR 0107 inchangé : `contexts-stock` ne dépend ni d'Organisation ni de la plateforme).
3. **Langage publié d'Organisation étendu** (noms métier) : `AnnuaireDesCentres.actifsAuSensStrict()` et `VerrouDuCentre.verrouiller(centre)`
   (implémentation `VerrouDuCentreJdbc`, SQL repris à l'identique du Stock). `actifs()`, `tous()` et `fuseauHoraireDe()` du lot A suffisent au reste
   (aucune lecture de salle ni de médecin dans le Stock : audit du SQL exact des quatre fichiers).
4. **O4-R3, décision déléguée : reproduire le comportement de CHAQUE consommateur, sans uniformiser.** Le Stock exclut aujourd'hui un centre dont
   `actif` est NULL (`actif = TRUE`), les autres consommateurs l'incluent (`COALESCE(actif, TRUE)`). Variante stricte explicitement nommée
   `actifsAuSensStrict()`, SQL EXACT du Stock ; variante large `actifs()` inchangée. Écart consigné : **KB-ORG-09**. Contrôle de données en
   production : §72 (SELECT seulement). Si 0 centre a `actif` NULL, la variante stricte est sans effet et le Stock pourra passer à la variante
   large (retrait de `actifsAuSensStrict`), sur décision des métiers / DPO.
5. **Filet AVANT migration** (vert sur les lecteurs SQL d'origine) : cas « état NULL exclu » ajouté à `CentresActifsLegacyTest` ;
   `LecturesDesCentresDuStockTest` (liste de TOUS les centres des opérations de stock, verrou de période sur centre connu et inconnu) ;
   `HorlogeDesCentresLegacyTest` couvrait déjà centre inconnu, fuseau absent, blanc, illisible. Après migration : mêmes assertions, seule la
   construction de l'objet testé change (`CentresFournisSurJdbcDeTest`, doublure de test portant les requêtes d'origine pour les contrats
   PostgreSQL du Stock). Parité sur le vrai langage publié : `CentresDuStockParOrganisationTest` (plateforme, H2). Contrat `AnnuairesContrat`
   étendu (mémoire et H2) : un centre NULL dans chaque variante.
6. **Liste blanche** `TableDeLOrganisationTest` : 22 -> 18 (les quatre fichiers ne lisent plus aucune table d'Organisation ; ils restent dans le Stock
   pour leurs propres tables).
7. **Seams** : deux créations de traducteurs dans la plateforme (hook de sécurité du legacy), inscrites dans `seamAllowed` avant création puis `seamAllowed`
   remis à `[]` ; tracées dans `seams.md`.

## Conséquences et risques
- La variante stricte est une dette consignée, pas un choix métier : à confirmer avant le 2026-12-15 (KB-ORG-09, §72).
- Le verrou du centre est pris dans la transaction de l'appelant : il suppose la même source de données et le même gestionnaire de transactions
  (c'est le cas : un seul `JdbcTemplate`). Le contrat PostgreSQL 16 de `VerrouDuCentre` n'existe pas (O4-R7 reste ouvert).
- `CentresFournisSurJdbcDeTest` duplique du SQL en code de TEST pour les contrats PostgreSQL du Stock : il remplace, dans ces contrats, le traducteur de la
  plateforme que le Stock ne peut pas importer.
