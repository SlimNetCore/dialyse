---
slice: seance-se7d-lectures-absences-planification-dossier
status: characterized (audit vérifié sur le code et les tests réels, filet complété le 2026-11-08 ; aucun code de production modifié)
date: 2026-11-08
context: seance (consommateurs : absences-patients, planification, dossier-medical)
sources: [.ddd/migration/slices/seance-se7d-lectures-absences-planification-dossier.md, TableDesSeancesTest (allow-list, 17 lecteurs), filets A1 à A4, P6, P8, DM1, contrats LecturesDuSuivi / LecturesDeLaSemaine / HistoriqueDesConstantes]
---

# SE7d : audit et filet des 5 lecteurs SQL de `seances` hors plateforme

Chemins relatifs à `backend/contexts/`. Légende : **couvert** = un test échoue si la requête change (prédicats, colonnes, tri, centre) ; **partiel** = cas nominal seulement ; **non couvert**. « avant » = filet existant relu dans le code des tests ; « après » = avec SE7d.

Correction de la fiche de slice : 8 requêtes et non 8 « occurrences » au sens de la fiche (Absences `SeancesRealiseesJdbc` 3, `ExclusionsDeSeancesJdbc` 1, `PatientsAttendusJdbc` 1 sur `seances` + 3 sur d'autres tables, Planification 1, Dossier médical 2). La Planification n'a AUCUNE autre requête sur `seances` (la fiche parle d'un port `attenduLe` : c'est `PresenceDesPatients`, sans SQL sur `seances`).

## 1. Requêtes, appelants, tests

| # | Requête (`chemin:ligne`) | Colonnes / prédicats / tri / centre / NULL | Appelant observable (route, rôle, job) | Tests existants qui l'observent réellement | Avant | Après |
|---|---|---|---|---|---|---|
| A1 | `absences-patients/…/acl/legacyseance/SeancesRealiseesJdbc.java:31` `duCentre` | `DISTINCT patient_id, date_seance` ; `center_id = ?`, `date_seance BETWEEN ? AND ?` (inclus, bornes inversées = vide), `statut IN ('VALIDEE','SIGNEE','FACTUREE')` ; `ORDER BY date_seance` SEUL ; colonnes NOT NULL | `GET /api/v1/absences-patients/semaine` (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER) via `SuivreLaSemaine` (dimanche à samedi ; sans date : jour du centre) | contrat `LecturesDuSuiviContrat.les_seances_realisees_du_centre_…` (double, H2, PG16 `LecturesDuSuiviPostgresContractIT`) ; A1 `SuiviDeLaSemaineEtSynthese…` (`semaine_du_dimanche_ancre`, voisines, centre B) | partiel (aucune égalité de date entre patients, casse) | couvert ; ordre intra-jour NON verrouillé (KB-SEA-77) |
| A2 | même fichier `:39` `joursDuPatient` | `DISTINCT date_seance` ; centre, patient, `BETWEEN`, 3 statuts ; `ORDER BY date_seance` | `GET /api/v1/absences-patients/{id}/seances-rattrapage` (4 rôles) via `ListerLesSeancesDeRattrapage` (de `premierJourPossible` à aujourd'hui du centre) | contrat `les_jours_de_seance_d_un_patient_…` ; A1 `SeancesDeRattrapageCharacterizationIntegrationTest` (CREE, ABSENT, doublon VALIDEE+SIGNEE, centre B) | partiel (casse, blancs) | couvert |
| A3 | même fichier `:47` `realisee` | `COUNT(*)` ; centre, patient, `date_seance = ?`, 3 statuts | `POST /api/v1/absences-patients` (ADMIN, SECRETAIRE, INFIRMIER : 422 `ABSENCE_SEANCE_REALISEE`) ; `PUT /{id}/rattrapage` (4 rôles) ; job `ControleDeNuitDesAbsences` (02:30, fuseau du centre) et `POST /rattrapage-detection` (ADMIN) via `ReconciliationAbsences` | contrat `une_seance_est_realisee_ce_jour_la_…` ; A2 `DeclarationDUneAbsence…` (6 statuts), `RattrapageDUneAbsence…` ; A3 `ReconciliationDesAbsences…` (6 statuts) | partiel (casse) | couvert |
| A4 | `absences-patients/…/acl/legacyseance/ExclusionsDeSeancesJdbc.java:23` | `COUNT(*)` ; `center_id = ? AND exclusion_absence_id = ?` ; AUCUN prédicat de statut | `PUT /api/v1/absences-patients/{id}/annulation` (4 rôles) via `AnnulerUneAbsence` (422 `ABSENCE_D_EXCLUSION`) | `ExclusionsDeSeancesJdbcTest` (H2, table minimale : lié, libre, autre centre) ; A2 `ExclusionEtReinclusion….l_absence_d_une_exclusion_ne_s_annule_pas…` | partiel (statut et centre jamais en HTTP, jamais sur le schéma réel) | couvert (KB-SEA-81) |
| A5 | `absences-patients/…/acl/legacyplanning/PatientsAttendusJdbc.java:50` | `patient_id` (SANS `DISTINCT`, ramené à un ensemble en Java) ; centre, `date_seance = ?`, 3 statuts | job `ControleDeNuitDesAbsences`, `POST /rattrapage-detection` (ADMIN), rattrapage au démarrage, via `DetectionAbsences` | `PatientsAttendusJdbcTest` (6 statuts, autre jour, autre centre) ; `DetectionPostgresContractIT` (PG16) ; A3 `PatientAttendu…` (6 statuts, centre B) | partiel (casse) | couvert |
| P1 | `planification/…/acl/legacyseance/SeancesRealiseesJdbc.java:36` | `patient_id, date_seance, salle_id, creneau_id, generateur_id, p.nom, p.prenom` ; `JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id` ; centre, `BETWEEN`, 3 statuts ; `ORDER BY date_seance` SEUL (la Javadoc `:23` annonce aussi l'identifiant : faux) ; place nulle = place actuelle ; `prenom` nul inatteignable (NOT NULL) | `GET /api/v1/planning/semaine` (4 rôles) via `ConsulterLaSemaine` ; `POST /api/v1/planning/optimisations` (ADMIN, SECRETAIRE) et `POST /{id}/application` (ADMIN) via `LireLesDonneesDeLOptimisation` (empreinte seulement) | contrat `LecturesDeLaSemaineJdbcContrat.should_read_the_realised_sessions_…` ; P6 `DeplacementsEtSeancesRealisees…` (8 tests : statuts, place nulle, patient sorti, hors semaine, centre B) ; P8 `effet_de_chaque_changement_sur_l_application` (séance PLANIFIEE seulement) ; domaine `EmpreinteEtIndicateursDOptimisationTest` | partiel (jointure au centre du patient, deux séances le même jour, nom rogné, casse) | couvert pour la semaine ; appelant optimisation : partiel (voir limites) |
| D1 | `dossier-medical/…/persistence/HistoriqueDesConstantesJdbc.java:34` (total) | `count(*)` ; `center_id = ? AND patient_id = ?` ; tous statuts ; pas de jointure `patients` | `GET /api/v1/patients/{id}/constantes` (MEDECIN, ADMIN ; `page`, `size` 1 à 100) via `ConsulterLesConstantesDuPatient` | contrat `HistoriqueDesConstantesContrat` (9 tests, double, H2, PG16 via `dossier-medical-dm1-pg16-rejeu.sh`) ; DM1 `LectureDesConstantesDuPatient…` | couvert | couvert |
| D2 | même fichier `:25` (page) | `s.id, date_seance` + 7 mesures ; `LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id` ; `ORDER BY date_seance DESC, created_at DESC, id DESC LIMIT ? OFFSET ?` ; `created_at` nullable | idem | contrat `should_list_most_recent_session_first_…` (créations distinctes) ; DM1 (égalité de date, pagination, volet d'un autre centre) | partiel (égalité complète, création nulle, divergence H2/PG16 jamais observées) | couvert (H2 golden + PG16 rejeu, KB-SEA-79) |

## 2. Filet ajouté (points d'observation)

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se7d/` : harnais `Se7dCharacterizationSupport` (au-dessus de SE7b : MockMvc, Spring complet, sécurité réelle + `RoleScopeFilter`, filtre d'écart de 2 ms, alias d'UUID ; dates FIXES `<D+n>`, semaine du dimanche 2026-03-01, aucun `now`, aucune zone système) et 3 classes, 12 tests, 12 goldens `backend/platform/src/test/resources/characterization/seance/se7d/{absences,planification,dossier-medical}/` approuvés un par un (lecture du `.received`, écriture de l'`.approved.json`).

| Classe | Tests | Point d'observation | Requêtes |
|---|---|---|---|
| `LesSeancesLuesParLesAbsencesCharacterizationIntegrationTest` | semaine (3 patients le même jour, doublon, bornes, centre B) ; rattrapage (casse, blanc de fin, doublon, 404 hors centre) ; annulation x lien d'exclusion (sans lien, séance ABSENT, séance VALIDEE, séance du centre B) ; matrice 14 statuts x 5 lectures ; bornes inversées et centre | HTTP ; ports câblés en production pour la matrice de casse (la route ne peut pas l'isoler) | A1-A5, P1 |
| `LesSeancesLuesParLaPlanificationCharacterizationIntegrationTest` | fiche du patient dans un autre centre ; nom recomposé (prénom vide, blanc, espaces) ; deux séances réalisées le même jour ; chaque centre ses séances | HTTP `GET /planning/semaine` (+ port pour le nom et le compte) | P1 |
| `LesSeancesLuesParLeDossierMedicalCharacterizationIntegrationTest` | égalité complète (identifiant décroissant, pages coupant l'égalité) ; création nulle (H2) ; total tous statuts et centre B | HTTP `GET /patients/{id}/constantes` | D1, D2 |

PostgreSQL 16 : `backend/scripts/seance-se7d-pg16-rejeu.sh` (cluster jetable, port 55548 par défaut, 5432 refusé, mot de passe `SEANCE_PG16_PASSWORD`) rejoue les 8 requêtes à l'identique sur le schéma Flyway : 15 vérifications, code 0. Confirmé : création nulle en PREMIER sur PG16 (H2 : en dernier) ; blancs de fin significatifs ; UUID comparés en octets non signés (même ordre que H2) ; `prenom` NOT NULL.

## 3. Seams
Aucun demandé, aucun appliqué (`seamAllowed` inchangé).

## 4. Bugs constatés (non corrigés)
KB-SEA-77 à KB-SEA-82 dans `known-bugs.md` (ordre intra-jour, tri annoncé faux et case non déterministe, création nulle H2/PG16, jointure au centre du patient, lien d'exclusion sans statut, casse et blancs). Observation sans KB : le Dossier médical lit pour le centre B une séance posée dans B pour un patient sans fiche dans B (pas de jointure `patients`, cohérent avec KB-MED-03).

## 5. Limites (ce qui n'est PAS verrouillé)
- Ordre entre patients d'une même date (A1) et entre deux séances du même patient le même jour (P1) : non déterministe par construction, volontairement non figé (KB-SEA-77, 78) ; seuls l'ordre des dates et l'ensemble des lignes le sont.
- Appelant optimisation de P1 : l'effet d'une séance RÉALISÉE ajoutée après le calcul (empreinte périmée, KB-PLN-95) n'est observé que par le test de domaine ; en HTTP seul le statut PLANIFIEE est rejoué (P8). Non ajouté ici : l'horizon d'optimisation est relatif à la semaine courante, incompatible avec la règle « aucune lecture de l'heure ».
- Création nulle : le golden fige H2 ; la production (PG16) est verrouillée par le script de rejeu, pas par la suite Maven.
- Concurrence (lecture pendant une validation, une exclusion) : non rejouée.
- Couverture de branches et mutation : non mesurées (PIT absent du projet, comme pour SE7c).

## 6. Exécutions
Nouveaux tests : 3 exécutions consécutives vertes (12/12 chacune), la 3e avec `-Dsurefire.runOrder=random` ; aucun `.received` restant. Suite complète `./mvnw -o test -fae` (un seul build, aucune relance) : BUILD SUCCESS, 7 609 tests exécutés selon les résumés Maven des 15 modules, 0 échec, 0 erreur, 10 ignorés. La somme des XML Surefire présents dans les `target/` donne 8 453 tests (31 ignorés) : elle inclut des rapports plus anciens qui n'ont pas été écrasés (contrats PostgreSQL lancés par des scripts de rejeu antérieurs), elle ne mesure donc pas ce build. `check-arch` : 0 violation. Rejeu PG16 : code 0.
