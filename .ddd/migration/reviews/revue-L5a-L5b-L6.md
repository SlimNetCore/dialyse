# Revue indépendante L5a / L5b / L6 (2026-11-10)

Périmètre : commits 98fe657a (L5a, ADR 0117), 552b175a (L5b, ADR 0118), 25522c98 (L6, ADR 0119). Lecture seule : diffs `git show`, aucun test, build ni scan lancé. La preuve « rouge puis vert » n'est donc pas rejouée : seule la présence de tests ciblés est vérifiée.

## Verdict : pass-with-actions (aucun blocker)

## Constats

| # | Sév. | Constat | Preuve | Correction proposée |
|---|---|---|---|---|
| 1 | major | L5b : 3 goldens ré-approuvés sans le suffixe `_nouveau_code`, alors que le message de commit annonce « 9 goldens re-approved as _nouveau_code ». Leur archive `_avant_l5b.legacy.json` existe, mais le golden vivant garde un nom « parité » alors qu'il diverge du legacy. | `git show --stat 552b175a` : `dm8/stats/l_export_pdf_reprend_les_statistique/pdf.approved.json`, `dm9/stats/le_nom_du_centre_avec_balises_et_esp/nom_du_centre.approved.json`, `seance/se7e/pilotage/la_repartition_par_caisse_ne_compte_/repartition_par_caisse.approved.json` | Renommer en `*_nouveau_code.approved.json` (et adapter le support de test qui les nomme), comme le font L5a et L6. |
| 2 | minor | L5a : le déterminisme de l'évolution du poids n'est pas complet. La sous-requête du poids sec trie `pm.updated_at DESC` sans `NULLS` ni identifiant : si `updated_at` est nullable, H2 (nulls en dernier) et PG (nulls en premier) divergent, et l'égalité reste au choix du SGBD. KB-SEA-86 est annoncé corrigé pour cette lecture. La nullabilité n'a pas pu être établie : le grep des migrations `*prescription*` ne trouve rien. | `backend/contexts/seance/.../StatistiquesDesSeancesJdbc.java:38` | Ajouter `NULLS LAST, pm.id DESC` (ou documenter que la colonne est NOT NULL) et un cas de contrat à `updated_at` égal. |
| 3 | minor | L5b : le cloisonnement de la chaîne payeur est partiel. `centre_payeur` est borné au centre du patient, mais `agence` ne l'est pas (`ag.id = cp.agence_id` sans `ag.center_id`). Une agence d'un autre centre reste donc rattachée à la caisse. La facturation, elle, borne `ag.center_id = f.center_id`. Les résultats sont donc asymétriques entre le chiffre d'affaires et les effectifs et séances par caisse. La même chaîne existe dans l'Organisation (non évaluée). | `PatientsDesCentresJdbc.java:33`, `SeancesRealiseesDesCentresJdbc.java:29`, `ReferentielsCommunsJdbc.java:46` ; correctif facturation dans le diff de `SyntheseFinanciereDesCentresJdbc` | Ajouter `AND ag.center_id = cp.center_id` aux deux JOIN et un test de contrat (agence d'un autre centre), ou noter l'écart dans l'ADR 0118 si l'agence est volontairement partagée. |
| 4 | minor | L6 : « aujourd'hui » reste hétérogène sur `/api/v1/direction/gmao`. Le module Pilotage lit l'horloge injectée (Algiers), mais la route gmao passe par `DirectionGmaoQueryService`, qui appelle `LocalDate.now()` (fuseau de la JVM, UTC). Entre 23 h et minuit UTC, la période par défaut de gmao diffère de celle de `/overview`. Le grep « aucun `LocalDate.now()` » est propre dans `backend/contexts/pilotage-direction` (0 occurrence ; seul `LocalDate.now(horloge)` à `HorlogeDuPilotage.java:39`) : l'occurrence est dans la plateforme. | `backend/platform/.../application/direction/DirectionGmaoQueryService.java:63` | Injecter `HorlogeDuPilotage` (ou la période) dans ce service, ou lister gmao dans le résiduel de l'ADR 0119. |
| 5 | minor | L6 : le 405 ne respecte pas « 403 prioritaire » pour les routes où l'on vise la priorité des droits. Le handler 405 ne contrôle pas `@PreAuthorize`, car l'erreur est levée avant le choix du contrôleur. Un profil sans droit qui fait un `POST /api/v1/direction/overview` reçoit un 405 avec `Allow`, c'est-à-dire la confirmation de la route et de ses méthodes. L'impact est faible : le préfixe `/api/v1/direction/` est protégé en amont par l'authentification. | `DatesIllisiblesDeLaDirectionAdvice.java:134-150` ; test `MethodesNonSupporteesDeLaDirectionAdviceTest` (aucun cas de profil sans droit) | Soit documenter le choix dans l'ADR 0119, soit ajouter une règle `/api/v1/direction/**` authentifiée à la couche sécurité et un test 405 vs 403/401. |
| 6 | minor | L5a : `absenceLiee` (KB-SEA-81) compare `statut = 'ABSENT'` en casse stricte, alors que KB-SEA-82 (casse et blancs du statut) reste « reproduit sans correction » dans la même classe. Deux conventions de statut cohabitent. Aucune donnée en minuscules n'a été détectée par les SELECT du §79. | `LecturesDesSeancesRealiseesJdbc.java:68` ; entête de classe, lignes 14-22 | Documenter dans KB-SEA-81 que seule la forme canonique `ABSENT` est reconnue (cohérent avec `FacturationDesSeancesJdbc.java:105`). |
| 7 | minor | Clôture KB-SEA-96 (b) : une créatinine à 0 est retenue comme « dernière » et masque les mesures antérieures. Le stade devient « non évaluable » sans mesure plus ancienne exploitable. La clôture est honnête (note « validation médecins ») mais touche une donnée clinique, avec une échéance 2026-12-15 sans propriétaire nommé. | `EvaluationKdigoDuCandidat.java` (javadoc `fonctionRenale`) ; `.ddd/safety-net/known-bugs.md` (KB-SEA-96 b) | Nommer le référent médical dans la ligne KB, puis ouvrir un ticket DM6 « refuser le 0 à la saisie ». |
| 8 | minor | KB-SEA-85 (b) change une règle de gestion : les statistiques paramédicales ne comptent plus que les séances VALIDEE, SIGNEE et FACTUREE, et les ANNULEE et CREE sortent des moyennes. La décision est « déléguée » : elle est tracée dans l'ADR et couverte par §80.4, mais pas confirmée par les infirmiers et médecins. Le texte « 6.00 » devient « 6 » dans le PDF. | `StatistiquesDesSeancesJdbc.java` (`baseFrom`) ; `PatientStatsQueryService.java` (`safeCount`) | Garder la confirmation du 2026-12-15 comme condition de déploiement et la reprendre dans le plan de recette. |
| 9 | minor | Rétrocompatibilité des instantanés : les tranches d'âge déjà figées gardent « l'âge au jour de la capture ». Les nouveaux sont calculés « à la fin de période ». Les deux populations cohabitent sans marqueur de version. | `ConsulterLaRepartition.java:41-42` ; §81 (interprétation) | Ajouter un champ de version ou une note dans la lecture des instantanés (RG-DIR-104) pour que l'écran ne compare pas les deux. |

## Vérifications positives (preuve)

1. **KB corrigées réellement**
   - KB-SEA-77 : `LecturesDesSeancesRealiseesJdbc.java:39`.
   - KB-SEA-78 : `LecturesDesSeancesRealiseesJdbc.java:80`.
   - KB-SEA-79 : `ConstantesDesSeancesJdbc.java`, `created_at DESC NULLS FIRST`.
   - KB-SEA-81 : `LecturesDesSeancesRealiseesJdbc.java:68`.
   - KB-SEA-86 : `StatistiquesDesSeancesJdbc.java:41`, et clés en minuscules via `enMinuscules`.
   - KB-SEA-88 : `cahier_dialyse.jrxml` (COALESCE avec `LEFT JOIN forfait fo` borné au centre).
   - KB-SEA-89 : `SeancesDeLaRepriseJdbc.java:53`.
   - KB-SEA-96 a : `DerniereCreatinineJdbc`, `ORDER BY date_prelevement DESC, created_at DESC NULLS LAST, id DESC LIMIT 1`.
   - KB-DIR-05 et KB-SEA-83 : `SeancesRealiseesDesCentresJdbc.java:84` (LEFT JOIN).
   - KB-SEA-84 : bornage de `cp.center_id` (sous réserve du constat 3), `ag.center_id` en facturation, et départage par code dans `RepartitionDeLaSociete`.
   - KB-SEA-85 : `volet_paramedical` borné au centre et filtre de statut dans `baseFrom`.
   - KB-DIR-02 : advice 400 (`DatesIllisiblesDeLaDirectionAdvice.java:38-44`) et 405 avec `Allow`.
   - KB-DIR-03 : `HorlogeDuPilotage` et `@Scheduled(zone=...)`.
   - KB-DIR-06 e : `InstantanesMensuelsPublies`, 422 `INSTANTANE_ILLISIBLE`.
   - KB-DIR-08 : `ConsulterLaRepartition.java:42` (`periode.fin()`).
2. **Tests ciblés présents**
   - Contrats mémoire, H2 et PG pour L5a/L5b, ex. `should_rank_a_null_creation_first_in_descending_order_on_every_database`.
   - `RepartitionDeLaSocieteTest`, `ConsulterLaRepartitionTest`, `HorlogeDuPilotageTest` (23h30 UTC = lendemain à Alger), `CaptureDuMoisEcoulePlanifieeTest` (zone du cron), `InstantanesMensuelsPubliesTest`, `DatesIllisiblesDeLaDirectionAdviceTest` (400 et 403 avant 400, sans détail technique), `MethodesNonSupporteesDeLaDirectionAdviceTest`.
   - Rouge puis vert non rejoué (interdiction de lancer des tests).
3. **SQL H2/PG** : tous les tris ajoutés écrivent `NULLS FIRST/LAST` explicitement (sauf constat 2) et se terminent par `id`. Les identifiants sont comparés en octets non signés (documenté). Le prédicat Direction est unique (LEFT JOIN patients sur id ET centre). Pas de fuite inter-centres introduite : les volets, forfaits et centres payeurs sont bornés.
4. **Goldens** : l'archive `_ecarts-legacy/**/*_avant_<lot>.legacy.json` existe pour chaque golden modifié. Tous les goldens de L5a et L6 sont en `_nouveau_code`, sauf les 3 de L5b (constat 1).
5. **Clôtures documentées** : KB-SEA-75/80/90/91 (L5b), KB-SEA-87 et 96 b (L5a), KB-DIR-04/06 a-d/07 (L6) sont chacune justifiées avec test, détection §80/§81, alternative écartée et échéance de confirmation. KB-SEA-92/97/98 : « déjà clos » non rejoué ici.
6. **§79/§80/§81 de `verifications-production.md`** : uniquement des SELECT, aucun UPDATE ni DELETE actif ; `Rien n'est activé` répété. ADR 0117, 0118 et 0119 présents. La décision d'horloge Alger est tracée comme décision déléguée, avec le résiduel « fuseau par centre » nommé.
7. **Hexagonal et langage** : l'adaptateur 422 lève `RegleDuPilotageViolee` (domaine), `InstantsDuPilotage` est un type dédié dans `configuration`, les lectures restent des adaptateurs JDBC derrière des ports publiés. Termes cohérents avec les glossaires mis à jour (`seance.md`, `pilotage-direction.md`, `facturation.md`).

## Top actions
1. Renommer les 3 goldens L5b en `_nouveau_code` (constat 1).
2. Rendre `pm.updated_at` déterministe ou prouver son NOT NULL, puis borner `agence` au centre payeur (constats 2 et 3).
3. Aligner `DirectionGmaoQueryService` sur l'horloge du Pilotage ou le déclarer en résiduel de l'ADR 0119 (constat 4).
