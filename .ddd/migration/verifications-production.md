---
status: proposed
date: 2026-10-12
sources: fiches gmao-g1 à g5 ; .ddd/safety-net/known-bugs.md (KB-GMAO-09, 21, 27, 33, 34, 52 à 68) ; revue .ddd/verify/gmao-g4-g5-review.md
---
# Vérifications de production G1-G5 (lecture seule)

À exécuter **par vous** sur la production ou, mieux, sur une copie récente. Aucune requête ci-dessous n'écrit : uniquement des `SELECT`.
Vérifiez que chaque requête commence bien par `SELECT` avant de l'exécuter ; ouvrez la session en lecture seule si possible
(`SET default_transaction_read_only = on;` dans la session psql). Aucun identifiant ni donnée réelle n'est donné ici : notez
seulement les résultats (comptages, noms de colonnes), pas les données de patients.
Schéma supposé : `public` (adaptez si besoin). Les noms de colonnes suivent la stratégie de nommage de Spring Boot (`snake_case`).

## 1. Colonne « tâches à effectuer » des plans (KB-GMAO-21)
```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'gmao_plans_maintenance'
  AND column_name IN ('tachesaeffectuer', 'taches_a_effectuer')
ORDER BY column_name;
```
- **Vérifie** : laquelle des deux colonnes existe (le legacy lit `tachesaeffectuer`, la migration V5 déclare `taches_a_effectuer`).
- **Attendu** : au moins `tachesaeffectuer` ; les deux colonnes est un résultat normal sous `ddl-auto: update`.
  Constat PG16 jetable (2026-10-12) : sur le schéma Flyway pur, seule `taches_a_effectuer` existe ; `tachesaeffectuer` n'apparaît qu'après un démarrage
  d'Hibernate en `update`. Avec `validate` et sans cette colonne, le démarrage échoue (legacy compris).
- **Si différent** : seulement `taches_a_effectuer` ou aucune des deux : le code des plans (G1) mappe `tachesaeffectuer` (comme le legacy) ;
  sans cette colonne, les plans ne se lisent ni ne s'écrivent (et `validate` refuse le démarrage). **Signalez-le avant déploiement.**
  Si les deux existent, comptez les lignes renseignées de chaque côté :
