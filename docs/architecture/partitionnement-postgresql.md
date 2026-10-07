# Étude et plan — Partitionnement PostgreSQL et performance des données

> Statut : **étude + plan d'exécution** (aucun code modifié). Rédigée à partir du schéma, des entités JPA, des
> requêtes JDBC et du jeu de démonstration RENADIAL. **Aucune base de production n'a été mesurée** : les volumes
> ci-dessous sont des estimations à confirmer par la phase 0.

## 1. Verdict en trois lignes

1. **Le partitionnement n'est pas le premier levier.** Avec ~13 000 séances par an pour 17 centres (jeu RENADIAL), les
   tables métier restent à quelques centaines de milliers de lignes pendant des années : PostgreSQL les traite sans
   partitionnement **si les index existent**. Or ils manquent sur les tables les plus lues (§3).
2. **Deux tables en profitent réellement dès maintenant** : `audit_log` et `notification_evenement` (écriture continue,
   lecture par période, purge quotidienne par `DELETE` → gonflement de la table). Le partitionnement mensuel remplace la
   purge par un `DROP PARTITION` instantané.
3. **Ne pas partitionner `seances`, `factures`, `patients`, les volets médicaux** : la clé primaire (`id` seul) et les
   contraintes d'unicité (`UNIQUE(seance_id)`, `UNIQUE(center_id, numero_facture)`) obligeraient à toucher le code
   et à ralentir les recherches par identifiant, pour un gain nul à cette échelle. Seuils de réexamen au §6.

## 2. État des lieux

| Élément                     | Constat                                                                                                                                                                    |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tables                      | ~100 (62 entités JPA + tables JDBC : audit, notifications, planning, facturation, direction…)                                                                              |
| Gestion du schéma           | **Flyway n'est pas actif** : `ddl-auto: update` + `db/schema.sql` + `db/seed.sql`. Les scripts `db/migration/V*.sql` sont une référence documentaire (cf. mémoire projet). |
| Bases                       | H2 (`MODE=PostgreSQL`) en dev/tests, PostgreSQL 16 en production. H2 **ne sait pas partitionner**.                                                                         |
| Clés étrangères             | 5 seulement dans le schéma de base (liens par UUID, sans FK) : bonne nouvelle, rien ne bloque la partition.                                                                |
| Multi-centre                | Toute requête filtre `center_id` (règle AGENTS §2) : la sélectivité vient déjà de ce critère.                                                                              |
| Volumes (RENADIAL, 12 mois) | ~410 patients, ~13 000 séances, ~4 400 factures, ~3 700 règlements, ~3 900 biologies, ~3 600 administrations EPO/fer.                                                      |
| Rétention existante         | `audit_log` : 365 j (purge `DELETE` quotidienne) ; `notification_evenement` : 30 j (`NotificationPurgeScheduler`).                                                         |

### Projection de volume (hypothèse : 10 × RENADIAL, soit ~170 centres, 5 ans d'historique)

| Table                         | Lignes/an (≈)                                          | Lignes à 5 ans         | Verdict taille           |
|-------------------------------|--------------------------------------------------------|------------------------|--------------------------|
| `seances`                     | 130 000                                                | 650 000                | petite                   |
| `factures` / `facture_lignes` | 44 000 / 130 000                                       | 220 000 / 650 000      | petite                   |
| `stock_movements`             | 0,5 – 1 M (consommables par séance)                    | 2,5 – 5 M              | moyenne                  |
| `audit_log` (365 j glissants) | 1 – 5 M (toute écriture + lectures du dossier médical) | borné par la rétention | **moyenne, churn élevé** |
| `notification_evenement`      | 0,2 – 1 M                                              | borné (30 j)           | petite, churn élevé      |
| `lignes_ecriture`             | 0,3 – 1 M                                              | 1,5 – 5 M              | moyenne                  |

