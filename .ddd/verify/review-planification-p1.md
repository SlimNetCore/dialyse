---
status: proposed
date: 2026-10-26
scope: slice planification-p1-parametrage (non commité, au-dessus de f94ad140)
sources: check-arch.ts (0 violation), state.ts show, git status/diff, ADR 0049/0053/0055, fiche de slice, known-bugs KB-PLN, verifications-production §32, code listé ci-dessous
reviewer: relecteur indépendant (lecture seule, aucun build ni test lancé)
---
# Revue — Planification P1 (paramétrage)

Contrôles automatiques : `check-arch` 0 violation (0 en baseline, 0 nouvelle). `archunit.properties` (`failOnEmptyShould=false`) supprimé comme prévu par le squelette.
Scan incrémental non relancé (aucun nouveau cycle ni nouvelle table partagée attendus : mêmes tables, cf. ADR 0055 §5).

## Constats

| # | Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| 1 | major | archi / tactique | **Latent (pas une violation P1).** Le contrat du port `VerrouDuCentre` grave « rien n'est créé » : un centre sans ligne `planning_parametres` n'a **aucune exclusion mutuelle**. L'ADR 0049 protège l'agrégat cœur `PlacesDuCentre` par ce même verrou **avec création de la ligne par défaut**. Si P2+ réutilise `VerrouDuCentreJdbc` tel quel, deux placements simultanés dans un centre jamais paramétré ne seront pas sérialisés (double occupation possible). L'ADR 0055 ne limite pas la variante à P1, et §32 ne compte pas les centres sans ligne. | `domain/VerrouDuCentre.java:5-11` ; `infrastructure/persistence/VerrouDuCentreJdbc.java:20-23` ; ADR 0049 l.19-20 ; ADR 0055 l.17-19, 31 ; `verifications-production.md:1196-1217` | Dans l'ADR 0055, écrire noir sur blanc : « variante valable seulement pour le paramétrage ; `PlacesDuCentre` (P2) devra créer la ligne (INSERT … ON CONFLICT DO NOTHING puis FOR UPDATE) ». Ajouter à §32 `SELECT count(*) FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id=c.id)`. |
| 2 | minor | stratégique / docs | La règle de gestion, le glossaire et §32 disent qu'une écriture simultanée est « refusée en 409 ». En réalité, la seconde écriture **attend puis passe** (le dernier écrit gagne). Le 409 n'arrive qu'au-delà de 5 s d'attente ou si deux écritures créent en même temps la même clé. Le test du contrat le prouve. | `docs/reference/regles-de-gestion/05-planning-placement.md:27-28` ; glossaire `planification.md:61` ; `verifications-production.md:1217` ; `contrat/ParametrageJdbcContrat.java:298-319` | Reformuler : « les écritures d'un même centre passent l'une après l'autre ; 409 si l'attente dépasse 5 s ou si une clé est créée entre-temps ». Ce 409 est rare : la surveillance en tiendra compte. |
| 3 | minor | archi / legacy | Deux propriétaires écrivent `salle.isolement` : le référentiel d'administration (énumération `isolement`) et la Planification (port `SallesDuCentre`). Aucun verrou commun entre eux, donc le dernier qui écrit gagne. La situation est déjà notée dans l'ADR 0053 (B8) et vaut le legacy. L'écriture elle-même est correcte : même `JdbcTemplate`, même transaction, `WHERE center_id = ?` et contrôle `containsAll` sous le verrou. | `acl/legacyorganisation/SallesDuCentreJdbc.java:39-44` ; `bc-referential/.../ReferentialKind.java:37` ; `ModifierLesParametres.java:45-51` | Rappeler cette double écriture dans la fiche (« Reste à faire ») avec le lien vers le futur contexte Organisation (ADR 0053 l.39). |
| 4 | minor | legacy | `covered.json` liste encore les 10 fichiers legacy supprimés (2 contrôleurs, 2 services, 3 adaptateurs JDBC, adaptateur, entité et repository JPA). Rien à ce sujet dans `seams.md`. | `.ddd/safety-net/covered.json` (bloc ajouté, lignes +287 à +296 du diff) | Retirer les chemins supprimés et ne garder que les ports `bc-seance` et les 4 ACL. |
| 5 | minor | tests / frontend | La page « paramètres du planning » affiche le message générique `PLANNING.PARAMS.ERR.SAVE` pour un 409, mais aussi pour le 400 « salle d'un autre centre ». Le point est noté dans la fiche. | `frontend/src/app/features/planning/planning.store.ts:150-151` ; fiche l.39 | Faire comme `preferencesErrorKey` : afficher le code `PLANIFICATION_ECRITURE_CONCURRENTE` et le message 400 métier. Faible urgence (le 409 est rare, cf. #2). |
| 6 | nit | langage | La javadoc de `PatientsParPosteEtSerie` cite RG-PLN-004 ; le glossaire et la règle de gestion citent RG-PLN-003. | `domain/PatientsParPosteEtSerie.java:4` ; `05-planning-placement.md:17` | Corriger la javadoc (RG-PLN-003). |
| 7 | nit | legacy | §32 contrôle les bornes des paramètres et des réglages, mais pas celles du taux d'activité des profils (10-100). Or le nouveau code refuse à la lecture une valeur hors bornes, et l'ACL d'optimisation lirait l'erreur. | `domain/ProfilDePlanification.java:19-21` ; `verifications-production.md:1211-1214` | Ajouter `SELECT … FROM infirmier_profil_planning WHERE taux_activite NOT BETWEEN 10 AND 100` (attendu 0). |
| 8 | nit | archi | Le 409 et le 500 dépendent du nom de la table à verrouiller en premier. Aujourd'hui l'ordre est `planning_parametres` puis `salle`, puis la table propre. Aucune règle écrite n'empêche une slice future de prendre un autre verrou avant celui du centre (risque d'interblocage, transformé en 409 par `RefusDeVerrou`). | `RefusDeVerrou.java:44-55` ; `ModifierLesParametres.java:45-50` | Ajouter à l'ADR 0049/0055 : « le verrou du centre est toujours pris en premier ». |

