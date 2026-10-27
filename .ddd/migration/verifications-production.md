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

## 8. Version Flyway et migrations (V34 à V41)
```sql
SELECT max(installed_rank) AS dernier_rang, max(version::int) AS version_courante
FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';

SELECT version, description, success, checksum
FROM flyway_schema_history
WHERE version IN ('34', '35', '36', '37', '38', '39', '40', '41')
ORDER BY installed_rank;
```
Le numéro **36 a deux histoires** : l'**ancienne V36** (projection nouveau vers legacy, schéma `gmao`) a été supprimée du dépôt et n'a
jamais été déployée ; la **V36 actuelle** est une migration différente, `V36__gmao_taches_intervention.sql` (tâches d'intervention,
G4, additive et ré-exécutable), dont la description Flyway est **`gmao taches intervention`**. Même numéro, autre migration.
- **Vérifie** : la version Flyway courante, qu'aucune migration supprimée du dépôt n'a été appliquée, et l'état de la ligne 36.
- **Attendu en production** (G4 n'est pas encore déployé) :
  - **aucune ligne 34, 38** ; la ligne **37** (unicité des écritures par source, Comptabilité) n'existe qu'après le
    déploiement qui l'apporte (voir § 17) ;
  - la ligne **35** (registre d'événements `event_publication`) peut exister ou être absente : absente = normal avant le premier
    déploiement, V35 sera appliquée au démarrage ;
  - la ligne **36** est **soit absente** (la V36 actuelle sera appliquée au premier démarrage du nouveau code : cas attendu),
    **soit présente avec la description `gmao taches intervention`** et `success = true` (déjà déployée) ;
  - les lignes **39** (Facturation F2, § 18), **40** (Facturation F3, § 19) et **41** (Facturation F4, règlements, § 20) sont
    **absentes** avant le déploiement qui les apporte ; après déploiement, présentes avec `success = true` ;
  - version courante inférieure à 34 avant déploiement ; 35, 36 ou 41 après selon le lot déployé.
- **Si la ligne 36 existe avec une autre description** (ancienne V36, projection) : ne la supprimez pas ici ; la validation Flyway
  échouerait au démarrage du nouveau code (checksum différent). Dites-le avant déploiement.
- **Si des lignes 34, 37, 38, 39, 40 ou 41 existent avant le déploiement qui les apporte** : même consigne, ne rien supprimer, le signaler.
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

## 14. Numéros de compte comptable en minuscules et doublons après normalisation (Comptabilité C1, KB-CPT-04)

Le nouveau code enregistre les numéros de compte **en majuscules** (comme le code d'un journal). **SELECT seulement**,
comptages :

```sql
-- (a) numéros contenant au moins une minuscule (seront lus tels quels, mais plus jamais recréés)
SELECT count(*) AS numeros_avec_minuscules FROM comptes_comptables WHERE numero <> upper(numero);
-- (b) doublons après normalisation, par centre (ex. abc9 et ABC9 : deux comptes aujourd'hui)
SELECT count(*) AS groupes_en_double FROM (
  SELECT center_id, upper(numero) FROM comptes_comptables GROUP BY center_id, upper(numero) HAVING count(*) > 1) d;
-- (c) comptes de payeurs pointant vers un numéro en minuscules ou hors plan enregistré
SELECT count(*) AS payeurs_vers_minuscules FROM comptes_payeurs WHERE compte <> upper(compte);
```

- **Attendu** : 0 partout. Sinon, dites-le : on traite ces lignes à la main avant la livraison (le nouveau code ne les
  modifie pas).

## 15. Règles de TVA comptables : dates inversées, chevauchements, taux, fonction
`random_uuid` (Comptabilité C2, KB-CPT-16, 17, 21)

Le nouveau code refuse (400) l' **ajout** d'une règle dont la fin précède le début ou qui chevauche une règle active du
même centre et du même type ; il **ne modifie ni ne revalide** les règles déjà en base. **SELECT seulement**,
comptages :

```sql
-- (a) règles dont la date de fin précède la date de début (inapplicables à jamais)
SELECT count(*) AS dates_inversees FROM tva_types WHERE date_fin_validite IS NOT NULL AND date_fin_validite < date_debut_validite;
-- (b) paires de règles ACTIVES du même centre et du même type qui se chevauchent (bornes incluses, fin absente = ouverte)
SELECT count(*) AS paires_en_chevauchement
FROM tva_types a JOIN tva_types b
  ON a.center_id = b.center_id AND a.type_prestation = b.type_prestation AND a.id < b.id
 AND a.actif AND b.actif
 AND a.date_debut_validite <= COALESCE(b.date_fin_validite, DATE '9999-12-31')
 AND b.date_debut_validite <= COALESCE(a.date_fin_validite, DATE '9999-12-31');
-- (c) règles au taux supérieur à 100 (la Facturation refuse de les lire : GET /tva-types et la préparation de facturation échouent)
SELECT count(*) AS taux_superieur_a_100 FROM tva_types WHERE taux > 100;
-- (d) KB-CPT-21 : la fonction random_uuid existe-t-elle en production ? (attendu : aucune ligne ; sinon l'ancien POST fonctionnait)
SELECT proname FROM pg_proc WHERE proname IN ('random_uuid');
```

- **Attendu** : (a) 0, (b) 0, (c) 0, (d) aucune ligne. Un chevauchement ou une date inversée existant n'empêche rien
  (les lecteurs retiennent la règle commencée le plus récemment) mais le centre concerné ne pourra plus ajouter de règle
  dans les dates concernées : dites-le, on les corrige à la main ou on décide d'un rattrapage. Aucune donnée n'est
  modifiée par la livraison.

## 16. Écritures comptables C3 : lisibilité et horodatage d'export

À exécuter en lecture seule avant le basculement C3 ; ces requêtes renvoient uniquement des comptages et des
métadonnées.
Le nouveau repository refuse la lecture/export d'une écriture touchée qui n'est pas équilibrée, qui a moins de deux
lignes,
ou porte un statut inconnu. C3 réutilise `ecritures_comptables.updated_at` comme date de transition vers `EXPORTEE` (ADR
0026),
sans migration ; les écritures déjà exportées n'ont pas nécessairement une date d'export historique fiable.

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'ecritures_comptables'
  AND column_name IN ('updated_at', 'center_id', 'statut');

SELECT count(*) AS statuts_inconnus
FROM ecritures_comptables WHERE statut NOT IN ('BROUILLON', 'VALIDEE', 'EXPORTEE');

SELECT count(*) AS exportees_sans_updated_at
FROM ecritures_comptables WHERE statut = 'EXPORTEE' AND updated_at IS NULL;

SELECT count(*) AS ecritures_illisibles
FROM (
    SELECT e.id
    FROM ecritures_comptables e LEFT JOIN lignes_ecriture l ON l.ecriture_id = e.id
    GROUP BY e.id
    HAVING count(l.id) < 2
       OR sum(l.montant_debit) IS NULL OR sum(l.montant_credit) IS NULL
       OR round(sum(l.montant_debit), 2) <> round(sum(l.montant_credit), 2)
) fautives;
```

- **Attendu** : colonnes présentes ; `statuts_inconnus` = 0, `ecritures_illisibles` = 0. `exportees_sans_updated_at` est
  informatif : ces lignes restent exportées et ne seront pas réexportées ; ne pas remplacer leur date historique.
- **Si différent** : ne pas déployer C3 avant revue des comptages avec la comptabilité ; aucune correction automatique
  n'est
  effectuée par la slice.

## 17. Écritures comptables C4 : doublons historiques avant une éventuelle contrainte unique (B1/B2)

C4/C5/C6 prennent maintenant des verrous transactionnels pour les nouvelles attributions de numéros et les nouvelles
écritures par source. Aucun index unique ni changement de schéma n'est inclus ; ces comptages lecture seule sont à
vérifier
avant d'envisager une migration additive ultérieure.

```sql
SELECT count(*) AS groupes_numero_duplique
FROM (
    SELECT center_id, journal_code, numero_piece
    FROM ecritures_comptables
    GROUP BY center_id, journal_code, numero_piece
    HAVING count(*) > 1
) doublons;

SELECT count(*) AS groupes_source_dupliquee
FROM (
    SELECT center_id, source_id
    FROM ecritures_comptables
    WHERE source_id IS NOT NULL
    GROUP BY center_id, source_id
    HAVING count(*) > 1
) doublons;
```

- **Attendu avant toute contrainte unique** : les deux comptages = 0. Les verrous empêchent de nouvelles courses entre
  versions ayant ce code, mais n'effacent pas les doublons historiques et ne remplacent pas le nettoyage préalable.
- **Si un comptage est positif** : ne pas ajouter de contrainte ; transmettre uniquement les comptages, puis prévoir une
  revue/déduplication métier séparée. Ne pas renvoyer ni copier les identifiants réels dans les tickets.

### Unicité des écritures par source : migration V37 (2026-10-19)

`V37__comptabilite_unicite_ecriture_par_source.sql` crée l'index unique
`uq_ecriture_center_source (center_id, source_id)`
**seulement si** `groupes_source_dupliquee` (ci-dessus) vaut 0. Sinon elle n'écrit rien, se termine en succès avec un
`WARNING` (« V37 : n source (s) portent plusieurs écritures comptables… ») et ne bloque pas le démarrage. Après
déploiement :

```sql
SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'ecritures_comptables'
  AND indexname = 'uq_ecriture_center_source';
```

- **Attendu** : une ligne (index créé).
- **Si aucune ligne** : des doublons historiques existent ; rapportez le comptage `groupes_source_dupliquee`. Le
  traitement est
  une décision métier (extourne de l'écriture en trop, jamais de suppression) ; une migration ultérieure créera alors
  l'index. En attendant, le verrou applicatif continue d'empêcher tout nouveau doublon.
- La ligne **37** apparaît désormais dans `flyway_schema_history` après déploiement (le point 8 en tient compte).

## 18. Facturation F2 : exclusion de séance, absence déclarée et migration V39 (2026-10-20)

`V39__facturation_exclusion_de_seance.sql` ajoute deux colonnes **nullables** à `seances` (aucun `DROP`, aucun `DELETE`,
aucune
donnée modifiée) : `statut_avant_exclusion` et `exclusion_absence_id` (ADR 0031). Contrôles en lecture seule, après
déploiement :

```sql
-- 18.1 les deux colonnes existent et sont nullables
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'seances'
  AND column_name IN ('statut_avant_exclusion', 'exclusion_absence_id');

-- 18.2 séances ABSENT (avant la slice : exclusions de la Facturation sans motif, ou absences réelles) : aucune ne porte la mémoire d'exclusion
SELECT count(*) AS seances_absent, count(statut_avant_exclusion) AS avec_memoire
FROM seances
WHERE statut = 'ABSENT';

-- 18.3 séances exclues par la nouvelle version sans absence correspondante (attendu : 0)
SELECT count(*) AS exclues_sans_absence
FROM seances s
WHERE s.statut = 'ABSENT'
  AND s.statut_avant_exclusion IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM absence_patient a WHERE a.id = s.exclusion_absence_id AND a.center_id = s.center_id);

-- 18.4 mémoire d'exclusion périmée : séance qui n'est plus ABSENT mais garde la mémoire (attendu : 0)
SELECT count(*) AS memoire_perimee
FROM seances
WHERE statut <> 'ABSENT'
  AND (statut_avant_exclusion IS NOT NULL OR exclusion_absence_id IS NOT NULL);

-- 18.5 séances ABSENT sans absence de patient à la même date (exclusions d'avant la slice ; information, pas une anomalie de la nouvelle version)
SELECT count(*) AS absent_sans_absence_patient
FROM seances s
WHERE s.statut = 'ABSENT'
  AND NOT EXISTS (SELECT 1 FROM absence_patient a
                  WHERE a.center_id = s.center_id AND a.patient_id = s.patient_id AND a.date_seance = s.date_seance);

-- 18.6 absences référencées par une exclusion mais annulées ou rattrapées depuis (information)
SELECT a.statut, count(*) AS n
FROM seances s
JOIN absence_patient a ON a.id = s.exclusion_absence_id AND a.center_id = s.center_id
WHERE s.statut = 'ABSENT'
GROUP BY a.statut;
```

- **Attendu** : 18.1 deux lignes `is_nullable = YES` ; 18.2 `avec_memoire = 0` avant la première exclusion de la
  nouvelle
  version ; 18.3 et 18.4 = 0 en permanence. 18.5 compte les exclusions d'avant la slice (sans motif ni absence) : elles
  ne se
  réincluent pas ; les rapporter pour décider d'une régularisation métier (déclarer l'absence, ou repasser la séance).
- La ligne **39** apparaît dans `flyway_schema_history` après déploiement (le point 8 en tient compte).

## 19. Facturation F3 : numérotation, forfaits non définis et auteur de l'émission (migration V40, 2026-10-20)

La validation de facturation du nouveau code réserve la séquence de façon atomique, répond **409** si le numéro réservé
est
déjà pris (au lieu de 500), refuse (**422**) d'émettre tant qu'une séance éligible n'a pas de forfait, et enregistre
l'auteur
de l'émission (colonne `factures.emise_par`, migration additive V40). Ces requêtes sont des `SELECT` ; aucune ne modifie
la base.
Les adapter si le modèle de numérotation d'un centre n'est pas de la forme `...{SEQ}` en fin de numéro.

```sql
-- 19.1 colonne d'auteur ajoutée par V40 (attendu : une ligne uuid / YES)
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'factures' AND column_name = 'emise_par';

-- 19.2 séquences en retard sur les numéros existants : par centre et année de facturation, la séquence est absente ou plus
--      petite que le plus grand suffixe numérique des numéros déjà émis (attendu : aucune ligne ; sinon la première validation
--      de l'année répondra 409 jusqu'à ce que la séquence rattrape les numéros)
SELECT f.center_id,
       extract(year FROM f.date_facturation)::int AS annee,
       count(*) AS factures,
       max(substring(f.numero_facture FROM '([0-9]+)$')::bigint) AS plus_grand_suffixe,
       s.seq_value AS sequence_courante
FROM factures f
LEFT JOIN facture_sequence s
       ON s.center_id = f.center_id AND s.seq_year = extract(year FROM f.date_facturation)::int
WHERE f.numero_facture ~ '[0-9]+$'
GROUP BY f.center_id, extract(year FROM f.date_facturation), s.seq_value
HAVING s.seq_value IS NULL OR s.seq_value < max(substring(f.numero_facture FROM '([0-9]+)$')::bigint)
ORDER BY f.center_id, annee;

-- 19.3 doublons de numéro de facture dans un centre (attendu : aucune ligne ; la contrainte UNIQUE (center_id, numero_facture)
--      de la migration V1 doit les interdire)
SELECT center_id, numero_facture, count(*) AS n
FROM factures
GROUP BY center_id, numero_facture
HAVING count(*) > 1;

-- 19.4 contrainte d'unicité du numéro par centre (attendu : factures_center_id_numero_facture_key). Pas de contrainte
--      supplémentaire à ajouter tant que 19.2 et 19.3 n'ont pas été examinés en production.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'factures'::regclass AND contype = 'u';

-- 19.5 modèles de numérotation sans jeton de séquence {SEQ} ou {SEQn} (attendu : aucune ligne ; sinon toutes les factures du
--      centre porteraient le même numéro et la deuxième validation répondrait 409)
SELECT center_id, code_format
FROM facturation_settings
WHERE code_format !~ '\{SEQ[0-9]*\}';

-- 19.6 séances éligibles à la facturation sans forfait (ni surcharge, ni prise en charge avec forfait), par centre : la
--      validation les refuse (422) tant qu'on ne leur a pas donné de forfait (attendu : 0 ; sinon à régulariser avant la bascule)
SELECT s.center_id, count(*) AS seances_eligibles_sans_forfait
FROM seances s
WHERE s.statut IN ('VALIDEE', 'SIGNEE')
  AND s.facture_id IS NULL
  AND s.forfait_override_id IS NULL
  AND NOT EXISTS (SELECT 1
                  FROM prise_en_charge p
                  JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id
                  WHERE p.center_id = s.center_id AND p.patient_id = s.patient_id)
GROUP BY s.center_id
ORDER BY seances_eligibles_sans_forfait DESC;

-- 19.7 factures déjà émises à un forfait « Forfait non defini » à zéro (information : l'ancienne version les émettait)
SELECT center_id, count(*) AS lignes_forfait_non_defini
FROM facture_lignes
WHERE forfait_id IS NULL AND forfait_label = 'Forfait non defini'
GROUP BY center_id;

-- 19.8 après déploiement : factures émises sans auteur (information ; les factures d'avant V40 gardent NULL)
SELECT count(*) AS factures, count(emise_par) AS avec_auteur
FROM factures;
```

- **Attendu** : 19.1 une ligne `uuid / YES` ; 19.2, 19.3 et 19.5 aucune ligne ; 19.4 la contrainte de la migration V1 ;
  19.6 : rapporter le nombre par centre (une valeur non nulle bloque la validation du mois concerné jusqu'à
  régularisation).
  19.7 et 19.8 sont informatifs.
- La ligne **40** apparaît dans `flyway_schema_history` après déploiement (le point 8 en tient compte).

## 20. Facturation F4 : règlements, remboursements, mode et clé d'idempotence (migration V41, 2026-10-21)

Lecture seule (SELECT). À exécuter avant le déploiement (20.1 à 20.7) puis après (20.8, 20.9).

```sql
-- 20.1 avant V41 : les trois colonnes n'existent pas (aucune ligne attendue) ; après V41 : trois lignes, toutes YES
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_name = 'facture_reglements' AND column_name IN ('mode_reglement', 'reference_paiement', 'cle_idempotence');

-- 20.2 règlements sans facture dans le centre (orphelins : la route ne les voit pas, la Comptabilité les relit)
SELECT fr.center_id, count(*) AS reglements_orphelins
FROM facture_reglements fr
LEFT JOIN factures f ON f.id = fr.facture_id AND f.center_id = fr.center_id
WHERE f.id IS NULL
GROUP BY fr.center_id;

-- 20.3 FACTURES (pas leurs écritures : voir 20.10) dont la somme signée des règlements est négative ou dont un remboursement
-- n'a aucun règlement préalable (le nouveau code refuse ces remboursements ; les anciens restent en base)
SELECT f.center_id, f.numero_facture, SUM(fr.montant) AS total_regle,
       SUM(CASE WHEN fr.montant < 0 THEN fr.montant ELSE 0 END) AS total_rembourse
FROM facture_reglements fr
JOIN factures f ON f.id = fr.facture_id AND f.center_id = fr.center_id
GROUP BY f.center_id, f.numero_facture
HAVING SUM(fr.montant) < 0 OR (SUM(CASE WHEN fr.montant < 0 THEN fr.montant ELSE 0 END) < 0
                                AND SUM(CASE WHEN fr.montant > 0 THEN fr.montant ELSE 0 END) = 0);

-- 20.4 factures en trop-perçu (information : le trop-perçu reste accepté)
SELECT f.center_id, count(*) AS factures_en_trop_percu, sum(r.regle - f.total_ttc) AS total_trop_percu
FROM factures f
JOIN (SELECT facture_id, center_id, SUM(montant) AS regle FROM facture_reglements GROUP BY facture_id, center_id) r
  ON r.facture_id = f.id AND r.center_id = f.center_id
WHERE r.regle > f.total_ttc
GROUP BY f.center_id;

-- 20.5 doublons probables : même facture, même date, même montant et même auteur, plusieurs fois (envois répétés, KB-FAC-39)
SELECT fr.center_id, fr.facture_id, fr.date_reglement, fr.montant, fr.saisi_par, count(*) AS occurrences
FROM facture_reglements fr
GROUP BY fr.center_id, fr.facture_id, fr.date_reglement, fr.montant, fr.saisi_par
HAVING count(*) > 1
ORDER BY occurrences DESC
LIMIT 50;

-- 20.6 auteurs saisis (information : avant F4 le texte libre du corps, « system » par défaut ; ensuite l'identifiant de l'utilisateur)
SELECT CASE WHEN saisi_par ~* '^[0-9a-f-]{36}$' THEN 'identifiant d''utilisateur'
            WHEN saisi_par IS NULL THEN 'vide' ELSE 'texte libre' END AS nature, count(*) AS reglements
FROM facture_reglements
GROUP BY 1;

-- 20.7 règlements sans code (les règlements de reprise ont un code REP-…, les autres REG-…)
SELECT center_id, count(*) FILTER (WHERE code_reglement IS NULL) AS sans_code,
       count(*) FILTER (WHERE code_reglement LIKE 'REP-%') AS de_reprise
FROM facture_reglements
GROUP BY center_id;

-- 20.8 après déploiement : règlements par mode (les anciens règlements ont un mode NULL, comptabilisés en banque)
SELECT coalesce(mode_reglement, '(sans mode)') AS mode, count(*) AS reglements
FROM facture_reglements
GROUP BY 1
ORDER BY 2 DESC;

-- 20.9 après déploiement : l'index d'unicité de la clé d'idempotence existe et aucune clé n'est répétée pour une facture
SELECT indexname FROM pg_indexes WHERE indexname = 'ux_facture_reglements_cle_idempotence';
SELECT center_id, facture_id, cle_idempotence, count(*) FROM facture_reglements
WHERE cle_idempotence IS NOT NULL GROUP BY 1, 2, 3 HAVING count(*) > 1;

-- 20.10 avant déploiement : ÉCRITURES de remboursement déjà passées en valeur absolue (source = règlement négatif), à extourner
-- avec le comptable ; le nouveau code ne les réécrit pas (écriture de sens inverse seulement pour les nouveaux remboursements)
SELECT e.center_id, e.journal_code, e.numero_piece, e.date_ecriture, fr.montant AS montant_du_reglement
FROM ecritures_comptables e
JOIN facture_reglements fr ON fr.id = e.source_id AND fr.center_id = e.center_id
WHERE fr.montant < 0
ORDER BY e.center_id, e.date_ecriture;
```

- **Attendu** : 20.1 aucune ligne avant, trois lignes `YES` après ; 20.2 aucune ligne (sinon les lister, ils ne sont visibles
  d'aucun écran) ; 20.3 : rapporter le nombre (ces factures ont un total réglé incohérent à régulariser par le métier) ; 20.4 à 20.8
  informatifs (20.5 : transmettre le nombre de lignes, pour décider d'un nettoyage ; ne rien supprimer sans validation écrite) ; 20.9 une
  ligne puis aucune. **20.10** liste les écritures de remboursement passées en valeur absolue (sens d'encaissement) avant F4 :
  rapporter le nombre et les pièces, à extourner par le comptable (20.3 ne liste que les factures concernées).
- La ligne **41** apparaît dans `flyway_schema_history` après déploiement (le point 8 en tient compte).
- Les règlements comptabilisés en valeur absolue avant F4 (remboursements) restent tels quels dans la comptabilité : voir
  `known-bugs.md` § « Décisions F4 » pour l'écart sur l'argent et la régularisation à décider avec le comptable.

## 21. Facturation F5 : totaux enregistrés des factures et chiffres de la synthèse (aucune migration, 2026-10-21)

Lecture seule (SELECT). À exécuter avant le déploiement ; aucune migration. La synthèse mensuelle affiche désormais les totaux
enregistrés des factures (HT, TVA, TTC) au lieu du TTC recalculé par ligne : ces contrôles mesurent l'écart attendu.

```sql
-- 21.1 factures dont le TTC enregistré n'est pas HT + TVA (incohérence à signaler ; aucune ligne attendue)
SELECT center_id, numero_facture, total_ht, total_tva, total_ttc, total_ht + total_tva - total_ttc AS ecart
FROM factures
WHERE total_ttc <> total_ht + total_tva
ORDER BY center_id, date_facturation;

-- 21.2 écart entre les totaux enregistrés et la somme des lignes, par centre et par mois (la nouvelle synthèse suit les premiers)
SELECT f.center_id, TO_CHAR(f.date_facturation, 'YYYY-MM') AS mois,
       SUM(f.total_ht) AS ht_enregistre, SUM(l.ht_lignes) AS ht_lignes,
       SUM(f.total_ttc) AS ttc_enregistre,
       SUM(l.ht_lignes * (1 + f.tva_rate / 100)) AS ttc_ancienne_synthese,
       SUM(f.total_ttc) - SUM(l.ht_lignes * (1 + f.tva_rate / 100)) AS ecart_ttc
FROM factures f
LEFT JOIN (SELECT facture_id, SUM(line_ht) AS ht_lignes FROM facture_lignes GROUP BY facture_id) l ON l.facture_id = f.id
GROUP BY f.center_id, TO_CHAR(f.date_facturation, 'YYYY-MM')
ORDER BY f.center_id, mois;

-- 21.3 factures dont le HT enregistré diffère de la somme de leurs lignes (la part de TTC par ligne suppose l'égalité ; aucune ligne attendue)
SELECT f.center_id, f.numero_facture, f.total_ht, COALESCE(SUM(l.line_ht), 0) AS somme_lignes
FROM factures f
LEFT JOIN facture_lignes l ON l.facture_id = f.id
GROUP BY f.center_id, f.numero_facture, f.total_ht
HAVING f.total_ht <> COALESCE(SUM(l.line_ht), 0)
ORDER BY f.center_id, f.numero_facture;

-- 21.4 factures sans ligne (comptées maintenant dans les montants globaux de la synthèse ; aucune ligne attendue)
SELECT f.center_id, f.numero_facture, f.total_ht, f.total_ttc
FROM factures f
WHERE NOT EXISTS (SELECT 1 FROM facture_lignes l WHERE l.facture_id = f.id)
ORDER BY f.center_id, f.numero_facture;

-- 21.5 factures dont le HT enregistré est nul alors que des lignes existent (part de TTC par ligne mise à zéro ; aucune ligne attendue)
SELECT f.center_id, f.numero_facture, f.total_ttc
FROM factures f
WHERE f.total_ht = 0 AND EXISTS (SELECT 1 FROM facture_lignes l WHERE l.facture_id = f.id AND l.line_ht <> 0)
ORDER BY f.center_id, f.numero_facture;

-- 21.6 factures sans caisse (agence figée absente ou caisse introuvable) : colonne « Inconnu » de la synthèse
SELECT f.center_id, COUNT(*) AS factures_sans_caisse
FROM factures f
LEFT JOIN agence a ON a.id = f.agence_id_snapshot
LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id
WHERE ca.id IS NULL
GROUP BY f.center_id
ORDER BY f.center_id;
```

- **Attendu** : 21.1, 21.3, 21.4 et 21.5 aucune ligne (sinon les lister : elles expliquent un écart entre la synthèse et le détail) ;
  21.2 : transmettre les écarts par centre et par mois (c'est ce que la direction verra changer dans la synthèse imprimée) ;
  21.6 informatif.
- Aucune migration : rien à voir dans `flyway_schema_history`.


## 22. Facturation F6 : factures et règlements de reprise comptabilisés par erreur (aucune migration, 2026-10-21)

Depuis F6 (KB-FAC-67), le rebuild comptable exclut les factures de reprise des soldes d'ouverture (`numero_facture` commençant par
`REPRISE-`) et leurs règlements. Les écritures **déjà générées** pour ces pièces avant le déploiement sont à repérer (SELECT seulement) ;
leur extourne est à décider avec le comptable et n'est pas exécutée par le code.

```sql
-- 22.1 écritures de ventes (journal VE) dont la source est une facture de reprise
SELECT e.center_id, e.journal_code, e.numero_piece, e.date_ecriture, e.statut, e.updated_at, f.numero_facture, f.total_ttc
FROM ecritures_comptables e
JOIN factures f ON f.id = e.source_id AND f.center_id = e.center_id
WHERE f.numero_facture LIKE 'REPRISE-%'
ORDER BY e.center_id, e.date_ecriture, f.numero_facture;

-- 22.2 écritures de règlement dont la source est un règlement de reprise (auteur REPRISE sur une facture de reprise : critère exact du code)
SELECT e.center_id, e.journal_code, e.numero_piece, e.date_ecriture, e.statut, e.updated_at, r.code_reglement, r.montant, f.numero_facture
FROM ecritures_comptables e
JOIN facture_reglements r ON r.id = e.source_id AND r.center_id = e.center_id
JOIN factures f ON f.id = r.facture_id
WHERE f.numero_facture LIKE 'REPRISE-%'
  AND r.saisi_par = 'REPRISE'
ORDER BY e.center_id, e.date_ecriture, f.numero_facture;

-- 22.3 totaux par centre et par statut (exportées ou non : une écriture exportée vers le logiciel comptable demande une extourne, pas une suppression)
SELECT e.center_id, e.statut, COUNT(*) AS ecritures
FROM ecritures_comptables e
WHERE e.source_id IN (SELECT id FROM factures WHERE numero_facture LIKE 'REPRISE-%')
   OR e.source_id IN (SELECT r.id FROM facture_reglements r JOIN factures f ON f.id = r.facture_id WHERE f.numero_facture LIKE 'REPRISE-%' AND r.saisi_par = 'REPRISE')
GROUP BY e.center_id, e.statut
ORDER BY e.center_id, e.statut;

-- 22.4 encaissements REELS saisis dans la plateforme sur une facture de reprise (auteur différent de REPRISE) : a GARDER, ne pas extourner
SELECT e.center_id, e.journal_code, e.numero_piece, e.date_ecriture, e.statut, r.code_reglement, r.montant, r.saisi_par, f.numero_facture
FROM ecritures_comptables e
JOIN facture_reglements r ON r.id = e.source_id AND r.center_id = e.center_id
JOIN factures f ON f.id = r.facture_id
WHERE f.numero_facture LIKE 'REPRISE-%'
  AND COALESCE(r.saisi_par, '') <> 'REPRISE'
ORDER BY e.center_id, e.date_ecriture, f.numero_facture;

-- 22.5 modèles de numérotation et numéros existants commençant par REPRISE- hors reprise des soldes (ils seraient exclus de la comptabilité)
-- (a) modèles de numérotation (la saisie les refuse désormais en 400)
SELECT center_id, code_format, updated_at, updated_by
FROM facturation_settings
WHERE UPPER(TRIM(code_format)) LIKE 'REPRISE-%';
-- (b) factures REPRISE-% qui ne sont pas des factures de reprise (une facture de reprise n'a ni TVA ni autre auteur de règlement que REPRISE)
SELECT f.center_id, f.numero_facture, f.date_facturation, f.total_tva, f.total_ttc
FROM factures f
WHERE f.numero_facture LIKE 'REPRISE-%'
  AND (f.total_tva <> 0
       OR NOT EXISTS (SELECT 1 FROM facture_reglements r WHERE r.facture_id = f.id AND r.saisi_par = 'REPRISE'))
ORDER BY f.center_id, f.date_facturation;
```

- **22.5 attendu** : aucune ligne. Sinon ces factures ne sont pas comptabilisées par le code : changer le modèle de numérotation puis comptabiliser les ventes manquantes (`POST /api/v1/comptabilite/rebuild`) après décision du comptable.
- **Note pour le comptable** : seules les écritures de 22.1 (factures de reprise) et 22.2 (règlements dont l'auteur est `REPRISE`) sont des candidates à l'extourne, car ce sont exactement les pièces que le code n'envoie plus en comptabilité. La requête 22.4 liste les vrais encaissements (banque, caisse) saisis ensuite sur ces factures : ils restent comptabilisés par le code et **ne doivent pas être extournés**. 22.3 ne compte que les candidates (22.1 et 22.2).
- **Attendu** : aucune ligne si le rebuild n'a jamais été lancé sur des centres ayant fait une reprise de soldes ; sinon transmettre les totaux
  de 22.3 au comptable (la procédure d'extourne se décide avec lui : écriture inverse datée d'une période ouverte, ou suppression si la
  pièce n'a pas été exportée et la période est ouverte). Aucune écriture n'est modifiée par le déploiement.
- Aucune migration : rien à voir dans `flyway_schema_history`.
- Vérifier avant l'exécution les noms de colonnes (`journal_code`, `numero_piece`, `statut`, `updated_at` : date d'export d'une écriture exportée, ADR 0026, `source_id`) sur le schéma de production.

## 23. Facturation F7 : clôture mensuelle de facturation (migration V42, 2026-10-21)

V42 crée deux tables vides, sans toucher aucune donnée existante. **SELECT seulement.**

```sql
-- 23.1 les deux tables existent (attendu : 2 lignes)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('periodes_facturation', 'periodes_facturation_journal')
ORDER BY table_name;

-- 23.2 colonnes de periodes_facturation (attendu : center_id, annee, mois, statut, cloturee_par, cloturee_le, rouverte_par, rouverte_le, motif_reouverture)
SELECT column_name, data_type, is_nullable FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'periodes_facturation' ORDER BY ordinal_position;

-- 23.3 au départ, AUCUN mois n'est clos et le journal est vide (attendu : 0 et 0)
SELECT (SELECT COUNT(*) FROM periodes_facturation WHERE statut = 'CLOTURE') AS mois_clos,
       (SELECT COUNT(*) FROM periodes_facturation_journal) AS lignes_de_journal;

-- 23.4 unicité (centre, année, mois) et contraintes CHECK présentes (attendu : une clé primaire de 3 colonnes, 2 contraintes CHECK sur periodes_facturation)
SELECT conname, contype FROM pg_constraint WHERE conrelid = 'periodes_facturation'::regclass ORDER BY conname;
```

- **Après quelques clôtures** (contrôle de cohérence, attendu : 0 ligne) : un mois clos dont le journal ne contient pas de clôture, ou un mois
  clos sans auteur.
  ```sql
  SELECT p.center_id, p.annee, p.mois FROM periodes_facturation p
  WHERE p.statut = 'CLOTURE'
    AND (p.cloturee_par IS NULL OR NOT EXISTS (SELECT 1 FROM periodes_facturation_journal j
         WHERE j.center_id = p.center_id AND j.annee = p.annee AND j.mois = p.mois AND j.type_acte = 'CLOTURE'));
  ```
- Rappel de la règle de réouverture : refusée si la période comptable du même mois et du même centre est clôturée
  (`SELECT center_id, annee, mois FROM periodes_comptables WHERE cloturee` pour les repérer).
- Annoncer aux administrateurs que clôturer est **immédiat et sans précondition** : lire l'avertissement chiffré avant de confirmer.

## 24. Absences de patients A2 : unicité du rattrapage (migration V43, 2026-10-22)

V43 crée un **index unique partiel** sur `absence_patient (center_id, patient_id, date_rattrapage) WHERE statut = 'RATTRAPEE'`. Elle ne modifie
aucune donnée, mais **échoue (Flyway l'annule, rien n'est touché) s'il existe déjà un doublon** : le contrôle 24.1 doit donc rendre 0 ligne
AVANT le déploiement (décision 6 du 2026-10-21 : « unicité du rattrapage par contrainte, APRÈS vérification en production »). **SELECT seulement.**

```sql
-- 24.1 BLOQUANT : une même séance de rattrapage utilisée par plusieurs absences rattrapées d'un patient (attendu : 0 ligne)
SELECT center_id, patient_id, date_rattrapage, COUNT(*) AS nb_absences_rattrapees
FROM absence_patient
WHERE statut = 'RATTRAPEE' AND date_rattrapage IS NOT NULL
GROUP BY center_id, patient_id, date_rattrapage
HAVING COUNT(*) > 1
ORDER BY center_id, patient_id, date_rattrapage;

-- 24.2 absences rattrapées sans date de rattrapage (attendu : 0 ligne ; l'index ne les couvre pas, à corriger avec les experts métier)
SELECT center_id, COUNT(*) AS rattrapees_sans_date FROM absence_patient
WHERE statut = 'RATTRAPEE' AND date_rattrapage IS NULL GROUP BY center_id;

-- 24.3 INFORMATIF : absences ANNULEES qui ont gardé leur date de rattrapage (annulation d'une rattrapée, KB-ABS-10). Elles ne réservent plus
-- la séance ; l'index les ignore volontairement. Rapporter le nombre, ne rien modifier.
SELECT center_id, COUNT(*) AS annulees_avec_date_de_rattrapage FROM absence_patient
WHERE statut = 'ANNULEE' AND date_rattrapage IS NOT NULL GROUP BY center_id;

-- 24.4 INFORMATIF, À TRAITER À LA MAIN : absences d'une exclusion de séance déjà ANNULEES (annulation permise avant A2). La séance reste ABSENT
-- et sa réinclusion est refusée (ABSENCE_D_EXCLUSION_MODIFIEE) ; aucune ligne n'est supprimée par l'application. Rapporter la liste ; la sortie
-- (réinclure puis supprimer la ligne) exige la validation écrite de l'utilisateur.
SELECT a.center_id, s.id AS seance_id, a.id AS absence_id, a.patient_id, a.date_seance, a.statut, a.commentaire
FROM absence_patient a JOIN seances s ON s.exclusion_absence_id = a.id AND s.center_id = a.center_id
WHERE a.statut = 'ANNULEE'
ORDER BY a.center_id, a.date_seance;

-- 24.5 après déploiement : V43 appliquée et index présent (attendu : une ligne success = true ; un index uq_absence_patient_rattrapage)
SELECT version, description, success FROM flyway_schema_history WHERE version = '43';
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'absence_patient' AND indexname = 'uq_absence_patient_rattrapage';
```

- **Si 24.1 rend des lignes** : ne déployez pas V43 (la migration échouerait ET l'application ne démarrerait pas, Flyway s'exécutant au démarrage). Pour chaque ligne, un expert métier décide quelle absence garde la séance ; l'autre repasse
  par l'interface (la correction passe par l'application, pas par un `UPDATE` ici). Ne rien supprimer.
- **Comportements nouveaux à annoncer aux utilisateurs** (écarts décidés le 21 et le 22/10, voir `.ddd/migration/slices/absences-patients-a2-cycle-de-vie.md`) :
  une absence rattrapée ne s'annule plus ; seuls l'administrateur et le médecin rattrapent ; entrées illisibles en 400 ; une écriture concurrente sur
  la même absence ou le même jour reçoit 409 (relire puis recommencer) ; l'absence créée par l'exclusion d'une séance ne s'annule plus (seule la réinclusion la retire).

## 25. Absences de patients A3 : détection de nuit (aucune migration, 2026-10-22)

La slice A3 ne change aucun schéma (l'index `seances (center_id, patient_id, date_seance)` existe depuis V31). Tout ce qui suit est
en **SELECT seul**, à exécuter sur une COPIE ou en lecture seule ; ne rien corriger ici.

```sql
-- 25.1 INDEX : attendu une ligne (idx_seances_center_patient, V31) ; sinon la détection reste correcte mais plus lente.
SELECT indexname FROM pg_indexes WHERE tablename = 'seances' AND indexname = 'idx_seances_center_patient';

-- 25.2 À SAISIR (informatif, aucune exigence bloquante) : patients SORTIS (transféré, décédé, greffé, guéri) SANS DATE D'ÉVÉNEMENT. Ils ne sont
-- jamais attendus par la détection (comportement conservé) : aucune absence n'est créée pour eux, y compris pour leurs vrais jours manqués. Liste
-- à compléter par la saisie de la date d'événement (par l'application) ; Patient et couverture interdira bientôt une sortie sans date (roadmap).
SELECT p.center_id, p.etat_patient, COUNT(*) AS patients_sans_date
FROM patients p
WHERE p.etat_patient IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI') AND p.date_evenement_etat IS NULL
GROUP BY p.center_id, p.etat_patient ORDER BY p.center_id, p.etat_patient;

-- 25.3 INFORMATIF : jours de l'événement concernés par l'uniformisation (TRANSFERE et GUERRI : le jour de l'événement n'est plus attendu, il l'était).
-- Nombre de patients dont la date d'événement tombe dans les 31 derniers jours (la détection ne recréera pas ce jour-là).
SELECT p.center_id, p.etat_patient, COUNT(*) AS patients
FROM patients p
WHERE p.etat_patient IN ('TRANSFERE', 'GUERRI') AND p.date_evenement_etat BETWEEN current_date - 31 AND current_date - 1
GROUP BY p.center_id, p.etat_patient ORDER BY p.center_id;

-- 25.4 INFORMATIF : le jour de l'événement est désormais exclu aussi pour TRANSFERE et GUERRI (il était inclus). Absences automatiques « à
-- qualifier » déjà créées ce jour-là : elles restent (à annuler à la main si l'expert métier le décide), elles ne seront plus créées.
SELECT a.center_id, COUNT(*) AS absences_le_jour_de_l_evenement
FROM absence_patient a JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id
WHERE p.etat_patient IN ('TRANSFERE', 'GUERRI') AND p.date_evenement_etat = a.date_seance
  AND a.source = 'AUTOMATIQUE' AND a.statut = 'A_QUALIFIER'
GROUP BY a.center_id;

-- 25.5 INFORMATIF : absences DÉCLARÉES par un utilisateur, restées « à qualifier », dont la séance a été réalisée le même jour. L'ancienne
-- réconciliation les annulait ; la nouvelle ne touche que les absences détectées : elles resteront à qualifier à la main.
SELECT a.center_id, COUNT(*) AS declarees_a_qualifier_avec_seance_realisee
FROM absence_patient a
WHERE a.source = 'DECLAREE' AND a.statut = 'A_QUALIFIER'
  AND EXISTS (SELECT 1 FROM seances s WHERE s.center_id = a.center_id AND s.patient_id = a.patient_id AND s.date_seance = a.date_seance
              AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE'))
GROUP BY a.center_id;

-- 25.6 INFORMATIF : centres INACTIFS, qui ne sont plus contrôlés la nuit (ni détection ni notification).
SELECT id, code, name FROM centers WHERE actif = FALSE;

-- 25.7 INFORMATIF : notifications « absences à qualifier » déjà créées, par centre, sur les 30 derniers jours. À partir de A3 il n'y en a plus qu'une par centre et par jour
-- du centre ; avant, une par passage.
SELECT n.center_id, date_trunc('day', n.cree_le) AS jour, COUNT(*) AS notifications
FROM notification_evenement n
WHERE n.type = 'ABSENCES_A_QUALIFIER' AND n.cree_le >= now() - interval '30 days'
GROUP BY n.center_id, date_trunc('day', n.cree_le) ORDER BY n.center_id, jour;

-- 25.8 INFORMATIF : fuseau du serveur de base (les dates du centre sont calculées par l'application, fuseau Africa/Algiers par défaut).
SHOW timezone;
```

- **Comportements nouveaux à annoncer** (écarts décidés le 22/10, voir `.ddd/migration/slices/absences-patients-a3-detection-de-nuit.md`) :
  jour de l'événement exclu pour tous les états de sortie (sans date : jamais attendu, inchangé) ; centres inactifs non contrôlés ; une seule
  notification par centre et par jour ; réconciliation limitée aux absences détectées hors
  période facturée ; entrées illisibles du rattrapage de détection en 400 ; fuseau du centre.

## 26. Absences de patients A4 : lectures publiées du pilotage (aucune migration, 2026-10-23)

Le tableau de bord des séances (nombre, détail, exports) et la vue Direction ne comptent plus aucune absence datée **après aujourd'hui**
(jour du fuseau du centre, KB-ABS-34, 35). Requête de recette en **lecture seule** (SELECT uniquement), à lancer sur une copie ou en lecture :

```sql
SELECT center_id, COUNT(*) AS absences_futures
FROM absence_patient
WHERE date_seance > current_date AND statut <> 'ANNULEE'
GROUP BY center_id;
```

- **26.1** Résultat attendu : aucune ligne (le domaine interdit de déclarer une date future depuis A2). Sinon, rapporter le **nombre de lignes par centre** (sans identifiant
  de patient) : ces absences héritées ne seront plus comptées dans les chiffres du pilotage tant que leur date n'est pas passée.
- **26.2** Informatif : `GET /api/v1/seances/dashboard/export?format=txt` répond désormais 400 (était : CSV) ; `GET /api/v1/direction/absences?from=hier` répond 400 (était : 500).

## 27. Absences de patients A6 : clôture mensuelle des absences (migration V44, 2026-10-23)

La migration V44 est **additive** (tables `mois_absences` et `mois_absences_journal`, aucune ligne, aucun DROP ni
DELETE) : aucun mois n'est clos au déploiement. Avant la **première
clôture** d'un mois d'absences, regarder ce qui resterait figé. Toutes les requêtes ci-dessous sont en **lecture seule**
(SELECT uniquement), à lancer sur une copie ou en lecture :

```sql
-- 27.1 Absences encore à qualifier et en retard (plus de 3 jours) par centre et par mois
SELECT center_id,
       to_char(date_seance, 'YYYY-MM') AS mois,
       COUNT(*)                        AS a_qualifier,
       COUNT(*)                           FILTER (WHERE date_seance < current_date - 3)   AS en_retard, to_char(date_seance, 'YYYY-MM') < to_char(current_date, 'YYYY-MM') AS cloturable
FROM absence_patient
WHERE statut = 'A_QUALIFIER'
GROUP BY center_id, to_char(date_seance, 'YYYY-MM')
ORDER BY center_id, mois;

-- 27.2 V44 : tables créées et vides (aucun mois clos au départ)
SELECT (SELECT COUNT(*) FROM mois_absences)         AS mois,
       (SELECT COUNT(*) FROM mois_absences_journal) AS journal;

-- 27.3 Séances exclues de la facturation, par centre et par mois (la réinclusion sera refusée tant que le mois d'absences est clos)
SELECT s.center_id, to_char(s.date_seance, 'YYYY-MM') AS mois, COUNT(*) AS seances_exclues
FROM seances s
WHERE s.exclusion_absence_id IS NOT NULL
GROUP BY s.center_id, to_char(s.date_seance, 'YYYY-MM')
ORDER BY s.center_id, mois;
```

- **27.1** Rapporter, pour chaque centre, le nombre d'absences à qualifier et en retard par mois **passé** (sans
  identifiant de patient). Un mois dont le chiffre est élevé doit être qualifié
  avant d'être clos (la clôture n'est pas refusée, elle fige le mois : rouvrir exige un motif). Le mois en cours ne se
  clôt pas (`cloturable = false` ;
  `current_date` est la date du serveur PostgreSQL : en bord de mois, se fier au fuseau du centre).
- **27.2** Résultat attendu : `0` et `0` juste après la migration (une ligne `OUVERT` apparaît au premier verrouillage
  d'un mois : écriture d'une absence ou clôture ; sans effet métier).
- **27.3** Informatif : nombre de séances exclues par mois ; leur réinclusion devra attendre la réouverture du mois
  d'absences si celui-ci est clos.
- **Rejeu local du 2026-10-23** (base de développement PostgreSQL 16.15, schéma en V33, données de démonstration) : 27.1
  s'exécute (5 centres ; 2026-09 : 35 à 36
  à qualifier, toutes en retard ; 2026-10 : 266 dont 224 en retard). 27.2 et 27.3 non exécutables sur cette base (tables
  V44 et colonne `seances.exclusion_absence_id` de V39
  absentes) ; 27.2 est l'équivalent du contrôle « tables vides » de `backend/scripts/absences-a6-pg16-rejeu.sh` (vert le
  2026-10-23 sur PostgreSQL 16 jetable) ; 27.3 est relue
  contre `V39__facturation_exclusion_de_seance.sql` (colonnes `seances.center_id`, `date_seance`,
  `exclusion_absence_id`). **Le résultat de production reste à rapporter.**
- **27.4** Informatif : l'ordre recommandé est « clore les absences, puis la facturation du mois » ; rien n'est bloqué
  dans l'autre sens, la clôture de facturation avertit seulement (champ `absencesDuMoisNonCloses`).

## 28. Facturation : seul un mois terminé se clôture (ADR 0044) et délai de verrou (ADR 0043), 2026-10-23

Aucune migration. Depuis ce changement, la clôture du mois en cours ou d'un mois futur est refusée (422
`PERIODE_NON_TERMINEE`). Un mois futur
aurait pu être clos **avant** (F7 l'acceptait) : à repérer, puis à rouvrir avec un motif s'il l'a été par erreur.
Lecture seule :

```sql
-- 28.1 Mois de facturation clos qui ne sont pas encore terminés (mois en cours ou futur, date du serveur)
SELECT center_id, annee, mois, cloturee_le
FROM periodes_facturation
WHERE statut = 'CLOTURE'
  AND annee * 12 + mois >= EXTRACT(YEAR FROM current_date) * 12 + EXTRACT(MONTH FROM current_date)
ORDER BY center_id, annee, mois;
```

- **28.1** Résultat attendu : **0 ligne**. Sinon, rapporter le nombre par centre (sans identifiant d'utilisateur) : ces
  mois restent clos (la règle
  ne s'applique qu'aux nouvelles clôtures) ; les rouvrir avec un motif si la clôture était une erreur.
- **28.2** Informatif (ADR 0043) : un acte de facturation qui attend un verrou plus de 5 s répond désormais 409
  `FACTURATION_ECRITURE_CONCURRENTE`
  au lieu d'attendre ; à surveiller dans les journaux les premiers jours (fréquence attendue : rare, pendant une
  validation très longue).

## 29. Stock S5 : lignes de bons de réception validés sans lot (2026-10-23)

La correction d'un bon validé retrouve le lot de chaque ligne par `bons_reception_lignes.lot_id`. Les bons validés
**avant S5**
n'ont pas ce lien (`lot_id` nul) : la correction apparie alors lignes et lots par article puis par numéro de lot. À
vérifier avant
la livraison (lecture seule) :

```sql
-- Bons validés dont toutes les lignes n'ont pas de lot rattaché (tous les bons validés avant S5)
SELECT COUNT(*) FROM bons_reception b
WHERE b.statut = 'VALIDE'
  AND EXISTS (SELECT 1 FROM bons_reception_lignes l WHERE l.bon_reception_id = b.id AND l.lot_id IS NULL);

-- Cas ambigus : un même article porté par plusieurs lignes d'un même bon validé (appariement par numéro de lot)
SELECT l.bon_reception_id, l.article_id, COUNT(*) AS lignes
FROM bons_reception_lignes l JOIN bons_reception b ON b.id = l.bon_reception_id
WHERE b.statut = 'VALIDE' AND l.lot_id IS NULL
GROUP BY l.bon_reception_id, l.article_id HAVING COUNT(*) > 1;

-- Cohérence : nombre de lots et nombre de lignes par bon validé (doivent être égaux)
SELECT b.id, (SELECT COUNT(*) FROM lots x WHERE x.bon_reception_id = b.id) AS lots,
       (SELECT COUNT(*) FROM bons_reception_lignes l WHERE l.bon_reception_id = b.id) AS lignes
FROM bons_reception b WHERE b.statut = 'VALIDE'
  AND (SELECT COUNT(*) FROM lots x WHERE x.bon_reception_id = b.id) <> (SELECT COUNT(*) FROM bons_reception_lignes l WHERE l.bon_reception_id = b.id);
```

**À rapporter** : les trois comptages. Le deuxième indique les bons dont une correction pourrait apparier deux lignes du
même article
par l'ordre des numéros de lot ; une régularisation (renseigner `lot_id`) est alors proposée.

Rejeu PostgreSQL 16 jetable (`FOR UPDATE` et délai de 5 s de la requête de verrou, `stock-pg16-rejeu.sh`) : à exécuter
avant la
livraison de S5.

## 30. Stock S8 : lots proches d'expiration avant la première notification (aucune migration, 2026-10-26)

Le contrôle de 07:00 notifie désormais le pharmacien et l'administrateur (centres **actifs**, lots d'articles **actifs**,
J+30 inclus ; périmés le lendemain puis chaque semaine). Pour prévoir le volume de la **première** alerte de chaque centre
(lecture seule, `SELECT` uniquement) :

```sql
-- 30.1 Par centre (actif ou non) : lots en stock qui périment d'ici 30 jours ou sont déjà périmés, articles actifs / inactifs
SELECT c.code AS centre, c.actif AS centre_actif, a.active AS article_actif,
       COUNT(*) FILTER (WHERE l.date_peremption < CURRENT_DATE)                                   AS deja_perimes,
       COUNT(*) FILTER (WHERE l.date_peremption BETWEEN CURRENT_DATE AND CURRENT_DATE + 30)       AS dans_30_jours
FROM lots l JOIN articles a ON a.id = l.article_id JOIN centers c ON c.id = l.center_id
WHERE l.quantite_restante > 0 AND l.date_peremption IS NOT NULL AND l.date_peremption <= CURRENT_DATE + 30
GROUP BY c.code, c.actif, a.active ORDER BY c.code, c.actif DESC, a.active DESC;

-- 30.2 Périmés les plus anciens (un lot périmé depuis longtemps encore en stock est un constat à traiter, pas une alerte)
SELECT c.code AS centre, MIN(l.date_peremption) AS plus_ancienne_peremption, COUNT(*) AS lots
FROM lots l JOIN centers c ON c.id = l.center_id
WHERE l.quantite_restante > 0 AND l.date_peremption < CURRENT_DATE - 365 GROUP BY c.code ORDER BY c.code;

-- 30.3 Fuseau des centres actifs (jour du centre) : valeurs nulles = UTC
SELECT fuseau_horaire, COUNT(*) FROM centers WHERE actif GROUP BY fuseau_horaire;

-- 30.4 Destinataires : rôles PHARMACIEN et ADMIN par centre actif (au moins un de chaque)
SELECT c.code, COUNT(DISTINCT u.id) FILTER (WHERE r.code = 'PHARMACIEN') AS pharmaciens,
       COUNT(DISTINCT u.id) FILTER (WHERE r.code = 'ADMIN') AS admins
FROM centers c LEFT JOIN app_user_center uc ON uc.center_id = c.id
LEFT JOIN app_user u ON u.id = uc.user_id AND u.active
LEFT JOIN app_user_role ur ON ur.user_id = u.id LEFT JOIN app_role r ON r.id = ur.role_id
WHERE c.actif GROUP BY c.code ORDER BY c.code;
```

**À rapporter** : 30.1 (tableau), 30.2 (nombre de lignes), 30.3 (valeurs), 30.4 (centres sans destinataire). Un centre
actif sans pharmacien ni admin ne verra l'alerte qu'au rôle ciblé le plus proche ; les centres inactifs ne sont plus
parcourus (le legacy les signalait au journal). Rejeu PostgreSQL 16 jetable : `backend/scripts/stock-s8-pg16-rejeu.sh 55444`
(port différent de 5432).

## 31. Stock : bascule S1 à S8, liste ordonnée des contrôles avant la mise en production (2026-10-26)

Source : `.ddd/verify/verify-stock.md`, ADR 0045 à 0047. **Lecture seule, `SELECT` uniquement**, dans cet ordre (les points 31.1
à 31.5 conditionnent le déploiement ; 31.6 à 31.9 sont les points d'argent). Les points § 29 (S5) et § 30 (S8) font partie de la
liste (31.10 et 31.11). Jamais de `DROP`/`DELETE`/`UPDATE` pour « corriger » un écart relevé ici : rapportez-le d'abord.

**31.1 `ddl-auto` de la production** : voir § 7 (attendu `validate` ; les entités JPA résiduelles du stock restent compatibles).

**31.2 Flyway jusqu'à V46 et structures du Stock** (étend le § 8 : V38, V45, V46) :
```sql
SELECT version, description, success FROM flyway_schema_history WHERE version IN ('38', '45', '46') ORDER BY installed_rank;
SELECT max(version::int) AS version_courante FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';
-- V38 : saisi_le ; V46 : fuseau_horaire (attendu : 2 lignes)
SELECT table_name, column_name FROM information_schema.columns
WHERE (table_name = 'stock_movements' AND column_name = 'saisi_le') OR (table_name = 'centers' AND column_name = 'fuseau_horaire');
-- V45 : les 5 tables (attendu : 5 lignes)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('stock_parametres', 'sorties_pour_seances', 'sorties_pour_seances_lignes',
      'consommations_de_seance', 'consommations_de_seance_imputations');
```
Avant le déploiement qui les apporte, les lignes 38, 45 et 46 sont **absentes** (ou présentes avec `success = true` si déjà
déployées) ; ne rien supprimer si une ligne a un autre état, le signaler.

**31.3 Rôle `DIRECTEUR_ADMINISTRATIF`** : **aucune migration ni seed ne le crée** (vérifié : `seed.sql`, `seed-production.sql` et
`db/migration/*.sql` n'en contiennent pas ; `InitialSetupService` ne crée que `SUPERADMIN`). Il doit être créé par
l'administrateur avant d'attribuer le profil :
```sql
-- attendu : 1 ligne une fois le rôle créé ; 0 ligne avant (le rôle est une ligne de app_role)
SELECT id, code, name FROM app_role WHERE code = 'DIRECTEUR_ADMINISTRATIF';
-- rôles du Stock déjà présents (attendu : ADMIN, PHARMACIEN, INFIRMIER ; DIRECTEUR_ADMINISTRATIF seulement après création)
SELECT code FROM app_role WHERE code IN ('ADMIN', 'PHARMACIEN', 'DIRECTEUR_ADMINISTRATIF', 'INFIRMIER') ORDER BY code;
```
**À rapporter** : présent ou absent, et la liste des comptes à qui l'attribuer.

**31.4 État au moment de la bascule** (par centre) : aucun inventaire en cours, aucun recalcul de PMP en cours.
```sql
SELECT c.code, i.reference, i.statut FROM inventaires i JOIN centers c ON c.id = i.center_id WHERE i.statut = 'EN_COURS';
```
Attendu : 0 ligne. Le recalcul massif du PMP est une tâche en mémoire (KB-STK-43) : aucune requête ne le voit ; demandez à
l'exploitation qu'aucun recalcul ne soit lancé pendant le déploiement et déployez hors des heures de saisie.

**31.5 Prérequis S6b (ADR 0047) : une pièce préparée avant la première séance** — sans pièce, les consommations deviennent des
alertes `AUCUNE_PIECE` et le stock ne baisse plus. Par centre actif, pour le premier jour de séances après le déploiement
(remplacez `:jour`) :
```sql
-- Séances prévues ce jour-là et pièces « sortie pour séances » préparées ce jour-là (attendu : au moins une pièce par centre qui a des séances)
SELECT c.code AS centre,
       (SELECT COUNT(*) FROM seances s WHERE s.center_id = c.id AND s.date_seance = DATE ':jour') AS seances,
       (SELECT COUNT(*) FROM sorties_pour_seances p WHERE p.center_id = c.id AND p.jour = DATE ':jour') AS pieces,
       (SELECT COUNT(*) FROM sorties_pour_seances p WHERE p.center_id = c.id AND p.statut = 'ENREGISTREE' AND p.jour < DATE ':jour') AS pieces_anterieures_non_rapprochees
FROM centers c WHERE c.actif ORDER BY c.code;

-- Séances du jour déjà validées sous l'ancien mode (sorties historiques rattachées à une séance du jour)
SELECT c.code, COUNT(DISTINCT bs.seance_id) AS seances_avec_sortie_historique
FROM bons_sortie bs JOIN seances s ON s.id = bs.seance_id JOIN centers c ON c.id = s.center_id
WHERE s.date_seance = DATE ':jour' GROUP BY c.code ORDER BY c.code;

-- Profils capables de préparer la pièce : PHARMACIEN et ADMIN par centre actif (même requête que 30.4)
SELECT c.code, COUNT(DISTINCT u.id) FILTER (WHERE r.code = 'PHARMACIEN') AS pharmaciens,
       COUNT(DISTINCT u.id) FILTER (WHERE r.code = 'ADMIN') AS admins
FROM centers c LEFT JOIN app_user_center uc ON uc.center_id = c.id
LEFT JOIN app_user u ON u.id = uc.user_id AND u.active
LEFT JOIN app_user_role ur ON ur.user_id = u.id LEFT JOIN app_role r ON r.id = ur.role_id
WHERE c.actif GROUP BY c.code ORDER BY c.code;
```
**À rapporter** : un centre avec des séances et `pieces = 0` n'est pas prêt ; `pieces_anterieures_non_rapprochees > 0` bloquera
la prochaine pièce du même périmètre (rapprochement obligatoire) et l'ouverture d'un inventaire (T43).

**31.6 Point d'argent : stock de l'article = Σ des lots** (écarts hérités, KB-STK-50 et scénario « 48 au lieu de 46 » de S6),
à rapporter **avant toute clôture d'inventaire** :
```sql
SELECT c.code AS centre, a.code AS article, a.stock_quantity, COALESCE(SUM(l.quantite_restante), 0) AS somme_des_lots
FROM articles a JOIN centers c ON c.id = a.center_id LEFT JOIN lots l ON l.article_id = a.id AND l.center_id = a.center_id
GROUP BY c.code, a.code, a.id, a.stock_quantity
HAVING a.stock_quantity <> COALESCE(SUM(l.quantite_restante), 0)
ORDER BY c.code, a.code;
```

**31.7 Point d'argent : lots anormaux** :
```sql
-- lots négatifs ou supérieurs à la quantité initiale (attendu : 0 ligne)
SELECT c.code, a.code AS article, l.numero_lot, l.quantite_initiale, l.quantite_restante
FROM lots l JOIN articles a ON a.id = l.article_id JOIN centers c ON c.id = l.center_id
WHERE l.quantite_restante < 0 OR l.quantite_restante > l.quantite_initiale ORDER BY c.code, a.code;
-- lots d'articles gérés par lot sans numéro réel ou sans péremption
SELECT c.code, a.code AS article, COUNT(*) AS lots
FROM lots l JOIN articles a ON a.id = l.article_id JOIN centers c ON c.id = l.center_id
WHERE a.gere_par_lot AND l.quantite_restante > 0 AND (l.date_peremption IS NULL OR l.numero_lot LIKE 'AUTO-%')
GROUP BY c.code, a.code ORDER BY c.code, a.code;
```

**31.8 Point d'argent : PMP**. Ordre de rejeu (`CHRONOLOGIE`) : date de pièce (`created_at`), heure de saisie (`saisi_le`, nulle
pour l'ancien), entrées avant sorties.
```sql
-- articles dont le PMP courant diffère du pmp_apres du dernier mouvement dans l'ordre du rejeu
SELECT c.code AS centre, a.code AS article, a.pmp_courant, d.pmp_apres AS pmp_du_dernier_mouvement
FROM articles a JOIN centers c ON c.id = a.center_id
JOIN (SELECT DISTINCT ON (article_id) article_id, pmp_apres
      FROM stock_movements WHERE pmp_apres IS NOT NULL AND mouvement_type <> 'RETOUR'
      ORDER BY article_id, created_at DESC, saisi_le DESC NULLS LAST, (mouvement_type = 'SORTIE') DESC) d ON d.article_id = a.id
WHERE a.pmp_courant IS DISTINCT FROM d.pmp_apres ORDER BY c.code, a.code;
-- sorties sans valeur (SANS_VALEUR, KB-STK-01, KB-CPT-42/44)
SELECT c.code, COUNT(*) AS sorties_sans_valeur
FROM stock_movements m JOIN centers c ON c.id = m.center_id
WHERE m.mouvement_type = 'SORTIE' AND (m.pmp_apres IS NULL OR m.pmp_apres = 0 OR m.prix_unitaire IS NULL OR m.prix_unitaire = 0)
GROUP BY c.code ORDER BY c.code;
```
Un écart du premier tableau est à **examiner, pas à corriger** (les inventaires clôturés repartent d'un stock de départ). Puis
rejeu `backend/scripts/stock-pg16-rejeu.sh` sur une **copie** de la base.

**31.9 Point d'argent : valorisation et Comptabilité**. Mouvements valorisés d'un mois clos, par centre, à faire comparer par le
comptable aux écritures du journal du stock (lecture dans l'application : valorisation de période et écran des échecs de la
synchronisation du stock, qui renvoie la liste `echecs` — **pas de requête SQL** pour ces deux écrans) :
```sql
SELECT c.code AS centre, m.mouvement_type, COUNT(*) AS mouvements, SUM(m.quantite * COALESCE(m.prix_unitaire, 0)) AS valeur
FROM stock_movements m JOIN centers c ON c.id = m.center_id
WHERE m.created_at >= DATE ':debut_du_mois' AND m.created_at < DATE ':debut_du_mois_suivant'
GROUP BY c.code, m.mouvement_type ORDER BY c.code, m.mouvement_type;
-- les retours en stock (RETOUR) doivent valoir 0 au départ (nouveau type de mouvement V45)
SELECT COUNT(*) AS retours FROM stock_movements WHERE mouvement_type = 'RETOUR';
```
**À rapporter** : le tableau, le nombre de `RETOUR` (attendu 0) et la liste des `echecs` de la dernière synchronisation.

**31.10 § 29** (S5) : bons de réception validés sans `lot_id`, cas ambigus, lots ≠ lignes (trois comptages).

**31.11 § 30** (S8) : volume de la première alerte de péremption, fuseaux, destinataires.

**31.12 § 9** : publications non acquittées (`event_publication`), aucune attendue pour `LotProcheExpiration` (pas d'écouteur).

**31.13 Rejeux PostgreSQL 16 jetables** : `backend/scripts/stock-pg16-rejeu.sh` et `backend/scripts/stock-s8-pg16-rejeu.sh <port différent de 5432>`.

## Que nous rapporter
Un message court : le résultat de chaque point (OK, ou la valeur observée : colonnes présentes, nom des contraintes, comptages),
sans données de patients ni identifiants. Tant que les points 2, 4, 8, 10 et 11 ne sont pas confirmés, ne déployez pas
G1-G5
en production. Pour la bascule Comptabilité C3, confirmez également le point 16 ; pour C4, examinez le point 17 avant
toute décision d'ajout de contrainte unique. Pour la bascule Facturation F2, confirmez le point 18 ; pour F3, les points
19.1 à 19.6 ; pour F4, les points 20.1 à 20.5 et 20.10 (V41, remboursements et écritures de remboursement historiques) ; pour F5, le point 21.2
(écart des chiffres de la synthèse) à faire valider par la direction et la comptabilité ; pour F6, le point 22
(écritures déjà générées pour des factures de reprise, extourne à décider avec le comptable) ; pour F7, le point 23
(V42 : tables créées, aucun mois clos au départ) ; pour Absences A2, le point 24 (**24.1 doit rendre 0 ligne avant
V43**, 24.3 et 24.4 à rapporter) ; pour Absences A3, le point 25 (25.2 : sorties sans date à saisir, 25.3 et 25.6
informatifs) ; pour Absences A6, le point 27 (27.2 : V44, tables vides ; **27.1 à rapporter avant la première clôture
d'un mois**) ; pour la Facturation (ADR 0044), le point 28.1 (0 ligne attendue) ; pour Stock S8, le point 30 (volume de la première alerte de péremption par centre, avant son activation) ; **pour la bascule du Stock S1 à S8, le point 31 en entier, dans l'ordre : 31.2 (V38, V45, V46), 31.3 (rôle `DIRECTEUR_ADMINISTRATIF` à créer), 31.5 (une pièce préparée par centre avant la première séance, sinon le stock ne baisse plus) et les points d'argent 31.6 à 31.9 sont à confirmer avant le déploiement**.

## 32. Planification P1 : paramétrage (aucune migration, 2026-10-26) — SELECT seulement

Aucune migration : les quatre tables (`planning_parametres`, `planification_reglages`, `planning_preference_patient`, `infirmier_profil_planning`) sont celles de Flyway V10, V11, V16, V25. À contrôler avant la mise en production (lecture seule, base de production ou copie) :

```sql
-- 1. Préférences de patients sortis et profils d'infirmiers inactifs déjà enregistrés (le legacy les acceptait) : restent lisibles par l'optimisation, plus modifiables
SELECT count(*) FROM planning_preference_patient pp JOIN patients p ON p.id = pp.patient_id AND p.center_id = pp.center_id
 WHERE p.etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI');
SELECT count(*) FROM infirmier_profil_planning pr JOIN infirmier i ON i.id = pr.infirmier_id AND i.center_id = pr.center_id WHERE i.actif = FALSE;
-- 2. Compétences stockées illisibles (le nouveau code lève une erreur au-delà de PEDIATRIE, CATHETER) : attendu 0
SELECT infirmier_id, competences FROM infirmier_profil_planning
 WHERE competences IS NOT NULL AND btrim(competences) <> '' AND btrim(replace(replace(replace(competences, 'PEDIATRIE', ''), 'CATHETER', ''), ',', '')) <> '';
-- 3. Jours d'ouverture stockés illisibles : attendu 0
SELECT center_id, jours_ouverts FROM planning_parametres
 WHERE regexp_replace(jours_ouverts, 'DIMANCHE|LUNDI|MARDI|MERCREDI|JEUDI|VENDREDI|SAMEDI|,| ', '', 'g') <> '';
-- 4. Ratio ou patients par poste hors bornes (le nouveau code refuse de lire une valeur invalide) : attendu 0
SELECT center_id FROM planning_parametres WHERE patients_par_infirmier NOT BETWEEN 1 AND 20 OR patients_par_poste_serie NOT BETWEEN 1 AND 10;
-- 5. Réglages hors bornes : attendu 0
SELECT center_id FROM planification_reglages WHERE heures_par_vacation NOT BETWEEN 1 AND 12 OR heures_hebdo_temps_plein NOT BETWEEN 10 AND 60 OR repos_hebdo_min NOT BETWEEN 0 AND 6;
```

```sql
-- 6. Taux d'activité des profils hors bornes (le nouveau code refuse de lire une valeur invalide) : attendu 0
SELECT infirmier_id FROM infirmier_profil_planning WHERE taux_activite NOT BETWEEN 10 AND 100;
-- 7. Informatif (ADR 0055) : centres sans ligne planning_parametres = sans verrou de centre. Sans conséquence pour P1 ;
--    PlacesDuCentre (P5) devra créer la ligne ou verrouiller autrement avant d'écrire une place.
SELECT count(*) FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id = c.id);
```

Après la bascule : les écritures d'un même centre passent l'une après l'autre (la seconde attend puis passe, la dernière écrite l'emporte). Le 409 `PLANIFICATION_ECRITURE_CONCURRENTE` est rare : il n'arrive qu'après 5 s d'attente du verrou ou si la même clé est créée entre-temps (à surveiller dans le journal d'audit : statut 409 sur `/api/v1/planning/**`).

## 33. Planification P2 : référentiel des infirmiers et comptes (aucune migration, 2026-10-26) — SELECT seulement

Aucune migration, aucun index ajouté (décision et justification : ADR 0056) : la contrainte `UNIQUE (center_id, matricule)` et l'index `ux_infirmier_user (center_id, user_id)` existent (Flyway V11). **Le matricule est désormais enregistré en majuscules** et l'unicité s'apprécie sans la casse, par le code, sous le verrou du centre. À contrôler **avant la mise en production** (lecture seule, base de production ou copie) :

```sql
-- 1. Doublons de casse du matricule dans un même centre (m-100 et M-100) : attendu 0 ligne. S'il y en a, le legacy les a laissés
--    passer (contrainte sensible à la casse) : le nouveau code refusera de modifier l'une des deux fiches sous le matricule de l'autre ;
--    à régulariser par l'exploitation avant la bascule (renommer l'une des fiches), jamais par suppression.
SELECT center_id, upper(btrim(matricule)) AS matricule_normalise, count(*) AS fiches, array_agg(id) AS identifiants
  FROM infirmier GROUP BY center_id, upper(btrim(matricule)) HAVING count(*) > 1;
-- 2. Informatif : fiches dont le matricule n'est pas en majuscules (elles gardent leur casse jusqu'à leur modification)
SELECT count(*) FROM infirmier WHERE matricule <> upper(matricule);
-- 3. Identifiants de connexion en double sans tenir compte de la casse (app_user.username n'a AUCUNE contrainte d'unicité dans
--    Flyway V1 : KB-PLN-24) : attendu 0 ligne ; la création de compte depuis une fiche refuse déjà un identifiant existant, mais
--    elle ne corrige pas les doublons préexistants
SELECT lower(username), count(*) FROM app_user GROUP BY lower(username) HAVING count(*) > 1;
-- 4. États incohérents préexistants entre une fiche et son compte (KB-PLN-22) : ils restent lisibles ; réactiver la fiche (ou délier
--    une fiche inactive) sera désormais refusé en 422 tant que le compte n'est pas aligné depuis l'administration des utilisateurs
SELECT i.id, i.center_id, i.actif AS fiche_active, u.active AS compte_actif
  FROM infirmier i JOIN app_user u ON u.id = i.user_id WHERE i.actif <> u.active;
-- 5. Comptes DÉJÀ liés à des fiches de plusieurs centres (KB-PLN-15) : attendu 0 ligne. Le nouveau code interdit d'en créer
--    (422 COMPTE_LIE_A_UN_AUTRE_CENTRE) mais ne corrige pas l'existant : TRAITEMENT MANUEL AVANT LE DÉPLOIEMENT (délier la fiche
--    d'un des centres depuis l'écran des comptes, ou créer un compte par centre), jamais par suppression de données
SELECT user_id, count(*) AS fiches, array_agg(center_id) AS centres FROM infirmier
 WHERE user_id IS NOT NULL GROUP BY user_id HAVING count(DISTINCT center_id) > 1;
-- 6. Fiches liées à un compte supprimé (clé non contrainte) : informatif, listées avec compte absent
SELECT i.id FROM infirmier i LEFT JOIN app_user u ON u.id = i.user_id WHERE i.user_id IS NOT NULL AND u.id IS NULL;
-- 7. Colonnes à la borne (nouveau refus 400 à la saisie, jamais à la lecture) : informatif
SELECT count(*) FROM infirmier WHERE length(matricule) > 50 OR length(nom) > 255 OR length(prenom) > 255 OR length(telephone) > 50;
```

Après la bascule : surveiller dans le journal d'audit les actions `INFIRMIER_ENREGISTRE`, `INFIRMIER_MODIFIE`, `INFIRMIER_DESACTIVE`, `INFIRMIER_REACTIVE`, `INFIRMIER_COMPTE_LIE`, `INFIRMIER_COMPTE_CREE`, `INFIRMIER_COMPTE_DELIE` (identifiant de la fiche en `entity_id`), les statuts 400, 409 (`PLANIFICATION_ECRITURE_CONCURRENTE`, rare : après 5 s d'attente du verrou) et 422 sur `/api/v1/infirmiers/**`, et `DELETE /api/v1/users/{id}` en 422 `COMPTE_LIE_A_UNE_FICHE_ACTIVE`. Retour arrière : redéploiement de la version précédente (aucune migration ; les matricules déjà en majuscules restent valides pour l'ancien code).

## 34. Planification P3 : roulement, absences, remplacements (une migration additive V47, 2026-10-26) — SELECT seulement

**Migration** : `V47__infirmier_absence_auteur.sql` ajoute `infirmier_absence.declaree_par uuid NULL` (aucun index,
aucune donnée modifiée ; l'ancien code
l'ignore : le retour arrière est le redéploiement). Aucun index ni contrainte supplémentaire (ADR 0057). **Contrôles à
exécuter AVANT le déploiement,
en lecture seule** (ne jamais corriger par `DELETE`/`UPDATE` sans validation écrite de l'utilisateur ; ces requêtes sont
aussi rejouées, vides, sur la base jetable par
`backend/scripts/planification-p3-pg16-rejeu.sh`) :

```sql
-- 1. Affectations qui se chevauchent (KB-PLN-25 : deux POST simultanés ont pu créer deux lignes) : couples (infirmier, créneau, jour) en doublon.
--    Attendu 0 ligne. Le nouveau code les laisse lisibles et supprimables, refuse de modifier l'une tant qu'elle chevauche l'autre, et la présence
--    continue de signaler DOUBLE_AFFECTATION : à régulariser par l'exploitation en retirant l'affectation en trop depuis l'écran du roulement.
SELECT a.infirmier_id, a.creneau_id, j AS jour, count(*) AS affectations, array_agg(a.id) AS identifiants
  FROM infirmier_affectation a, unnest(string_to_array(a.jours, ',')) AS j
 GROUP BY a.infirmier_id, a.creneau_id, j HAVING count(*) > 1;
-- 2. Absences d'un même infirmier qui se chevauchent ou se répètent (KB-PLN-26) : attendu 0 ligne. Elles restent lisibles ; le nouveau code refuse
--    d'en créer de nouvelles (422 ABSENCE_CHEVAUCHEMENT) mais ne corrige pas l'existant (la présence retient la première absence trouvée).
SELECT a.infirmier_id, a.id AS absence, b.id AS autre_absence, a.date_debut, a.date_fin, b.date_debut AS autre_debut, b.date_fin AS autre_fin
  FROM infirmier_absence a JOIN infirmier_absence b
    ON a.infirmier_id = b.infirmier_id AND a.id < b.id AND a.date_debut <= b.date_fin AND b.date_debut <= a.date_fin;
-- 3. Remplacements sur un jour passé ou fermé, informatif (le nouveau code les refuse à la création, jamais à la lecture)
SELECT count(*) FROM infirmier_remplacement WHERE date_jour < current_date;
-- 4. Absences et affectations d'infirmiers désactivés (KB-PLN-35 : le nouveau code refuse d'en créer ; la présence les ignorait déjà) : informatif
SELECT count(*) FROM infirmier_absence a JOIN infirmier i ON i.id = a.infirmier_id WHERE NOT i.actif;
SELECT count(*) FROM infirmier_affectation a JOIN infirmier i ON i.id = a.infirmier_id WHERE NOT i.actif;
-- 5. Motifs d'absence à la borne (nouveau refus 400 à la saisie au-delà de 255, jamais à la lecture) : informatif
SELECT count(*) FROM infirmier_absence WHERE length(motif) >= 255;
-- 6. Absences à venir sans auteur connu (toutes celles antérieures à V47 : l'infirmier concerné peut encore les retirer, KB-PLN-39) : informatif
SELECT count(*) FROM infirmier_absence WHERE declaree_par IS NULL AND date_debut > current_date;
-- 7. Fuseau des centres (« aujourd'hui » de P3 est le jour du centre, Africa/Algiers par défaut) : centres sans fuseau ou avec un fuseau illisible
SELECT id, code, fuseau_horaire FROM centers WHERE fuseau_horaire IS NULL OR btrim(fuseau_horaire) = '';
```

Après la bascule : surveiller dans le journal d'audit les actions `AFFECTATION_AJOUTEE`, `AFFECTATION_MODIFIEE`,
`AFFECTATION_RETIREE`, `ABSENCE_DECLAREE`,
`ABSENCE_RETIREE`, `ABSENCE_DECLAREE_PAR_L_INFIRMIER`, `ABSENCE_RETIREE_PAR_L_INFIRMIER`, `REMPLACANT_AFFECTE`,
`REMPLACEMENT_ANNULE`, les statuts 400/409/422 sur
`/api/v1/infirmiers/**` (notamment 409 `PLANIFICATION_ECRITURE_CONCURRENTE`, 422 `ABSENCE_CHEVAUCHEMENT`,
`INFIRMIER_INACTIF`, `REMPLACEMENT_JOUR_FERME`) et les
notifications `INFIRMIER_ABSENCE_RETIREE`.

**Complément (revue P3, 2026-10-27)** — remplacements hérités incohérents (écrits hors API, par l'optimisation ou à la main) : le nouveau code les laisse annulables mais ne sait plus en créer. Informatif :

```sql
-- 8. Remplacements où le remplaçant est aussi le remplacé (jamais créés par l'API) : attendu 0 ligne
SELECT id, center_id, date_jour, infirmier_id FROM infirmier_remplacement WHERE infirmier_id = remplace_infirmier_id;
```

## Slice P4 — présence, alertes, charge, impression, contrôle de 07:15 (2026-10-27) — SELECT uniquement

Aucune migration de schéma. Contrôles à lancer avant le déploiement (lecture seule, sur une copie ou en lecture sur la production) :

```sql
-- 1. Ratios de sécurité hors 1-20 (lus comme le défaut 4 et journalisés par le nouveau code ; l'ancien code répondait 500 hors 1-99 et utilisait
--    tel quel 21 à 99). Décision du 27/10/2026 : on garde 1-20. Attendu 0 ligne ; sinon TRAITEMENT MANUEL AVANT DÉPLOIEMENT (corriger le ratio du centre)
SELECT center_id, patients_par_infirmier FROM planning_parametres
WHERE patients_par_infirmier IS NOT NULL AND (patients_par_infirmier < 1 OR patients_par_infirmier > 20);
-- 2. Centres actifs sans infirmier actif (désormais contrôlés à 07:15 : ils recevront une alerte de sous-effectif s'ils ont des patients) : informatif
SELECT c.id, c.code FROM centers c WHERE c.actif AND NOT EXISTS (SELECT 1 FROM infirmier i WHERE i.center_id = c.id AND i.actif);
-- 3. Doublons éventuels d'alertes de présence (ancien code : une notification par exécution) : informatif, sans suppression
SELECT center_id, type, cree_le::date AS jour, count(*) FROM notification_evenement
WHERE type IN ('INFIRMIER_SOUS_EFFECTIF', 'INFIRMIER_SUREFFECTIF') GROUP BY 1, 2, 3 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
-- 4. Modèles de documents de présence désactivés ou au format non servi (CSV, RTF, inconnu : l'impression répondra 400) : informatif
SELECT center_id, format_impression, active FROM modele_document
WHERE UPPER(type_document) = 'PLANNING_PRESENCE_INFIRMIERS' AND (NOT active OR UPPER(format_impression) NOT IN ('PDF', 'HTML', 'EXCEL', 'XLS', 'XLSX'));
-- 5. Fuseau des centres (le jour de la présence est le jour du centre) : centres sans fuseau ou avec un fuseau illisible
SELECT id, code, fuseau_horaire FROM centers WHERE fuseau_horaire IS NULL OR btrim(fuseau_horaire) = '';
```

Après le déploiement : surveiller les statuts 400 sur `/api/v1/infirmiers/presence/**` (les clients qui envoyaient des paramètres illisibles
recevaient 500), le journal `[PRESENCE]` (ratio illisible, contrôle impossible) et l'unicité des notifications `INFIRMIER_SOUS_EFFECTIF` /
`INFIRMIER_SUREFFECTIF` par centre et par jour.
