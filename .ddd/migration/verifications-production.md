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

## Slice P5 — placement, isolement, alerte du générateur (2026-10-27) — SELECT uniquement

Aucune migration de schéma. Contrôles à lancer avant le déploiement (lecture seule, copie ou lecture sur la production) :

```sql
-- 1. Centres SANS ligne planning_parametres : le premier contrôle de placement (fiche, sérologie) en CRÉERA une (valeurs par défaut) sous verrou.
--    Informatif : noter la liste, vérifier ensuite que les valeurs par défaut conviennent (ADR 0059, KB-PLN-53).
SELECT c.id, c.code FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id = c.id);
-- 2. Patients SANS risque infectieux placés dans une salle d'isolement (désormais refusés à l'enregistrement : PLACEMENT_ISOLEMENT_RESERVE ;
--    une fiche inchangée reste acceptée, mais un déplacement ou une modification de place sera refusé) : informatif, aucun traitement automatique
SELECT p.center_id, p.id, p.nom, p.prenom, s.nom AS salle FROM patients p JOIN salle s ON s.id = p.salle_id
WHERE s.isolement = 'OUI' AND NOT EXISTS (
  SELECT 1 FROM serologies_patient x WHERE x.center_id = p.center_id AND x.patient_id = p.id
    AND x.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND x.resultat = 'POSITIF'
    AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = x.center_id AND n.patient_id = x.patient_id
      AND n.marqueur = x.marqueur AND (n.date_prelevement > x.date_prelevement
        OR (n.date_prelevement = x.date_prelevement AND n.created_at > x.created_at))));
-- 3. Sérologies dont le patient n'est pas du centre de la sérologie ou n'existe pas (ancien code : acceptées ; nouveau : refusées en 422) : informatif
SELECT s.center_id, s.patient_id, count(*) FROM serologies_patient s
WHERE NOT EXISTS (SELECT 1 FROM patients p WHERE p.id = s.patient_id AND p.center_id = s.center_id) GROUP BY 1, 2;
-- 4. Centres dont le fuseau est absent (« aujourd'hui » du placement = jour du centre, KB-PLN-58)
SELECT id, code, fuseau_horaire FROM centers WHERE fuseau_horaire IS NULL OR btrim(fuseau_horaire) = '';
-- 5. Anciennes alertes GENERATEUR_INDISPONIBLE en doublon (même générateur, même jour : désormais une seule) : informatif, sans suppression
SELECT center_id, payload::jsonb->>'generateur' AS generateur, cree_le::date AS jour, count(*) FROM notification_evenement
WHERE type = 'GENERATEUR_INDISPONIBLE' GROUP BY 1, 2, 3 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
```

Après le déploiement : surveiller les 403 sur `POST`/`PUT /api/v1/patients` (garde de centre et rôles, KB-PLN-52), les 422 `PLACEMENT_*` et les 409
`PLANIFICATION_ECRITURE_CONCURRENTE`, les 400 sur `/planning/affectations` et `/planning/salles`, et l'unicité des alertes `GENERATEUR_INDISPONIBLE`,
`ISOLEMENT_IMPOSSIBLE` et `RETOUR_SALLE_ORDINAIRE_PROPOSE` par centre et par jour.

## Slice P6 — planning de la semaine et lectures publiées (2026-10-27) — SELECT uniquement

Aucune migration de schéma. Contrôles à lancer avant le déploiement (lecture seule) ; ils mesurent l'effet des décisions du 27/10 (ADR 0060) :

```sql
-- 1. Patients EN SOMMEIL qui ont encore une case (salle, créneau, jours) : ils DISPARAISSENT de la grille de la semaine (KB-PLN-71).
--    Informatif : la place reste tenue (colonnes de place inchangées, P5 les compte encore pour éviter un double placement).
SELECT center_id, count(*) AS en_sommeil_avec_case FROM patients
WHERE COALESCE(en_sommeil, FALSE) = TRUE AND salle_id IS NOT NULL AND position_id IS NOT NULL
  AND (jour_dimanche OR jour_lundi OR jour_mardi OR jour_mercredi OR jour_jeudi OR jour_vendredi OR jour_samedi)
GROUP BY center_id ORDER BY 2 DESC;
-- 2. Centres dont la file active (patients présents, hors sommeil) est de 1 à 4 : masqués ET exclus du total de /direction/capacite (KB-PLN-75).
--    Approximation de la file de l'année en cours ; la valeur exacte dépend de la période demandée.
SELECT c.id, c.code, count(p.id) AS file_active FROM centers c
LEFT JOIN patients p ON p.center_id = c.id AND COALESCE(p.en_sommeil, FALSE) = FALSE
  AND (p.date_admission IS NULL OR p.date_admission <= current_date)
  AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI')
       OR (p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= date_trunc('year', current_date)))
GROUP BY c.id, c.code HAVING count(p.id) BETWEEN 1 AND 4 ORDER BY 3;
-- 3. Générateurs de dialyse comptés par l'ancienne capacité mais PLUS par la nouvelle (hors service, en maintenance : seuls EN_SERVICE comptent, KB-PLN-76).
SELECT centre_id, statut, count(*) FROM gmao_equipements
WHERE type = 'GENERATEUR_DIALYSE' AND deleted_at IS NULL AND statut NOT IN ('EN_SERVICE', 'REFORME', 'DESACTIF')
GROUP BY centre_id, statut ORDER BY 1, 2;
-- 4. Jours d'ouverture stockés en minuscules ou mal formés (la lecture de la semaine répond 400, KB-PLN-72 reproduit) : doit être vide
SELECT center_id, jours_ouverts FROM planning_parametres
WHERE jours_ouverts IS NOT NULL AND jours_ouverts <> upper(jours_ouverts);
-- 5. Valeurs de patients_par_poste_serie hors 1..10 (lues comme 3 par défaut par la capacité Direction) : informatif
SELECT center_id, patients_par_poste_serie FROM planning_parametres WHERE patients_par_poste_serie IS NOT NULL
  AND (patients_par_poste_serie < 1 OR patients_par_poste_serie > 10);
-- 6. Jours fériés / fermetures exceptionnelles des 60 prochains jours : la capacité de leurs cases passe à 0 dans la grille (KB-PLN-72)
SELECT center_id, day_date, label FROM center_holiday WHERE day_date BETWEEN current_date AND current_date + 60
UNION ALL SELECT center_id, day_date, reason FROM center_closure_day WHERE day_date BETWEEN current_date AND current_date + 60 ORDER BY 2;
```