## Points examinés sans écart
- **(a)** Les 45 goldens `_nouveau_code` correspondent exactement aux 45 fichiers `_ecarts-legacy`. Exemples vérifiés : `reglages/temps_plein_61`, `refus_sans_repos`, `refus_entier_nul` (500 → 400), `parametres/lecture_centre_illisible` (500 → 400), `preferences-patients/patient_transfere_accepte_200` (200 → 422 `PREFERENCE_PATIENT_SORTI`). Tous relèvent de KB-PLN-01/02/07/08/09/10, donc d'écarts décidés.
- **(b)** Première écriture concurrente : toutes les clés sont protégées par une clé primaire (`schema.sql:980-987, 1185-1193, 1204-1211`). La perdante bloque sur l'index, puis reçoit `DuplicateKeyException` ou 55P03, traduit en 409 (`RefusDeVerrou.java:47-55`). Le cas est testé (`ParametrageJdbcContrat.java:352-379`) et rejoué sur PG16 d'après la fiche. Aucun chemin 500 identifié. Le `lock_timeout` n'est posé que pour une transaction nouvelle (`UniteDeTravailSpring.java:94-106`), avec le même `PlatformTransactionManager` que le legacy (`PlanificationConfiguration.java:60-61`).
- **(c)** Voir #3 : la transaction est atomique et la garde de centre est en place.
- **(d)** Le legacy lit le paramétrage par 4 ACL, uniquement au travers de `publishedlanguage` (`PlanningParametresDuContexte.java:27-33`). Les ports `bc-seance` ne servent plus qu'à la lecture. Seul `DirectionCapaciteQueryService.java:52-53` lit encore en SQL direct, et cette dette est consignée dans la fiche l.37.
- **(e)** Audit : `AuditActionResolver` couvre tout PUT `/api/v1/**`. Le test vérifie la route, le statut 200/400 et l'absence de trace pour les lectures. La valeur avant/après n'est pas tracée, comme dans le legacy *(suggestion, pas une violation)*.
- **(g)** Droits identiques au legacy (`@PreAuthorize` comparés à HEAD). `SessionLegacy` refuse un centre différent de la session, et le SUPERADMIN doit fournir un `centerId` (`SessionLegacy.java:48-71`). Aucune donnée d'un autre centre ne sort : toutes les requêtes filtrent sur `center_id`.
- **(h)** Pas de code mort repéré : `PlanningDonneesPort.sallesDuCentre` reste utilisé par `AffectationInfirmierService` et `RemplacementInfirmierService`.
- **(i)** Les tests n'utilisent ni `now()` ni `@Order`. Les attentes concurrentes sont bornées par des latches. Le seul `Instant.now()` est dans un adapter (`ParametresDuPlanningJdbc.java:62`, colonne `updated_at`), ce qui est acceptable.

## Verdict : **pass-with-actions**
Compte : 0 blocker · 1 major · 4 minor · 3 nit.

Top 3 actions :
1. ADR 0055 : limiter la variante « sans ligne » au paramétrage et imposer la création de la ligne pour `PlacesDuCentre` (P2), plus le comptage des centres sans ligne en §32 (#1).
2. Corriger la formulation du 409 dans la règle de gestion, le glossaire et §32 : les écritures attendent, le 409 n'arrive qu'au-delà de 5 s (#2).
3. Nettoyer `covered.json` (10 chemins supprimés) et documenter l'ordre des verrous (#4, #8).