Règle empirique retenue : **un partitionnement se justifie au-delà de ~20 – 50 M de lignes ou ~10 – 20 Go par table**,
ou quand la **rétention par suppression** est le besoin principal. Aucune table métier n'approche ce seuil ; seules les
tables de journal ont un besoin de rétention.

## 3. Le vrai gain rapide : les index manquants

Le partitionnement n'accélère une requête que s'il permet d' **exclure des partitions** (« partition pruning »). Les
requêtes de l'application filtrent par `center_id` puis par période : c'est exactement ce qu'un bon index composite
sert,
sans changer le schéma. Constats sur le schéma de base :

| Table                                                                                           | Index existants                                                                   | Requêtes dominantes (code)                                                                                       | Manque                                                             |
|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `seances`                                                                                       | `(center_id, facture_id)` **seul**                                                | `center_id = ? AND patient_id = ?` (≈ 11 requêtes), périodes de planning/dashboard, `center_id IN (…)` direction | `(center_id, date_seance)`, `(center_id, patient_id, date_seance)` |
| `stock_movements`                                                                               | `(article_id, created_at)`, lot, séance, `(center_id, inventaire_id, created_at)` | mouvements par centre et période, tableau de bord stock                                                          | `(center_id, created_at DESC)`                                     |
| `lignes_ecriture`                                                                               | aucun (la FK vers `ecritures_comptables` n'indexe pas côté enfant)                | lecture des lignes d'une écriture, balance, grand livre                                                          | `(ecriture_id)`                                                    |
| `administrations_anemie`                                                                        | aucun                                                                             | observance EPO/fer par patient et période                                                                        | `(center_id, patient_id, date_administration)`                     |
| `resultats_analyses`, `observations_biologiques`                                                | aucun                                                                             | biologie/KDIGO par patient, indicateurs direction                                                                | `(center_id, patient_id, date_prelevement)`                        |
| `prescriptions_medicales`                                                                       | aucun                                                                             | prescription en vigueur (dernière par date)                                                                      | `(center_id, patient_id, date_prescription DESC)`                  |
| `volet_medical` / `volet_paramedical`                                                           | `UNIQUE(seance_id)` (utile)                                                       | bien servi                                                                                                       | —                                                                  |
| `factures`, `facture_reglements`, `ecritures_comptables`, `audit_log`, `notification_evenement` | **déjà** `(center_id, période)`                                                   | —                                                                                                                | couverts                                                           |

> Les index déclarés par `@Index` JPA s'ajoutent à ceux de `schema.sql` via `ddl-auto: update` ; le tableau ci-dessus
> reflète les deux sources. Vérifier en production avec `\d+ <table>` avant d'appliquer (§5, phase 0).

Index proposés (à créer `CONCURRENTLY` en production, voir §5) :

```sql
CREATE INDEX CONCURRENTLY idx_seances_center_date      ON seances (center_id, date_seance);
CREATE INDEX CONCURRENTLY idx_seances_center_patient   ON seances (center_id, patient_id, date_seance DESC);
CREATE INDEX CONCURRENTLY idx_stock_mvt_center_date    ON stock_movements (center_id, created_at DESC);
CREATE INDEX CONCURRENTLY idx_lignes_ecriture_ecriture ON lignes_ecriture (ecriture_id);
CREATE INDEX CONCURRENTLY idx_admin_anemie_patient     ON administrations_anemie (center_id, patient_id, date_administration DESC);
CREATE INDEX CONCURRENTLY idx_resultats_patient_date   ON resultats_analyses (center_id, patient_id, date_prelevement DESC);
CREATE INDEX CONCURRENTLY idx_obs_bio_patient_date     ON observations_biologiques (center_id, patient_id, date_prelevement DESC);
CREATE INDEX CONCURRENTLY idx_prescriptions_patient    ON prescriptions_medicales (center_id, patient_id, date_prescription DESC);
```

Index partiels envisageables (à valider par `EXPLAIN`) : séances non facturées
`(center_id) WHERE facture_id IS NULL` (écran de facturation), séances non validées du jour (planning).

## 4. Évaluation du partitionnement table par table

| Table                                         | Clé de partition candidate  | Décision                         | Raison                                                                                                                                                                                                                                            |
|-----------------------------------------------|-----------------------------|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `audit_log`                                   | `occurred_at` (mois)        | **Oui — phase 2**                | Append-only, lecture par `(center_id, occurred_at)`, purge quotidienne par `DELETE` (bloat, vacuum). `DROP` d'une partition = instantané. Pas de FK, pas de lookup par `id`.                                                                      |
| `notification_evenement`                      | `cree_le` (mois ou semaine) | **Oui — phase 2**                | Même profil, rétention 30 j. Table `notification_lecture` liée par identifiant d'alerte, sans FK.                                                                                                                                                 |
| `direction_alert_history`                     | `first_seen_at`             | Plus tard                        | Faible volume ; à regrouper avec la rétention si elle grossit.                                                                                                                                                                                    |
| `stock_movements`                             | `created_at` (trimestre/an) | **Conditionnel — phase 3**       | Append-only, mais lu par `article_id`, `lot_id`, `seance_id` (index globaux locaux à chaque partition) ; intérêt réel seulement > 20 M de lignes. Préférer l'index §3 d'abord.                                                                    |
| `ecritures_comptables` + `lignes_ecriture`    | `date_ecriture` (exercice)  | **Conditionnel — phase 3**       | Clôture d'exercice naturelle, mais la FK `lignes_ecriture → ecritures_comptables` impose de partitionner les deux de façon cohérente (clé `(id, date_ecriture)`). À réserver à un gros volume.                                                    |
| `seances`                                     | `date_seance`               | **Non**                          | PK `id` seul ; ≈ 68 fichiers y accèdent, dont de nombreux `WHERE id = ?` sans date → balayage de **toutes** les partitions. `UNIQUE(seance_id)` sur les volets, FK logiques depuis stock, bons de sortie, administrations. Gain nul < 5 M lignes. |
| `factures`, `facture_*`, `facture_reglements` | `date_facturation`          | **Non**                          | `UNIQUE(center_id, numero_facture)` ne contient pas la date (interdit en partition) ; volume faible.                                                                                                                                              |
| `patients`, `assure*`, référentiels           | —                           | **Non**                          | Petites tables, lues par `id`.                                                                                                                                                                                                                    |
| Partition par `center_id` (hash/liste)        | `center_id`                 | **Non, sauf besoin d'isolation** | Le filtre `center_id` est déjà servi par l'index de tête ; hash = surcoût de planification sans exclusion utile pour les requêtes direction (qui lisent plusieurs centres).                                                                       |

## 5. Plan d'exécution par phases

Chaque phase est livrable seule, réversible, et validée par mesure avant la suivante.

### Phase 0 — Mesurer (1 à 2 jours, aucun changement applicatif)

- Activer `pg_stat_statements` ; relever les 20 requêtes les plus coûteuses (temps total, appels) sur une semaine.
- Relever tailles et lignes : `pg_total_relation_size`, `n_live_tup`, `n_dead_tup`, et `pg_stat_user_indexes` (index
  jamais utilisés / manquants : `seq_scan` élevés sur `seances`, `stock_movements`…).
- `EXPLAIN (ANALYZE, BUFFERS)` des écrans lents : tableau de bord direction/centre, liste des séances, facturation,
  mouvements de stock, règlements, balance comptable.
- **Critère de sortie** : liste chiffrée « requête → temps → cause » ; confirme ou infirme le §3.

### Phase 1 — Index et requêtes (≈ 1 semaine)

1. Créer les index du §3 (`CONCURRENTLY`, hors transaction) via un script PostgreSQL `db/postgres/`; déclarer les
   mêmes index en `@Index` JPA / `schema.sql` pour que H2 et les tests restent alignés (règle AGENTS §10).
2. **Activer Flyway pour la production** (aujourd'hui inactif) : sans migrations versionnées, ni index ni partitions ne
   sont reproductibles. Passer `ddl-auto` à `validate` (ou `none`) en profil production : `update` ne sait ni créer ni
   maintenir une table partitionnée.
3. Corriger les requêtes détectées en phase 0 (tris sans index, `IN (…)` volumineux, `COUNT(*)` exhaustifs pour la
   pagination — remplacer par comptage estimé ou borné quand le total exact n'est pas affiché).
4. Tableau de bord : le calcul direction agrège en direct ; les mois clos sont déjà figés dans `direction_snapshot`.
   Étendre ce principe (agrégats pré-calculés ou vues matérialisées rafraîchies à la clôture de période) plutôt que
   d'agréger de l'historique à chaque ouverture. Les caches existants (règle AGENTS §6) restent la première défense.

- **Critère de sortie** : p95 des écrans cibles divisé par ≥ 2 sur la base de recette avec volume ×10 (§7).

### Phase 2 — Partitionner `audit_log` et `notification_evenement` (≈ 1 semaine)

Principe (PostgreSQL 16, partition déclarative par intervalle mensuel) :

```sql
-- Nouvelle table partitionnée (la clé de partition fait partie de la PK)
CREATE TABLE audit_log_p (LIKE audit_log INCLUDING DEFAULTS)
    PARTITION BY RANGE (occurred_at);
ALTER TABLE audit_log_p ADD PRIMARY KEY (id, occurred_at);
CREATE INDEX ON audit_log_p (center_id, occurred_at DESC);
CREATE INDEX ON audit_log_p (societe_id, occurred_at DESC);
CREATE INDEX ON audit_log_p (user_id, occurred_at DESC);
CREATE TABLE audit_log_default PARTITION OF audit_log_p DEFAULT;   -- filet de sécurité
-- une partition par mois, créée à l'avance
CREATE TABLE audit_log_2026_10 PARTITION OF audit_log_p
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
```

Bascule sans coupure longue : créer `audit_log_p` → copier l'historique par lots → dans une courte transaction,
`ALTER TABLE audit_log RENAME TO audit_log_old; ALTER TABLE audit_log_p RENAME TO audit_log;` → conserver
`audit_log_old` une semaine puis le supprimer.

Impact applicatif **minimal** : `AuditWriterService`, `AuditQueryService` et `NotificationJournalJdbcAdapter` sont en
JDBC pur, sans `@Entity` ; leurs requêtes (`INSERT`, filtres par `center_id` et période) ne changent pas. À modifier :

- `AuditWriterService.purgeOlderThan` / `NotificationJournalJdbcAdapter` : remplacer `DELETE … WHERE date < ?` par
  `DROP TABLE <partition>` des mois entièrement périmés (liste via `pg_inherits`), en conservant le `DELETE` comme repli
  sur H2 (pas de partition en dev).
- **Entretien des partitions** : tâche planifiée (comme `AuditScheduler`) qui crée les partitions des 3 prochains mois
  et détache/supprime les périmées ; alerte si une ligne tombe dans la partition `DEFAULT`. Alternative : extension
  `pg_partman` si disponible sur l'hébergement.
- Les questions de compatibilité : le script de partitionnement est **réservé à PostgreSQL** (dossier `db/postgres/`,
  jamais exécuté par `schema.sql` ni sur H2). En H2 la table reste simple ; le code n'a aucune branche de plus que le
  choix « DROP partition si PostgreSQL, DELETE sinon ».
- Pagination de l'écran d'audit : le `COUNT(*)` exhaustif (`AuditQueryService`) se limite au centre et à la période
  choisis ; garder le filtre de période obligatoire par défaut (30 j) pour bénéficier de l'exclusion de partitions.
- **Critère de sortie** : purge mensuelle < 1 s au lieu d'un `DELETE` de plusieurs millions de lignes ; plus de
  croissance du bloat ; écran d'audit inchangé fonctionnellement.

### Phase 3 — Partitions conditionnelles (seulement si le seuil est franchi)

Déclencheurs de la décision (à relever dans la phase 0 et à surveiller ensuite, §7) :

- `stock_movements` > 20 M de lignes **ou** > 10 Go : partition par trimestre sur `created_at`, PK `(id, created_at)`.
  Impact JPA : `StockMovementJpaEntity` a un `@Id` simple → soit entité en `@IdClass`, soit passage de la
  lecture/écriture
  en JDBC (cohérent avec le reste du module), soit conserver l'`id` unique applicatif sans contrainte (acceptable pour
  une table de journal). Décision à prendre à ce moment-là avec les chiffres.
- `ecritures_comptables`/`lignes_ecriture` > 20 M : partition par exercice, les deux tables ensemble.
- Archivage plutôt que partition pour les historiques froids (> 5 ans) : table d'archive + export, avec accès restreint.

### Phase 4 — Hors périmètre sauf nouveau besoin

Partition par centre/société, sharding, réplicas de lecture pour la direction : à n'étudier qu'au-delà de plusieurs
centaines de centres ou si un client exige l'isolement physique des données.

## 6. Seuils de réexamen

| Indicateur (par table)           | Seuil d'alerte | Action                                      |
|----------------------------------|----------------|---------------------------------------------|
| Taille totale                    | > 10 Go        | Étudier le partitionnement                  |
| Lignes                           | > 20 M         | Étudier le partitionnement                  |
| Lignes mortes / vivantes (bloat) | > 20 %         | Revoir purge/vacuum, partition si journal   |
| p95 d'une requête métier cible   | > 500 ms       | `EXPLAIN`, index, puis partition en dernier |
| Temps de purge ou de VACUUM      | > 5 min        | Partitionner la table concernée             |

## 7. Validation, tests et sécurité

- **Base de recette** : générer ×10 à ×50 le jeu RENADIAL sur un PostgreSQL 16 (le script `generate-renadial-seed.cjs`
  accepte déjà des paramètres de volume à étendre) pour comparer avant/après avec `EXPLAIN (ANALYZE, BUFFERS)`.
- **Tests** (règle AGENTS §7) : le projet n'a pas de Testcontainers, donc **aucun test automatisé ne tourne sur
  PostgreSQL**. Ajouter `testcontainers` (PostgreSQL 16) dans un profil Maven dédié (`-Ppostgres-it`) pour : créer les
  partitions, vérifier l'exclusion de partitions (`EXPLAIN` ne liste que le mois demandé), l'isolation par `center_id`,
  la purge par `DROP`, l'insertion hors partition (→ `DEFAULT`). Les tests H2 existants restent inchangés.
- **Multi-centre** : aucune de ces opérations ne contourne `center_id` ; l'isolation des données reste portée par le
  filtre applicatif et l'index de tête.
- **Rollback** : phase 1 = `DROP INDEX CONCURRENTLY` ; phase 2 = renommage inverse (l'ancienne table est conservée une
  semaine).
- **Traçabilité** : `docs/architecture/` (ce document) et, à la livraison de la phase 2, mise à jour du référentiel
  des règles de gestion pour la rétention (RG audit / notifications) ainsi que de `docs/client`.

## 8. Recommandation

1. **Maintenant** : phase 0 puis phase 1 (index + activation de Flyway en production + `ddl-auto: validate`). C'est le
   levier qui améliore tableau de bord, séances, factures, stock, règlements et comptabilité.
2. **Ensuite** : phase 2 (`audit_log`, `notification_evenement`) — gain net, risque faible, code JDBC peu impacté.
3. **Ne pas faire** : partitionner `seances`, `factures`, `patients` ni par centre tant que les seuils du §6 ne sont pas
   franchis ; le coût de complexité (clés composites, contraintes d'unicité, entités JPA) dépasse le bénéfice.