```sql
SELECT count(*) FILTER (WHERE tachesaeffectuer IS NOT NULL)     AS lignes_tachesaeffectuer,
       count(*) FILTER (WHERE taches_a_effectuer IS NOT NULL)   AS lignes_taches_a_effectuer
FROM gmao_plans_maintenance;
```
  (à lancer seulement si les deux colonnes existent, sinon l'erreur « colonne inexistante » est normale.) Des lignes dans la
  colonne `taches_a_effectuer` alors que le legacy écrit `tachesaeffectuer` sont à signaler : données possiblement divergentes.

## 2. Contraintes d'unicité de `gmao_equipements` (KB-GMAO-33)
```sql
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'public.gmao_equipements'::regclass AND contype IN ('u', 'p');

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'gmao_equipements' AND indexdef ILIKE '%UNIQUE%';
```
- **Vérifie** : l'unicité réelle du code. V5 déclare `code UNIQUE` (global) ; l'entité déclare (`centre_id`, `code`) sous le nom
  `uk_gmao_equipement_centre_code`.
- **Attendu** : la contrainte `uk_gmao_equipement_centre_code` sur (`centre_id`, `code`) ; une contrainte globale sur `code` seul peut
  aussi exister (héritée de V5).
- **Constat sur PostgreSQL 16 jetable (2026-10-12)** : sur le schéma Flyway pur, seule `gmao_equipements_code_key UNIQUE (code)` existe ; après
  `ddl-auto=update`, les deux contraintes coexistent et un même code dans deux centres est refusé par la base. C'est le résultat probable en production.
- **Si une contrainte globale sur `code` seul existe** : un même code dans deux centres est refusé en 500 par la base ; le
  nouveau code reproduit le comportement tant que la contrainte est là. Dites-le nous : décision à prendre (conserver, ou migration
  dédiée plus tard, jamais dans cette livraison). **Si `uk_gmao_equipement_centre_code` est absente** : signalez-le avant déploiement.

## 3. Doublons de code par centre, casse et espaces (KB-GMAO-09)
```sql
-- doublons exacts par centre (ne devrait rien renvoyer si la contrainte existe)
SELECT centre_id, code, count(*) AS n
FROM gmao_equipements GROUP BY centre_id, code HAVING count(*) > 1;

-- doublons « presque identiques » : casse ou espaces différents dans un même centre
SELECT centre_id, lower(btrim(code)) AS code_normalise, count(*) AS n, count(DISTINCT code) AS variantes
FROM gmao_equipements
GROUP BY centre_id, lower(btrim(code))
HAVING count(DISTINCT code) > 1;
```
- **Vérifie** : que le code est comparé exactement (le nouveau code reproduit le legacy : `g-01`, ` G-01`, `G-01 ` sont acceptés).
- **Attendu** : première requête vide ; seconde vide ou quelques variantes historiques.
- **Si différent** : première requête non vide = la contrainte d'unicité manque (voir point 2). Seconde non vide = variantes de
  casse/espaces déjà présentes : pas de blocage, comportement reproduit ; à nettoyer fonctionnellement plus tard si vous le souhaitez.

## 4. Équipements par statut et suppression logique (KB-GMAO-34, KB-GMAO-06)
```sql
SELECT statut, count(*) AS n, count(*) FILTER (WHERE deleted_at IS NOT NULL) AS dont_supprimes_logiquement
FROM gmao_equipements
GROUP BY statut ORDER BY statut;

SELECT count(*) AS supprimes_logiquement FROM gmao_equipements WHERE deleted_at IS NOT NULL;
```
- **Vérifie** : la présence de statuts qui n'existent plus dans les parcours (`DESACTIF`, `A_REFORMER`, `REFORME`...) et de lignes
  avec `deleted_at` non nul (aucune commande ne le pose).
- **Attendu** : uniquement des statuts connus (`EN_SERVICE`, `EN_MAINTENANCE`, `HORS_SERVICE`, `EN_ATTENTE_PIECE`, `REFORME`) ;
  `supprimes_logiquement` = 0.
- **Si différent** : des `DESACTIF` ou `A_REFORMER` : le nouveau code les lit selon le legacy mais ne sait pas les produire ;
  dites-nous le nombre. Des `deleted_at` non nuls : ils restent consultables et pilotables (comportement reproduit) mais absents de
  la liste ; toute écriture sur eux remet `deleted_at` à nul. Dites-nous le nombre avant déploiement.

## 5. Tarif des intervenants (KB-GMAO-27, KB-GMAO-26)
```sql
SELECT column_name, data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'gmao_intervenants' AND column_name = 'tarif_horaire_defaut';

-- valeurs avec plus de 2 décimales (impossible si l'échelle est 2)
SELECT count(*) AS tarifs_plus_de_2_decimales
FROM gmao_intervenants
WHERE tarif_horaire_defaut IS NOT NULL AND tarif_horaire_defaut <> round(tarif_horaire_defaut, 2);
```
- **Vérifie** : le type réel du tarif (`numeric(10,2)` attendu) et l'absence de valeur plus fine.
- **Attendu** : `numeric`, précision 10, échelle 2 ; comptage = 0.
- **Si différent** : une autre échelle (ex. 4) ou un comptage > 0 : le nouveau code aligne ses colonnes sur `numeric(10,2)` ;
  signalez-le, les arrondis de la valorisation à la clôture pourraient différer de l'historique.

## 6. Nature et tarif nuls des intervenants
```sql
SELECT count(*) AS total,
       count(*) FILTER (WHERE type IS NULL)                  AS nature_nulle,
       count(*) FILTER (WHERE type NOT IN ('INTERNE','EXTERNE')) AS nature_inconnue,
       count(*) FILTER (WHERE tarif_horaire_defaut IS NULL)  AS tarif_nul,
       count(*) FILTER (WHERE tarif_horaire_defaut = 0)      AS tarif_zero
FROM gmao_intervenants;
```
- **Vérifie** : les intervenants que la clôture d'intervention ne valorise pas (tarif nul ou 0 : aucune ligne de coût, KB-GMAO-31).
- **Attendu** : `nature_nulle` = 0 et `nature_inconnue` = 0 (colonne obligatoire, valeurs `INTERNE`/`EXTERNE`) ; `tarif_nul` peut être > 0.
- **Si différent** : une nature nulle ou inconnue fait échouer la lecture de l'intervenant (500) comme avant ; signalez le nombre.
  Un `tarif_nul` ou `tarif_zero` élevé n'est pas une anomalie, c'est l'information utile pour le métier.

## 7. `ddl-auto` de la production
Pas de SQL : lisez la configuration **de l'environnement de production** (variable `HIBERNATE_DDL_AUTO`, sinon valeur par défaut
du profil `prod` : `validate`). Le profil par défaut (`application.yml`) est `update`.
- **Vérifie** : si Hibernate peut modifier le schéma au démarrage (ce qui explique la double colonne du point 1).
- **Attendu** : `validate` en production.
- **Si `update`** : les colonnes manquantes sont créées automatiquement au démarrage, aucune ligne n'est supprimée ; mais le schéma réel
  peut différer de V5 (points 1 et 2). Notez la valeur et envisagez `validate` (décision hors de cette livraison).