Écart entre les règles de présence (impact avant déploiement, revue P6) : la grille, la détection des absences et la file active excluent désormais le
sommeil et les places libérées, alors que la vue de la semaine de l'infirmier (jusqu'à P8) et la Séance (jusqu'à P9) gardent l'ancienne règle.

```sql
-- 7. Patients dont les règles divergent : en sommeil avec case (la grille ne les montre plus, la vue infirmier oui), ou sortis (place libérée) dont les colonnes de place sont encore remplies
SELECT center_id,
       count(*) FILTER (WHERE COALESCE(en_sommeil, FALSE) AND salle_id IS NOT NULL AND position_id IS NOT NULL) AS sommeil_avec_case,
       count(*) FILTER (WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND date_evenement_etat IS NOT NULL
                          AND date_evenement_etat < current_date AND salle_id IS NOT NULL AND position_id IS NOT NULL) AS sortis_avec_case,
       count(*) FILTER (WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND date_evenement_etat IS NULL) AS sortis_sans_date
FROM patients GROUP BY center_id HAVING count(*) FILTER (WHERE COALESCE(en_sommeil, FALSE) OR etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI')) > 0
ORDER BY 1;
```

Après le déploiement : surveiller les 400 sur `GET /api/v1/planning/semaine` (date ou `centerId` illisible, jours d'ouverture en minuscules) et sur
`GET /api/v1/direction/capacite`, la note « centres masqués » de l'écran de capacité de la Direction, et l'absence d'écart entre le nombre de patients
attendus (Absences, détection de nuit) et l'ancienne règle (mêmes cas ; seul le sommeil était déjà exclu).

## Slice P7 — libération des places, job de 04:00 au fuseau du centre (2026-10-27) — SELECT uniquement

> **PRÉREQUIS BLOQUANT AVANT TOUT DÉPLOIEMENT DE P7 — à lancer PAR L'UTILISATEUR (SELECT seulement) : la requête 1 ci-dessous.** Elle liste les
> centres NON actifs (`actif = FALSE`) ou sans ligne `centers` dont des patients sortis tiennent encore une place échue : le job de 04:00 ne les
> libère plus (le legacy libérait tous les centres). Un centre dont `actif` est NULL est ACTIF (`COALESCE`, comme les Absences). Toute ligne
> rendue est à traiter AVANT la mise en production (réactiver le centre ou libérer ces places avec l'utilisateur) ; résultat vide = feu vert.
> Les requêtes utilisent `current_date` (jour du serveur SQL) : au plus un jour d'écart avec le jour du centre (Africa/Algiers) autour de minuit.

Changements observables au déploiement (aucune migration) : le job ne libère plus que les centres ACTIFS
(`centers.actif`), centre par centre à son jour, un patient
par transaction (un patient en échec est signalé à l'administrateur, notification `LIBERATION_PLACES_EN_ECHEC`, une par
centre et par jour), et il prend le verrou des
places du centre (une ligne `planning_parametres` par défaut est créée pour un centre qui n'en avait pas). Le mouvement
`PLACE_LIBEREE` est daté du jour réel du centre (plus de J-1 à l'enregistrement d'une fiche) et n'est jamais écrit deux
fois de suite pour la même place. Rien n'est écrit dans `patients` hors des colonnes de place (`en_sommeil` NULL reste
NULL).

```sql
-- 1. Centres NON actifs (ou sans ligne centers) dont des patients sortis tiennent encore une place échue : le job ne les libère plus (décision du 27/10), à traiter à la main avec l'utilisateur
SELECT p.center_id, c.actif, count(*) AS places_echues
FROM patients p LEFT JOIN centers c ON c.id = p.center_id
WHERE (p.salle_id IS NOT NULL OR p.position_id IS NOT NULL OR p.generateur_id IS NOT NULL)
  AND (c.id IS NULL OR c.actif = FALSE)
  AND ((p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat < current_date)
    OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat <= current_date)
    OR (p.etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND p.date_evenement_etat IS NULL))
GROUP BY p.center_id, c.actif ORDER BY 3 DESC;
-- 2. Places échues encore tenues dans les centres actifs : doit tomber à 0 après la première nuit suivant le déploiement (sinon : notification d'échec reçue ?)
SELECT p.center_id, p.etat_patient, count(*) AS places_echues
FROM patients p JOIN centers c ON c.id = p.center_id AND COALESCE(c.actif, TRUE) = TRUE
WHERE (p.salle_id IS NOT NULL OR p.position_id IS NOT NULL OR p.generateur_id IS NOT NULL)
  AND ((p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat < current_date)
    OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat <= current_date)
    OR (p.etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND p.date_evenement_etat IS NULL))
GROUP BY p.center_id, p.etat_patient ORDER BY 3 DESC;
-- 3. États jamais libérés (permanent avec date, état inconnu, minuscules) qui tiennent une place avec une date d'évènement passée : informatif (comportement reproduit, KB-PLN-85)
SELECT center_id, etat_patient, count(*) FROM patients
WHERE (salle_id IS NOT NULL OR position_id IS NOT NULL OR generateur_id IS NOT NULL) AND date_evenement_etat < current_date
  AND (etat_patient IS NULL OR etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER'))
GROUP BY center_id, etat_patient ORDER BY 3 DESC;
-- 4. Patients sortis ET en sommeil qui tiennent une place échue : le sommeil n'empêche pas la libération (VOULU, décision du 28/10/2026, KB-PLN-83)
SELECT center_id, count(*) FROM patients WHERE COALESCE(en_sommeil, FALSE) AND (salle_id IS NOT NULL OR position_id IS NOT NULL)
  AND etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') GROUP BY center_id ORDER BY 2 DESC;
-- 5. Mouvements PLACE_LIBEREE consécutifs identiques pour un même patient (doublons de l'ancien code, KB-PLN-83) : n'augmente plus après le déploiement
SELECT center_id, patient_id, count(*) FROM mouvement_patient WHERE type = 'PLACE_LIBEREE'
GROUP BY center_id, patient_id, etat_nouveau, salle_id, position_id, generateur_id, jours_dialyse HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
-- 6. Mouvements PLACE_LIBEREE par jour d'effet sur les 14 derniers jours : la date est celle du jour réel du centre (plus de J-1 à l'enregistrement)
SELECT date_effet, count(*) FROM mouvement_patient WHERE type = 'PLACE_LIBEREE' AND date_effet >= current_date - 14 GROUP BY date_effet ORDER BY 1 DESC;
-- 7. Alertes d'échec de la libération (une par centre et par jour) : toute ligne est à traiter
SELECT center_id, count(*), max(cree_le) FROM notification_evenement WHERE type = 'LIBERATION_PLACES_EN_ECHEC' GROUP BY center_id;
-- 8. Centres actifs sans ligne planning_parametres (la ligne par défaut sera créée au premier verrou de la nuit) : informatif
SELECT c.id, c.code FROM centers c WHERE COALESCE(c.actif, TRUE) = TRUE AND NOT EXISTS (SELECT 1 FROM planning_parametres pp WHERE pp.center_id = c.id);
```

Ordre de nuit à vérifier après déploiement (Africa/Algiers) : détection des absences à 02:30, libération à 04:00 ; les
journaux contiennent `[PLACES]` à 04:00 (pas avant 02:30 + durée du contrôle).

## Slice planification-p8-optimisation (2026-11-02) : requêtes de contrôle (SELECT seulement)

Après déploiement des migrations V48 (colonnes `instance_id`, `battement_le`) et V49 (`target_user_id`). Ces 12 requêtes sont exécutées sur
le schéma réel (PostgreSQL 16 jetable) par `backend/scripts/planification-p8-pg16-rejeu.sh`, qui rejoue aussi le contrat de persistance et la
course lancement / reprise / terminaison. Lecture seule : jamais d'UPDATE ni de DELETE sur une base partagée.

```sql
-- 1. Exécutions par centre, statut et périmètre
SELECT center_id, statut, perimetre, count(*), max(cree_le) FROM planification_optimisation GROUP BY center_id, statut, perimetre ORDER BY 1, 2, 3;
-- 2. Calculs restés EN_COURS depuis plus d'une heure : à examiner (la reprise interrompt un calcul sans battement depuis 60 s)
SELECT id, center_id, cree_le, lance_par FROM planification_optimisation WHERE statut = 'EN_COURS' AND cree_le < now() - interval '1 hour' ORDER BY cree_le;
-- 3. Centres avec plusieurs calculs EN_COURS : DOIT être vide (un seul calcul par centre)
SELECT center_id, count(*) FROM planification_optimisation WHERE statut = 'EN_COURS' GROUP BY center_id HAVING count(*) > 1;
-- 4. Plus de 20 exécutions par centre : DOIT être vide (historique plafonné)
SELECT center_id, count(*) FROM planification_optimisation GROUP BY center_id HAVING count(*) > 20;
-- 5. Propositions appliquées
SELECT center_id, perimetre, count(*), max(applique_le) FROM planification_optimisation WHERE applique_le IS NOT NULL GROUP BY center_id, perimetre ORDER BY 1, 2;
-- 6. Empreintes absentes ou de longueur inattendue (64 attendus) : doit être vide
SELECT id, center_id, length(empreinte) FROM planification_optimisation WHERE empreinte IS NULL OR length(empreinte) <> 64;
-- 7. Calendriers orphelins (sans exécution) : doit être vide
SELECT c.center_id, c.run_id, count(*) FROM planification_calendrier_case c WHERE NOT EXISTS (SELECT 1 FROM planification_optimisation o WHERE o.id = c.run_id AND o.center_id = c.center_id) GROUP BY c.center_id, c.run_id;
-- 8. Déplacements temporaires dont la cible est incohérente (générateur hors service, salle ou créneau d'un autre centre) : doit être vide
SELECT d.center_id, d.patient_id, d.date_seance FROM deplacement_temporaire d WHERE NOT EXISTS (SELECT 1 FROM gmao_equipements e WHERE e.id = d.generateur_id AND e.statut = 'EN_SERVICE' AND e.deleted_at IS NULL) OR NOT EXISTS (SELECT 1 FROM salle s WHERE s.id = d.salle_id AND s.center_id = d.center_id) OR NOT EXISTS (SELECT 1 FROM position_creneau p WHERE p.id = d.creneau_id AND p.center_id = d.center_id) ORDER BY d.date_seance;
-- 9. Déplacements temporaires passés : informatif
SELECT center_id, count(*), min(date_seance), max(date_seance) FROM deplacement_temporaire WHERE date_seance < current_date GROUP BY center_id;
-- 10. Remplacements sans remplacé : informatif (héritage d'avant la liaison du remplacé, KB-PLN-97 ; la couverture en relie désormais un quand il existe)
SELECT center_id, count(*) FROM infirmier_remplacement WHERE remplace_infirmier_id IS NULL GROUP BY center_id;
-- 11. Alertes de l'optimisation (dont les deux alertes personnelles d'infirmier)
SELECT type, count(*), max(cree_le) FROM notification_evenement WHERE type IN ('OPTIMISATION_PROPOSITION','SEANCES_DEPLACEES','REPLANIFICATION_NOCTURNE','INFIRMIER_ROULEMENT_MODIFIE','INFIRMIER_REMPLACEMENT_AFFECTE') GROUP BY type;
-- 12. Centres actifs avec replanification de nuit (ceux que le job de 02:30 traite)
SELECT r.center_id FROM planification_reglages r JOIN centers c ON c.id = r.center_id AND COALESCE(c.actif, TRUE) = TRUE WHERE r.replanification_auto = TRUE ORDER BY 1;
```

Attendus après la première nuit : (3), (4), (6), (7), (8) vides ; aucune ligne `EN_COURS` ancienne en (2) ; les alertes personnelles
de (11) portent `target_user_id` pour les infirmiers ayant un compte.

## Slice planification-p9-retrait-du-legacy (2026-11-02) : requêtes de contrôle (SELECT seulement)

Effet de la présence unique (KB-PLN-50 et KB-PLN-106, décision du 2026-11-02) : un patient en sommeil n'est plus compté **nulle part** :
ni dans les occupants, ni dans le nombre `patients` d'une case, ni dans l'effectif requis, ni dans les alertes de sous-effectif et de
sur-effectif (07:15 et écran), ni dans la charge, ni dans « Mon planning ». Il garde sa place sur sa fiche. À mesurer en recette, avant
livraison, pour annoncer l'effet aux infirmiers : les requêtes 1, 3 et 4 (SELECT seul).

```sql
-- 1. Patients en sommeil qui tiennent encore une place (ils n'apparaissent plus dans la présence ni dans « Mon planning » mais gardent leur place)
SELECT center_id, count(*) FROM patients WHERE en_sommeil = TRUE AND salle_id IS NOT NULL AND position_id IS NOT NULL GROUP BY center_id;
-- 2. Capacité des salles (RG-PLN-061) : salles dont le nombre de générateurs affectés dépasse la capacité (informatif)
SELECT s.center_id, s.code, s.capacite, count(g.id) AS generateurs FROM salle s
  JOIN gmao_equipements g ON g.salle_id = s.id AND g.type = 'GENERATEUR_DIALYSE' AND g.deleted_at IS NULL AND g.statut NOT IN ('REFORME','DESACTIF')
 WHERE s.capacite IS NOT NULL GROUP BY s.center_id, s.code, s.capacite HAVING count(g.id) > s.capacite;
-- 3. Cases de la grille type (centre, salle, créneau, jour de dialyse) qui contiennent au moins un patient en sommeil : patients et effectif requis
--    AVANT (sommeil compté) et APRÈS (sommeil exclu), au ratio du centre (4 par défaut). Approximation : états de sortie écartés, dates de séjour ignorées.
SELECT p.center_id, p.salle_id, p.position_id, j.jour,
       count(*) AS patients_avant, count(*) FILTER (WHERE NOT COALESCE(p.en_sommeil, FALSE)) AS patients_apres,
       ceil(count(*)::numeric / COALESCE(pp.patients_par_infirmier, 4)) AS requis_avant,
       ceil(count(*) FILTER (WHERE NOT COALESCE(p.en_sommeil, FALSE))::numeric / COALESCE(pp.patients_par_infirmier, 4)) AS requis_apres
FROM patients p
CROSS JOIN LATERAL (VALUES ('DIMANCHE', p.jour_dimanche), ('LUNDI', p.jour_lundi), ('MARDI', p.jour_mardi), ('MERCREDI', p.jour_mercredi),
                           ('JEUDI', p.jour_jeudi), ('VENDREDI', p.jour_vendredi), ('SAMEDI', p.jour_samedi)) AS j(jour, coche)
LEFT JOIN planning_parametres pp ON pp.center_id = p.center_id
WHERE j.coche AND p.salle_id IS NOT NULL AND p.position_id IS NOT NULL
  AND COALESCE(p.etat_patient, 'PERMANENT') NOT IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI')
GROUP BY p.center_id, p.salle_id, p.position_id, j.jour, pp.patients_par_infirmier
HAVING count(*) FILTER (WHERE COALESCE(p.en_sommeil, FALSE)) > 0
ORDER BY p.center_id, p.salle_id, p.position_id, j.jour;
-- 4. Par centre : nombre de cases dont l'effectif requis BAISSE (moins de sous-effectif, plus de sur-effectif possible : alertes différentes à 07:15)
SELECT center_id, count(*) AS cases_dont_le_requis_baisse FROM (
  SELECT p.center_id, p.salle_id, p.position_id, j.jour
  FROM patients p
  CROSS JOIN LATERAL (VALUES ('DIMANCHE', p.jour_dimanche), ('LUNDI', p.jour_lundi), ('MARDI', p.jour_mardi), ('MERCREDI', p.jour_mercredi),
                             ('JEUDI', p.jour_jeudi), ('VENDREDI', p.jour_vendredi), ('SAMEDI', p.jour_samedi)) AS j(jour, coche)
  LEFT JOIN planning_parametres pp ON pp.center_id = p.center_id
  WHERE j.coche AND p.salle_id IS NOT NULL AND p.position_id IS NOT NULL
    AND COALESCE(p.etat_patient, 'PERMANENT') NOT IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI')
  GROUP BY p.center_id, p.salle_id, p.position_id, j.jour, pp.patients_par_infirmier
  HAVING ceil(count(*)::numeric / COALESCE(pp.patients_par_infirmier, 4))
       > ceil(count(*) FILTER (WHERE NOT COALESCE(p.en_sommeil, FALSE))::numeric / COALESCE(pp.patients_par_infirmier, 4))
) t GROUP BY center_id ORDER BY 2 DESC;
```

Attendu : (1) le nombre de patients qui ne compteront plus ; (3) et (4) l'impact chiffré sur le requis et les alertes, à annoncer aux
infirmiers et à la direction ; (2) sans effet de bord. Les requêtes 1, 3 et 4 ont été exécutées sans erreur sur un PostgreSQL 16 jetable
(schéma Flyway) ; la requête 2 lit `gmao_equipements.salle_id`, colonne créée par JPA et absente des migrations Flyway (présente en production).

## 35. Planification P1 à P9 : liste ordonnée des contrôles avant la mise en service (2026-11-02)

Source : `.ddd/verify/verify-planification.md` (§ 3), sections 32 à 34 et « Slice P4 » à « Slice P9 » de ce fichier. **Lecture seule, `SELECT`
uniquement**, sur une copie ou en lecture sur la production, **dans cet ordre** : les lignes 0 à 9 sont **bloquantes** (aucun déploiement
tant qu'une ligne annoncée « 0 ligne » en renvoie une ou qu'un prérequis manque) ; 10 à 17 sont informatives ; 18 est la surveillance après
déploiement. Jamais de `DROP`/`DELETE`/`UPDATE` pour « corriger » un écart relevé ici : le rapporter d'abord (règle de sécurité du legacy n° 7).

**Décisions encore « à décider » (non tranchées, reproduites telles quelles par le nouveau code, à faire arbitrer par l'utilisateur)**

| KB | Comportement reproduit | Question |
|---|---|---|
| KB-PLN-04 | nombres décimaux tronqués sans erreur (ratio 4.9 lu 4, 20.9 lu 20, 0.9 refusé ; taux d'activité 75.5 lu 75) | refuser les décimaux ? |
| KB-PLN-06 | salle d'isolement inconnue = même 400 qu'une salle d'un autre centre ; colonne `planning_parametres.salles_isolement` jamais réécrite (reste NULL ou valeur ancienne) | conserver ; retirer la colonne à terme ? |
| KB-PLN-14 | noms de la fiche et du compte non synchronisés, dans les deux sens | propager (dans quel sens) ou assumer deux noms ? |

Décidé le 2026-11-02 (et non plus « à décider ») : le patient en sommeil est exclu partout (KB-PLN-50, KB-PLN-106) ; la libération d'une
place échue n'est pas empêchée par le sommeil (VOULU, 28/10/2026, KB-PLN-83).

| Ordre | Contrôle (requête de la section citée) | Réf. | Attendu / décision |
|---|---|---|---|
| 0 | **Prérequis** : version Flyway courante (V47 déployée ; V48 et V49 en attente), base de copie disponible | § 8, § 34, P8 | **BLOQUANT** : V47 présente avant déploiement ; V48 puis V49 appliquées par le déploiement |
| 1 | Fuseau des centres absent ou illisible | § 34-7 (= P4-5, P5-4) | 0 ligne ; sinon fixer le fuseau du centre |
| 2 | Ratio hors 1 à 20 ; patients par poste hors 1 à 10 | P4-1, § 32-4, P6-5 | **BLOQUANT** : traitement manuel avant déploiement |
| 3 | Jours d'ouverture illisibles ou en minuscules | § 32-3, P6-4 | **BLOQUANT** (sinon 400 sur la semaine) |
| 4 | Compétences illisibles ; taux d'activité hors 10 à 100 ; réglages hors bornes | § 32-2, § 32-6, § 32-5 | **BLOQUANT** (sinon lecture en erreur) |
| 5 | Comptes liés à des fiches de plusieurs centres | § 33-5 | **BLOQUANT** : délier, ou créer un compte par centre |
| 6 | Doublons de matricule selon la casse ; doublons d'`username` | § 33-1, § 33-3 | **BLOQUANT** : régulariser |
| 7 | Affectations qui se chevauchent ; absences qui se chevauchent | § 34-1, § 34-2 | 0 ligne attendue ; sinon régulariser à l'écran |
| 8 | **Centres inactifs (ou sans ligne `centers`) qui tiennent des places échues** | P7-1 | **PRÉREQUIS BLOQUANT** : à traiter avant la première nuit, sinon la libération de 04:00 les ignore ou échoue |
| 9 | Calculs d'optimisation `EN_COURS`, ou plusieurs par centre, **à lancer avant V48** puis après déploiement | P8-2, P8-3 | 0 ligne (sinon la reprise les interrompt) |
| 10 | Centres sans ligne `planning_parametres` (la ligne par défaut sera créée) | § 32-7, P5-1, P7-8 | Informatif : faire valider les valeurs par défaut |
| 11 | Patients sains en salle d'isolement ; sérologies hors centre | P5-2, P5-3 | Informatif (refus futur) |
| 12 | **Patients en sommeil avec une case : nombre, et impact sur le requis et les alertes** ; sortis encore placés | P9-1, P9-3, P9-4, P6-1, P6-7, P7-3, P7-4 | Informatif ; **à annoncer** aux infirmiers (« Mon planning », alertes de 07:15) et à la direction ; mesure SELECT seule à faire en recette avant livraison |
| 13 | File active de 1 à 4 patients (centres masqués) ; générateurs hors `EN_SERVICE` ; fériés à 60 jours | P6-2, P6-3, P6-6 | Informatif |
| 14 | Fiche/compte incohérents ; fiche dont le compte est supprimé ; colonnes à la borne | § 33-4, § 33-6, § 33-7 | Informatif |
| 15 | Remplacements passés, auto-remplacements, sans remplacé ; absences ou affectations d'inactifs ; motifs de 255 caractères ; absences futures sans auteur | § 34-3, § 34-8, P8-10, § 34-4, § 34-5, § 34-6 | Informatif |
| 16 | Préférences de patients sortis, profils inactifs ; centres sans infirmier actif ; modèles d'impression ; doublons de notifications | § 32-1, P4-2, P4-4, P4-3, P5-5 | Informatif |
| 17 | Capacité des salles dépassée (RG-PLN-061) | P9-2 | Informatif |
| 18 | **Après déploiement** : P7-2 à 0 après la première nuit ; P7-5 à P7-7 ; P8-1 à P8-12 ; journaux `[PRESENCE]` et `[PLACES]` ; ordre 02:30, 04:00, 07:15 | P7, P8 | Surveillance |

**À rapporter** : pour chaque ligne, le nombre de lignes renvoyées par centre et, pour les lignes bloquantes, la liste des centres ou
comptes concernés.

## 36. Organisation O1 à O5 : schéma et droits (deux migrations additives V51 et V52, 2026-11-02) — SELECT seulement

Contexte : `SallesJdbc` lit `salle.ouverte`, `MachinesJdbc` et `ProtocolesJdbc` lisent `machine` et `protocole`. Ces trois éléments
n'existaient que dans `db/schema.sql` (H2), pas dans la chaîne Flyway V1..V50. La migration **V51** (additive, idempotente : `ADD COLUMN
IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`, aucune donnée modifiée) les crée ; la migration **V52** ajoute `medecin.actif` (suppression
logique des médecins, `DEFAULT TRUE`). Elles doivent être appliquées AVANT le déploiement du code.
Rejeu PostgreSQL 16 jetable : `backend/scripts/organisation-pg16-rejeu.sh`. Rejeu du filet contre le legacy réel (preuve que les goldens
legacy sont exacts) : `backend/scripts/organisation-rejeu-legacy.sh`.

| N° | Requête (SELECT) | Attendu | Si écart |
|---|---|---|---|
| ORG-1 | `SELECT column_name FROM information_schema.columns WHERE table_name = 'salle' AND column_name = 'ouverte'` | 1 ligne après V51 (0 avant : appliquer V51) | Bloquant : ne pas déployer |
| ORG-2 | `SELECT table_name FROM information_schema.tables WHERE table_name IN ('machine','protocole')` | 2 lignes après V51 | Bloquant |
| ORG-3 | `SELECT count(*) FROM salle WHERE ouverte = FALSE` | 0 juste après V51 (les salles existantes restent ouvertes) | Informatif : une valeur > 0 avant action utilisateur est anormale |
| ORG-4 | `SELECT count(*) FROM machine` ; `SELECT count(*) FROM protocole` | 0 et 0 juste après V51 | Informatif |
| ORG-5 | `SELECT m.id, m.center_id FROM medecin m LEFT JOIN centers c ON c.id = m.center_id WHERE c.id IS NULL` | 0 ligne (médecins orphelins de centre) | À corriger avec le métier avant d'activer le filtre de centre |
| ORG-6 | `SELECT s.id, s.center_id FROM salle s LEFT JOIN centers c ON c.id = s.center_id WHERE c.id IS NULL` ; idem `position_creneau` | 0 ligne | Idem |
| ORG-7 | `SELECT center_id, code, count(*) FROM salle GROUP BY center_id, code HAVING count(*) > 1` ; idem `position_creneau` | 0 ligne (doublons de code) | Informatif |
| ORG-8 | `SELECT s.id FROM societes s WHERE NOT EXISTS (SELECT 1 FROM centers c WHERE c.societe_id = s.id AND c.actif)` | 0 ligne (société sans centre actif) | Bloquant pour l'invariant « dernier centre actif » |
| ORG-9 | `SELECT count(*) FROM medecin x JOIN patients p ON p.medecin_traitant_id = x.id` | Informatif : médecins référencés par des patients (décision du 2026-11-02 : suppression LOGIQUE, `medecin.actif`, migration V52) | Informatif |
| ORG-10 | `SELECT column_name FROM information_schema.columns WHERE table_name = 'medecin' AND column_name = 'actif'` ; `SELECT count(*) FROM medecin WHERE actif = FALSE` | 1 ligne après V52 ; 0 médecin désactivé juste après V52 | Bloquant si la colonne manque : appliquer V52 avant le déploiement |
| ORG-11 | `SELECT code, count(*) FROM centers GROUP BY code HAVING count(*) > 1` | 0 ligne (doublons de code de centre : `centers.code` n'a PAS de contrainte d'unicité en base ; l'unicité n'est vérifiée que par lecture puis écriture) | À corriger avec le métier. Tant que des doublons existent, ne PAS créer d'index unique (la création échouerait). Une fois ORG-11 vide, proposition (NON livrée comme migration Flyway : un index unique qui échoue bloquerait le démarrage) : `CREATE UNIQUE INDEX IF NOT EXISTS ux_centers_code ON centers (code)` |
| ORG-12 | `SELECT u.id, u.username FROM app_user u WHERE u.active AND NOT EXISTS (SELECT 1 FROM app_user_center c WHERE c.user_id = u.id)` | Comptes actifs sans centre. Les comptes SUPERADMIN et DIRECTION n'ont pas de centre par conception ; **tout compte ADMIN (ou autre rôle de centre) de cette liste perd l'accès à `GET /api/v1/referentials/*` (200 devient 403, garde de centre)** | À rattacher à un centre avant le déploiement, ou à accepter |

Contrôles de droits après déploiement (avec comptes de test, jamais en production réelle) : `POST /api/v1/organisation/referentiels/medecins`
et `POST /api/v1/organisation/salles/{id}/ouvrir` avec un SECRETAIRE ou un ADMIN d'un autre centre : **403** ; `GET /api/v1/referentials/salles?centerId=<autre centre>`
avec un compte de centre : **403** (garde de centre, décision du 2026-11-02 ; le legacy répondait 200) ; un identifiant ou un paramètre UUID
illisible : **400** (décision du 2026-11-02, KB-ORG-02 ; le legacy répondait 500) ; sans `centerId` : 500 comme le legacy. Un SUPERADMIN
atteint `/api/v1/societes/**`, `/api/v1/organisation/societes` et `/api/v1/organisation/centres`, et RIEN d'autre sous `/api/v1/organisation`
(403 `ROLE_SCOPE`, comme `/api/v1/referentials` et `/api/v1/admin/referentials`). Voir `.ddd/safety-net/organisation-retroactif.md`.

## 37. Dossier médical : gel du module et revert de `lignes_demande_examen.center_id` (2026-11-02) — SELECT seulement
Décision : AUCUNE migration. Le commit `21f6ff7a` avait ajouté au code legacy une colonne JPA `center_id` (NOT NULL) absente de Flyway V1..V52 ;
le code est revenu à la version de `21f6ff7a^` (rejeu PostgreSQL 16 jetable : `backend/scripts/dossier-medical-pg16-rejeu.sh`, 14 entités médicales
conformes à Flyway, `lignes_demande_examen` sans `center_id`). À exécuter en production AVANT le prochain déploiement :
```sql
-- 1. La colonne ne doit pas exister (ou, si une base de dev en `update` l'a créée, voir 2).
SELECT count(*) FROM information_schema.columns WHERE table_name = 'lignes_demande_examen' AND column_name = 'center_id';
-- 2. Seulement si la requête 1 renvoie 1 : lignes sans centre (aucune action destructive, constat seul).
SELECT count(*) FROM lignes_demande_examen WHERE center_id IS NULL;
-- 3. Aucune table du module gelé ne doit exister (attendu : 0 ligne).
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
  AND table_name IN ('serologies', 'antecedents', 'allergies', 'bilans_pregreffe', 'etapes_bilan_pregreffe');
-- 4. Les tables réelles du legacy médical existent (attendu : 9 lignes au moins).
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
  AND table_name IN ('serologies_patient', 'antecedents_medicaux', 'allergies_patient', 'bilans_pre_greffe', 'etapes_bilan_pre_greffe',
                     'ordonnances', 'prescriptions_medicales', 'demandes_examen', 'observations_biologiques');
```
Aucune route `/api/v1/dossier-medical/*` n'est servie (le module n'est pas dans le jar). Les routes réelles restent `/api/v1/patients/{id}/...` (rôles
inchangés : `MEDECIN` en écriture, `MEDECIN`/`ADMIN` en lecture). Voir `backend/contexts/dossier-medical/GEL.md` et l'ADR 0072.

## 38. Patient et couverture : gel du module, aucun changement de production (2026-11-02) — SELECT seulement
Décision : AUCUNE migration et aucun revert. Audit de `2978e639` et des commits suivants : entités JPA patient, assuré, affectation, attestation et PEC inchangées ;
aucune table ajoutée à `schema.sql` pour ce contexte ; aucune entrée patient-couverture dans `covered.json`. Rejeu PostgreSQL 16 jetable :
`backend/scripts/patient-couverture-pg16-rejeu.sh` (V1..V52, 5 entités conformes, `patients.date_sortie` absente). Le module gelé n'est pas dans le jar : aucune route
`/api/v1/patient-couverture/*` n'est servie. Contrôles ordonnés proposés par `verify-patient-couverture.md`, à exécuter AVANT la reprise (constats seuls) :
```sql
-- 1. Sortis sans date d'évènement (aussi § 25.2) : le legacy les refuse à l'écriture (RG-PAT-031) mais des lignes anciennes peuvent exister.
SELECT center_id, etat_patient, count(*) FROM patients
 WHERE etat_patient IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI') AND date_evenement_etat IS NULL GROUP BY 1, 2;
-- 2. Valeurs distinctes des états et des types (vocabulaire réel du legacy).
SELECT etat_patient, type_patient, count(*) FROM patients GROUP BY 1, 2 ORDER BY 1, 2;
-- 3. Deux assurés principaux actifs pour un même patient (règle R1 non imposée par la base).
SELECT patient_id, center_id, count(*) FROM assure_patient
 WHERE is_primary = true AND date_fin_affectation IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
-- 4. PEC à période incohérente (début après fin).
SELECT statut, count(*) FROM prise_en_charge
 WHERE (date_fin_effectif IS NOT NULL AND date_debut_effectif > date_fin_effectif)
    OR (date_fin_demande IS NOT NULL AND date_debut_demande > date_fin_demande) GROUP BY 1;
-- 5. Double source de l'assuré : champs assure_* de patients renseignés alors qu'aucune affectation n'existe.
SELECT count(*) FROM patients p
 WHERE p.assure_nom IS NOT NULL AND NOT EXISTS (SELECT 1 FROM assure_patient ap WHERE ap.patient_id = p.id);
-- 6. Après reprise et câblage (hors périmètre du gel) : relancer les goldens legacy sur le nouveau code et contrôler la garde de centre (`*_cross_centre_adminB`).
```
Les noms de colonnes viennent des entités JPA (vérifiées par le rejeu PG16). Voir `backend/contexts/patient-couverture/GEL.md` et l'ADR 0073.

## 39. Séance : gel du module, aucun changement de comportement en production (2026-11-02) — SELECT seulement
Décision : AUCUN revert de comportement. Audit de `2978e639` : le code legacy de Séance (`bc-seance`, `SeanceRestController`, adapters, `SeanceJpaEntity`) n'est pas modifié sans filet ; résidus corrigés : `CacheConfig` (2 caches inutilisés retirés), colonne H2 `seances.verrouille_facturation` retirée de `schema.sql`, entrée `SeanceUseCase.java` retirée de `covered.json`. Rejeu PostgreSQL 16 jetable : `backend/scripts/seance-pg16-rejeu.sh` (V1..V52, aucun doublon de version, `SeanceJpaEntity` conforme, `pec_id` / `type_seance` / `verrouille_facturation` absentes). Le module gelé n'est pas dans le jar : aucune route nouvelle.

**Migration V50 `derogations_regularisation`** (renumérotée depuis V22, doublon de `V22__fiche_article_complete`) : table utilisée par le seul module gelé, donc par aucun code déployé. Conservée (additive, idempotente, vide). Contrôles (constats seuls, rien à supprimer sans validation écrite) :
```sql
-- 1. La version 22 est bien la fiche article (une seule ligne) et la 50 la dérogation.
SELECT version, description FROM flyway_schema_history WHERE version IN ('22', '50') ORDER BY installed_rank;
-- 2. La table existe et est vide (attendu : 0 ligne).
SELECT count(*) FROM derogations_regularisation;
-- 3. Colonnes inexistantes lues par le module gelé (attendu : 0 ligne, ne PAS les créer).
SELECT column_name FROM information_schema.columns WHERE table_name = 'seances' AND column_name IN ('pec_id', 'type_seance', 'verrouille_facturation');
-- 4. Vocabulaire réel des statuts (le module gelé ignore FACTUREE et ABSENT).
SELECT statut, count(*) FROM seances GROUP BY 1 ORDER BY 1;
-- 5. Séances facturées sans facture, ou facture sans statut FACTUREE (incohérence du verrou legacy).
SELECT statut, count(*) FROM seances WHERE (statut = 'FACTUREE') <> (facture_id IS NOT NULL) GROUP BY 1;
```
Voir `backend/contexts/seance/GEL.md` et l'ADR 0074.

## 40. Pilotage direction : gel du module, aucun changement de production (2026-11-02) — SELECT seulement
Décision : AUCUN revert. Audit depuis `2978e639^` : le legacy Direction (`application/direction`, `DirectionDashboardRestController`, `DirectionSnapshotRestController`, tests) n'est pas modifié ; aucune table ni colonne ajoutée au schéma de production ; `schema.sql` H2 de `platform` sans table parallèle ; aucune entrée `covered.json` à retirer. Rejeu PostgreSQL 16 jetable : `backend/scripts/pilotage-direction-pg16-rejeu.sh` (V1..V52, colonnes de `direction_snapshot` et `direction_alert_history` lues en JDBC par le legacy présentes, tables parallèles absentes). Le module gelé n'est pas dans le jar : aucune route `/api/v1/pilotage/*`. Contrôles (constats seuls) :
```sql
-- 1. Les tables parallèles du module gelé n'existent pas (attendu : 0 ligne, ne PAS les créer).
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('tableaux_de_bord', 'instantanes_mensuels', 'alertes_direction', 'exports_synthese');
-- 2. Volumes des tables legacy réellement utilisées.
SELECT (SELECT count(*) FROM direction_snapshot) AS instantanes, (SELECT count(*) FROM direction_alert_history) AS alertes;
-- 3. Alertes ouvertes par centre et code (base de la décision R2 à la reprise : plusieurs codes ouverts le même jour ?).
SELECT center_id, code, count(*) FROM direction_alert_history WHERE resolved_at IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
SELECT center_id, first_seen_at::date AS jour, count(DISTINCT code) FROM direction_alert_history GROUP BY 1, 2 HAVING count(DISTINCT code) > 1 LIMIT 20;
```
Voir `backend/contexts/pilotage-direction/GEL.md` et l'ADR 0075.

## Patient et couverture PC1 à PC5 : ORDRE D'EXÉCUTION des contrôles (revue du 2026-11-05)
Les §41 à §44 ci-dessous sont des listes de requêtes par slice ; l'ordre à suivre avant la mise en production est celui-ci :
1. **Fuseaux** : §43-0 (`SHOW timezone`, `centers.fuseau_horaire` nul / vide / illisible, fuseau de la JVM `TZ` / `-Duser.timezone`). Lire tous les contrôles en `current_date` (§43-5, §43-6, §44-1 à §44-3) avec ce fuseau.
2. Données bloquantes : §41-1 (doublons de numéro d'assurance), §41-2 à §41-4 (états, dates), §42-1 (doubles assurés principaux), §42-6 et §42-7 (PEC orphelines, statuts hors cycle), §42-9 et §44-4 (forfaits absents ou d'un autre centre), §43-1 et §43-2 (attestations orphelines, périodes inversées).
3. Centres et comptes : §42-11 (centres sans `planning_parametres`), §42-12 (comptes ADMIN sans centre), à lancer UNE fois (valables pour PC2, PC3 et PC4).
4. Procédure : import et rollback de migration hors activité du centre, puis rejouer §42-1, §42-2, §43-3 et §43-4.
5. Volumétrie à annoncer (non bloquant) : §41-5 à §41-8, §42-2 à §42-5, §42-8, §42-10, §43-3 à §43-6.
6. Parité avant ET après déploiement, même fuseau de session : §44-1 à §44-4 (§44-2 : le code prend désormais le jour du CENTRE, la requête `current_date` : session au fuseau du centre).

## 41. Patient et couverture PC1 (identité et mouvements) : reprise sur place, session S2 (2026-11-03) — SELECT seulement
Le module `contexts/patient-couverture` est de nouveau dans le réacteur (mêmes tables `patients` et `mouvement_patient`, mêmes URLs `/api/v1/patients/**` et `/api/v1/mouvements-patients`). Aucune contrainte SQL ni index n'est créé. Constats à lancer AVANT la bascule (recette), le résultat décide d'un éventuel nettoyage manuel validé par écrit ; rejeu sur schéma PostgreSQL 16 jetable : `backend/scripts/patient-couverture-pc1-pg16-rejeu.sh`.
```sql
-- 1. Doublons de numéro d'assurance par centre (casse et espaces ignorés : l'unicité est désormais contrôlée à la création ET à la modification). Attendu : 0 ligne ; sinon corriger à la main avant la bascule. Index proposé SEULEMENT après nettoyage : CREATE UNIQUE INDEX ... ON patients (center_id, UPPER(TRIM(numero_assurance))) (non créé).
SELECT center_id, UPPER(TRIM(numero_assurance)), count(*) FROM patients GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
-- 2. États hors de la liste fermée (rendus tels quels et jamais réécrits par un PUT sans état ; le patient n'est pas plaçable, 422 PATIENT_ETAT_INCONNU ; la saisie d'un tel état est refusée en 422). À corriger à la main AVANT la mise en production si le volume n'est pas nul (une sortie mal orthographiée ne libère plus de place).
SELECT center_id, etat_patient, count(*) FROM patients WHERE etat_patient IS NOT NULL AND etat_patient NOT IN ('PERMANENT','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER','TRANSFERE','DECEDE','GREFFE','GUERRI') GROUP BY 1, 2 ORDER BY 3 DESC;
-- 3. Sorties et séjours limités sans date d'évènement (la saisie exige désormais la date aussi pour un séjour limité : ces fiches ne peuvent plus être enregistrées, 422 à chaque PUT, tant qu'on ne les corrige pas ; idem pour la requête 4 : annoncer le volume aux secrétaires avant la mise en production).
SELECT center_id, etat_patient, count(*) FROM patients WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') AND date_evenement_etat IS NULL GROUP BY 1, 2 ORDER BY 3 DESC;
-- 4. Naissance future ou postérieure à l'admission (désormais refusée en 422 à la saisie).
SELECT center_id, count(*) FROM patients WHERE date_naissance > current_date OR date_naissance > date_admission GROUP BY 1 ORDER BY 2 DESC;
-- 5. Lignes héritées aux colonnes nulles (lues avec les défauts : permanent, assuré lui-même, jours faux).
SELECT center_id, count(*) FILTER (WHERE etat_patient IS NULL) AS etat_nul, count(*) FILTER (WHERE qualite_assure IS NULL) AS qualite_nulle, count(*) FILTER (WHERE en_sommeil IS NULL) AS sommeil_nul FROM patients GROUP BY 1;
-- 6. Assuré lui-même dont le numéro d'assuré diffère du numéro du patient (divergence historique du PUT, KB-PAT-03).
SELECT center_id, count(*) FROM patients WHERE qualite_assure = 'ASSURE_LUI_MEME' AND assure_numero_assurance IS DISTINCT FROM numero_assurance GROUP BY 1 ORDER BY 2 DESC;
-- 7. Patients sans mouvement d'admission ; 8. types de mouvement hors liste (une liste complète des mouvements échouait en 400 avant, KB-PAT-13).
SELECT p.center_id, count(*) FROM patients p WHERE NOT EXISTS (SELECT 1 FROM mouvement_patient m WHERE m.patient_id = p.id AND m.type = 'ADMISSION') GROUP BY 1 ORDER BY 2 DESC;
SELECT type, count(*) FROM mouvement_patient WHERE type NOT IN ('ADMISSION','SEJOUR_TEMPORAIRE','REPRISE','TRANSFERT','DECES','GREFFE','GUERISON','PLACE_LIBEREE') GROUP BY type;
```
Ordre global des verrous des écritures de fiche : centre (Planification, `PlacementDesPatients.verifier`) puis fiche puis app_user ; `lock_timeout` posé à l'entrée de l'unité de travail, refus en 409. Les contrôleurs legacy `PatientRestController` et `MouvementPatientRestController` et les services, adapters et DTO de la fiche devenus morts sont SUPPRIMÉS (liste dans `.ddd/safety-net/seams.md`, seams fermés le 2026-11-03) ; les URL sont servies par `contexts/patient-couverture`. Rollback : revenir au commit précédent (pas de drapeau de routage, refactoring sur place, ADR 0024).

## 42. Patient et couverture PC2 (assuré principal) et PC3 (prise en charge) : reprise sur place, session S3 (2026-11-03) — SELECT seulement
Mêmes tables (`assure`, `assure_patient`, `prise_en_charge`) et mêmes URL ; aucune contrainte SQL ni index créé (ADR 0077). Constats à lancer AVANT la bascule ; aucun ne modifie la base.
```sql
-- 1. Patients avec plusieurs assurés principaux actifs (R1, KB-PAT-29). Attendu : 0 ligne ; sinon corriger à la main (la règle est désormais tenue sous le verrou du centre, pas par le SQL).
SELECT patient_id, center_id, count(*) FROM assure_patient WHERE is_primary AND date_fin_affectation IS NULL GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
-- 2. Assurés partagés entre centres : numéro d'assuré lié à des patients de plusieurs centres (la capture d'un assuré d'un autre centre est désormais refusée 422, KB-PAT-31) ou ligne d'affectation dans un centre différent de celui de l'assuré.
SELECT ap.numero_assurance, count(DISTINCT ap.center_id) AS centres FROM assure_patient ap GROUP BY 1 HAVING count(DISTINCT ap.center_id) > 1 ORDER BY 2 DESC LIMIT 50;
SELECT ap.numero_assurance, ap.center_id AS centre_affectation, a.center_id AS centre_assure FROM assure_patient ap JOIN assure a ON a.numero_assurance = ap.numero_assurance WHERE a.center_id <> ap.center_id LIMIT 50;
-- 3. Lignes assure_patient dupliquées (même patient, même assuré, KB-PAT-32 : une ligne ajoutée à chaque enregistrement). Informatif ; le nouveau code n'en ajoute plus.
SELECT patient_id, numero_assurance, count(*) FROM assure_patient GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50;
-- 4. Affectations sans date de début ou de durée nulle ou inversée (correction de dates permissive reproduite).
SELECT center_id, count(*) FILTER (WHERE date_debut_affectation IS NULL) AS sans_debut, count(*) FILTER (WHERE date_fin_affectation < date_debut_affectation) AS inversees, count(*) FILTER (WHERE date_fin_affectation = date_debut_affectation) AS duree_nulle FROM assure_patient GROUP BY 1;
-- 5. Assurés sans nom (le PUT exige désormais le nom, 400, KB-PAT-33 : ces assurés ne pourront plus être modifiés sans le saisir).
SELECT center_id, count(*) FROM assure WHERE nom IS NULL OR TRIM(nom) = '' GROUP BY 1 ORDER BY 2 DESC;
-- 6. PEC sans centre ou sans patient existant, ou patient d'un autre centre (la garde de centre les rendra introuvables ou refusées).
SELECT count(*) FROM prise_en_charge WHERE center_id IS NULL OR patient_id IS NULL;
SELECT c.center_id AS centre_pec, p.center_id AS centre_patient, count(*) FROM prise_en_charge c JOIN patients p ON p.id = c.patient_id WHERE c.center_id <> p.center_id GROUP BY 1, 2;
SELECT count(*) FROM prise_en_charge c WHERE NOT EXISTS (SELECT 1 FROM patients p WHERE p.id = c.patient_id);
-- 7. Statuts hors du cycle (REFUSEE, minuscules, vide) : la lecture rend 400 « Statut de prise en charge inconnu » pour la validation, la clôture et session-allowed.
SELECT statut, count(*) FROM prise_en_charge WHERE statut NOT IN ('CREE','VALIDEE','CLOTUREE') GROUP BY 1;
-- 8. PEC validées simultanément pour un même patient, chevauchements et dates absentes ou inversées (aucun contrôle, KB-PAT-24, ARGENT : à décider avec les métiers).
SELECT patient_id, count(*) FROM prise_en_charge WHERE statut = 'VALIDEE' GROUP BY 1 HAVING count(*) > 1 ORDER BY 2 DESC LIMIT 50;
SELECT a.patient_id, a.id, b.id FROM prise_en_charge a JOIN prise_en_charge b ON a.patient_id = b.patient_id AND a.id < b.id AND a.date_debut_demande <= b.date_fin_demande AND b.date_debut_demande <= a.date_fin_demande LIMIT 50;
SELECT center_id, count(*) FILTER (WHERE date_debut_demande IS NULL OR date_fin_demande IS NULL) AS sans_dates, count(*) FILTER (WHERE date_fin_demande < date_debut_demande) AS inversees FROM prise_en_charge GROUP BY 1;
-- 9. Forfaits référencés inconnus ou d'un autre centre (aucun contrôle) ; PEC validées sans forfait effectif (valorisation des absences RG-ABS-030 : KB-PAT-26).
SELECT c.center_id, count(*) FROM prise_en_charge c LEFT JOIN forfait f ON f.id = c.forfait_effectif_id WHERE c.forfait_effectif_id IS NOT NULL AND (f.id IS NULL OR f.center_id <> c.center_id) GROUP BY 1;
SELECT center_id, count(*) FROM prise_en_charge WHERE statut = 'VALIDEE' AND forfait_effectif_id IS NULL GROUP BY 1;
-- 10. Identifiants de PEC saisis sur la fiche déjà pris (409 PEC_IDENTIFIANT_DEJA_UTILISE) : PEC dont le patient diffère de celui qui la réclamerait ne se détecte qu'à la saisie ; relever le volume de PEC par patient.
SELECT patient_id, count(*) FROM prise_en_charge GROUP BY 1 HAVING count(*) > 5 ORDER BY 2 DESC LIMIT 20;
-- 11. (revue 2026-11-04) Centres SANS ligne planning_parametres : la première écriture d'assuré, de PEC ou d'attestation la crée (ADR 0059) ; deux premières écritures simultanées peuvent voir la seconde refusée 409 (KB-PAT-37). Attendu : 0 ligne, sinon laisser un seul utilisateur écrire en premier. (Requête unique pour PC2, PC3 et PC4.)
SELECT c.id, c.name FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id = c.id);
-- 12. (revue 2026-11-04) Comptes ADMIN actifs SANS centre de rattachement : désormais refusés 403 sur les routes de PEC ET d'attestation (le legacy les laissait passer avec le centre de la requête). Prévenir ces utilisateurs avant la bascule. (Requête unique pour PC3 et PC4.)
SELECT u.username, u.full_name FROM app_user u JOIN app_user_role ur ON ur.user_id = u.id JOIN app_role r ON r.id = ur.role_id WHERE u.active AND r.code = 'ADMIN' AND NOT EXISTS (SELECT 1 FROM app_user_center uc WHERE uc.user_id = u.id);
```
Changements de comportement visibles : garde de centre sur toutes les routes de PEC (un ADMIN d'un autre centre ou sans centre est refusé 403), PEC inconnue 404, formats illisibles 400, `pecId` déjà pris 409 (la fiche est enregistrée), diffusions avec le nom réel, audit `PEC_VALIDATION` et `PEC_CLOTURE`. Les routes d'attestation restent au legacy (PC4) ; `PecValidationJdbc` du module gelé de Séance et `SeanceBillingEligibilityPort` (règle de production de la création de séance) ne sont pas touchés. **Import de migration (revue du 2026-11-04, ADR 0077 point 12)** : `AssureMigrator` et `AffectationMigrator` écrivent `assure` et `assure_patient` SANS le verrou du centre. **Lancer l'import hors activité du centre** (aucune saisie de fiche ni d'assuré pendant l'import), puis rejouer §42-1 (R1) et §42-2 (assurés partagés). Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 43. Patient et couverture PC4 (attestations de droits) : reprise sur place, session S3 (2026-11-04) — SELECT seulement
Même table (`attestation_droit`), mêmes URL (`/api/v1/pec/attestations**`) ; aucune contrainte SQL ni index créé (ADR 0078). Constats à lancer AVANT la bascule ; aucun ne modifie la base.
**Premier contrôle, AVANT tous les autres : les fuseaux.** Le jour d'un centre (tableau de bord, séance sans date, attestation du non-vacancier) est le jour de `centers.fuseau_horaire`, à défaut Africa/Algiers ; les requêtes 5 et 6 ci-dessous utilisent `current_date`, qui est le jour du fuseau de la SESSION PostgreSQL, pas celui du centre : lire leurs résultats avec le fuseau relevé ici.
```sql
-- 0. Fuseaux (premier contrôle) : fuseau de la session PostgreSQL, valeurs de centers.fuseau_horaire (NULL, vide ou illisible = repli sur Africa/Algiers), centres sans fuseau. Relever aussi le fuseau de la JVM du conteneur de production (variable TZ / -Duser.timezone).
SHOW timezone;
SELECT id, name, fuseau_horaire, (fuseau_horaire IS NULL OR TRIM(fuseau_horaire) = '') AS sans_fuseau FROM centers ORDER BY name;
SELECT count(*) FILTER (WHERE fuseau_horaire IS NULL) AS fuseau_null, count(*) FILTER (WHERE TRIM(fuseau_horaire) = '') AS fuseau_vide, count(*) AS centres FROM centers;
SELECT name FROM pg_timezone_names WHERE name IN (SELECT fuseau_horaire FROM centers) ORDER BY 1;
-- 1. Attestations orphelines : patient absent ou d'un autre centre (la création est désormais refusée 422 ; la recherche les rend avec les champs du patient nuls).
SELECT a.id, a.center_id, a.patient_id FROM attestation_droit a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL LIMIT 50;
-- 2. Périodes inversées (fin avant début) : refusées à la saisie, possibles en base (migration historique). Attendu : 0 ligne.
SELECT center_id, count(*) FROM attestation_droit WHERE date_fin < date_debut GROUP BY 1;
-- 3. Doublons exacts de période pour un patient (aucun contrôle, ARGENT : à décider avec les métiers).
SELECT patient_id, date_debut, date_fin, count(*) FROM attestation_droit GROUP BY 1, 2, 3 HAVING count(*) > 1 ORDER BY 4 DESC LIMIT 50;
-- 4. Chevauchements de périodes pour un patient (même remarque).
SELECT a.patient_id, a.id, b.id FROM attestation_droit a JOIN attestation_droit b ON a.patient_id = b.patient_id AND a.id < b.id AND a.date_debut <= b.date_fin AND b.date_debut <= a.date_fin LIMIT 50;
-- 5. Attestations DÉJÀ échues comptées « à échéance » par le tableau de bord (date_fin <= aujourd'hui + horizon : reproduit, à confirmer avec les métiers).
SELECT center_id, count(*) FILTER (WHERE date_fin < current_date) AS deja_echues, count(*) FILTER (WHERE date_fin >= current_date AND date_fin <= current_date + 30) AS a_echeance_non_echues, count(*) AS total FROM attestation_droit GROUP BY 1;
-- 6. Patients à PEC validée sans attestation couvrant aujourd'hui (séance non facturable, session-allowed faux, y compris pour un vacancier : la facturation exige l'attestation, KB-PAT-46).
SELECT c.center_id, count(DISTINCT c.patient_id) FROM prise_en_charge c WHERE c.statut = 'VALIDEE' AND NOT EXISTS (SELECT 1 FROM attestation_droit a WHERE a.patient_id = c.patient_id AND a.center_id = c.center_id AND current_date BETWEEN a.date_debut AND a.date_fin) GROUP BY 1;
-- 7 et 8 : centres sans ligne planning_parametres et comptes ADMIN sans centre : voir §42-11 et §42-12 (mêmes requêtes, valables pour les attestations ; supprimées ici pour éviter les doublons).
```
Changements de comportement visibles : garde de centre 403 sur les 4 routes d'attestation, patient inexistant 422, suppression d'un identifiant inconnu 404 (sans diffusion), formats illisibles 400, recherche à jokers échappés (`%`, `_`) et date de filtre illisible 400, audit `ATTESTATION_*`, jour du centre pour le tableau de bord et la séance sans date. **Écrivain hors verrou** : l'import de migration historique (`HistoricalRecordJdbcAdapter`, plateforme) et le rollback de migration (`MigrationRollbackJdbcAdapter`) écrivent `attestation_droit` SANS le verrou du centre : **lancer l'import et le rollback hors activité du centre**, puis rejouer §42-1, §42-2 et §43-3 et 4. (`bc-assure` reste pour les migrateurs d'assurés et d'affectations ; il ne référence aucune attestation.) Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 44. Patient et couverture PC5 (langage publié `patientcouverture.v1`) : parité des lectures migrées, session S3 (2026-11-04) — SELECT seulement
Aucune donnée ni comportement ne change : six lecteurs (Absences : existence, noms, forfait applicable ; Facturation : PEC tarifées ; Séance : éligibilité à la facturation ; tableau de bord) passent par des ports publiés dont les adapters reprennent leur SQL À L'IDENTIQUE. Contrôle de parité AVANT / APRÈS la bascule : lancer ces requêtes sur la base de production avant et après, les résultats doivent être identiques. Aucune requête n'écrit.
```sql
-- 1. Parité des compteurs du tableau de bord (GET /api/v1/dashboard/stats?centerId=… sans mois, 30 jours) : mêmes comptes avant et après, centre par centre.
SELECT c.id,
       (SELECT count(1) FROM prise_en_charge WHERE center_id = c.id AND statut = 'CREE') AS pec_cree,
       (SELECT count(1) FROM prise_en_charge WHERE center_id = c.id AND statut = 'VALIDEE') AS pec_validee,
       (SELECT count(1) FROM prise_en_charge WHERE center_id = c.id AND statut = 'VALIDEE' AND date_fin_demande <= current_date + 30) AS pec_a_echeance,
       (SELECT count(1) FROM attestation_droit WHERE center_id = c.id) AS attestation_total,
       (SELECT count(1) FROM attestation_droit WHERE center_id = c.id AND date_fin <= current_date + 30) AS attestation_a_echeance
FROM centers c ORDER BY c.name;
-- 2. Effectif d'aujourd'hui (patientCount sans mois) : même prédicat qu'avant (admission au plus tard aujourd'hui, pas déjà parti).
SELECT center_id, count(1) FROM patients p WHERE (p.date_admission IS NULL OR p.date_admission <= current_date) AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ('DECEDE','GREFFE','GUERRI','TRANSFERE','OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') OR (p.etat_patient IN ('OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') AND (p.date_evenement_etat IS NULL OR p.date_evenement_etat >= current_date)) OR (p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= current_date) OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat > current_date)) GROUP BY 1 ORDER BY 1;
-- 3. Éligibilité à la facturation AU JOUR (filtre de date : current_date, jour du fuseau de la SESSION PostgreSQL, à lire avec §43-0) : nombre de patients par étage, dans l'ordre du code : (1) PEC porteuse d'un forfait dont la période (effective, sinon demandée) couvre le jour ; (2) dont une est validée ; (3) dont une attestation couvre aussi le jour. Avant et après le déploiement, les trois nombres doivent être identiques centre par centre.
SELECT c.center_id,
       count(DISTINCT c.patient_id) FILTER (WHERE COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL
             AND ((c.date_debut_effectif IS NOT NULL AND current_date >= c.date_debut_effectif AND (current_date <= c.date_fin_effectif OR c.date_fin_effectif IS NULL))
               OR (c.date_debut_demande IS NOT NULL AND current_date >= c.date_debut_demande AND (current_date <= c.date_fin_demande OR c.date_fin_demande IS NULL)))) AS etage_1_pec_couvrante,
       count(DISTINCT c.patient_id) FILTER (WHERE c.statut = 'VALIDEE' AND COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL
             AND ((c.date_debut_effectif IS NOT NULL AND current_date >= c.date_debut_effectif AND (current_date <= c.date_fin_effectif OR c.date_fin_effectif IS NULL))
               OR (c.date_debut_demande IS NOT NULL AND current_date >= c.date_debut_demande AND (current_date <= c.date_fin_demande OR c.date_fin_demande IS NULL)))
             ) AS etage_2_validee,
       count(DISTINCT c.patient_id) FILTER (WHERE c.statut = 'VALIDEE' AND COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL
             AND ((c.date_debut_effectif IS NOT NULL AND current_date >= c.date_debut_effectif AND (current_date <= c.date_fin_effectif OR c.date_fin_effectif IS NULL))
               OR (c.date_debut_demande IS NOT NULL AND current_date >= c.date_debut_demande AND (current_date <= c.date_fin_demande OR c.date_fin_demande IS NULL)))
             AND EXISTS (SELECT 1 FROM attestation_droit a WHERE a.center_id = c.center_id AND a.patient_id = c.patient_id
                         AND current_date >= a.date_debut AND (current_date <= a.date_fin OR a.date_fin IS NULL))) AS etage_3_facturable
FROM prise_en_charge c GROUP BY 1 ORDER BY 1;
-- 4. Patients dont un forfait de PEC référence un forfait d'un autre centre ou absent (ignoré par le forfait applicable et par les PEC tarifées).
SELECT c.center_id, count(*) FROM prise_en_charge c LEFT JOIN forfait f ON f.id = COALESCE(c.forfait_effectif_id, c.forfait_demande_id) AND f.center_id = c.center_id WHERE COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL AND f.id IS NULL GROUP BY 1;
```
Jalons restants (sources qui lisent encore ces tables en SQL, liste blanche gardée par `TableDuPatientCouvertureTest`) : voir l'ADR 0079. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 45. Dossier médical DM1 (lecture des constantes) : parité et gardes de centre (2026-11-06) — SELECT seulement
Aucune migration, aucune table ni colonne ajoutée. À exécuter sur une COPIE ou en lecture seule (données de santé : ne rien exporter hors du poste de vérification).
```sql
-- 1. Séances et volets par centre (le JOIN de l'adapter : seance_id ET center_id).
SELECT s.center_id, count(*) AS seances, count(v.id) AS avec_volet FROM seances s LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id GROUP BY s.center_id;
-- 2. Volets rattachés à un autre centre que celui de leur séance (ignorés par la vue des constantes ; à expliquer).
SELECT count(*) FROM volet_paramedical v JOIN seances s ON s.id = v.seance_id WHERE v.center_id <> s.center_id;
-- 3. Volets sans séance (orphelins).
SELECT count(*) FROM volet_paramedical v LEFT JOIN seances s ON s.id = v.seance_id WHERE s.id IS NULL;
-- 4. Fuite inter-centres déjà survenue (KB-MED-01) : volets écrits sous un centre différent de celui de la séance = requête 2.
-- 5. Séances sans date de création (ordre d'égalité de date indéterminé).
SELECT count(*) FROM seances WHERE created_at IS NULL;
```
Recette obligatoire AVANT bascule (revue DM1) : (a) le front envoie bien le `centerId` de la SESSION sur toutes les routes gardées (`GET /seances`, `/seances/journal`, `/seances/calendar`, `/seances/{id}`, `POST /seances`, `POST /seances/scan`, `PUT /seances/{id}`, `PUT /seances/{id}/volet-paramedical`, `stats/paramedical`, `stats/medical`, `stats/export` et les `search`) : parcourir chaque écran avec un compte du centre ; (b) avec un compte d'un AUTRE centre, chaque route doit rendre 403. Aucun appel du front n'envoie un `centerId` nul sur les `search` (`@NotNull` côté API). Ajout du 2026-11-07, même recette (front : `centerId` de la session ; autre centre : 403) pour `PUT /seances/{id}/forfait`, `POST .../valider`, `POST .../deverrouiller-regularisation`, `POST .../signer-medecin`, `POST/PUT/DELETE .../consommables`, `POST/DELETE /seances/calendar/holiday` et `/closure`, `PUT /seances/{id}/volet-medical` : parcourir validation, signature, consommables, calendrier (jours fériés et fermetures) et volet médical avec un compte du centre ; tout 403 légitime signale un front qui envoie un autre `centerId`.
Surveillance après déploiement : hausse attendue des 403 sur `PUT /seances/{id}/volet-paramedical`, `GET /seances/{id}`, `GET /seances`, `/seances/journal`, `/seances/calendar`, `POST/PUT /seances`, `stats/*` et des 400 sur `GET /patients/{id}/constantes` (taille > 100) : tout 403 d'un utilisateur légitime signale une session ou un front qui envoie le mauvais `centerId`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 46. Dossier médical DM2 (ordonnances, prescriptions, impression de l'ordonnance) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, mêmes tables (`ordonnances`, `lignes_ordonnance`, `prescriptions_medicales`, `app_settings` `SEQ_ORD`). À exécuter sur une COPIE ou en lecture seule ; rejouées sur PostgreSQL 16 jetable par `backend/scripts/dossier-medical-dm2-pg16-rejeu.sh`.
```sql
-- 1. Ordonnances par centre et statut (avant/après : identiques, la reprise ne change aucune ligne).
SELECT center_id, statut, count(*) AS ordonnances FROM ordonnances GROUP BY center_id, statut;
-- 2. Numéros d'ordonnance en double dans un centre (attendu : aucun).
SELECT center_id, numero, count(*) FROM ordonnances WHERE numero IS NOT NULL GROUP BY center_id, numero HAVING count(*) > 1;
-- 3. Trous de la séquence légale ORD (KB-MED-20, reproduit : un refus de signature consomme un numéro) : compteur contre ordonnances numérotées.
SELECT a.center_id, a.dernier_compteur, (SELECT count(*) FROM ordonnances o WHERE o.center_id = a.center_id AND o.numero IS NOT NULL) AS numerotees FROM app_settings a WHERE a.cle = 'SEQ_ORD';
-- 4. Ordonnances sans ligne (invariant : au moins une).
SELECT o.center_id, count(*) FROM ordonnances o WHERE NOT EXISTS (SELECT 1 FROM lignes_ordonnance l WHERE l.ordonnance_id = o.id) GROUP BY o.center_id;
-- 5. Fiches orphelines (KB-MED-19, créées avant la garde) : ordonnances et prescriptions dont le patient n'existe pas dans le centre.
SELECT 'ordonnances' AS fiche, o.center_id, count(*) FROM ordonnances o LEFT JOIN patients p ON p.id = o.patient_id AND p.center_id = o.center_id WHERE p.id IS NULL GROUP BY o.center_id
UNION ALL SELECT 'prescriptions', m.center_id, count(*) FROM prescriptions_medicales m LEFT JOIN patients p ON p.id = m.patient_id AND p.center_id = m.center_id WHERE p.id IS NULL GROUP BY m.center_id;
-- 6. Prescriptions réaffectées d'un centre à l'autre (KB-MED-17, avant la correction) : patient de la prescription d'un autre centre que le sien.
SELECT m.center_id AS centre_prescription, p.center_id AS centre_patient, count(*) FROM prescriptions_medicales m JOIN patients p ON p.id = m.patient_id WHERE p.center_id <> m.center_id GROUP BY m.center_id, p.center_id;
-- 7. KB-MED-33 / migration V53 : les 4 colonnes de détail de alertes_observance existent-elles AVANT le déploiement ? (attendu avant V53 : 0 à 4 lignes ;
--    après V53 : 4 lignes, toutes nullables, varchar(10) / integer / integer / varchar(10)). Si une base a déjà ces colonnes (ddl-auto: update), V53 est sans effet.
SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'alertes_observance'
  AND column_name IN ('unite_dose', 'dose_prescrite', 'frequence_valeur', 'frequence_unite') ORDER BY column_name;
-- 8. V53 est-elle appliquée (et V1..V52 sans trou) ? (attendu après déploiement : success = true pour la version 53)
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version::int >= 50 ORDER BY installed_rank;
-- 9. Alertes d'observance existantes : leurs détails sont nuls si elles datent d'avant V53 sur une base issue de Flyway (rien à corriger, lecture seule).
SELECT center_id, count(*) AS alertes, count(unite_dose) AS avec_detail FROM alertes_observance GROUP BY center_id;
```
KB-MED-33 (décision déléguée, À CONFIRMER avec le DBA et les métiers) : la migration `V53__alertes_observance_detail.sql` est ADDITIVE et IDEMPOTENTE (quatre colonnes nullables, aucune donnée lue ni modifiée). À exécuter AVANT la bascule de l'anémie sur le module : exécuter la requête 7 sur la copie de production, appliquer Flyway, puis les requêtes 7 et 8. Aucune sauvegarde des données n'est nécessaire pour la migration elle-même ; sauvegarde habituelle avant tout déploiement. Rollback : revenir au commit précédent ne retire pas les colonnes (inoffensives : nullables, ignorées par le code précédent).
Recette obligatoire AVANT bascule : (a) créer, signer, imprimer et annuler une ordonnance, puis créer, modifier et supprimer une prescription, avec un compte MEDECIN du centre ; (b) avec un compte d'un AUTRE centre : création d'ordonnance ou de prescription pour un patient inconnu = 404, modification d'une prescription du premier centre = 404 (jamais 200), impression d'une ordonnance avec le `centerId` du premier centre = 403 ; (c) imprimer une ordonnance avec un compte INFIRMIER ou SECRETAIRE = 403 (le front doit réserver le bouton d'impression au médecin et à l'administrateur). Les requêtes 5 et 6 listent les fiches à expliquer avec les métiers avant correction de données (aucune correction automatique, DELETE interdit sans validation écrite).
Surveillance après déploiement : hausse attendue des 400 (corps ou paramètre illisible, avant 500), des 404 (patient inconnu, prescription introuvable) et des 403 sur `POST /documents/print` type `ORDONNANCE`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 47. Dossier médical DM3 (demandes d'examen) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, mêmes tables (`demandes_examen`, `lignes_demande_examen` SANS `center_id`, ADR 0072). À exécuter sur une COPIE ou en lecture seule ; rejouées sur PostgreSQL 16 jetable par `backend/scripts/dossier-medical-dm3-pg16-rejeu.sh`.
```sql
-- 1. Demandes par centre et statut (avant/après : identiques, la reprise ne change aucune ligne).
SELECT center_id, statut, count(*) AS demandes FROM demandes_examen GROUP BY center_id, statut;
-- 2. Demandes sans ligne (invariant : au moins une).
SELECT d.center_id, count(*) AS sans_ligne FROM demandes_examen d WHERE NOT EXISTS (SELECT 1 FROM lignes_demande_examen l WHERE l.demande_id = d.id) GROUP BY d.center_id;
-- 3. Lignes orphelines (aucune clé étrangère).
SELECT l.demande_id FROM lignes_demande_examen l WHERE NOT EXISTS (SELECT 1 FROM demandes_examen d WHERE d.id = l.demande_id);
-- 4. Données incohérentes (KB-MED-39) : statut ou catégorie illisibles. Elles ne cassent plus la liste mais en sont ABSENTES (et refusent toute transition) : à expliquer avec les métiers.
SELECT center_id, id, statut, categorie FROM demandes_examen WHERE statut NOT IN ('DEMANDE','PRELEVE','RESULTAT_DISPONIBLE','VALIDE','ANNULE') OR categorie NOT IN ('BIOLOGIE','IMAGERIE','FONCTIONNEL','ANAPATH');
-- 5. Demandes orphelines (KB-MED-38, créées avant la garde) : patient inexistant dans le centre de la demande.
SELECT d.center_id, count(*) AS patient_absent FROM demandes_examen d LEFT JOIN patients p ON p.id = d.patient_id AND p.center_id = d.center_id WHERE p.id IS NULL GROUP BY d.center_id;
-- 6. Demandes validées sans conclusion (KB-MED-42, reproduit : la conclusion n'est pas obligatoire).
SELECT center_id, id FROM demandes_examen WHERE statut = 'VALIDE' AND conclusion IS NULL;
-- 7. Références souples de DM6 (KB-MED-44, NON corrigées ici) : observations liées à une demande inconnue ou d'un autre centre.
SELECT o.center_id, count(*) AS observations_vers_demande_inconnue FROM observations_biologiques o WHERE o.demande_examen_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM demandes_examen d WHERE d.id = o.demande_examen_id AND d.center_id = o.center_id) GROUP BY o.center_id;
-- 8. Demandes dont UNE LIGNE est illisible (nomenclature inconnue, code CIM10 / LOINC mal formé, code de plus de 20 caractères) : IGNORÉES par la liste
--    (journal WARN « Demande d'examen <id> ignorée », identifiant seul) et introuvables (400) pour toute transition. Attendu : aucune ; sinon à expliquer avec les métiers.
SELECT d.center_id, d.id, d.statut, l.id AS ligne, l.analyte_code_system FROM demandes_examen d JOIN lignes_demande_examen l ON l.demande_id = d.id
 WHERE l.analyte_code IS NOT NULL AND l.analyte_code_system IS NOT NULL AND (l.analyte_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')
   OR length(trim(l.analyte_code)) > 20
   OR (upper(l.analyte_code_system) = 'LOINC' AND upper(trim(l.analyte_code)) !~ '^[0-9]{1,5}-[0-9]$')
   OR (upper(l.analyte_code_system) = 'CIM10' AND upper(trim(l.analyte_code)) !~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$'));
```
Limite de pagination (consignée, ADR 0082) : `total` compte les demandes aux statut et catégorie lisibles ; il peut donc dépasser le nombre d'éléments listés du nombre de demandes de la requête 8.
Recette obligatoire AVANT bascule : (a) demander un examen avec un compte MEDECIN, le prélever, déclarer le résultat disponible, le valider (avec et sans conclusion), puis annuler une autre demande avant et après le résultat (422 après) ; (b) avec un compte d'un AUTRE centre : demande pour un patient inconnu = 404 `PATIENT_INTROUVABLE`, transition d'une demande du premier centre = 400 (jamais 200), `centerId` d'un autre centre dans le corps = 403 ; (c) un compte INFIRMIER ou SECRETAIRE reçoit 403 même avec un corps illisible ; (d) ADMIN : lit mais ne peut pas écrire (à confirmer avec les métiers). La requête 4 liste les demandes à expliquer avant toute correction de données (aucune correction automatique, DELETE interdit sans validation écrite).
Surveillance après déploiement : hausse attendue des 400 (corps, date, `urgent`, catégorie ou paramètre illisible, texte trop long, avant 500), des 404 (patient inconnu) et des 403 (corps illisible d'un profil sans droit). Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 48. Dossier médical DM4 (sérologies) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, même table (`serologies_patient`, sans contrainte d'unicité ni clé étrangère ; le schéma parallèle gelé `serologies` n'est ni lu ni écrit, ADR 0072). Données de santé très sensibles (VIH, hépatites) : exécuter sur une COPIE ou en lecture seule, ne jamais exporter les lignes ; préférer les comptages. Rejouées sur PostgreSQL 16 jetable par `backend/scripts/dossier-medical-dm4-pg16-rejeu.sh`.
```sql
-- 1. Sérologies par centre, marqueur et résultat (avant/après : identiques, la reprise ne change aucune ligne).
SELECT center_id, marqueur, resultat, count(*) AS serologies FROM serologies_patient GROUP BY center_id, marqueur, resultat;
-- 2. DOUBLONS (KB-MED-56) : plusieurs résultats pour le même patient, marqueur et date de prélèvement (l'unicité n'était qu'applicative, sans verrou ni contrainte).
--    Attendu : aucune ligne ; sinon la synthèse du patient départage par date de création puis identifiant : à expliquer avec les métiers (aucune correction automatique).
SELECT center_id, patient_id, marqueur, date_prelevement, count(*) AS exemplaires FROM serologies_patient GROUP BY center_id, patient_id, marqueur, date_prelevement HAVING count(*) > 1;
-- 3. Données incohérentes (marqueur ou résultat illisible) : IGNORÉES par les lectures du module (avant : 500 pour toute la liste du patient), journal WARN « Sérologie <id> ignorée » (identifiant seul), et ni corrigeables (400) ni supprimables (204 sans effet, sans audit) par le module (limite consignée, ADR 0083). Attendu : aucune ; sinon à expliquer avec les métiers (aucune correction automatique).
SELECT center_id, id, marqueur, resultat FROM serologies_patient
 WHERE marqueur NOT IN ('VIH_AC','AG_HBS','AC_HBS','AC_HBC','AC_VHC','ARN_VHC','TPHA','CMV_IGG','CMV_IGM','TOXO_IGG','TOXO_IGM','EBV_IGG','EBV_IGM')
    OR resultat NOT IN ('POSITIF','NEGATIF','DOUTEUX','EN_COURS');
-- 4. Valeurs hors bornes du module (KB-MED-52 : refusées en 400 à la saisie, mais peuvent exister en base, importées) : titre négatif ou de plus de 7 chiffres entiers, unité de plus de 20 caractères (impossible en base : colonne 20), laboratoire de plus de 255 (impossible : colonne 255).
SELECT center_id, count(*) FILTER (WHERE titre < 0) AS titres_negatifs, count(*) FILTER (WHERE abs(titre) >= 10000000) AS titres_hors_borne,
       count(*) FILTER (WHERE length(unite) > 20) AS unites_trop_longues, count(*) FILTER (WHERE length(laboratoire) > 255) AS laboratoires_trop_longs
  FROM serologies_patient GROUP BY center_id;
-- 5. Positifs sans conduite à tenir (règle de positivité : impossible par l'API, possible par import ou SQL).
SELECT center_id, count(*) AS positifs_sans_conduite FROM serologies_patient WHERE resultat = 'POSITIF' AND (conduite_a_tenir IS NULL OR btrim(conduite_a_tenir) = '') GROUP BY center_id;
-- 6. Sérologies d'un patient inexistant dans le centre de la sérologie (cloisonnement : patient absent ou d'un autre centre).
SELECT s.center_id, count(*) AS patient_absent FROM serologies_patient s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE p.id IS NULL GROUP BY s.center_id;
-- 7. Dates de prélèvement hors du raisonnable (futur impossible par l'API ; avant 1990 : à expliquer) et prochain contrôle antérieur au prélèvement (accepté, reproduit).
SELECT center_id, count(*) FILTER (WHERE date_prelevement > current_date) AS futures, count(*) FILTER (WHERE date_prelevement < DATE '1990-01-01') AS tres_anciennes,
       count(*) FILTER (WHERE date_prochain_controle < date_prelevement) AS controle_avant_prelevement FROM serologies_patient GROUP BY center_id;
-- 8. Patients à risque infectieux au sens de la Planification (derniers AG_HBS, AC_VHC, ARN_VHC, VIH_AC positifs, KB-MED-53) hors salle d'isolement : voir la requête de contrôle de la Planification (P5) ; non rejouée ici.
```
Recette obligatoire AVANT bascule : (a) avec un compte MEDECIN, enregistrer un résultat négatif puis un positif avec conduite, corriger l'un vers l'autre, supprimer ; vérifier le déplacement en isolement d'un patient positif (VIH_AC, AG_HBS, AC_VHC, ARN_VHC) et son absence pour TPHA ou CMV ; (b) un résultat positif sans conduite : 422 ; un doublon marqueur et date : 422 ; (c) avec un compte d'un AUTRE centre : écriture refusée (403 avec `centerId` du centre voisin, 422 `PLACEMENT_PATIENT_INTROUVABLE` pour un patient voisin, la sérologie n'est pas créée) ; (d) INFIRMIER ou SECRETAIRE reçoit 403 même avec un corps illisible, ADMIN lit mais ne peut pas écrire (à confirmer avec les métiers) ; (e) deux enregistrements simultanés du même triplet : le second attend puis 409 `SEROLOGIE_ECRITURE_CONCURRENTE` ou 422 doublon ; (f) supprimer deux fois la même sérologie : 204 les deux fois, une seule ligne `SEROLOGIE_SUPPRIMEE` au journal d'audit. La requête 2 liste les doublons à expliquer avant toute correction de données (aucune correction automatique, DELETE interdit sans validation écrite).
Surveillance après déploiement : hausse attendue des 400 (corps, marqueur, résultat, date, `titre`, paramètre illisible, unité ou laboratoire trop long, avant 500), des 403 (corps illisible d'un profil sans droit) et disparition des lignes d'audit `SEROLOGIE_SUPPRIMEE` 204 sans suppression ; surveiller les 409 `SEROLOGIE_ECRITURE_CONCURRENTE`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 49. Dossier médical DM5 (antécédents, allergies, abords vasculaires) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, mêmes tables (`antecedents_medicaux`, `allergies_patient`, `abords_vasculaires`, sans contrainte d'unicité ni clé étrangère ; les schémas parallèles gelés `antecedents` / `allergies` ne sont ni lus ni écrits, ADR 0072). Données de santé sensibles. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement (ADR 0084).
```sql
-- 1. Fiches par centre (avant/après : identiques, la reprise ne change aucune ligne).
SELECT 'antecedents' AS table_, center_id, count(*) FROM antecedents_medicaux GROUP BY center_id
UNION ALL SELECT 'allergies', center_id, count(*) FROM allergies_patient GROUP BY center_id
UNION ALL SELECT 'abords', center_id, count(*) FROM abords_vasculaires GROUP BY center_id;
-- 2. DOUBLONS de diagnostic actif (KB-MED-64 : unicité applicative sans contrainte, course possible avant la reprise). Attendu : aucune ligne ; sinon expliquer avec les métiers (aucune correction automatique).
SELECT center_id, patient_id, diagnostic_code_system, diagnostic_code, count(*) AS exemplaires FROM antecedents_medicaux
 WHERE statut_clinique = 'ACTIF' AND diagnostic_code IS NOT NULL GROUP BY center_id, patient_id, diagnostic_code_system, diagnostic_code HAVING count(*) > 1;
-- 3. ORPHELINS (KB-MED-61 : avant la reprise, l'écriture pour un patient inconnu ou d'un autre centre était acceptée) : fiches dont le patient est absent du centre de la fiche.
SELECT 'antecedents' AS table_, a.center_id, count(*) AS patient_absent FROM antecedents_medicaux a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id
UNION ALL SELECT 'allergies', a.center_id, count(*) FROM allergies_patient a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id
UNION ALL SELECT 'abords', a.center_id, count(*) FROM abords_vasculaires a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id;
-- 4. ABORDS DONT LE PATIENT DIFFÈRE (KB-MED-62 : le PUT d'un abord le réaffectait à un autre patient ou à un autre centre) : abords d'un patient dont le centre n'est pas celui de l'abord.
SELECT a.center_id AS centre_de_l_abord, p.center_id AS centre_du_patient, count(*) FROM abords_vasculaires a JOIN patients p ON p.id = a.patient_id AND p.center_id <> a.center_id GROUP BY a.center_id, p.center_id;
-- 5. TYPES D'ABORD hors de ceux que la Planification reconnaît (KB-MED-63 : texte libre ; seuls KT_AIGU et KT_TUNNELISE exigent la compétence CATHETER, RG-PLN-097) : à expliquer, pas à corriger.
SELECT center_id, type_abord, count(*) FROM abords_vasculaires WHERE type_abord NOT IN ('KT_AIGU','KT_TUNNELISE') GROUP BY center_id, type_abord ORDER BY 3 DESC;
-- 6. LIGNES ILLISIBLES : IGNORÉES par les lectures du module (avant : 500 pour toute la liste du patient), journal WARN « Antécédent / Allergie <id> ignoré(e) » (identifiant seul), et ni corrigeables ni supprimables par le module.
SELECT 'antecedents' AS table_, center_id, id FROM antecedents_medicaux
 WHERE type_antecedent NOT IN ('MEDICAL','CHIRURGICAL','FAMILIAL','OBSTETRICAL','COMORBIDITE') OR statut_clinique NOT IN ('ACTIF','RESOLU','INACTIF')
    OR (diagnostic_code IS NOT NULL AND (diagnostic_code_system IS NULL OR diagnostic_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')))
UNION ALL SELECT 'allergies', center_id, id FROM allergies_patient
 WHERE categorie NOT IN ('MEDICAMENT','ALIMENT','ENVIRONNEMENT','BIOLOGIQUE') OR criticite NOT IN ('BASSE','HAUTE') OR type_reaction NOT IN ('ALLERGIE','INTOLERANCE')
    OR statut_verification NOT IN ('SUSPECTEE','CONFIRMEE','REFUTEE') OR substance_code_system IS NULL OR substance_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL');
-- 7. Règles de gestion violables par import ou SQL (impossibles par l'API) : allergie HAUTE sans manifestations, abord dont la fin précède la création, antécédent dont la fin précède le début, allergie constatée dans le futur.
SELECT 'allergies_hautes_sans_manifestations' AS controle, center_id, count(*) FROM allergies_patient WHERE criticite = 'HAUTE' AND (manifestations IS NULL OR btrim(manifestations) = '') GROUP BY center_id
UNION ALL SELECT 'abords_fin_avant_creation', center_id, count(*) FROM abords_vasculaires WHERE date_fin < date_creation GROUP BY center_id
UNION ALL SELECT 'antecedents_fin_avant_debut', center_id, count(*) FROM antecedents_medicaux WHERE date_fin < date_debut GROUP BY center_id
UNION ALL SELECT 'allergies_dans_le_futur', center_id, count(*) FROM allergies_patient WHERE date_constatation > current_date GROUP BY center_id;
-- 8. Valeurs hors bornes de colonne du module (400 à la saisie ; impossibles en base car les colonnes sont bornées) : contrôle de non-régression du schéma.
SELECT max(length(severite)) AS severite_max, max(length(libelle_libre)) AS libelle_max, max(length(diagnostic_code)) AS code_max FROM antecedents_medicaux;
```
Recette obligatoire AVANT bascule : (a) avec un compte MEDECIN, enregistrer un antécédent codé puis le même code (422), le résoudre (puis 422 au rejeu), le corriger, le supprimer ; déclarer une allergie HAUTE sans manifestations (422) puis avec, la corriger, vérifier le bandeau `critiques` (une allergie réfutée y reste) ; enregistrer un abord KT_AIGU, vérifier la compétence CATHETER exigée dans la Planification du jour, le remplacer, le supprimer ; (b) avec un compte INFIRMIER, vérifier le refus (403) sur antécédents, allergies et `critiques` et la lecture des abords ; (c) PUT d'un abord inconnu ou d'un autre patient : 404, rien créé ni déplacé (requête 4) ; (d) écriture pour un patient d'un autre centre : 404.
Surveillance après déploiement : hausse attendue des 400 (corps, énumération, date, `actif`, paramètre illisible, valeur hors borne de colonne, avant 500), des 403 (corps illisible d'un profil sans droit), des 404 (patient inconnu ou d'un autre centre à l'écriture, PUT d'abord introuvable) et disparition des lignes d'audit `PATIENTS_SUPPRESSION` 204 sans suppression ; surveiller les 409 `ANTECEDENT_ECRITURE_CONCURRENTE`, `ALLERGIE_ECRITURE_CONCURRENTE`, `ABORD_VASCULAIRE_ECRITURE_CONCURRENTE` et les WARN « ignoré(e) : … illisible en base ». Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 50. Dossier médical DM6 (observations biologiques et analyses) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, mêmes tables (`observations_biologiques`, `resultats_analyses`, sans contrainte d'unicité ni clé étrangère ; `lignes_demande_examen` n'a PAS de `center_id` et n'en reçoit pas ; les tables du module gelé ne sont ni lues ni écrites, ADR 0072). Données de santé sensibles. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement (ADR 0085). Aucune correction automatique : toute ligne ramenée est expliquée avec les métiers et le DPO.
```sql
-- 1. Fiches par centre (avant/après : identiques, la reprise ne change aucune ligne).
SELECT 'observations' AS table_, center_id, count(*) FROM observations_biologiques GROUP BY center_id
UNION ALL SELECT 'analyses', center_id, count(*) FROM resultats_analyses GROUP BY center_id;
-- 2. ORPHELINS (KB-MED-73 : avant la reprise, l'écriture pour un patient inconnu ou d'un autre centre était acceptée) : bilans et observations dont le patient est absent du centre de la fiche.
SELECT 'observations' AS table_, o.center_id, count(*) AS patient_absent FROM observations_biologiques o LEFT JOIN patients p ON p.id = o.patient_id AND p.center_id = o.center_id WHERE p.id IS NULL GROUP BY o.center_id
UNION ALL SELECT 'analyses', a.center_id, count(*) FROM resultats_analyses a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id;
-- 3. ANALYSES DONT LE PATIENT DIFFÈRE (KB-MED-74 : le PUT d'un bilan le réaffectait à un autre patient ou à un autre centre) : bilans d'un patient dont le centre n'est pas celui du bilan.
SELECT a.center_id AS centre_du_bilan, p.center_id AS centre_du_patient, count(*) FROM resultats_analyses a JOIN patients p ON p.id = a.patient_id AND p.center_id <> a.center_id GROUP BY a.center_id, p.center_id;
-- 4. DOUBLONS (deux envois identiques créent deux lignes, reproduit ; plusieurs bilans le même jour sont permis) : à expliquer, pas à corriger.
SELECT center_id, patient_id, analyte_code, date_prelevement, valeur_num, unite, valeur_texte, count(*) AS exemplaires FROM observations_biologiques
 GROUP BY center_id, patient_id, analyte_code, date_prelevement, valeur_num, unite, valeur_texte HAVING count(*) > 1;
SELECT center_id, patient_id, date_prelevement, count(*) AS bilans FROM resultats_analyses GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1;
-- 5. OBSERVATIONS RATTACHÉES À UNE DEMANDE d'un AUTRE patient ou d'un AUTRE centre, ou inconnue (KB-MED-77 : référence souple, aucune clé étrangère ; ces lignes ne sont plus rendues par la lecture par demande).
SELECT o.center_id, count(*) AS demande_inconnue_ou_etrangere FROM observations_biologiques o
 LEFT JOIN demandes_examen d ON d.id = o.demande_examen_id AND d.patient_id = o.patient_id AND d.center_id = o.center_id
 WHERE o.demande_examen_id IS NOT NULL AND d.id IS NULL GROUP BY o.center_id;
-- 6. LIGNES D'OBSERVATION ILLISIBLES : IGNORÉES par les lectures du module (comptage compris), journal WARN « Observation <id> ignorée » (identifiant seul), et ni corrigeables ni supprimables par le module.
SELECT center_id, id FROM observations_biologiques
 WHERE analyte_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL') OR statut NOT IN ('PRELIMINAIRE','FINAL','CORRIGE') OR source NOT IN ('SAISIE_DIRECTE','DERIVEE_BILAN','IMPORT')
    OR (valeur_num IS NOT NULL AND (unite IS NULL OR btrim(unite) = ''));
-- 7. Règles de gestion violables par import ou SQL (impossibles par l'API) : valeur numérique ET texte, ni l'une ni l'autre, code d'analyte non LOINC, prélèvement dans le futur.
SELECT 'valeur_et_texte' AS controle, center_id, count(*) FROM observations_biologiques WHERE valeur_num IS NOT NULL AND valeur_texte IS NOT NULL AND btrim(valeur_texte) <> '' GROUP BY center_id
UNION ALL SELECT 'ni_valeur_ni_texte', center_id, count(*) FROM observations_biologiques WHERE valeur_num IS NULL AND (valeur_texte IS NULL OR btrim(valeur_texte) = '') GROUP BY center_id
UNION ALL SELECT 'analyte_non_loinc', center_id, count(*) FROM observations_biologiques WHERE analyte_code_system <> 'LOINC' GROUP BY center_id
UNION ALL SELECT 'observations_dans_le_futur', center_id, count(*) FROM observations_biologiques WHERE date_prelevement > current_date GROUP BY center_id
UNION ALL SELECT 'bilans_dans_le_futur', center_id, count(*) FROM resultats_analyses WHERE date_prelevement > current_date GROUP BY center_id;
-- 8. Lecteurs inchangés (suivi de l'anémie, KDIGO, statistiques, CSV, FHIR) : dernier bilan par patient, avant et après, identique.
SELECT patient_id, max(date_prelevement) AS dernier_bilan, count(*) FROM resultats_analyses GROUP BY patient_id ORDER BY 1 LIMIT 50;
```
Recette obligatoire AVANT bascule : (a) avec un compte MEDECIN, enregistrer une observation numérique avec unité puis textuelle, la corriger (statut `CORRIGE`), la supprimer ; lire les observations d'une demande du patient puis d'une demande d'un autre patient (liste vide) ; enregistrer un bilan, le modifier (les mesures absentes sont effacées), modifier un bilan inconnu (404 `ANALYSE_INTROUVABLE`), le supprimer ; rechercher des analyses (page 0, taille 20) ; (b) avec un compte INFIRMIER, vérifier le refus (403) ; avec un compte ADMIN, la lecture et le refus d'écriture (403) ; (c) vérifier que le suivi de l'anémie, l'évaluation KDIGO, les statistiques médicales, les exports CSV et FHIR rendent les mêmes valeurs qu'avant la reprise.
Surveillance après déploiement : hausse attendue des 400 (corps, système de codage, statut, date, paramètre illisible, valeur hors borne de colonne, avant 500), des 403 (corps illisible d'un profil sans droit), des 404 (patient inconnu ou d'un autre centre à l'écriture, PUT d'analyse introuvable) et disparition des lignes d'audit `PATIENTS_SUPPRESSION` 204 sans suppression ; surveiller les 409 `OBSERVATION_ECRITURE_CONCURRENTE`, `ANALYSE_ECRITURE_CONCURRENTE` et les WARN « Observation … ignorée : … illisible en base ». Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 51. Dossier médical DM7 (bilan pré-greffe, RCP, étapes, donneurs vivants, KDIGO, export PDF) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, mêmes tables (`bilans_pre_greffe`, `decisions_rcp`, `etapes_bilan_pre_greffe`, `donneurs_vivants`, sans contrainte d'unicité ni clé étrangère ; les tables du module gelé ne sont ni lues ni écrites, ADR 0072). Données de santé sensibles (bilan immunologique, identité de donneurs). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement (ADR 0086). Aucune correction automatique : toute ligne ramenée est expliquée avec les métiers et le DPO.
```sql
-- 1. Fiches par centre (avant/après : identiques, la reprise ne change aucune ligne).
SELECT 'bilans' AS table_, center_id, count(*) FROM bilans_pre_greffe GROUP BY center_id
UNION ALL SELECT 'decisions_rcp', b.center_id, count(*) FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id GROUP BY b.center_id
UNION ALL SELECT 'etapes', center_id, count(*) FROM etapes_bilan_pre_greffe GROUP BY center_id
UNION ALL SELECT 'donneurs', center_id, count(*) FROM donneurs_vivants GROUP BY center_id;
-- 2. DOUBLONS DE BILAN (KB-MED-90 : deux lectures simultanées pouvaient créer deux bilans pour un patient). Le module lit le PLUS ANCIEN et journalise un WARN « Bilan pré-greffe <id> ignoré : doublon ». À expliquer avec les métiers, pas à corriger automatiquement.
SELECT center_id, patient_id, count(*) AS bilans, min(created_at) AS le_plus_ancien FROM bilans_pre_greffe GROUP BY center_id, patient_id HAVING count(*) > 1;
-- 3. ORPHELINS (KB-MED-86 : avant la reprise, l'écriture pour un patient inconnu ou d'un autre centre était acceptée) : lignes dont le patient est absent du centre de la ligne.
SELECT 'bilans' AS table_, t.center_id, count(*) AS patient_absent FROM bilans_pre_greffe t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id
UNION ALL SELECT 'etapes', t.center_id, count(*) FROM etapes_bilan_pre_greffe t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id
UNION ALL SELECT 'donneurs', t.center_id, count(*) FROM donneurs_vivants t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id;
-- 4. ÉTAPES EN DOUBLE (KB-MED-90 : deux générations simultanées pouvaient doubler la liste standard ; l'unicité n'existe pas en base, reproduit pour les étapes libres).
SELECT center_id, patient_id, categorie, libelle, count(*) AS exemplaires FROM etapes_bilan_pre_greffe GROUP BY center_id, patient_id, categorie, libelle HAVING count(*) > 1;
-- 5. LIGNES ILLISIBLES : IGNORÉES par les lectures du module (WARN avec l'identifiant seul), supprimables mais non modifiables ; un statut de bilan ou d'avis illisible répond 422 `BILAN_PRE_GREFFE_ILLISIBLE`.
SELECT 'bilans' AS table_, center_id, id FROM bilans_pre_greffe
 WHERE statut NOT IN ('NON_DEBUTE','BILAN_EN_COURS','ELIGIBLE','INSCRIT_LISTE_ATTENTE','CONTRE_INDICATION_TEMPORAIRE','CONTRE_INDICATION_DEFINITIVE','GREFFE_REALISEE')
UNION ALL SELECT 'decisions_rcp', b.center_id, d.id FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id WHERE d.avis NOT IN ('FAVORABLE','DEFAVORABLE','AJOURNE')
UNION ALL SELECT 'etapes', center_id, id FROM etapes_bilan_pre_greffe
 WHERE categorie NOT IN ('CARDIOLOGIQUE','PNEUMOLOGIQUE','DENTAIRE','ORL','GYNECOLOGIQUE','UROLOGIQUE','DIGESTIF','ONCOLOGIQUE','PSYCHIATRIQUE','VIROLOGIQUE','IMMUNOLOGIQUE','VACCINATION','NUTRITIONNEL','AUTRE','DERMATOLOGIQUE')
    OR statut NOT IN ('A_FAIRE','PLANIFIE','FAIT','NON_APPLICABLE')
UNION ALL SELECT 'donneurs', center_id, id FROM donneurs_vivants
 WHERE lien_parente NOT IN ('CONJOINT','PARENT','ENFANT','FRERE_SOEUR','AUTRE_FAMILLE','NON_APPARENTE')
    OR statut_bilan NOT IN ('CANDIDAT','BILAN_EN_COURS','COMPATIBLE','INCOMPATIBLE','EXCLU','RETENU')
    OR crossmatch_resultat NOT IN ('NON_FAIT','NEGATIF','POSITIF');
-- 6. Règles violables par import ou SQL (impossibles par l'API) : étape faite sans date, décision de RCP dans le futur.
SELECT 'etape_faite_sans_date' AS controle, center_id, count(*) FROM etapes_bilan_pre_greffe WHERE statut = 'FAIT' AND date_realisation IS NULL GROUP BY center_id
UNION ALL SELECT 'rcp_dans_le_futur', b.center_id, count(*) FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id WHERE d.date_reunion > current_date GROUP BY b.center_id;
-- 7. Lecteurs inchangés (KDIGO, export PDF, statistiques, direction) : statut des bilans, avant et après, identique.
SELECT statut, count(*) FROM bilans_pre_greffe GROUP BY statut ORDER BY 1;
```
Les valeurs d'énumération de la requête 5 sont celles du code : à comparer avec `CategorieDEtape`, `LienDeParente` et `StatutDuBilanDuDonneur` du module avant exécution (une valeur oubliée ramènerait une ligne lisible à tort).
Recette obligatoire AVANT bascule : (a) avec un compte MEDECIN, lire le bilan d'un patient (ouverture d'un bilan « non débuté » si absent), passer le statut à « bilan en cours » puis « éligible » puis « inscrit », refuser « greffé » depuis « non débuté » (422), saisir le bilan immunologique et les notes, ajouter une décision de RCP, générer la liste standard deux fois (23 étapes, jamais 46), mettre à jour puis supprimer une étape, enregistrer, mettre à jour et supprimer un donneur ; lire l'évaluation KDIGO et télécharger le PDF ; (b) avec un patient d'un AUTRE centre ou inconnu : 404 sur le bilan, les étapes, les donneurs, KDIGO et le PDF ; (c) avec un compte INFIRMIER : étapes en lecture et écriture (suppression refusée), tout le reste refusé en 403 ; avec un compte ADMIN : lecture du bilan et des donneurs, écriture refusée (403), PDF refusé ; (d) vérifier que KDIGO et le PDF rendent les mêmes valeurs qu'avant la reprise (le risque KB-MED-94 reste présent : ne pas le « corriger » sans décision médicale).
Surveillance après déploiement : hausse attendue des 400 (corps, énumération, date, identifiant, borne de colonne, avant 500), des 403 (corps illisible d'un profil sans droit), des 404 (patient inconnu ou d'un autre centre, étape ou donneur introuvable, avant 400 ou 200) et disparition des lignes d'audit `PATIENTS_SUPPRESSION` 204 sans suppression ; surveiller les 409 `BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE`, `ETAPE_BILAN_ECRITURE_CONCURRENTE`, `DONNEUR_VIVANT_ECRITURE_CONCURRENTE`, les 422 `BILAN_PRE_GREFFE_ILLISIBLE` et les WARN « … ignoré : … illisible en base » ou « doublon ». Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 52. Dossier médical DM8 (dossier de base, export FHIR, statistiques médicales, indicateurs Direction) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, même table `dossier_medical_patient` (11 colonnes, clé primaire et unicité GLOBALE sur `patient_id`), mêmes lectures sur `ordonnances`, `observations_biologiques`, `abords_vasculaires`. Données de santé sensibles. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement (ADR 0087). Aucune correction automatique.
```sql
-- 1. Dossiers par centre (avant/après : identiques, la reprise ne change aucune ligne).
SELECT center_id, count(*) FROM dossier_medical_patient GROUP BY center_id;
-- 2. DOUBLONS (impossibles tant que l'unicité de patient_id existe ; si elle a été retirée, le module lit le PLUS ANCIEN et journalise un WARN avec l'identifiant seul).
SELECT patient_id, count(*) AS dossiers FROM dossier_medical_patient GROUP BY patient_id HAVING count(*) > 1;
-- 3. ORPHELINS (KB-MED-98 : avant la reprise, l'écriture pour un patient inconnu ou d'un autre centre était acceptée) : dossiers dont le patient est absent du centre du dossier. À expliquer avec les métiers et le DPO.
SELECT t.center_id, count(*) AS patient_absent_ou_autre_centre FROM dossier_medical_patient t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id;
-- 4. ORDONNANCES AU STATUT ILLISIBLE (KB-MED-102, RISQUE CLINIQUE : ignorées en silence par les lectures, dont l'export FHIR, avec un WARN serveur portant l'identifiant seul ; le destinataire du bundle n'en est pas informé).
SELECT center_id, id FROM ordonnances WHERE statut NOT IN ('BROUILLON','SIGNEE','IMPRIMEE','ANNULEE');
-- 5. ALLERGIES, OBSERVATIONS ET ANTÉCÉDENTS ILLISIBLES (KB-MED-102, RISQUE CLINIQUE : ignorés en silence par l'export FHIR, comme par leurs propres routes). Les abords vasculaires n'ont pas de valeur énumérée : aucune ligne illisible possible. Un NULL dans une colonne énumérée rend aussi la ligne illisible.
SELECT 'allergies' AS famille, center_id, id FROM allergies_patient
 WHERE categorie IS NULL OR categorie NOT IN ('MEDICAMENT','ALIMENT','ENVIRONNEMENT','BIOLOGIQUE')
    OR criticite IS NULL OR criticite NOT IN ('BASSE','HAUTE')
    OR type_reaction IS NULL OR type_reaction NOT IN ('ALLERGIE','INTOLERANCE')
    OR statut_verification IS NULL OR statut_verification NOT IN ('SUSPECTEE','CONFIRMEE','REFUTEE')
    OR substance_code_system IS NULL OR substance_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')
UNION ALL SELECT 'observations', center_id, id FROM observations_biologiques
 WHERE analyte_code_system IS NULL OR analyte_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')
    OR statut IS NULL OR statut NOT IN ('PRELIMINAIRE','FINAL','CORRIGE')
    OR source IS NULL OR source NOT IN ('SAISIE_DIRECTE','DERIVEE_BILAN','IMPORT')
    OR (valeur_num IS NOT NULL AND (unite IS NULL OR TRIM(unite) = ''))
UNION ALL SELECT 'antecedents', center_id, id FROM antecedents_medicaux
 WHERE type_antecedent IS NULL OR type_antecedent NOT IN ('MEDICAL','CHIRURGICAL','FAMILIAL','OBSTETRICAL','COMORBIDITE')
    OR statut_clinique IS NULL OR statut_clinique NOT IN ('ACTIF','RESOLU','INACTIF')
    OR (diagnostic_code IS NOT NULL AND (diagnostic_code_system IS NULL OR diagnostic_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')));
-- 6. PATIENTS DONT L'EXPORT FHIR EST TRONQUÉ (KB-MED-101, RISQUE CLINIQUE reproduit : seules les 500 observations et les 500 ordonnances les plus récentes sont exportées, sans avertissement).
SELECT 'observations' AS source, center_id, patient_id, count(*) AS nombre FROM observations_biologiques GROUP BY center_id, patient_id HAVING count(*) > 500
UNION ALL SELECT 'ordonnances', center_id, patient_id, count(*) FROM ordonnances GROUP BY center_id, patient_id HAVING count(*) > 500;
```
Recette obligatoire AVANT bascule : (a) avec un compte MEDECIN, créer puis remplacer le dossier d'un patient valide ; (b) avec un patient inconnu ou d'un AUTRE centre : 404 `PATIENT_INTROUVABLE` à l'écriture et à l'export FHIR ; (c) INFIRMIER : lecture seule du dossier, export FHIR refusé (403) ; (d) un champ trop long, une date illisible ou un corps illisible : 400 (avant 500) ; (e) lire les statistiques médicales, leur export CSV et PDF et les indicateurs Direction : mêmes valeurs qu'avant ; une période illisible : 400 ; (f) rappel des trois risques à faire arbitrer AVANT bascule : KB-MED-104 (les totaux Direction incluent les effectifs masqués < 5 : fuite par différence, PRIORITAIRE pour le DPO), KB-MED-101 (export FHIR tronqué en silence : requête 6) et KB-MED-102 (lignes illisibles omises en silence de l'export FHIR : requêtes 4 et 5 ; décision médicale : corriger les lignes, signaler l'omission dans le bundle ou refuser l'export) ; (g) les requêtes 4 et 5 doivent ramener 0 ligne, ou chaque ligne ramenée doit être expliquée avec les médecins avant la bascule.
Surveillance après déploiement : hausse attendue des 400 (avant 500), des 404 (patient inconnu ou d'un autre centre) ; surveiller les 409 `DOSSIER_MEDICAL_ECRITURE_CONCURRENTE` et les WARN « … ignorée : statut illisible en base » ou « doublon ». Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 53. Séance SE1 (création, scan, consultation) : parité et gardes (2026-11-07) — SELECT seulement

Aucune migration, même table `seances` (28 colonnes), mêmes URLs (ADR 0088). Ces requêtes sont celles que `backend/scripts/seance-se1-pg16-rejeu.sh` exécute sur un cluster PG16 jetable (schéma réel, jamais le port 5432). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucune correction automatique.
```sql
-- 1. DOUBLONS (KB-SEA-02) : plusieurs séances pour le même patient le même jour dans un centre. Le scan répond désormais 409 SEANCE_DU_JOUR_AMBIGUE pour ces patients (avant : 500). Aucune unicité n'existe ni n'est inventée ; la création manuelle de doublons reste possible (décision métier ouverte).
SELECT center_id, patient_id, date_seance, count(*) AS seances FROM seances GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1;
-- 2. STATUTS ILLISIBLES (KB-SEA-07) : la liste ignore ces lignes (WARN avec l'identifiant seul), le détail est refusé. À expliquer, puis corriger à la main avec les métiers.
SELECT id, center_id, statut FROM seances WHERE statut NOT IN ('CREE','VALIDEE','SIGNEE','FACTUREE','ABSENT');
-- 3. HORS PLANNING INCOHÉRENT : motif absent ou inconnu, ou précision manquante pour AUTRE.
SELECT id, center_id FROM seances WHERE hors_planning AND (motif_hors_planning IS NULL OR motif_hors_planning NOT IN ('RATTRAPAGE','URGENCE','AUTRE') OR (motif_hors_planning = 'AUTRE' AND (precision_hors_planning IS NULL OR TRIM(precision_hors_planning) = '')));
-- 4. ORPHELINES : séances dont le patient est absent du centre de la séance (le scan et la création répondent 404 PATIENT_INTROUVABLE pour un patient inconnu ou d'un autre centre).
SELECT s.center_id, count(*) AS patient_absent_ou_autre_centre FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE p.id IS NULL GROUP BY s.center_id;
```
Recette AVANT bascule : (a) créer une séance sans date puis par scan, entre minuit et 1 h heure du centre : la date est celle du jour du centre (KB-SEA-01) ; (b) scan d'un patient aux séances multiples : 409 ; (c) POST sans centre : 400 « Centre requis » ; (d) QR sans caractère alphanumérique : 400 ; patient d'un autre centre : 404 ; (e) précision de motif de plus de 255 caractères : 400 ; (f) les requêtes 1 et 2 doivent ramener 0 ligne ou chaque ligne doit être expliquée. Risque à arbitrer avant mise en production : KB-SEA-03 (la création manuelle ne contrôle pas le planning). Surveillance : hausse attendue des 400, 404 et 409 `SEANCE_DU_JOUR_AMBIGUE`, WARN de statut illisible. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 54. Séance SE2 (validation, signature du médecin, déverrouillage de régularisation) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, même table `seances`, mêmes URLs (ADR 0089). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP`.
```sql
-- 1. SIGNATURES INCOHÉRENTES (KB-SEA-20) : signée sans validation infirmière ni signature médicale complète, ou signature médicale sur une séance non validée.
SELECT id, center_id, statut FROM seances WHERE statut = 'SIGNEE' AND (validated_at IS NULL OR signed_infirmier_by IS NULL OR signed_medecin_at IS NULL OR signed_medecin_by IS NULL);
SELECT id, center_id, statut FROM seances WHERE statut IN ('CREE','VALIDEE') AND (signed_medecin_at IS NOT NULL OR signed_medecin_by IS NOT NULL);
-- 2. SIGNATAIRES TROP LONGS : plus de 100 caractères (le module répond désormais 400 au lieu de 500).
SELECT id, center_id FROM seances WHERE length(signed_infirmier_by) > 100 OR length(signed_medecin_by) > 100 OR length(regularisation_deverrouillee_by) > 100;
-- 3. SIGNATAIRES DISTINCTS PAR CENTRE (KB-SEA-11) : le signataire vient du corps de la requête, jamais de la session ; une valeur inattendue (« system », compte d'un autre centre, texte libre) est à expliquer avec le juridique et le DPO avant la mise en production.
SELECT center_id, signed_medecin_by AS signataire_medical, count(*) AS signatures FROM seances WHERE signed_medecin_by IS NOT NULL GROUP BY center_id, signed_medecin_by ORDER BY center_id, signatures DESC;
-- 4. SÉANCES À VENIR VALIDÉES (KB-SEA-12) : validation anticipée admise par le legacy et reproduite.
SELECT center_id, count(*) AS validees_a_venir FROM seances WHERE statut IN ('VALIDEE','SIGNEE') AND date_seance > CURRENT_DATE GROUP BY center_id;
-- 5. DÉVERROUILLAGES ANCIENS (KB-SEA-13) : séances déverrouillées plus de 7 jours après leur date (aucune borne n'existe, la fenêtre de 7 jours ne concerne que le rappel).
SELECT center_id, count(*) AS deverrouillees_au_dela_de_7_jours FROM seances WHERE regularisation_deverrouillee_at IS NOT NULL AND CAST(regularisation_deverrouillee_at AS DATE) - date_seance > 7 GROUP BY center_id;
-- 6. DÉVERROUILLÉES NON RÉGULARISÉES : déverrouillage posé mais séance toujours créée (travail en attente des infirmiers).
SELECT center_id, count(*) AS en_attente FROM seances WHERE regularisation_deverrouillee_at IS NOT NULL AND statut = 'CREE' GROUP BY center_id;
-- 7. VALIDÉES SANS PLACE FIGÉE (RG-SEA-049) : les séances validées avant la migration V26 n'en ont pas ; une validation répétée ne la remplace jamais.
SELECT center_id, count(*) AS sans_place FROM seances WHERE statut IN ('VALIDEE','SIGNEE') AND (salle_id IS NULL OR creneau_id IS NULL) GROUP BY center_id;
```
Recette AVANT bascule : (a) valider une séance du jour avec et sans consommable, puis la revalider (cumul des consommables attendu, KB-SEA-15) ; (b) valider un jour passé sans déverrouillage : 422 `SEANCE_REGULARISATION_NON_DEVERROUILLEE` pour l'infirmier, 200 pour l'administrateur ; (c) signer une séance créée : refus, une séance validée : `SIGNEE` ; (d) déverrouiller une séance créée d'un jour passé (idempotent) puis la valider en infirmier ; (e) corps illisible, `centerId` absent, signataire de plus de 100 caractères : 400 ; mauvais rôle : 403 avant le 400 ; (f) deux validations simultanées : la seconde attend le verrou ou répond 409 `ECRITURE_CONCURRENTE` ; (g) entre minuit et 1 h heure du centre, le « jour passé » est celui du centre ; (h) les requêtes 1 et 2 doivent ramener 0 ligne ou chaque ligne doit être expliquée. Risques à arbitrer AVANT mise en production : KB-SEA-11 (signataire lu dans le corps : imputation d'une signature médicale, décision juridique et DPO), KB-SEA-12, KB-SEA-13, KB-SEA-15 (voir l'ADR 0089). Surveillance : hausse attendue des 400 (corps illisible, signataire long) et des 404 `PATIENT_INTROUVABLE`, baisse des 500 sur les trois routes, 409 `ECRITURE_CONCURRENTE`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 55. Séance SE3 (consommables déclarés, raccourcis de consommables) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, mêmes tables (`seance_raccourcis_articles`, `consommations_de_seance`, `consommations_de_seance_imputations`), mêmes URLs (ADR 0090). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP`.
```sql
-- 1. RACCOURCIS HORS BORNE : plus de 12 raccourcis pour un centre (le module refuse d'en écrire plus de 12).
SELECT center_id, count(*) AS raccourcis FROM seance_raccourcis_articles GROUP BY center_id HAVING count(*) > 12;
-- 2. RACCOURCIS D'UN ARTICLE INACTIF, INCONNU OU D'UN AUTRE CENTRE (le module n'en écrit pas ; un existant est à expliquer).
SELECT r.center_id, r.article_id, a.code, a.active FROM seance_raccourcis_articles r LEFT JOIN articles a ON a.id = r.article_id WHERE a.id IS NULL OR a.active = FALSE OR a.center_id <> r.center_id;
-- 3. CENTRES SANS RACCOURCI (KB-SEA-31 : un PUT avec un corps vide efface tous les raccourcis du centre ; à rapprocher du journal d'audit SEANCES_MODIFICATION sans entité).
SELECT c.id AS center_id FROM centers c WHERE NOT EXISTS (SELECT 1 FROM seance_raccourcis_articles r WHERE r.center_id = c.id);
-- 4. CONSOMMATIONS D'UNE SÉANCE INTROUVABLE OU D'UN AUTRE CENTRE (KB-SEA-27 : l'ancien code pouvait écrire puis répondre 400).
SELECT cs.seance_id, cs.center_id FROM consommations_de_seance cs LEFT JOIN seances s ON s.id = cs.seance_id WHERE s.id IS NULL OR s.center_id <> cs.center_id;
-- 5. CONSOMMATIONS SUR SÉANCE NON VALIDÉE (KB-SEA-24 : modification et retrait acceptés sur CREE ou ABSENT, reproduit).
SELECT s.center_id, s.statut, count(*) AS consommations FROM consommations_de_seance cs JOIN seances s ON s.id = cs.seance_id WHERE s.statut NOT IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, s.statut;
-- 6. IMPUTATIONS SANS ENTÊTE DE CONSOMMATION.
SELECT i.seance_id, count(*) AS imputations FROM consommations_de_seance_imputations i LEFT JOIN consommations_de_seance cs ON cs.seance_id = i.seance_id WHERE cs.seance_id IS NULL GROUP BY i.seance_id;
```
Recette AVANT bascule : (a) ajouter un consommable à une séance validée (200) ; créée ou facturée : refus avec le message actuel ; (b) modifier puis retirer : 200, aucun mouvement de stock (KB-SEA-22) ; (c) article inconnu, d'un autre centre, inactif, quantité nulle ou négative : 400 `BAD_REQUEST` sur les trois routes (KB-SEA-25) ; (d) corps illisible : 400, mauvais rôle : 403 avant le 400 (KB-SEA-26) ; (e) patient disparu : 404 `PATIENT_INTROUVABLE`, rien d'écrit (KB-SEA-27) ; (f) raccourcis : lire, remplacer par 12 articles actifs puis 13 (refus), relire (cache purgé), `PUT {}` sur un centre de test uniquement (KB-SEA-31) ; (g) deux écritures simultanées sur la même séance : la seconde attend ou répond 409 `ECRITURE_CONCURRENTE` ; (h) les requêtes 1, 2, 4 et 6 doivent ramener 0 ligne ou chaque ligne doit être expliquée. Risques à arbitrer AVANT mise en production : KB-SEA-33 (auteur ignoré par l'adapter du Stock : risque médico-légal, DPO), KB-SEA-22, KB-SEA-24, KB-SEA-31 (voir l'ADR 0090). Surveillance : hausse attendue des 400 (formes) et des 404 `PATIENT_INTROUVABLE`, baisse des 500, 409 `ECRITURE_CONCURRENTE`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024).

## 56. Séance SE4 (rappel quotidien des séances à régulariser ; la « dérogation » n'est pas un agrégat) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ajoutée, aucune route (ADR 0091). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement, puis chaque semaine tant que KB-SEA-37 n'est pas tranchée. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP` (la table V50 n'est jamais supprimée).
```sql
-- 1. KB-SEA-37 (RISQUE, À LANCER CHAQUE SEMAINE) : séances « créées » oubliées HORS fenêtre de rappel (plus de 7 jours) ET non déverrouillées : bloquées pour l'infirmier, jamais rappelées.
SELECT s.center_id, count(*) AS oubliees_hors_fenetre, min(s.date_seance) AS plus_ancienne FROM seances s WHERE s.statut = 'CREE' AND s.date_seance < current_date - 7 AND s.regularisation_deverrouillee_at IS NULL GROUP BY s.center_id;
-- 2. KB-SEA-40 : contenu de la table V50 (jamais écrite par le code : doit ramener 0 ; toute ligne est à expliquer, quelqu'un a écrit à la main).
SELECT count(*) AS lignes FROM derogations_regularisation;
SELECT center_id, seance_id, motif, autorite, date_derogation FROM derogations_regularisation ORDER BY date_derogation DESC LIMIT 50;
-- 3. SÉANCES « CRÉÉES » DÉVERROUILLÉES MAIS PAS ENCORE VALIDÉES (le rappel ne les compte plus : à suivre pour qu'elles ne restent pas en suspens).
SELECT s.center_id, count(*) AS creees_deverrouillees, min(s.regularisation_deverrouillee_at) AS depuis FROM seances s WHERE s.statut = 'CREE' AND s.regularisation_deverrouillee_at IS NOT NULL GROUP BY s.center_id;
-- 4. KB-SEA-38 : séances « créées » dont le patient a disparu (comptées par le rappel, refusées au déverrouillage et à la validation : intervention en base à décider).
SELECT s.center_id, s.id AS seance_id, s.date_seance FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.statut = 'CREE' AND p.id IS NULL;
-- 5. KB-SEA-35 : plus d'un rappel le même jour et pour le même centre dans le journal (les rappels antérieurs au déploiement en ont un par appel ; après, 0 ligne attendue).
SELECT center_id, cree_le::date AS jour, count(*) AS rappels FROM notification_evenement WHERE type = 'SEANCES_A_REGULARISER' GROUP BY center_id, cree_le::date HAVING count(*) > 1 ORDER BY jour DESC LIMIT 50;
-- 6. RAPPEL DU JOUR : un par centre ayant des séances à régulariser dans la fenêtre (comparer avec la requête 7 après 07:00 du fuseau du centre).
SELECT center_id, payload, cree_le FROM notification_evenement WHERE type = 'SEANCES_A_REGULARISER' AND cree_le >= current_date ORDER BY center_id;
-- 7. Séances à régulariser dans la fenêtre, par centre (même règle que le rappel : CREE, J-7 à J-1, non déverrouillées).
SELECT s.center_id, count(*) AS a_regulariser, min(s.date_seance) AS plus_ancienne FROM seances s WHERE s.statut = 'CREE' AND s.date_seance BETWEEN current_date - 7 AND current_date - 1 AND s.regularisation_deverrouillee_at IS NULL GROUP BY s.center_id;
```
Recette AVANT bascule : (a) créer des séances `CREE` à J-1, J-4, J-7, J-8 dans un centre de test : après 07:00 (fuseau de la planification), UN seul rappel `SEANCES_A_REGULARISER` de 3 séances (la plus ancienne J-7) ; (b) relancer le travail le même jour : aucun second rappel ni seconde ligne de journal (KB-SEA-35) ; (c) déverrouiller puis valider : le rappel du lendemain décroît ; (d) la séance J-8 n'est PAS rappelée (KB-SEA-37 : risque connu) ; (e) deux instances du serveur : un seul rappel par centre et par jour. **Condition de déploiement : le planificateur est actif dès le déploiement, sans indicateur d'activation** (le planificateur legacy est supprimé, il n'y a pas de double déclenchement) ; vérifier avant la mise en production soit qu'**une seule instance** porte le planificateur, soit que le verrou consultatif par centre et jour (`rappel-regularisation|centre|jour`, PostgreSQL) est opérationnel : **le verrou est la vraie garantie contre le double rappel de deux instances** (contrat `VerrousDesRappelsPostgresContractIT`, script `seance-se4-pg16-rejeu.sh`) ; il n'y a PAS de repli sur la violation de clé du journal (sur PostgreSQL elle abandonne la transaction), et le scénario complet à deux instances n'est pas prouvé : la recette (e) en tient lieu. Risque à arbitrer AVANT mise en production : KB-SEA-37 (voir l'ADR 0091, revue au plus tard le 2026-12-15). Surveillance : un rappel par centre et par jour, requête 5 à 0 ligne après le déploiement, journal applicatif `[SEANCES]`. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; le rappel redevient celui du service legacy (un identifiant aléatoire par appel).

## 57. Séance SE5 (surcharge du forfait d'une séance, « imposer un forfait ») : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune nouvelle route (ADR 0092 ; `PUT /api/v1/seances/{id}/forfait` change de contrôleur). **Risques d'argent à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-41, 42, 43, 46, 47, 49 ; la décision écrite de chacun est une condition de mise en production. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement, puis chaque semaine tant que ces risques ne sont pas tranchés. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP`. Les requêtes 1 à 10 sont rejouées sur le schéma réel par `backend/scripts/seance-se5-pg16-rejeu.sh`.
```sql
-- 1. KB-SEA-41 (ARGENT) : séances surchargées SANS PRIX ou à 0 (le forfait imposé n'a pas de prix, ou 0.00 : la séance sera facturée à 0 ou sans prix, sans alerte).
SELECT s.center_id, count(*) AS surchargees_sans_prix_ou_a_zero FROM seances s WHERE s.forfait_override_id IS NOT NULL AND (s.forfait_override_prix IS NULL OR s.forfait_override_prix = 0) GROUP BY s.center_id;
-- 2. KB-SEA-43 (ARGENT) : surcharges posées sur une séance « créée » ou « absente » (seul FACTUREE est refusé).
SELECT s.center_id, s.statut, count(*) AS surchargees FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.statut IN ('CREE','ABSENT') GROUP BY s.center_id, s.statut;
-- 3. KB-SEA-43 (ARGENT) : séances portant une facture ET un forfait surchargé (la surcharge a pu être posée ou changée APRÈS la facturation : comparer l'instant de la surcharge à la date de la facture).
SELECT s.center_id, s.id, s.statut, s.facture_id, s.forfait_override_updated_at FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.facture_id IS NOT NULL AND s.forfait_override_updated_at IS NOT NULL ORDER BY s.forfait_override_updated_at DESC LIMIT 50;
-- 4. KB-SEA-47 (ARGENT, DÉTECTION) : forfait surchargé SANS CODE (la Facturation `poserLeForfait` n'écrit pas forfait_override_code ; le cahier de dialyse l'affiche).
SELECT s.center_id, count(*) AS id_sans_code FROM seances s WHERE s.forfait_override_id IS NOT NULL AND (s.forfait_override_code IS NULL OR s.forfait_override_code = '') GROUP BY s.center_id;
-- 5. KB-SEA-47 (ARGENT, DÉTECTION) : code surchargé DIFFÉRENT du code du référentiel pour le même forfait (code resté celui d'une ancienne surcharge après un changement de forfait de simulation, ou référentiel renommé depuis).
SELECT s.center_id, s.id, s.forfait_override_code, f.code AS code_du_referentiel FROM seances s INNER JOIN forfait f ON f.id = s.forfait_override_id AND f.center_id = s.center_id WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS DISTINCT FROM f.code;
-- 6. KB-SEA-42 : auteurs des surcharges (champ libre du corps, jamais le compte de session : à rapprocher des comptes réels).
SELECT s.center_id, s.forfait_override_updated_by, count(*) AS surcharges FROM seances s WHERE s.forfait_override_id IS NOT NULL GROUP BY s.center_id, s.forfait_override_updated_by ORDER BY 3 DESC LIMIT 50;
-- 7. Forfait surchargé introuvable dans le référentiel du centre (forfait supprimé malgré le blocage, ou d'un autre centre) : doit ramener 0 ligne.
SELECT s.center_id, s.id FROM seances s LEFT JOIN forfait f ON f.id = s.forfait_override_id AND f.center_id = s.center_id WHERE s.forfait_override_id IS NOT NULL AND f.id IS NULL;
-- 8. KB-SEA-46 (ARGENT) : séances SANS forfait imposé dont le forfait affiché peut venir d'une prise en charge NON VALIDÉE (le forfait courant ignore le statut de la PEC), par centre et par statut de la PEC.
SELECT s.center_id, p.statut, count(DISTINCT s.id) AS seances_couvertes_par_une_pec_non_validee FROM seances s INNER JOIN prise_en_charge p ON p.center_id = s.center_id AND p.patient_id = s.patient_id AND p.statut <> 'VALIDEE' AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL WHERE s.forfait_override_id IS NULL AND ((p.date_debut_effectif IS NOT NULL AND s.date_seance >= p.date_debut_effectif AND (s.date_seance <= p.date_fin_effectif OR p.date_fin_effectif IS NULL)) OR (p.date_debut_demande IS NOT NULL AND s.date_seance >= p.date_debut_demande AND (s.date_seance <= p.date_fin_demande OR p.date_fin_demande IS NULL))) GROUP BY s.center_id, p.statut;
-- 9. KB-SEA-49 (ARGENT, DÉTECTION) : factures dont le nombre de séances rattachées (seances.facture_id) diffère du nombre de séances déclaré par leurs lignes (un changement de date `PUT /api/v1/seances/{id}` d'une séance non FACTUREE efface son facture_id). Doit ramener 0 ligne.
SELECT f.center_id, f.id, f.numero_facture, COALESCE(l.declarees, 0) AS seances_declarees, COALESCE(s.rattachees, 0) AS seances_rattachees FROM factures f LEFT JOIN (SELECT facture_id, center_id, sum(seance_count) AS declarees FROM facture_lignes GROUP BY facture_id, center_id) l ON l.facture_id = f.id AND l.center_id = f.center_id LEFT JOIN (SELECT facture_id, center_id, count(*) AS rattachees FROM seances WHERE facture_id IS NOT NULL GROUP BY facture_id, center_id) s ON s.facture_id = f.id AND s.center_id = f.center_id WHERE COALESCE(l.declarees, 0) <> COALESCE(s.rattachees, 0);
-- 10. KB-SEA-49 (ARGENT, EXPOSITION) : séances qui portent une facture sans être FACTUREE (celles dont le prochain changement de date ou de forfait efface ou contourne la facture), par centre et par statut.
SELECT s.center_id, s.statut, count(*) AS seances_avec_facture_non_facturees FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id, s.statut;
```
Recette AVANT bascule : (a) imposer un forfait à une séance validée : 200, forfait à sept clés dans le détail et la liste, le référentiel modifié ensuite ne change pas la séance ; (b) l'imposer deux fois : tout est remplacé, l'instant change, aucun historique ; (c) corps illisible, identifiant illisible, auteur de 101 caractères : 400 et non 500 ; séance facturée : 422 ; infirmier, médecin, secrétaire : 403 ; (d) deux impositions simultanées de la même séance : la seconde reçoit 409 `ECRITURE_CONCURRENTE` (délai de 5 s), jamais un écrasement silencieux ; (e) un forfait sans prix est accepté (KB-SEA-41 : risque connu). **Conditions de mise en production (décision écrite, ADR 0092)** : prix absent ou nul (requête 1), statuts autorisés et séance déjà facturée (requêtes 2 et 3), statut de la prise en charge (requête 8), code écrit par la Facturation (requêtes 4 et 5, slice Facturation), auteur libre (requête 6), `facture_id` effacé par le changement de date d'une séance non facturée (KB-SEA-49, requêtes 9 et 10, route non reprise par SE5, hors périmètre, NON corrigé). Limite connue : le verrou de ligne ne couvre pas `poserLeForfait` de la Facturation (résidu daté, ADR 0092). Surveillance : requêtes 1, 3, 4 et 10 chaque semaine, requêtes 7 et 9 à 0 ligne. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; les colonnes ne changent pas, aucune donnée n'est à restaurer.

**Mise à jour SE6 (2026-11-08) :** KB-SEA-49 est CORRIGÉ pour l'avenir par SE6 (le changement de jour n'écrit plus que `date_seance`, ADR 0093) ; les requêtes 9 et 10 ci-dessus restent valables pour DÉTECTER les dégâts déjà commis par le legacy (voir la section 58, requêtes 1, 3, 4 et 8).

## 58. Séance SE6 (changement de jour, suppression d'une séance) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune nouvelle route (ADR 0093 ; `PUT` et `DELETE /api/v1/seances/{id}` changent de contrôleur). **Risques d'intégrité de facturation et d'argent à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-49, 50, 54, 55, 51 ; la décision écrite de chacun est une condition de mise en production. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement, puis chaque semaine tant que ces risques ne sont pas tranchés. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP`. Les requêtes 1 à 9 sont rejouées sur le schéma réel par `backend/scripts/seance-se6-pg16-rejeu.sh` (même numérotation).
```sql
-- 1. KB-SEA-50 (INTÉGRITÉ, DÉTECTION DES FACTURES DÉJÀ ORPHELINES) : factures dont le nombre de séances déclarées par leurs lignes dépasse celui des séances encore rattachées (seances.facture_id) : une séance a été supprimée (KB-SEA-50) ou son facture_id effacé (KB-SEA-49). Doit ramener 0 ligne ; sinon la Facturation traite chaque facture AVANT la mise en production.
SELECT f.center_id, f.id, f.numero_facture, COALESCE(l.declarees, 0) AS seances_declarees, COALESCE(s.rattachees, 0) AS seances_rattachees FROM factures f LEFT JOIN (SELECT facture_id, center_id, sum(seance_count) AS declarees FROM facture_lignes GROUP BY facture_id, center_id) l ON l.facture_id = f.id AND l.center_id = f.center_id LEFT JOIN (SELECT facture_id, center_id, count(*) AS rattachees FROM seances WHERE facture_id IS NOT NULL GROUP BY facture_id, center_id) s ON s.facture_id = f.id AND s.center_id = f.center_id WHERE COALESCE(l.declarees, 0) > COALESCE(s.rattachees, 0);
-- 2. KB-SEA-50 (INTÉGRITÉ, ORIGINE) : suppressions journalisées dont le jour tombe dans la période d'une facture du même patient (le journal ne garde ni la facture ni son lien : c'est le seul rapprochement possible, à confirmer ligne à ligne avec la requête 1).
SELECT ss.center_id, ss.seance_id, ss.patient_id, ss.date_seance, ss.statut, ss.supprime_le, f.id AS facture, f.numero_facture FROM seance_suppression ss INNER JOIN factures f ON f.center_id = ss.center_id AND f.patient_id = ss.patient_id AND ss.date_seance BETWEEN f.period_start AND f.period_end ORDER BY ss.supprime_le DESC LIMIT 100;
-- 3. KB-SEA-49 / 50 (EXPOSITION) : séances qui portent une facture sans être FACTUREE, par centre et par statut (le legacy les laissait supprimer ou effacer leur facture_id ; SE6 refuse la suppression et préserve la facture).
SELECT s.center_id, s.statut, count(*) AS rattachees_non_facturees FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id, s.statut;
-- 4. KB-SEA-54 (ARGENT, DÉTECTION) : séances rattachées à une facture dont le jour est HORS de la période de cette facture (un changement de jour après facturation ou rattachement a déplacé la séance).
SELECT s.center_id, s.id, s.statut, s.date_seance, s.facture_id FROM seances s INNER JOIN factures f ON f.id = s.facture_id AND f.center_id = s.center_id WHERE s.date_seance NOT BETWEEN f.period_start AND f.period_end LIMIT 100;
-- 5. KB-SEA-54 / 55 (TRAÇABILITÉ) : changements de jour réussis tracés par l'audit (le seul vestige : compte, instant, route ; ni ancien ni nouveau jour).
SELECT a.center_id, a.entity_id, a.username, a.occurred_at, a.status_code FROM audit_log a WHERE a.action_code = 'SEANCES_MODIFICATION' AND a.http_method = 'PUT' AND a.route_template = '/api/v1/seances/{seanceId}' AND a.status_code BETWEEN 200 AND 299 ORDER BY a.occurred_at DESC LIMIT 100;
-- 6. Suppressions réussies par centre et par compte (surveillance : un pic ou un compte inattendu).
SELECT a.center_id, a.username, count(*) AS suppressions FROM audit_log a WHERE a.action_code = 'SEANCES_SUPPRESSION' AND a.status_code = 204 GROUP BY a.center_id, a.username ORDER BY 3 DESC LIMIT 50;
-- 7. KB-SEA-51 (STOCK) : suppressions de séances validées ou signées par centre (leurs consommations imputées sont effacées SANS restitution de stock : à rapprocher de l'état des lots).
SELECT ss.center_id, ss.statut, count(*) AS suppressions_de_seances_validees_ou_signees FROM seance_suppression ss WHERE ss.statut IN ('VALIDEE','SIGNEE') GROUP BY ss.center_id, ss.statut;
-- 8. KB-SEA-49 (INTÉGRITÉ, ORIGINE) : séances SANS facture dont le jour tombe dans la période d'une facture du même patient (candidates à une facture_id effacée par l'ancien changement de date ; une séance créée après la facture figure aussi ici : à rapprocher de la requête 1).
SELECT s.center_id, s.id, s.statut, s.date_seance, f.id AS facture, f.numero_facture FROM seances s INNER JOIN factures f ON f.center_id = s.center_id AND f.patient_id = s.patient_id AND s.date_seance BETWEEN f.period_start AND f.period_end WHERE s.facture_id IS NULL AND s.statut IN ('CREE','VALIDEE','SIGNEE') ORDER BY s.date_seance DESC LIMIT 100;
-- 9. KB-SEA-51 (INVENTAIRE DES LIGNES CONSERVÉES) : bons de sortie et mouvements de stock dont la séance n'existe plus (aucune clé étrangère : la suppression les laisse, comme le legacy). « dont_suppression_journalisee » = celles qu'explique le journal ; l'écart est à expliquer (ancienne suppression non journalisée, ou donnée d'origine).
SELECT 'bons_sortie' AS table_liee, b.center_id, count(*) AS lignes_sans_seance, count(ss.seance_id) AS dont_suppression_journalisee FROM bons_sortie b LEFT JOIN seances s ON s.id = b.seance_id LEFT JOIN seance_suppression ss ON ss.seance_id = b.seance_id WHERE s.id IS NULL GROUP BY b.center_id UNION ALL SELECT 'stock_movements', m.center_id, count(*), count(ss.seance_id) FROM stock_movements m LEFT JOIN seances s ON s.id = m.seance_id LEFT JOIN seance_suppression ss ON ss.seance_id = m.seance_id WHERE m.seance_id IS NOT NULL AND s.id IS NULL GROUP BY m.center_id;
```
Recette AVANT bascule : (a) changer le jour d'une séance validée : 200 `{id, status, dateSeance}`, `facture_id`, forfait imposé, signatures et place inchangés ; (b) changer le jour d'une séance facturée : 422 `SEANCE_FACTUREE_NON_MODIFIABLE` ; (c) date `31/12/2026`, texte ou nombre `20261231`, corps vide, identifiant illisible : 400 et non 500, séance inchangée ; (d) supprimer une séance `CREE` avec un motif de 5 caractères : 204, volets, administrations et consommations effacés, une ligne dans `seance_suppression`, diffusion `SEANCE_SUPPRIMEE` ; (e) supprimer une séance validée ou signée qui PORTE une facture : 422 `SEANCE_RATTACHEE_A_UNE_FACTURE_NON_SUPPRIMABLE`, rien n'est supprimé ni journalisé ; (f) supprimer sans `centerId` : 400 « Centre requis » ; motif vide : 422 ; séance d'un autre centre : 422 `SEANCE_INTROUVABLE` ; infirmier, médecin, secrétaire : 403 ; (g) deux écritures simultanées sur la même séance : la seconde reçoit 409 `ECRITURE_CONCURRENTE` (5 s). **Conditions de mise en production (décision écrite, ADR 0093)** : factures déjà orphelines (requêtes 1, 2 et 8, traitées par la Facturation), séances rattachées non facturées et jour hors période (requêtes 3 et 4), changement de jour après validation ou signature et contournement du verrou des jours passés (KB-SEA-54 et 55, requête 5), aucun stock restitué à la suppression et lignes de stock conservées sans séance (KB-SEA-51, requêtes 7 et 9), séance rattachée non facturée dont le jour peut changer alors que sa suppression est refusée (décision D10, ADR 0093 ; requêtes 3 et 4), échec de diffusion avalé (D9, ADR 0093), clients qui n'envoyaient pas `centerId` à la suppression (désormais 400). Surveillance : requêtes 1, 3, 4 et 6 chaque semaine ; requête 1 à 0 ligne après traitement. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; les colonnes ne changent pas, aucune donnée n'est à restaurer (les suppressions déjà effectuées sont irréversibles).

## 59. Séance SE7a (journal du jour, calendrier du centre, volet médical, séances récentes) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune nouvelle route (ADR 0094 ; les routes changent de contrôleur). **Risques cliniques, de confidentialité et d'intégrité à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-59 (volet médical remplacé en entier, route ouverte sur séance signée, `ADMIN` écrit, aucun plafond de taille) et KB-SEA-60 (constantes de séances `CREE` exposées) ; la décision écrite de chacun est une condition de mise en production. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement, puis chaque semaine tant que ces risques ne sont pas tranchés. Aucune correction automatique, aucun `UPDATE`, `DELETE` ni `DROP`. Les requêtes 1 à 7 sont rejouées sur le schéma réel (PostgreSQL 16 jetable) par `backend/scripts/seance-se7a-pg16-rejeu.sh` (même numérotation, même texte).
```sql
-- 1. KB-SEA-59 (2) (CLINIQUE) : volets médicaux modifiés APRÈS la signature du médecin (la signature ne verrouille pas ses observations).
SELECT v.center_id, count(*) AS volets_sur_seance_signee FROM volet_medical v INNER JOIN seances s ON s.id = v.seance_id AND s.center_id = v.center_id WHERE s.statut = 'SIGNEE' AND v.updated_at > s.signed_medecin_at GROUP BY v.center_id;
-- 2. KB-SEA-59 (3) (CLINIQUE, CORRIGÉE) : écritures du volet médical réussies par centre, profil et compte, avec le nombre d'écritures faites par un compte SANS le profil MEDECIN (ADMIN seul). La version précédente ne regroupait que par compte et ne permettait pas de distinguer un administrateur d'un médecin ; la colonne `audit_log.roles` porte les profils de la session.
SELECT a.center_id, a.roles, a.username, count(*) AS ecritures, count(*) FILTER (WHERE a.roles NOT LIKE '%MEDECIN%') AS ecritures_sans_role_medecin FROM audit_log a WHERE a.action_code = 'SEANCES_MODIFICATION' AND a.http_method = 'PUT' AND a.route_template = '/api/v1/seances/{seanceId}/volet-medical' AND a.status_code BETWEEN 200 AND 299 GROUP BY a.center_id, a.roles, a.username ORDER BY 4 DESC LIMIT 50;
-- 3. KB-SEA-59 (1) et (5) (CLINIQUE) : volets par centre, volets entièrement vides (indice d'un corps sans champ qui a tout effacé) et longueur maximale d'une observation (aucun plafond applicatif : colonnes text non bornées).
SELECT v.center_id, count(*) AS volets, count(*) FILTER (WHERE v.prescription IS NULL AND v.tolerance_seance IS NULL AND v.examen_clinique IS NULL AND v.resultats_biologiques IS NULL AND v.ajustements_therapeutiques IS NULL AND v.conclusion_medicale IS NULL) AS volets_vides, max(GREATEST(COALESCE(length(v.prescription),0), COALESCE(length(v.tolerance_seance),0), COALESCE(length(v.examen_clinique),0), COALESCE(length(v.resultats_biologiques),0), COALESCE(length(v.ajustements_therapeutiques),0), COALESCE(length(v.conclusion_medicale),0))) AS longueur_maximale_d_une_observation FROM volet_medical v GROUP BY v.center_id;
-- 4. KB-SEA-60 (CONFIDENTIALITÉ) : séances CREE (non validées) d'un jour passé, dont les constantes sont servies par les séances récentes.
SELECT s.center_id, s.statut, count(*) AS seances_exposees_aux_recentes FROM seances s WHERE s.statut = 'CREE' AND s.date_seance < CURRENT_DATE GROUP BY s.center_id, s.statut;
-- 5. KB-SEA-58 (suite) : séances posées un jour férié (le calendrier n'est pas contrôlé à l'ajout d'un jour).
SELECT h.center_id, h.day_date, count(*) AS seances_un_jour_ferie FROM center_holiday h INNER JOIN seances s ON s.center_id = h.center_id AND s.date_seance = h.day_date GROUP BY h.center_id, h.day_date ORDER BY 3 DESC LIMIT 100;
-- 6. KB-SEA-58 (suite) : séances posées un jour de fermeture exceptionnelle.
SELECT c.center_id, c.day_date, count(*) AS seances_un_jour_de_fermeture FROM center_closure_day c INNER JOIN seances s ON s.center_id = c.center_id AND s.date_seance = c.day_date GROUP BY c.center_id, c.day_date ORDER BY 3 DESC LIMIT 100;
-- 7. KB-SEA-58 : libellés de plus de 200 caractères (la borne passe à 255 avec un 400 au lieu du 500 ; contrôle de proximité de la borne).
SELECT 'center_holiday' AS table_liee, count(*) AS libelles_de_plus_de_200_caracteres FROM center_holiday WHERE length(label) > 200 UNION ALL SELECT 'center_closure_day', count(*) FROM center_closure_day WHERE length(reason) > 200;
```
Recette AVANT bascule : (a) `GET /journal?centerId&dateSeance` : 200 `{dateSeance, patients, sortiesArticles}`, patients triés par nom puis prénom, tous statuts ; date absente ou illisible : 400 et non 500 ; (b) `GET /calendar?centerId&year&month` : 200 `{year, month, holidays, closures}` ; mois 13 ou année absente : 400 ; (c) `POST /calendar/holiday` puis le même jour : 200 puis 409 `JOUR_DEJA_AU_CALENDRIER` ; sans date ou libellé de plus de 255 caractères : 400 ; `DELETE` d'un identifiant inconnu : 200 `{deleted:true}` (reproduit, signalé) ; centre étranger : 403 avant tout 400 ; (d) `PUT /{id}/volet-medical` par un médecin : 200 `{id, seanceId, updatedAt}`, un corps ne portant que `centerId` EFFACE les six observations (reproduit, alerte), séance `CREE`, `ABSENT` ou `FACTUREE` : 422 ; secrétaire ou infirmier : 403 avant toute erreur de corps ; deux saisies simultanées : la seconde reçoit 409 `ECRITURE_CONCURRENTE` (5 s) ; (e) `GET /patient/{id}/recentes` : au plus 10 séances strictement antérieures au jour, `limit` 0 ramené à 1 ; patient d'un autre centre : `200 []` ; secrétaire : 403 ; (f) les écritures (ajout, doublon 409, retrait, volet 200 et 403) figurent dans le journal d'audit avec route et statut ; les lectures n'y figurent pas. **Conditions de mise en production (décision écrite, ADR 0094)** : KB-SEA-59 (1) à (5) (requêtes 1, 2 et 3) et KB-SEA-60 (requête 4) arbitrés par les médecins et le DPO ; clients qui comptaient sur un 500 ou sur un doublon accepté par la base ; cohérence calendrier et séances (requêtes 5 et 6). Surveillance : requêtes 1, 2 et 4 chaque semaine. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; les colonnes ne changent pas, aucune donnée n'est à restaurer.

## 60. Séance SE7b (tableau de bord mensuel, détail nominatif, exports) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune nouvelle route (ADR 0095 ; le contrôleur change). **Risques à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-66 (détail nominatif servi à tous les profils autorisés, lectures non auditées), KB-SEA-64 (compteurs différents du détail, effectif contaminé entre centres), KB-SEA-65 (présent et absent le même jour), KB-SEA-62 (clés françaises à 0) ; corrections KB-SEA-63 (500 devient 400) à confirmer. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`. Les requêtes 1 à 6 et les 7 requêtes des adapters sont rejouées sur PostgreSQL 16 jetable par `backend/scripts/seance-se7b-pg16-rejeu.sh`.
```sql
-- 1. KB-SEA-64 : séances rattachées à un patient d'un AUTRE centre (source de la contamination de l'effectif du mois).
SELECT s.center_id, count(*) AS seances_de_patients_d_un_autre_centre FROM seances s INNER JOIN patients p ON p.id = s.patient_id WHERE p.center_id <> s.center_id GROUP BY s.center_id;
-- 2. KB-SEA-66 (CONFIDENTIALITÉ) : volume exposé par le détail nominatif (patients du centre lisibles d'un coup par tout profil autorisé). ATTENTION : les LECTURES du détail ne sont PAS auditées (le résolveur d'audit ne journalise que les écritures) ; il n'existe donc aucune requête qui dise QUI a lu le détail, et une requête sur `audit_log` renverrait toujours 0 (fausse assurance, retirée).
SELECT p.center_id, count(*) AS patients_lisibles_dans_le_detail FROM patients p GROUP BY p.center_id;
-- 3. KB-SEA-64 (présences gonflées) : plusieurs séances réalisées le même jour pour un même patient.
SELECT s.center_id, s.date_seance, s.patient_id, count(*) AS seances_le_meme_jour FROM seances s WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY s.center_id, s.date_seance, s.patient_id HAVING count(*) > 1 ORDER BY 4 DESC LIMIT 100;
-- 4. Démographie : patients dont le sexe est absent (NULL) ou non reconnu, comptés comme AUTRE par le code.
SELECT p.center_id, count(*) AS patients_comptes_comme_autre FROM patients p WHERE p.sexe IS NULL OR upper(trim(p.sexe)) NOT IN ('M', 'H', 'F') GROUP BY p.center_id;
-- 5. Fin de prise en charge : patients sortis sans date d'événement (effectif du mois).
SELECT p.center_id, p.etat_patient, count(*) AS sortis_sans_date FROM patients p WHERE p.etat_patient IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI') AND p.date_evenement_etat IS NULL GROUP BY p.center_id, p.etat_patient;
-- 6. KB-SEA-65 (détection des chiffres) : patients DISTINCTS à la fois présents (séance réalisée) et absents (absence non annulée) un même jour.
SELECT s.center_id, count(DISTINCT s.patient_id) AS patients_distincts_presents_et_absents_le_meme_jour FROM seances s INNER JOIN absence_patient a ON a.center_id = s.center_id AND a.patient_id = s.patient_id AND a.date_seance = s.date_seance AND a.statut <> 'ANNULEE' WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY s.center_id;
```
Recette AVANT bascule : (a) `GET /dashboard?centerId&year&month` : 200, mêmes champs que le legacy ; année ou mois absent ou texte, centre illisible : 400 et non 500, centre étranger : 403 avant tout 400 ; (b) `GET /dashboard/details?kind=presence|absence` : 200 ; `kind` absent ou inconnu : 400 ; (c) `GET /dashboard/export?format=csv|pdf|xlsx` : mêmes octets qu'avant (CSV `text/plain` sans charset), format inconnu : 400 ; (d) les lectures du détail nominatif ne sont PAS auditées (aucune ligne d'audit, ni avant ni après : à ne pas chercher dans `audit_log`) ; seules les écritures le sont. **Conditions de mise en production (décision écrite, ADR 0095)** : KB-SEA-66 (requête 2 pour le volume exposé, ET décision DPO sur la traçabilité des lectures nominatives, option : audit de lecture dans une slice dédiée), KB-SEA-64 (requêtes 1 et 3), KB-SEA-65 (requête 6), KB-SEA-62, confirmation de KB-SEA-63 et de l'écart de fuseau (D-FUSEAU) ; clients qui comptaient sur un 500. Surveillance : requêtes 1 et 6 chaque semaine. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; les colonnes ne changent pas, aucune donnée n'est à restaurer.

## 61. Séance SE7c1 (lectures publiées de la facturation et de la place d'une séance, pour le Stock) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune route (ADR 0096) ; le Stock lit désormais la Séance par son langage publié (mêmes requêtes qu'avant, déplacées). **Risques à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-68 (demi-journée devinée par un mot du libellé du créneau), KB-SEA-69 (salle ou créneau absent ou d'un autre centre : périmètre perdu en silence), KB-SEA-70 (statut `FACTUREE` reconnu sans tenir compte de la casse par le Stock seulement). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`. Les 7 requêtes de contrôle et les 4 requêtes de l'adapter sont rejouées sur PostgreSQL 16 jetable par `backend/scripts/seance-se7c1-pg16-rejeu.sh`.
```sql
-- 1. KB-SEA-69 : séances dont la salle appartient à un AUTRE centre (le Stock l'ignore et retombe sur la sortie « journée »).
SELECT s.center_id, count(*) AS seances_avec_une_salle_d_un_autre_centre FROM seances s INNER JOIN salle sa ON sa.id = s.salle_id WHERE sa.center_id <> s.center_id GROUP BY s.center_id;
-- 2. KB-SEA-69 : séances dont le créneau appartient à un AUTRE centre.
SELECT s.center_id, count(*) AS seances_avec_un_creneau_d_un_autre_centre FROM seances s INNER JOIN position_creneau p ON p.id = s.creneau_id WHERE p.center_id <> s.center_id GROUP BY s.center_id;
-- 3. KB-SEA-69 : séances dont la salle a disparu (aucune clé étrangère).
SELECT s.center_id, count(*) AS seances_avec_une_salle_disparue FROM seances s WHERE s.salle_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM salle sa WHERE sa.id = s.salle_id) GROUP BY s.center_id;
-- 4. KB-SEA-69 : séances dont le créneau a disparu.
SELECT s.center_id, count(*) AS seances_avec_un_creneau_disparu FROM seances s WHERE s.creneau_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM position_creneau p WHERE p.id = s.creneau_id) GROUP BY s.center_id;
-- 5. KB-SEA-70 : statuts `FACTUREE` écrits autrement qu'en majuscules (reconnus « facturés » par le Stock seulement).
SELECT s.center_id, s.statut, count(*) AS seances_au_statut_facturee_hors_casse_canonique FROM seances s WHERE upper(s.statut) = 'FACTUREE' AND s.statut <> 'FACTUREE' GROUP BY s.center_id, s.statut;
-- 6. Cohérence : séances `FACTUREE` sans facture (le Stock les refuse, la Facturation ne les voit pas).
SELECT s.center_id, count(*) AS seances_facturees_sans_facture FROM seances s WHERE s.statut = 'FACTUREE' AND s.facture_id IS NULL GROUP BY s.center_id;
-- 7. Cohérence : séances rattachées à une facture mais pas au statut `FACTUREE` (le Stock les croit modifiables).
SELECT s.center_id, count(*) AS seances_avec_facture_sans_statut_facturee FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id;
```
Recette AVANT bascule : (a) valider une séance avec consommables puis lire ses favoris : la pièce retenue est celle de la salle et de la demi-journée de la séance (matin, après-midi), sinon celle de la salle, sinon de la demi-journée, sinon « journée » ; (b) valider une séance dont la salle est vide : sortie « journée » ; (c) tenter de modifier une séance `FACTUREE` (date, forfait, consommable) : refus, et le stock ne bouge pas ; (d) les sorties pour séances du jour sont les mêmes qu'avant le déploiement. **Conditions de mise en production** : requêtes 1 à 4 et 7 à zéro ou décision métier écrite (KB-SEA-69), requête 5 (KB-SEA-70), décision métier sur la demi-journée (KB-SEA-68). Surveillance : requêtes 1 à 4 chaque semaine. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; aucune donnée n'est à restaurer.

## 62. Séance SE7c2 (écritures et lectures publiées de la Facturation sur la table des séances, ARGENT) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne ajoutée, aucune clé étrangère, aucune route (ADR 0097) ; la Facturation lit et écrit les séances par le langage publié de la Séance (mêmes requêtes qu'avant, déplacées ; aucun montant recalculé). Une seule transaction d'émission, non scindée. **Risques à arbitrer AVANT la mise en production** (revue au plus tard le 2026-12-15) : KB-SEA-47 (code du forfait imposé non écrit), KB-SEA-71 (SEUL écart décidé : le port refuse désormais de lier une absence à une séance facturée ; défense en profondeur sans effet aujourd'hui sur le trafic HTTP, l'exclusion refusant déjà une séance facturée ; les cas déjà présents sont listés par la requête 3), KB-SEA-72 (mémoire d'exclusion blanche), KB-SEA-73 (statut en casse exacte), KB-SEA-74 (unicité « une séance, une facture » sans clé étrangère ni contrainte), KB-SEA-75 / KB-FAC-54 (deux définitions de « séance facturée »). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`. Rejeu sur PostgreSQL 16 jetable par `backend/scripts/seance-se7c2-pg16-rejeu.sh` (schéma Flyway réel, base neuve) : les requêtes 1, 3 et 4 y sont exécutées **à l'identique** et vérifiées à 0 ligne ; les requêtes 2, 5, 6 et 7 y sont exécutées sous une forme équivalente découpée (même prédicat, comptage par centre). Le rejeu prouve que les requêtes s'exécutent sur le schéma réel, pas l'état des données de production.
```sql
-- 1. KB-SEA-47 : surcharges dont le code ne correspond plus au forfait imposé (code périmé ou absent).
SELECT s.center_id, s.id, s.forfait_override_id, s.forfait_override_code FROM seances s LEFT JOIN forfait f ON f.id = s.forfait_override_id WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS DISTINCT FROM f.code LIMIT 100;
-- 2. KB-SEA-74 : séances dont facture_id désigne une facture inexistante ou d'un AUTRE centre (aucune clé étrangère).
SELECT s.center_id, s.id, s.statut, s.facture_id FROM seances s LEFT JOIN factures f ON f.id = s.facture_id WHERE s.facture_id IS NOT NULL AND (f.id IS NULL OR f.center_id <> s.center_id) LIMIT 100;
-- 3. KB-SEA-71 : séances ABSENT qui portent une facture (absence liée à une séance facturée avant la garde).
SELECT s.center_id, s.id, s.facture_id, s.exclusion_absence_id FROM seances s WHERE s.statut = 'ABSENT' AND s.facture_id IS NOT NULL LIMIT 100;
-- 4. KB-SEA-74 : factures du même patient dont les périodes se chevauchent (risque de double facturation d'une même séance).
SELECT a.center_id, a.patient_id, a.id AS facture_a, b.id AS facture_b FROM factures a INNER JOIN factures b ON b.center_id = a.center_id AND b.patient_id = a.patient_id AND b.id > a.id AND b.period_start <= a.period_end AND a.period_start <= b.period_end LIMIT 100;
-- 5. KB-SEA-74 / 75 : séances au statut FACTUREE sans facture, et séances rattachées à une facture sans le statut FACTUREE.
SELECT s.center_id, count(*) FILTER (WHERE s.statut = 'FACTUREE' AND s.facture_id IS NULL) AS facturees_sans_facture, count(*) FILTER (WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE') AS avec_facture_sans_statut FROM seances s GROUP BY s.center_id;
-- 6. KB-SEA-72 : mémoires d'exclusion blanches (lues comme absentes par la lecture, tenues pour présentes par les écritures).
SELECT s.center_id, count(*) AS memoires_blanches FROM seances s WHERE s.statut_avant_exclusion IS NOT NULL AND trim(s.statut_avant_exclusion) = '' GROUP BY s.center_id;
-- 7. KB-SEA-73 : statuts hors casse canonique (ni facturables, ni liables, ni forfaitables, ni réinclusibles).
SELECT s.center_id, s.statut, count(*) AS seances FROM seances s WHERE s.statut <> upper(s.statut) GROUP BY s.center_id, s.statut;
```
Recette AVANT bascule : (a) simuler la facturation d'un mois : mêmes séances, mêmes forfaits, mêmes totaux qu'avant le déploiement ; (b) exclure puis réinclure une séance validée : statut et mémoire rétablis ; (c) imposer un forfait : id, nom et prix changent, le code ne change pas ; (d) valider la facturation : les séances passent à `FACTUREE` avec la facture, et un échec sur une séance annule TOUTE l'émission (factures, séquence, séances) ; (e) exclure une séance déjà facturée (`FACTUREE` avec facture, ou `VALIDEE` portant une facture si la requête 5 en trouve sur la copie) , sur un mois NON clos : refusé par l'**exclusion** (« Seance introuvable ou non modifiable pour exclusion », 422), aucune absence déclarée, statut, facture et mémoire d'exclusion inchangés. La garde KB-SEA-71 de `lierALAbsence` n'est PAS observable en HTTP (l'exclusion, seul appelant, refuse avant elle) : elle est prouvée au niveau du port par le golden `lier_a_l_absence_une_seance_absente_deja_facturee_nouveau_code` et le contrat `should_refuse_to_link_an_absence_to_an_absent_seance_that_already_carries_an_invoice`, et ne se recette pas ici (revue SE7c2, constat m2) ; (f) tableau de bord et synthèse d'un mois : mêmes chiffres et même ordre des répartitions qu'avant (la définition de « facturée » diffère entre eux, KB-SEA-75). **Conditions de mise en production (décision écrite, ADR 0097)** : requêtes 2, 3 et 4 à zéro ligne ou décision écrite, requête 1 (KB-SEA-47) à zéro ou décision métier, décisions KB-SEA-72, 73, 74 et 75 (direction, DBA). Surveillance : requêtes 2, 3 et 5 chaque semaine. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; les colonnes ne changent pas, aucune donnée n'est à restaurer.

## 63. Séance SE7d (lectures publiées de la table des séances pour les Absences de patients, la Planification et le Dossier médical) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne, aucune route, aucune écriture (ADR 0098) ; les huit lectures de `seances` des Absences (semaine, jours d'un patient, séance réalisée un jour, lien d'exclusion, patients écartés de la détection de nuit), de la Planification (séances réalisées avec leur place) et du Dossier médical (total et page de l'historique des constantes) passent par le langage publié de la Séance, **mêmes requêtes mot pour mot**, déplacées dans `contexts/seance/.../persistence/LecturesDesSeancesRealiseesJdbc` et `ConstantesDesSeancesJdbc`. **Aucun écart de comportement décidé.** Reproduits, **à confirmer par les métiers et le DPO au plus tard le 2026-12-15** : KB-SEA-77 (ordre de deux patients d'un même jour non défini), KB-SEA-78 (deux séances réalisées d'un patient le même jour : la grille garde la première lue), KB-SEA-79 (création nulle : en dernier sur H2, en PREMIER sur PostgreSQL, donc en production), KB-SEA-80 (séance réalisée invisible au planning si la fiche n'est pas dans le centre de la séance), KB-SEA-81 (lien d'exclusion sans statut), KB-SEA-82 (statut en casse exacte, sans blanc). À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`.

Rejeu effectué le 2026-11-08 par `backend/scripts/seance-se7d-pg16-rejeu.sh 55548` (PostgreSQL 16 JETABLE, schéma Flyway réel, jeu FICTIF de 32 séances ; mot de passe par `SEANCE_PG16_PASSWORD`, jamais le port 5432) : les cinq requêtes ci-dessous y sont exécutées **à l'identique** et donnent sur ce jeu fictif les valeurs attendues (1. une création nulle ; 2. un lien sur une séance `VALIDEE` ; 3. six groupes de statuts hors casse ou avec blancs ; 4. une séance réalisée dont la fiche est passée dans un autre centre ; 5. cinq groupes de plusieurs séances réalisées d'un patient le même jour), après les 14 vérifications des huit requêtes de production et avant les contrats PostgreSQL (`SeancesRealiseesEtConstantesPostgresContractIT` 15/15, `HistoriqueDesConstantesPostgresContractIT` 9/9) ; code de sortie 0. Le rejeu prouve que les requêtes s'exécutent sur le schéma réel et détectent les cas posés ; il ne dit RIEN de l'état des données de production.
```sql
-- 1. KB-SEA-79 : séances à création nulle (ordre de l'historique des constantes différent entre H2 et PostgreSQL).
SELECT center_id, count(*) FROM seances WHERE created_at IS NULL GROUP BY center_id;
-- 2. KB-SEA-81 : lien d'exclusion porté par une séance qui n'est pas ABSENT (bloque l'annulation de l'absence liée).
SELECT center_id, statut, count(*) FROM seances WHERE exclusion_absence_id IS NOT NULL AND statut <> 'ABSENT' GROUP BY center_id, statut;
-- 3. KB-SEA-82 : statuts réalisés hors casse canonique ou avec blancs (ni présence, ni rattrapage, ni séance réalisée, patient attendu).
SELECT center_id, statut, count(*) FROM seances WHERE upper(trim(statut)) IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND statut NOT IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, statut;
-- 4. KB-SEA-80 : séances réalisées dont la fiche patient n'est pas dans le centre de la séance (invisibles dans la semaine du planning).
SELECT s.center_id, count(*) FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND p.id IS NULL GROUP BY s.center_id;
-- 5. KB-SEA-78 : deux séances réalisées ou plus d'un même patient le même jour (case du planning non déterministe).
SELECT center_id, patient_id, date_seance, count(*) FROM seances WHERE statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1 LIMIT 100;
```
Recette AVANT bascule (mêmes réponses qu'avant le déploiement, sur une copie) : (a) `GET /api/v1/absences-patients/semaine` d'une semaine passée : mêmes séances réalisées par patient et par jour ; (b) `GET /api/v1/absences-patients/{id}/seances-rattrapage` : mêmes jours ; (c) déclarer une absence un jour où le patient a une séance réalisée : refus 422 `ABSENCE_SEANCE_REALISEE` inchangé ; (d) annuler l'absence d'une exclusion : refus 422 `ABSENCE_D_EXCLUSION` inchangé ; (e) `POST /api/v1/absences-patients/rattrapage-detection` (ADMIN) sur un jour passé : mêmes absences détectées ; (f) `GET /api/v1/planning/semaine` d'une semaine passée : mêmes cases réalisées ; (g) `GET /api/v1/patients/{id}/constantes` : même total, même ordre, mêmes pages (si la requête 1 trouve des lignes pour ce patient, l'ordre de ces lignes est celui de PostgreSQL, KB-SEA-79). **Conditions de mise en production** : requêtes 1 à 5 exécutées sur une copie et leurs résultats joints à la décision ; décisions métiers / DPO sur KB-SEA-77 à 82 (ADR 0098, résidus D-R1 à D-R4). Surveillance : requêtes 2 et 4 chaque mois. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; aucune donnée n'est écrite ni à restaurer.

## 64. Séance SE7e (lectures et écritures publiées de la table des séances pour le pilotage, les statistiques, le cahier de dialyse, la reprise et le référentiel) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne, aucune route (ADR 0099) ; les seize requêtes Java de la plateforme sur `seances` (pilotage de la Direction E1 à E5, statistiques du patient E6 à E9, compte du cahier E10, reprise de données E12 à E16 dont les ÉCRITURES `INSERT`, `UPDATE` inatteignable et `DELETE`, usage d'un forfait E17) passent par le langage publié de la Séance, **mêmes requêtes mot pour mot**, déplacées dans `contexts/seance/.../persistence/SeancesRealiseesDesCentresJdbc`, `StatistiquesDesSeancesJdbc`, `SeancesDuCahierEtForfaitsImposesJdbc` et `SeancesDeLaRepriseJdbc`. L'état Jasper du cahier (`reports/cahier_dialyse.jrxml`, E11) garde sa requête, inchangée (modèle personnalisable, ADR 0099 décision 3). **Aucun écart de comportement décidé.** Reproduits, **à confirmer par les métiers et le DPO au plus tard le 2026-12-15** : KB-SEA-83 à 91. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`.

Rejeu effectué le 2026-11-08 par `backend/scripts/seance-se7e-pg16-rejeu.sh 55549` (PostgreSQL 16 JETABLE, chaîne Flyway réelle de 51 fichiers, jeu FICTIF de 41 séances ; mot de passe par `SEANCE_PG16_PASSWORD`, jamais le port 5432) : les huit requêtes ci-dessous y sont exécutées **à l'identique** et donnent sur ce jeu fictif les valeurs attendues (1. les six migrations, `success = t` ; 2. une fiche du centre A rattachée à la caisse du centre B ; 3. un volet posé dans un autre centre que sa séance ; 4. trois groupes de séances d'un patient le même jour ; 5. une séance par centre avec un forfait imposé sans code ; 6. quatre patients aux séances dans plusieurs centres ; 7. un forfait imposé à une séance d'un autre centre ; 8. aucune version personnalisée active du cahier), après les 19 vérifications des requêtes de production et avant le contrat PostgreSQL `PilotageEtRapportsDesSeancesPostgresContractIT` (14/14) ; code de sortie 0. La table `flyway_schema_history` du cluster jetable est tenue par le script (DDL de Flyway, une ligne par fichier appliqué) : la requête 1 y est rejouée telle quelle, mais seule son exécution en production dit quelles migrations y sont appliquées. Le rejeu prouve que les requêtes s'exécutent sur le schéma réel et détectent les cas posés ; il ne dit RIEN de l'état des données de production.
```sql
-- 1. Migrations Flyway qui créent ou modifient la table des séances (V1 création, V21, V23, V26, V39 colonnes) et son index (V31) : appliquées et réussies.
SELECT version, success FROM flyway_schema_history WHERE version IN ('1', '21', '23', '26', '31', '39') ORDER BY installed_rank;
-- 2. KB-SEA-84 : fiches rattachées à la caisse d'un autre centre (rangées sous cette caisse dans la répartition de la Direction).
SELECT p.center_id, ca.center_id AS centre_de_la_caisse, count(*) FROM patients p JOIN centre_payeur cp ON cp.id = p.centre_payeur_id JOIN agence ag ON ag.id = cp.agence_id JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE ca.center_id <> p.center_id GROUP BY p.center_id, ca.center_id;
-- 3. KB-SEA-85 : volets paramédicaux posés dans un autre centre que leur séance (lus par les statistiques du patient).
SELECT s.center_id, vp.center_id AS centre_du_volet, count(*) FROM volet_paramedical vp JOIN seances s ON s.id = vp.seance_id WHERE vp.center_id <> s.center_id GROUP BY s.center_id, vp.center_id;
-- 4. KB-SEA-86 : plusieurs séances d'un patient le même jour, tous statuts (ordre non défini dans les statistiques).
SELECT center_id, patient_id, date_seance, count(*) FROM seances GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1 LIMIT 100;
-- 5. KB-SEA-88 : forfait imposé sans code recopié (imprimé vide dans le cahier de dialyse).
SELECT center_id, count(*) FROM seances WHERE forfait_override_id IS NOT NULL AND (forfait_override_code IS NULL OR trim(forfait_override_code) = '') GROUP BY center_id;
-- 6. KB-SEA-90 : patients dont les séances sont dans plusieurs centres (une séance d'un autre centre bloque l'annulation d'un lot de reprise).
SELECT patient_id, count(DISTINCT center_id) FROM seances GROUP BY patient_id HAVING count(DISTINCT center_id) > 1 LIMIT 100;
-- 7. KB-SEA-91 : forfait imposé à une séance d'un autre centre que le forfait (suppression du forfait refusée).
SELECT s.center_id, f.center_id AS centre_du_forfait, count(*) FROM seances s JOIN forfait f ON f.id = s.forfait_override_id WHERE f.center_id <> s.center_id GROUP BY s.center_id, f.center_id;
-- 8. Résidu E-R3 : versions personnalisées actives du cahier de dialyse (leur requête, hors du dépôt, lit-elle seances ?).
SELECT md.center_id, mdv.version, position('seances' in lower(mdv.contenu)) > 0 AS lit_seances FROM modele_document_version mdv JOIN modele_document md ON md.id = mdv.modele_id WHERE md.code = 'CAHIER_DIALYSE' AND mdv.actif = TRUE ORDER BY md.center_id;
```
KB-SEA-83 (séance sur une fiche d'un autre centre : comptée dans la vue d'ensemble, absente des caisses) se détecte par la requête 4 de la section 63 (même jointure). Recette AVANT bascule (mêmes réponses qu'avant le déploiement, sur une copie) : (a) `GET /api/v1/direction/overview`, `/breakdown`, `/absences` d'une période passée (compte DIRECTION) : mêmes séances par centre, par mois, par caisse, même taux ; (b) `GET /api/v1/direction/report` : même PDF hors horodatage ; (c) `GET /api/v1/patients/{id}/stats/paramedical` et `/stats/export?format=pdf` : mêmes compte, moyennes, évolutions ; (d) `GET /api/v1/cahier-dialyse/{id}/imprimer` : même PDF, même refus `CAHIER_VIDE` ; (e) `DELETE /api/v1/admin/referentials/forfaits/{id}` d'un forfait imposé : même refus `REFERENTIAL_IN_USE` et même nombre ; (f) reprise : import de séances puis annulation d'un lot SUR UNE COPIE UNIQUEMENT (écritures) : mêmes avertissements `ALREADY_PRESENT_KEPT`, mêmes bloquants « patient « … » (seances) ». **Conditions de mise en production** : requêtes 1 à 8 exécutées sur une copie et leurs résultats joints à la décision ; si la requête 8 rend des lignes, les versions personnalisées sont relues (elles gardent leur propre requête) ; décisions métiers / DPO sur KB-SEA-83 à 91 (ADR 0099, résidus E-R1, E-R5). Surveillance : requêtes 3 et 6 chaque mois. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; aucune donnée n'est écrite par la bascule elle-même.

## 65. Séance SE7f (retrait de `bc-seance` : volet paramédical dans la Séance, dernière créatinine au Dossier médical, capacité théorique à la Planification) : parité et gardes (2026-11-08) — SELECT seulement

Aucune migration, aucune table ni colonne, aucune route nouvelle (ADR 0100) ; le module Maven legacy `bc-seance` et ses dix-sept utilisateurs de la plateforme sont retirés. La route `PUT /api/v1/seances/{id}/volet-paramedical` est servie par `contexts/seance` (mêmes URL, rôles, champs et codes), les lectures du planning, de la place, des forfaits, de l'historique, des générateurs, du référentiel et du jour du centre sont reprises **mot pour mot** dans `contexts/seance`, la dernière créatinine de KDIGO par `contexts/dossier-medical` et la capacité théorique par `contexts/planification`. **Aucun écart de comportement décidé.** Reproduits, **à confirmer par les métiers, les médecins et le DPO au plus tard le 2026-12-15** : KB-SEA-93 à 96. À exécuter sur une COPIE ou en lecture seule, avant et après le déploiement. Aucun `UPDATE`, `DELETE` ni `DROP`.

Rejeu effectué le 2026-11-08 par `backend/scripts/seance-se7f-pg16-rejeu.sh 55550` (PostgreSQL 16 JETABLE, chaîne Flyway réelle, jeu FICTIF de l'audit ; mot de passe par `SEANCE_PG16_PASSWORD`, jamais le port 5432) : les cinq requêtes ci-dessous y sont exécutées **à l'identique** après les vérifications F1 à F6 des requêtes de production et avant les contrats PostgreSQL de la Séance (`VoletsParamedicauxPostgresContractIT`, `LecturesReprisesDeLaPlateformePostgresContractIT`) et du Dossier médical (`DerniereCreatininePostgresContractIT`) ; sur ce jeu fictif elles donnent : 1. un volet paramédical et un volet médical du centre B posés sur une séance du centre A ; 2. aucune séance hors planning un jour férié (le scan administrateur n'est pas rejoué en SQL) ; 3. aucun patient ; 4. un patient avec deux créatinines le même jour ; 5. aucun volet orphelin. Le rejeu prouve que les requêtes s'exécutent sur le schéma réel et détectent les cas posés ; il ne dit RIEN de l'état des données de production.
```sql
-- 1. KB-SEA-94 : volets (paramédical, médical) posés dans un autre centre que leur séance (invisibles au détail, bloquent la saisie).
SELECT 'volet_paramedical' AS volet, s.center_id, v.center_id AS centre_du_volet, count(*) FROM volet_paramedical v JOIN seances s ON s.id = v.seance_id WHERE v.center_id <> s.center_id GROUP BY s.center_id, v.center_id
UNION ALL
SELECT 'volet_medical', s.center_id, v.center_id, count(*) FROM volet_medical v JOIN seances s ON s.id = v.seance_id WHERE v.center_id <> s.center_id GROUP BY s.center_id, v.center_id;
-- 2. KB-SEA-95 : séances validées hors planning un jour férié du centre (motif administrateur, aucune trace du férié).
SELECT s.center_id, s.motif_hors_planning, count(*) FROM seances s JOIN center_holiday h ON h.center_id = s.center_id AND h.day_date = s.date_seance WHERE s.hors_planning = TRUE GROUP BY s.center_id, s.motif_hors_planning;
-- 3. KB-SEA-96 : patients dont la créatinine la plus récente vaut 0 alors qu'une mesure strictement positive existe (fonction rénale NON_EVALUABLE).
SELECT r.center_id, r.patient_id, max(r.date_prelevement) AS derniere FROM resultats_analyses r WHERE r.creatinine_mg_dl IS NOT NULL GROUP BY r.center_id, r.patient_id HAVING max(CASE WHEN r.creatinine_mg_dl = 0 THEN r.date_prelevement END) = max(r.date_prelevement) AND max(CASE WHEN r.creatinine_mg_dl > 0 THEN r.date_prelevement END) IS NOT NULL;
-- 4. KB-SEA-96 : deux créatinines du même patient au centre le même jour (ordre non défini : l'une des deux est retenue).
SELECT center_id, patient_id, date_prelevement, count(*) FROM resultats_analyses WHERE creatinine_mg_dl IS NOT NULL GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1 LIMIT 100;
-- 5. Volets orphelins (séance disparue) : aucun n'est attendu, la suppression d'une séance efface ses volets (SE6).
SELECT 'volet_paramedical' AS volet, count(*) FROM volet_paramedical v WHERE NOT EXISTS (SELECT 1 FROM seances s WHERE s.id = v.seance_id)
UNION ALL
SELECT 'volet_medical', count(*) FROM volet_medical v WHERE NOT EXISTS (SELECT 1 FROM seances s WHERE s.id = v.seance_id);
```
Recette AVANT bascule (mêmes réponses qu'avant le déploiement, sur une copie) : (a) `PUT /api/v1/seances/{id}/volet-paramedical` par un infirmier : même réponse (`id`, `seanceId`, `updatedAt`), même ligne `volet_paramedical`, mêmes deux diffusions, mêmes refus (403 centre du corps, 400 séance inconnue, 422 facturée, 422 poids ou tension) ; (b) `GET /api/v1/seances/{id}` et `GET /api/v1/seances/patient/{id}/recentes` : mêmes volets ; (c) `POST /api/v1/seances/scan` un jour férié : 422 `SEANCE_CENTRE_FERME` sauf administrateur avec motif ; (d) `GET /api/v1/patients/{id}/greffe/kdigo` : même stade ; (e) `GET /api/v1/direction/capacite` : mêmes chiffres ; (f) `DELETE /api/v1/seances/{id}` : les deux volets disparaissent avec la séance. **Conditions de mise en production** : requêtes 1 à 5 exécutées sur une copie et leurs résultats joints à la décision ; si la requête 1 rend des lignes, décision KB-SEA-94 (réparer la donnée ou lire par séance) AVANT la bascule ou acceptation explicite du blocage reproduit ; décisions métiers / médecins / DPO sur KB-SEA-93 à 96 (ADR 0100, résidu F-R3). Surveillance : requêtes 1 et 5 chaque mois. Rollback : revenir au commit précédent (refactoring sur place, ADR 0024) ; aucune donnée n'est écrite par la bascule elle-même ; le schéma H2 (`db/schema.sql`) ne concerne que les tests et le poste de développement.

**Observation ajoutée le 2026-11-09 (revue SE7f m5, écart assumé sous concurrence, ADR 0100 « Parité attendue », RG-SEA-069)** : rejeu `seance-se7f-pg16-rejeu.sh 55550`, bloc **F7**, deux processus psql écrivant le volet paramédical de la MÊME séance par le chemin SQL du `PUT /api/v1/seances/{id}/volet-paramedical` du nouveau code (`SET LOCAL lock_timeout = 5000` ; `SELECT … FROM seances WHERE id = ? AND center_id = ? FOR UPDATE` ; UPDATE du volet au centre, sinon INSERT ; `COMMIT`) — ce n'est pas un appel HTTP, l'application n'est pas démarrée. Observé sur PostgreSQL 16 : **F7a** (la première écriture tient le verrou 7 s) : première écrite, seconde refusée après 5 s avec SQLSTATE 55P03 « canceling statement due to lock timeout » (= 409 `ECRITURE_CONCURRENTE` par `SeancesJdbc.refusDeVerrou` et `ErreursDuVoletParamedicalAdvice`), un seul volet, celui de la première (70.00) ; **F7b** (la première tient le verrou 1 s) : les deux écrites, séquentialisées (la seconde attend puis remplace : 73.00), un seul volet, aucun second INSERT. Le legacy (`save` JPA sans verrou ni transaction) aurait rendu deux 200 dans les deux cas, la dernière écriture écrasant l'autre. Ni la recette ni les requêtes 1 à 5 ne changent ; ajout à la recette AVANT bascule : (g) deux saisies simultanées du même volet par deux infirmiers : la seconde répond 200 (si la première a validé en moins de 5 s) ou 409 `ECRITURE_CONCURRENTE` (relire puis recommencer), jamais une écriture aveugle ; décision métiers attendue au plus tard le 2026-12-15 (conserver ce 409 ou revenir à l'écriture aveugle).

## 66. Dossier médical DM9 (filet du reliquat : lecteurs SQL de la Planification, statistiques, indicateurs Direction) : détections (2026-11-09) — SELECT seulement

Phase FILET : aucune migration, aucune table ni colonne, aucune route nouvelle, aucun code de production modifié (`.ddd/safety-net/dossier-medical-dm9.md`). Les cinq requêtes détectent en production les situations figées par les goldens DM9 et consignées en KB-MED-104 et KB-MED-106 à 109 ; elles éclairent les décisions du DPO et des médecins (section 9 de l'audit) AVANT la reprise. À exécuter sur une COPIE ou en lecture seule. Aucun `UPDATE`, `DELETE` ni `DROP`.

Rejeu effectué le 2026-11-09 par `backend/scripts/dossier-medical-dm9-pg16-rejeu.sh 55551` (PostgreSQL 16 JETABLE, chaîne Flyway réelle de 51 fichiers, jeu FICTIF de l'audit ; mot de passe par `DM9_PG16_PASSWORD`, jamais le port 5432) : les cinq requêtes ci-dessous y sont exécutées **à l'identique** après les vérifications M1 à M6 des requêtes de production ; sur ce jeu fictif elles donnent : 1. deux patients (douteux, en cours) ; 2. un patient (égalité totale) ; 3. deux sérologies positives posées au centre B pour des fiches du centre A ; 4. un patient avec deux bilans le même jour ; 5. un centre à 3 patients évalués (masqué, déductible). Le rejeu prouve que les requêtes s'exécutent sur le schéma réel et détectent les cas posés ; il ne dit RIEN de l'état des données de production.
```sql
-- 1. KB-MED-106 : patients dont le DERNIER résultat d'un marqueur à risque est DOUTEUX ou EN_COURS alors qu'un POSITIF antérieur existe (sortis de l'isolement sans résultat négatif).
SELECT d.center_id, d.patient_id, d.marqueur, d.resultat FROM serologies_patient d WHERE d.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND d.resultat IN ('DOUTEUX', 'EN_COURS') AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = d.center_id AND n.patient_id = d.patient_id AND n.marqueur = d.marqueur AND (n.date_prelevement > d.date_prelevement OR (n.date_prelevement = d.date_prelevement AND n.created_at > d.created_at))) AND EXISTS (SELECT 1 FROM serologies_patient p WHERE p.center_id = d.center_id AND p.patient_id = d.patient_id AND p.marqueur = d.marqueur AND p.resultat = 'POSITIF' AND p.date_prelevement < d.date_prelevement) ORDER BY 1, 2;
-- 2. KB-MED-107 : deux sérologies du même marqueur, même patient, même centre, même date de prélèvement et même horodatage de saisie, aux résultats différents (indépartageables : le positif l'emporte).
SELECT a.center_id, a.patient_id, a.marqueur, a.date_prelevement, count(*) FROM serologies_patient a JOIN serologies_patient b ON b.center_id = a.center_id AND b.patient_id = a.patient_id AND b.marqueur = a.marqueur AND b.date_prelevement = a.date_prelevement AND b.created_at = a.created_at AND b.id <> a.id AND b.resultat <> a.resultat GROUP BY a.center_id, a.patient_id, a.marqueur, a.date_prelevement;
-- 3. KB-MED-108 : sérologies à risque positives posées dans un autre centre que la fiche du patient (invisibles au centre de la fiche).
SELECT s.center_id AS centre_de_la_serologie, p.center_id AS centre_de_la_fiche, count(*) FROM serologies_patient s JOIN patients p ON p.id = s.patient_id WHERE s.center_id <> p.center_id AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND s.resultat = 'POSITIF' GROUP BY s.center_id, p.center_id;
-- 4. KB-MED-109 : plusieurs bilans du même patient au centre le même jour (ordre non défini dans les tendances, le CSV et le PDF ; double comptage dans les taux de la Direction).
SELECT center_id, patient_id, date_prelevement, count(*) FROM resultats_analyses GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1 LIMIT 100;
-- 5. KB-MED-104 : centres dont l'effectif évalué en hémoglobine sur la période (ici l'année en cours : adapter les bornes) est masqué (1 à 4 patients) et donc déductible des totaux de la société.
SELECT r.center_id, count(DISTINCT r.patient_id) AS evalues FROM resultats_analyses r WHERE r.hb_g_dl IS NOT NULL AND r.date_prelevement BETWEEN date_trunc('year', current_date)::date AND current_date GROUP BY r.center_id HAVING count(DISTINCT r.patient_id) BETWEEN 1 AND 4;
```
Aucune recette de bascule à ce stade (filet seul). **Conditions pour la phase `/ddd-slice`** : requêtes 1 à 5 exécutées sur une copie et résultats joints aux décisions de la section 9 de l'audit (DPO : KB-MED-104, KB-MED-100 ; médecins : KB-MED-106, 107, 109 ; métiers : KB-MED-108 et propriété de `volet_medical`).

## 67. Dossier médical DM9 (reprise du calcul médical dans le contexte, retrait de `bc-medical`, ADR 0101) : parité et gardes (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune table ni colonne, aucune route nouvelle ; les requêtes de production sont reprises MOT POUR MOT dans `contexts/dossier-medical` (`PatientsARisqueInfectieuxJdbc`, `PorteursDUnCatheterJdbc`, `StatistiquesMedicalesJdbc`, `IndicateursCliniquesJdbc`, `TraitementDeLAnemieJdbc`). Rien à exécuter en production au titre de DM9 au-delà des détections 66.1 à 66.5, qui restent valables telles quelles (mêmes tables, mêmes règles reproduites). Décisions attendues avant la mise en production (ADR 0101, en tête) : KB-MED-106 (médecins, priorité), KB-MED-104 (DPO), KB-MED-100, 107, 108, 109, valeurs KDIGO.

Rejeu PostgreSQL 16 : `backend/scripts/dossier-medical-dm9-pg16-rejeu.sh 55551` étendu ; après M1 à M6 et 66.1 à 66.5, il joue les contrats PostgreSQL des adaptateurs repris (`RisquesInfectieuxPostgresContractIT`, `LecturesReprisesDeLaPlateformePostgresContractIT`, `DerniereCreatininePostgresContractIT`, `AdministrationsPostgresContractIT`) contre la base jetable (schéma Flyway réel). Résultat consigné dans la fiche de slice.

**Recette (lecture seule, sur une copie)** : (a) `GET /api/v1/patients/{id}/greffe/kdigo` et `greffe/export-pdf` d'un patient connu rendent la même réponse qu'avant (goldens DM7) ; (b) pour un patient dont une étape de bilan porte une catégorie illisible en base (requête 51 de la section 51), le PDF est désormais rendu SANS cette étape (avertissement serveur) au lieu d'un 400, et un bilan au statut illisible répond 422 : écart non approuvé, à trancher ; (c) `GET /api/v1/direction/indicators`, `stats/medical`, `stats/export` : réponses identiques (goldens DM8, DM9).

## 68. Pilotage direction PD1 à PD4 (filet du legacy `platform/application/direction`) : détections (2026-11-09) — SELECT seulement

Phase FILET : aucune migration, aucune table ni colonne, aucune route nouvelle, aucun code de production modifié (`.ddd/safety-net/pilotage-direction.md`). Les cinq requêtes détectent en production les situations figées par les goldens `pilotagedirection/pd1..pd4` et consignées en KB-DIR-01, 04, 05 et 06 ; elles éclairent les décisions du DPO et de la direction (section 9 de l'audit) AVANT la reprise. À exécuter sur une COPIE ou en lecture seule. Aucun `UPDATE`, `DELETE` ni `DROP`.

Rejeu effectué le 2026-11-09 par `backend/scripts/pilotage-direction-pg16-rejeu.sh 55552` (PostgreSQL 16 JETABLE, chaîne Flyway réelle, jeu FICTIF de l'audit ; mot de passe par `PD_PG16_PASSWORD`, jamais le port 5432) : les cinq requêtes ci-dessous y sont exécutées **à l'identique** après les vérifications D1 à D8 des requêtes de production ; sur ce jeu fictif elles donnent : 1. un centre à 3 patients présents (masqué, déductible) ; 2. zéro doublon d'alerte ouverte ; 3. un instantané sans répartition ; 4. une séance réalisée dont la fiche est dans un autre centre ; 5. deux sociétés actives sans instantané du mois écoulé. Le rejeu prouve que les requêtes s'exécutent sur le schéma réel et détectent les cas posés ; il ne dit RIEN de l'état des données de production. La requête 1 reproduit le prédicat `EffectifSql.presentSur` tel qu'il est écrit le 2026-11-09 (états de sortie et de séjour de `FinOccupation`) pour la période « année en cours jusqu'à aujourd'hui » : adapter les bornes.
```sql
-- 1. KB-DIR-01 : centres dont l'effectif présent sur la période (ici : 1er janvier .. aujourd'hui) est masqué (1 à 4 patients) et donc déductible des totaux de la vue d'ensemble, de la répartition et du rapport PDF.
SELECT p.center_id, COUNT(*) AS effectif FROM patients p WHERE (p.date_admission IS NULL OR p.date_admission <= current_date) AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ('DECEDE','GREFFE','GUERRI','TRANSFERE','OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') OR (p.etat_patient IN ('OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') AND (p.date_evenement_etat IS NULL OR p.date_evenement_etat >= date_trunc('year', current_date)::date)) OR (p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= date_trunc('year', current_date)::date) OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat > date_trunc('year', current_date)::date)) GROUP BY p.center_id HAVING COUNT(*) BETWEEN 1 AND 4 ORDER BY 1;
-- 2. KB-DIR-04 : alertes ouvertes en double pour un même (société, centre, code) : l'identité de record() suppose qu'il n'y en a jamais ; toute ligne ici signale une course entre le recalcul sur signal et le balayage.
SELECT societe_id, center_id, code, COUNT(*) FROM direction_alert_history WHERE resolved_at IS NULL GROUP BY societe_id, center_id, code HAVING COUNT(*) > 1;
-- 3. KB-DIR-06 : instantanés sans répartition ('breakdown' absent du payload : rapport sans les sections de répartition) ou au payload qui n'est pas un objet JSON (500 à la lecture).
SELECT societe_id, mois, CASE WHEN payload NOT LIKE '{%' THEN 'ILLISIBLE' ELSE 'SANS_BREAKDOWN' END AS etat FROM direction_snapshot WHERE payload NOT LIKE '{%' OR POSITION('"breakdown"' IN payload) = 0 ORDER BY 1, 2;
-- 4. KB-DIR-05 : séances réalisées dont la fiche du patient est dans un autre centre : comptées dans la vue d'ensemble du centre de la séance, exclues de la répartition par caisse (KB-SEA-83).
SELECT s.center_id AS centre_de_la_seance, p.center_id AS centre_de_la_fiche, COUNT(*) FROM seances s JOIN patients p ON p.id = s.patient_id WHERE p.center_id <> s.center_id AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, p.center_id;
-- 5. KB-DIR-06 : sociétés actives sans instantané du mois écoulé (tâche du 1er du mois à 02:30 non exécutée ou en échec, journal serveur à consulter) ; la première demande d'une direction le créera rétroactivement avec les données d'AUJOURD'HUI.
SELECT s.id, s.raison_sociale FROM societes s WHERE s.actif = TRUE AND NOT EXISTS (SELECT 1 FROM direction_snapshot d WHERE d.societe_id = s.id AND d.mois = to_char(date_trunc('month', current_date) - interval '1 month', 'YYYY-MM'));
```
Aucune recette de bascule à ce stade (filet seul). **Conditions pour la phase `/ddd-slice`** : requêtes 1 à 5 exécutées sur une copie et résultats joints aux décisions de la section 9 de l'audit (DPO : KB-DIR-01 avec KB-MED-104 ; direction : règle R2, fuseau, date de référence du stock).

## 69. Pilotage direction, lot A = PD1 (tableau de bord) + PD4 (rapport de synthèse) (reprise dans le contexte, ADR 0102) : parité et condition de mise en production (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune table ni colonne, aucune route nouvelle ; les requêtes du tableau de bord sont reprises MOT POUR MOT dans `contexts/pilotage-direction` (`SocietesEtCentresJdbc`, `PatientsDesCentresJdbc`, `PredicatDEffectif`) et les lectures des séances, de la facturation et de l'anémie passent par les langages publiés de la Séance, de la Facturation et du Dossier médical (déjà rejouées aux sections 62 à 67). Les détections 68.1 à 68.5 restent valables telles quelles.

Rejeu PostgreSQL 16 le 2026-11-09 : `backend/scripts/pilotage-direction-pg16-rejeu.sh 55552` (cluster jetable, chaîne Flyway réelle, `PD_PG16_PASSWORD`, jamais le port 5432), étendu : D1 à D8 et 68.1 à 68.5 inchangés (D1 et D2 désignent maintenant les adaptateurs du module), plus 69.1 et 69.2 ci-dessous, plus le contrat PostgreSQL du module `LecturesDuPilotagePostgresContractIT` (7 tests : centres de la société triés par nom, société inconnue, effectif aux bornes, décès / greffe à la veille, sortie sans date, séjour limité, profil et caisse, prescriptions d'EPO). Code de sortie 0, toutes les vérifications `OK`. Rejeu de suite de revue (2026-11-09, même commande, port 55552) : 69.1 rendue discriminante, cas positif (instantané synthétique à un centre masqué et total 12 > 9 publié : détecté) et témoins (sans centre masqué, total nul : non détectés) puis nettoyage (2 instantanés restants) ; contrat PostgreSQL 7 tests verts ; code de sortie 0.

```sql
-- 69.1 KB-DIR-01 (CONDITION DE MISE EN PRODUCTION, DPO) : instantanés DÉJÀ figés par la tâche du 1er du mois (RG-DIR-090) dont un centre est MASQUÉ (patients nul) ET dont le total de patients de la société excède la somme des centres publiés (l'effectif masqué est donc déductible). La clé "totaux" figure dans TOUT instantané : elle ne discrimine pas (revue du lot A, m1). Discriminante, validée par un cas positif et deux témoins au rejeu PG16 du 2026-11-09. Le rapport mensuel imprime ces totaux tels quels ; un changement de politique de masquage (options a à d de l'ADR 0102) ne les corrige pas rétroactivement : les instantanés listés doivent être recalculés ou supprimés par décision du DPO. Limite : un payload non JSON (listé par 68.3) fait échouer la conversion si son texte commence par « { » sans être valide ; les instantanés d'avant les répartitions (sans « breakdown ») sont bien lus.
WITH s AS (SELECT societe_id, mois, CASE WHEN payload LIKE '{%' THEN payload::jsonb END AS j FROM direction_snapshot),
     c AS (SELECT s.societe_id, s.mois, e->'patients' AS p FROM s,
           jsonb_array_elements(CASE WHEN jsonb_typeof(s.j#>'{overview,centres}') = 'array' THEN s.j#>'{overview,centres}' ELSE '[]'::jsonb END) e)
SELECT s.societe_id, s.mois FROM s
WHERE jsonb_typeof(s.j#>'{overview,totaux,patients}') = 'number'
  AND EXISTS (SELECT 1 FROM c WHERE c.societe_id = s.societe_id AND c.mois = s.mois AND c.p = 'null'::jsonb)
  AND (s.j#>>'{overview,totaux,patients}')::bigint > COALESCE((SELECT SUM((c.p#>>'{}')::bigint) FROM c
        WHERE c.societe_id = s.societe_id AND c.mois = s.mois AND jsonb_typeof(c.p) = 'number'), 0)
ORDER BY 1, 2;
-- 69.2 Volume : nombre d'instantanés figés (borne du retraitement éventuel).
SELECT COUNT(*) FROM direction_snapshot;
```
**Recette (lecture seule, sur une copie)** : (a) `GET /api/v1/direction/overview`, `/breakdown`, `/societe` et `/report` d'une direction connue rendent la même réponse qu'avant (goldens PD1 et PD4 inchangés) ; (b) `/report` avec `from` après `to` rend 422 `PERIODE_INVALIDE` et une période de plus de 5 ans 422 `PERIODE_TROP_LONGUE` (mêmes codes) ; (c) 69.1 exécutée avant toute décision sur KB-DIR-01. Rien n'est activé par ce lot : aucun flag, aucune bascule (refactoring sur place, ADR 0024).

## 70. Pilotage direction, lot B = PD2 (instantanés mensuels, reprise dans le contexte, ADR 0103) : détections de données (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune colonne ; le SQL de `direction_snapshot` est repris MOT POUR MOT par `InstantanesMensuelsJdbc`. Les doublons (société, mois) sont impossibles (`uq_direction_snapshot`) ; les cas ci-dessous sont ceux que le filet a caractérisés (KB-DIR-06 a, e). À exécuter sur une copie avant la mise en production ; résultats à joindre à la décision métiers/DPO du 2026-12-15.

```sql
-- 70.1 Instantanés dont le mois n'est pas au format AAAA-MM (illisibles par GET /snapshots/{mois}, qui refuse un autre format).
SELECT societe_id, mois FROM direction_snapshot WHERE mois !~ '^[0-9]{4}-[0-9]{2}$' ORDER BY 1, 2;
-- 70.2 Instantanés dont le contenu n'est pas un objet JSON (listés, mais leur lecture répond 500, KB-DIR-06 e). Test de forme seulement (un « { » mal fermé n'est pas détecté ici : voir 68.3).
SELECT societe_id, mois FROM direction_snapshot WHERE payload NOT LIKE '{%' ORDER BY 1, 2;
-- 70.3 Instantanés antérieurs aux répartitions (sans « breakdown », servis breakdown nul, KB-DIR-06 a) : le rapport mensuel n'imprime pas les sections de répartition.
SELECT societe_id, mois FROM direction_snapshot WHERE payload LIKE '{%' AND payload NOT LIKE '%"breakdown"%' ORDER BY 1, 2;
-- 70.4 Sociétés actives sans instantané du mois précédent (ce que la tâche du 1er à 02:30 créerait au prochain passage ; 0 ligne après un passage réussi).
SELECT s.id, s.raison_sociale FROM societes s WHERE s.actif = TRUE AND NOT EXISTS (SELECT 1 FROM direction_snapshot d WHERE d.societe_id = s.id AND d.mois = to_char(date_trunc('month', current_date) - interval '1 month', 'YYYY-MM'));
```
**Recette (lecture seule, sur une copie)** : (a) `GET /api/v1/direction/snapshots`, `/snapshots/{mois}` et `/snapshots/{mois}/report` d'une direction connue rendent la même réponse qu'avant (goldens PD2 inchangés) ; (b) `POST /snapshots/{mois courant}` répond 422 `MOIS_NON_TERMINE`, un mois de plus de 60 mois 422 `MOIS_TROP_ANCIEN` ; (c) 69.1 (KB-DIR-01) reste la condition de mise en production : ce lot ne retraite aucun instantané. Rien n'est activé : aucun flag, aucune bascule (ADR 0024). Rejeu PostgreSQL 16 du script `pilotage-direction-pg16-rejeu.sh` : non modifié ; 70.1 à 70.4 sont des SELECT NON rejoués sur PostgreSQL 16 à ce stade (à exécuter à la main sur la copie ; le contrat JDBC des instantanés tourne sur H2 mode PostgreSQL seulement).

## 71. Pilotage direction, lot C = PD3 (alertes de la direction et temps réel, reprise dans le contexte, ADR 0104) : détections de données (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune colonne ; le SQL de `direction_alert_history` est repris MOT POUR MOT par `HistoriqueDesAlertesJdbc` (le rejeu D7 de `pilotage-direction-pg16-rejeu.sh` rejoue les requêtes d'historique à l'identique, inchangé hors libellés). Aucune contrainte d'unicité sur (société, centre, code) ouvert : les doublons sont possibles par course (PD3-R2). À exécuter sur une copie avant la mise en production ; résultats à joindre à la décision métiers/DPO du 2026-12-15.

```sql
-- 71.1 Alertes OUVERTES en double (même société, même centre, même code) : course entre deux recalculs simultanés (deux lignes ouvertes, PD3-R2). 0 ligne attendue.
SELECT societe_id, center_id, code, COUNT(*) AS ouvertes FROM direction_alert_history WHERE resolved_at IS NULL GROUP BY societe_id, center_id, code HAVING COUNT(*) > 1 ORDER BY 1, 2, 3;
-- 71.2 Alertes jamais résolues depuis plus de 30 jours (la direction ne s'est plus connectée : l'historique n'est alimenté que pendant une connexion, KB-DIR-04). Une ligne ancienne ouverte n'est PAS forcément un problème en cours.
SELECT societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at FROM direction_alert_history WHERE resolved_at IS NULL AND first_seen_at < now() - interval '30 days' ORDER BY first_seen_at;
-- 71.3 Codes inconnus de la politique (6 codes réels ; le rapport imprime un code inconnu tel quel) et gravités hors WARNING / CRITICAL.
SELECT code, severity, COUNT(*) FROM direction_alert_history WHERE code NOT IN ('KTV_CONFORMITE_BASSE','HB_HORS_CIBLE','OBSERVANCE_EN_RETARD','STOCK_SOUS_SEUIL','LOTS_PERIMES','LOTS_PEREMPTION_PROCHE') OR severity NOT IN ('WARNING','CRITICAL') GROUP BY 1, 2 ORDER BY 1, 2;
-- 71.4 Lignes résolues avant leur apparition (incohérence d'horodatage : horloges de plusieurs JVM, KB-DIR-03).
SELECT id, societe_id, center_id, code, first_seen_at, resolved_at FROM direction_alert_history WHERE resolved_at < first_seen_at ORDER BY 1;
```
**Recette (lecture seule, sur une copie)** : (a) `GET /api/v1/direction/alerts/history` et `/indicators` d'une direction connue rendent la même réponse qu'avant (goldens PD3 inchangés) ; (b) `limit=0` rend 1 entrée au plus, `limit=500` au plus 200, `limit=abc` répond 500 (KB-DIR-02, statu quo) ; (c) pendant qu'une direction est connectée, un changement de stock est diffusé sur `/topic/societe/{id}/dashboard` avec les mêmes champs (`type`, `societeId`, `at`, `changes[centerId, centre, family, name, before, after]`) ; (d) 69.1 (KB-DIR-01) reste la condition de mise en production. Rien n'est activé : aucun flag, aucune bascule (ADR 0024). 71.1 à 71.4 sont des SELECT NON rejoués sur PostgreSQL 16 à ce stade (à exécuter à la main sur la copie ; le contrat JDBC de l'historique tourne sur H2 mode PostgreSQL seulement).

## 72. Organisation O4, lot B (Stock : ports fournis, centre « actif » au sens strict, ADR 0108, KB-ORG-09) : détection de données (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune colonne. Le Stock lit « centre actif » AU SENS STRICT (`actif = TRUE` : un état NULL est exclu du contrôle de nuit des péremptions) alors que tous les autres consommateurs lisent `COALESCE(actif, TRUE)` (NULL = actif). Le lot B reproduit chaque comportement (`actifsAuSensStrict()` pour le Stock, `actifs()` pour les autres) ; ce contrôle dit si l'écart a un effet réel. À exécuter sur une copie avant la mise en production ; résultat à joindre à la décision métiers/DPO du 2026-12-15.

```sql
-- 72.1 Centres selon leur état : nuls = état jamais renseigné, actifs, inactifs.
SELECT count(*) FILTER (WHERE actif IS NULL) AS nuls, count(*) FILTER (WHERE actif) AS actifs, count(*) FILTER (WHERE NOT actif) AS inactifs FROM centers;
```
**Interprétation** : `nuls = 0` : l'écart de sens n'a aucun effet en production, le Stock pourra passer à la variante large (retrait de `actifsAuSensStrict`, décision métiers/DPO). `nuls > 0` : ces centres sont aujourd'hui EXCLUS du contrôle de nuit des péremptions du Stock mais INCLUS partout ailleurs (Absences, Planification, annuaire) ; les métiers décident s'ils doivent être actifs (renseigner `actif`, ou variante large) ou inactifs (renseigner `actif = FALSE`) : ne rien corriger avant leur décision. 72.1 est un SELECT NON rejoué sur PostgreSQL 16 à ce stade (à exécuter à la main sur la copie ; les contrats de l'annuaire tournent sur H2 mode PostgreSQL seulement, O4-R7).
**Recette (lecture seule, sur une copie)** : (a) le contrôle de nuit des péremptions et la comptabilisation de nuit parcourent les mêmes centres qu'avant (goldens et tests du Stock inchangés) ; (b) la clôture d'un inventaire « centre entier » verrouille toujours la ligne du centre. Rien n'est activé : aucun flag, aucune bascule (ADR 0024).

## 73. Organisation O4, lot C (libellés de salle, de créneau et de médecin, ADR 0109, KB-ORG-10) : détection de données (2026-11-09) — SELECT seulement

Refactoring sur place : aucune migration, aucune colonne. Le journal des mouvements de Patient et couverture joint la salle et le créneau SANS borne de centre ; le lot C reproduit ce comportement (`nomsDesSalles`, `libellesDesCreneaux`). Ce contrôle dit si l'absence de borne a un effet réel. À exécuter sur une copie ; résultat à joindre à la décision métiers/DPO du 2026-12-15.

```sql
-- 73.1 Mouvements dont la salle ou le créneau appartient à un AUTRE centre que le mouvement.
SELECT count(*) FILTER (WHERE s.id IS NOT NULL AND s.center_id <> m.center_id) AS salles_d_un_autre_centre,
       count(*) FILTER (WHERE c.id IS NOT NULL AND c.center_id <> m.center_id) AS creneaux_d_un_autre_centre
FROM mouvement_patient m LEFT JOIN salle s ON s.id = m.salle_id LEFT JOIN position_creneau c ON c.id = m.position_id;
-- 73.2 Patients dont le médecin traitant ou le créneau appartient à un AUTRE centre (le libellé affiché est alors vide : lecture bornée au centre).
SELECT count(*) FILTER (WHERE md.id IS NOT NULL AND md.center_id <> p.center_id) AS medecins_d_un_autre_centre,
       count(*) FILTER (WHERE c.id IS NOT NULL AND c.center_id <> p.center_id) AS creneaux_d_un_autre_centre
FROM patients p LEFT JOIN medecin md ON md.id = p.medecin_traitant_id LEFT JOIN position_creneau c ON c.id = p.position_id;
```
**Interprétation** : `0` partout : l'absence de borne du journal est sans effet, la lecture pourra être bornée au centre (suppression de `nomsDesSalles` / `libellesDesCreneaux` au profit de `codesDesSalles`-like bornés), décision métiers/DPO. `> 0` : ces mouvements affichent aujourd'hui le libellé d'une salle ou d'un créneau d'un autre centre (fuite d'affichage inter-centres) : ne rien corriger avant leur décision. SELECT NON rejoués sur PostgreSQL 16 (O4-R7).
**Recette (lecture seule, sur une copie)** : la liste des patients (colonnes médecin et créneau, filtres par libellé) et le journal des mouvements (salle, créneau) rendent les mêmes libellés qu'avant (goldens des consommateurs inchangés). Rien n'est activé : aucun flag, aucune bascule (ADR 0024).

## 74. Confidentialité L1 (suppression complémentaire des totaux, ADR 0112, KB-DIR-01 et KB-MED-104 CORRIGÉS) : détection de données (2026-11-09) — SELECT seulement

Aucune migration, aucune colonne, aucune réécriture : `direction_snapshot.payload` reste figé (RG-DIR-090). La correction masque à la LECTURE les totaux bruts des instantanés mensuels capturés avant elle. Ce contrôle dénombre les instantanés dont la lecture change (un centre masqué dans la vue d'ensemble : l'instantané relu perd son total de patients) ; il ne lit que des comptes.

```sql
-- 74.1 Instantanés figés dont un centre de la vue d'ensemble est masque (« patients »:null dans centres) : leur total est supprimé à la lecture. Recherche textuelle, JSON compact.
SELECT societe_id, count(*) AS instantanes, min(mois) AS premier, max(mois) AS dernier
FROM direction_snapshot
WHERE payload LIKE '%"patients":null%'
GROUP BY societe_id ORDER BY societe_id;
```
**Interprétation** : `0` ligne : aucun instantané existant ne change à la lecture. `> 0` : ces mois sont relus (JSON et PDF) SANS total de patients ; le contenu stocké garde les totaux bruts (lisibles par un accès SQL : résiduel DPO, ADR 0112). Prévenir la Direction que les rapports déjà émis ne sont pas rappelés. Format du JSON de `payload` à vérifier sur la copie (espaces) avant de conclure à `0`. SELECT NON rejoué sur PostgreSQL 16.
**Recette (lecture seule, sur une copie)** : `GET /api/v1/direction/snapshots/{mois}` d'un mois listé en 74.1 rend `overview.totaux.patients` nul et les mêmes valeurs par centre qu'avant ; `…/report` imprime « &lt; seuil » dans « Total société » ; `SELECT payload` est identique avant et après la lecture. Rien n'est activé : aucun flag, aucune bascule (ADR 0024).

## 75. Clinique L2 (dernière sérologie conclusive, ADR 0113, KB-MED-106 CORRIGÉ) : liste des patients qui entreront en isolement (2026-11-09) — SELECT seulement

Aucune migration, aucune donnée modifiée. La requête 66.1 sur-estime la liste : elle ne regarde pas un NEGATIF intercalé entre le positif et le douteux. 75.1 est la liste EXACTE des patients (par centre et marqueur) dont le dernier résultat est DOUTEUX ou EN_COURS et dont la dernière sérologie conclusive est POSITIVE : ils reviennent à risque infectieux, donc en isolement au prochain placement. **Cette liste doit être validée par les médecins avant la mise en production** (échéance 2026-12-15).

```sql
-- 75.1 Patients qui reviennent a risque : dernier resultat du marqueur DOUTEUX ou EN_COURS, dont la derniere serologie CONCLUSIVE (POSITIF ou NEGATIF) est POSITIVE.
SELECT d.center_id, d.patient_id, d.marqueur FROM serologies_patient d WHERE d.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND d.resultat IN ('DOUTEUX', 'EN_COURS') AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = d.center_id AND n.patient_id = d.patient_id AND n.marqueur = d.marqueur AND (n.date_prelevement > d.date_prelevement OR (n.date_prelevement = d.date_prelevement AND n.created_at > d.created_at))) AND EXISTS (SELECT 1 FROM serologies_patient p WHERE p.center_id = d.center_id AND p.patient_id = d.patient_id AND p.marqueur = d.marqueur AND p.resultat = 'POSITIF' AND p.date_prelevement <= d.date_prelevement AND NOT EXISTS (SELECT 1 FROM serologies_patient c WHERE c.center_id = p.center_id AND c.patient_id = p.patient_id AND c.marqueur = p.marqueur AND c.resultat IN ('POSITIF', 'NEGATIF') AND (c.date_prelevement > p.date_prelevement OR (c.date_prelevement = p.date_prelevement AND c.created_at > p.created_at)))) GROUP BY d.center_id, d.patient_id, d.marqueur ORDER BY 1, 2;
```
**Interprétation** : `0` ligne : aucun patient ne change d'état. `> 0` : chaque ligne est un patient qui entre en isolement ; vérifier la capacité des salles d'isolement du centre et prévenir les médecins. Rejouée à l'identique sur PostgreSQL 16 jetable (`backend/scripts/dossier-medical-dm9-pg16-rejeu.sh 55551`, jeu FICTIF : P6 et P7) ; ne dit RIEN de l'état de la production.
**Recette (lecture seule, sur une copie)** : le placement d'un patient listé le dirige vers une salle d'isolement ; `GET` de la liste des patients à risque d'un centre contient ces patients ; rien n'est activé : aucun flag, aucune bascule (ADR 0024).