## 8. Version Flyway et migrations (V34, V35, V36, V37, V38)
```sql
SELECT max(installed_rank) AS dernier_rang, max(version::int) AS version_courante
FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';

SELECT version, description, success, checksum
FROM flyway_schema_history
WHERE version IN ('34', '35', '36', '37', '38')
ORDER BY installed_rank;
```
Le numéro **36 a deux histoires** : l'**ancienne V36** (projection nouveau vers legacy, schéma `gmao`) a été supprimée du dépôt et n'a
jamais été déployée ; la **V36 actuelle** est une migration différente, `V36__gmao_taches_intervention.sql` (tâches d'intervention,
G4, additive et ré-exécutable), dont la description Flyway est **`gmao taches intervention`**. Même numéro, autre migration.
- **Vérifie** : la version Flyway courante, qu'aucune migration supprimée du dépôt n'a été appliquée, et l'état de la ligne 36.
- **Attendu en production** (G4 n'est pas encore déployé) :
  - **aucune ligne 34, 37, 38** ;
  - la ligne **35** (registre d'événements `event_publication`) peut exister ou être absente : absente = normal avant le premier
    déploiement, V35 sera appliquée au démarrage ;
  - la ligne **36** est **soit absente** (la V36 actuelle sera appliquée au premier démarrage du nouveau code : cas attendu),
    **soit présente avec la description `gmao taches intervention`** et `success = true` (déjà déployée) ;
  - version courante inférieure à 34 avant déploiement ; 35 ou 36 après.
- **Si la ligne 36 existe avec une autre description** (ancienne V36, projection) : ne la supprimez pas ici ; la validation Flyway
  échouerait au démarrage du nouveau code (checksum différent). Dites-le avant déploiement.
- **Si des lignes 34, 37 ou 38 existent** : même consigne, ne rien supprimer, le signaler.
- **Checksum** de la V36 actuelle : calculé par Flyway sur le fichier. Si la ligne 36 est déjà présente, comparez la valeur lue
  ci-dessus à celle que le nouveau backend annonce (`flyway info`, ou journal de démarrage) : elle doit être identique.
- Le nettoyage d'une base de **dev locale** est décrit (avec sa requête de contrôle sur la description) dans la section
  « Nettoyage de la base de dev » de `.ddd/migration/slices/gmao-g1-plans-maintenance.md` ; il n'est jamais à faire sur la production
  sans vous.

## 9. Publications d'événements non acquittées (après déploiement, à contrôler de temps en temps)
```sql
SELECT count(*) AS publications_incompletes, min(publication_date) AS la_plus_ancienne
FROM event_publication WHERE completion_date IS NULL;
```
- **Vérifie** : qu'aucun écouteur (journal, alerte Planification) n'est resté en échec. Pas de métrique : c'est le seul contrôle.
- **Attendu** : 0, ou quelques lignes très récentes (livraison en cours).
- **Si des lignes anciennes existent** : elles sont rejouées au prochain redémarrage du backend (`republish-outstanding-events-on-restart`) ; l'alerte
  Planification peut alors être émise une deuxième fois (livraison au moins une fois). Dites-nous le nombre si elles persistent après un redémarrage.

  La proposition de réforme (`REFORME_PROPOSEE`) a un identifiant déterministe : rejouée, elle n'est pas dupliquée ; et si son
  enregistrement durable échoue, l'erreur remonte, la publication reste incomplète ici et sera rejouée (rien n'est perdu en silence).

## 10. Tâches d'intervention : table vide avant V36 (G4)
```sql
SELECT count(*) AS taches FROM gmao_taches_intervention;
```
- **À exécuter AVANT** l'application de la V36 actuelle (ligne 36 absente au point 8). La table existe depuis V5 ; aucun code ne l'a
  jamais écrite.
- **Attendu** : 0. V36 ajoute des colonnes obligatoires (`centre_id`, `cree_le`) qu'elle renseigne depuis l'intervention ou `date_creation`.
- **Si > 0** : ne déployez pas ; notez le nombre (des lignes ont été créées hors application, leur statut et leur ordre seraient à revoir).
  Après la V36, ce même comptage est le nombre de tâches réelles, sans autre signification.

## 11. Habilitations de rectification et de réforme (KB-GMAO-52 et 32)
Les habilitations sont des rôles (`app_role.code`) attribués par `app_user_role`. La rectification exige désormais **`ADMIN` et
`GMAO_RECTIFICATION`** ; la réforme **`ADMIN` et `GMAO_REFORME`** ; la notification de proposition de réforme vise `GMAO_REFORME`.
```sql
SELECT r.code AS habilitation,
       count(DISTINCT u.id) AS utilisateurs_actifs,
       count(DISTINCT u.id) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM app_user_role ur2 JOIN app_role r2 ON r2.id = ur2.role_id
           WHERE ur2.user_id = u.id AND r2.code = 'ADMIN')) AS sans_admin
FROM app_user u
JOIN app_user_role ur ON ur.user_id = u.id
JOIN app_role r ON r.id = ur.role_id
WHERE u.active AND r.code IN ('GMAO_RECTIFICATION', 'GMAO_REFORME')
GROUP BY r.code
ORDER BY r.code;
```
- **Vérifie** : combien d'utilisateurs actifs détiennent l'habilitation, et combien n'ont pas `ADMIN` (ils perdent l'accès : 403).
- **Attendu** : `sans_admin` = 0 pour `GMAO_RECTIFICATION` (sinon ces personnes pouvaient rectifier avant, elles ne le pourront plus) ;
  `utilisateurs_actifs` > 0 pour `GMAO_REFORME` (sinon la notification de proposition de réforme n'a aucun destinataire).
- **Si `sans_admin` > 0** : décision métier avant déploiement (leur donner `ADMIN`, ou accepter la perte du droit) ; notez le nombre.
  Un centre sans détenteur de `GMAO_REFORME` ne recevra aucune proposition (le rôle se donne par l'écran Administration > Rôles).

## 12. Interventions par statut, supprimées logiquement (KB-GMAO-53, 54)
```sql
SELECT statut,
       count(*) FILTER (WHERE deleted_at IS NULL)     AS actives,
       count(*) FILTER (WHERE deleted_at IS NOT NULL) AS supprimees_logiquement
FROM gmao_interventions
GROUP BY statut
ORDER BY statut;
```
- **Vérifie** : la répartition par statut. Statuts connus : `PLANIFIEE`, `EN_COURS`, `TERMINEE`, `ANNULEE`, `EN_ATTENTE_VALIDATION`.
- **Attendu** : aucun autre statut (une valeur inconnue fait échouer la lecture). `EN_ATTENTE_VALIDATION` n'est atteint par aucune
  commande : il ne peut venir que d'une écriture directe en base ; ces interventions ne peuvent ni démarrer ni se clôturer (annulation
  et lignes de coût seulement). Des interventions supprimées logiquement existent peut-être : elles restent lisibles par leur
  identifiant, absentes des listes, et les pièces jointes y sont refusées (KB-GMAO-64).
- **Si `EN_ATTENTE_VALIDATION` > 0 ou statut inconnu** : signalez le nombre avant déploiement.

## 13. Pièces jointes déjà stockées et nouvelles règles (G5, KB-GMAO-59 à 61, 64)
Nouvelles règles : 3 formats (PDF, PNG, JPEG), 5 Mo au plus (5 242 880 octets), 50 pièces par intervention, nom assaini (150 caractères
au plus, sans séparateur de chemin ni caractère de contrôle), extension cohérente avec le format. Les pièces **existantes** ne sont
pas modifiées ni supprimées ; elles sont relues avec nom et type re-contrôlés (hors des trois formats : servies en
`application/octet-stream`). Ces requêtes ne lisent **jamais** le contenu (seulement `length(contenu)`, le nom et les types).
```sql
SELECT count(*)                                                                               AS total,
       count(*) FILTER (WHERE contenu IS NULL OR length(contenu) = 0)                         AS vides,
       count(*) FILTER (WHERE length(contenu) > 5242880)                                      AS plus_de_5_mo,
       count(*) FILTER (WHERE content_type IS NULL
                          OR content_type NOT IN ('application/pdf', 'image/png', 'image/jpeg')) AS type_hors_trois_formats,
       count(*) FILTER (WHERE nom ~ '[\\/<>:*?|"]' OR nom ~ '\.\.' OR nom ~ '[[:cntrl:]]'
                          OR nom ~ '^[[:space:].]' OR nom ~ '[[:space:].]$' OR length(nom) > 150) AS nom_a_assainir,
       count(*) FILTER (WHERE nom ~ '\.[A-Za-z0-9]{1,8}$'
                          AND lower(nom) !~ '\.(pdf|png|jpe?g)$')                              AS extension_hors_formats,
       count(*) FILTER (WHERE (lower(nom) ~ '\.pdf$' AND content_type <> 'application/pdf')
                          OR (lower(nom) ~ '\.png$' AND content_type <> 'image/png')
                          OR (lower(nom) ~ '\.jpe?g$' AND content_type <> 'image/jpeg'))       AS extension_et_type_incoherents
FROM gmao_documents_intervention;

SELECT i.statut, (i.deleted_at IS NOT NULL) AS supprimee, count(*) AS pieces
FROM gmao_documents_intervention d
JOIN gmao_interventions i ON i.id = d.intervention_id
WHERE i.statut IN ('TERMINEE', 'ANNULEE') OR i.deleted_at IS NOT NULL
GROUP BY i.statut, (i.deleted_at IS NOT NULL)
ORDER BY i.statut;

SELECT count(*) AS interventions_a_plus_de_50_pieces, max(n) AS maximum
FROM (SELECT intervention_id, count(*) AS n FROM gmao_documents_intervention
      GROUP BY intervention_id HAVING count(*) > 50) t;
```
- **Vérifie** : (1) les pièces existantes qui ne respecteraient pas les nouvelles règles ; (2) les pièces rattachées à des interventions
  terminées, annulées ou supprimées (lecture et téléchargement restent possibles ; ajout et retrait y sont désormais refusés, 422) ;
  (3) les interventions au-delà de 50 pièces (elles restent lisibles ; seul l'ajout est refusé).
- **Attendu** : aucun blocage ; ces comptages sont informatifs. `vides` > 0 ou `type_hors_trois_formats` > 0 est le signe de pièces
  téléversées avec l'ancien code (type déclaré par le client) : elles resteront téléchargeables, avec un type neutre.
- **Contrôle de signature optionnel** (la structure PDF/PNG/JPEG n'est vérifiable qu'avec l'en-tête ; cette requête ne lit que les
  **8 premiers octets** et ne renvoie qu'un comptage, jamais le contenu) :
```sql
SELECT count(*) FILTER (WHERE substring(contenu from 1 for 4) = '\x25504446') AS pdf,
       count(*) FILTER (WHERE substring(contenu from 1 for 8) = '\x89504e470d0a1a0a') AS png,
       count(*) FILTER (WHERE substring(contenu from 1 for 3) = '\xffd8ff') AS jpeg,
       count(*) AS total
FROM gmao_documents_intervention;
```
  `pdf + png + jpeg` inférieur à `total` : des pièces ne portent pas la signature d'un des trois formats ; elles ne sont jamais
  réécrites, seulement servies en `application/octet-stream`. Notez l'écart.
- **Si l'un des comptages est élevé** : dites-le, ce n'est pas bloquant mais le métier peut vouloir les revoir.

## Que nous rapporter
Un message court : le résultat de chaque point (OK, ou la valeur observée : colonnes présentes, nom des contraintes, comptages),
sans données de patients ni identifiants. Tant que les points 2, 4, 8, 10 et 11 ne sont pas confirmés, ne déployez pas G1-G5 en production.
