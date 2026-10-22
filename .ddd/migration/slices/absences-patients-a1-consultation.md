---
status: built
date: 2026-10-22
slice: absences-patients-a1-consultation
order: 1 (A1 à A6, voir `.ddd/tactical/absences-patients.md` § 10)
sources: [.ddd/tactical/absences-patients.md (§ 10 A1, décisions du 2026-10-21), .ddd/safety-net/absences-patients-a1-consultation.md, known-bugs.md (KB-ABS-01 à 09), ADR 0024, 0038, docs/reference/regles-de-gestion/06-absences-patients.md (RG-ABS-021, 023, 043, 050 à 053), 13-direction.md (RG-DIR-012), 07-seances.md (RG-SEA-051)]
---

> **Refonte sur place (ADR 0024)** : même table `absence_patient`, mêmes URLs, mêmes noms JSON ; **aucune migration** ; retour arrière =
> commit précédent. Pas de façade, de routage, de projection ni de métriques (donc pas de flag ni de plan de bascule par paliers).

# A1 — Consultation du suivi des absences de patients

**Comportement observable** : le personnel (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER) consulte la liste paginée et filtrable des absences de son
centre, le suivi de la semaine du planning, la synthèse « à qualifier / en retard » et les séances qui peuvent rattraper une absence.
Lectures seules : aucune route de A1 n'écrit.

| Route | Nouveau code |
|---|---|
| `GET /api/v1/absences-patients` | `AbsencesControleur.lister` -> `ListerLesAbsences` -> `LectureDesAbsences` (JDBC) |
| `GET .../semaine?date` | `SuivreLaSemaine` -> `LectureDesAbsences` + `SeancesRealisees` |
| `GET .../synthese` | `ObtenirLaSynthese` |
| `GET .../{id}/seances-rattrapage` | `ListerLesSeancesDeRattrapage` (+ domaine `SeancesDeRattrapage`) |

Tables lues : `absence_patient`, `patients` (nom), `seances` (réalisées). Les écritures (déclaration, qualification, rattrapage, annulation,
détection d'une période) restent dans le legacy, sur les mêmes URLs, jusqu'à A2/A3.

## Décisions de l'utilisateur (2026-10-22) et réalisation

| KB / sujet | Décision | Réalisation |
|---|---|---|
| KB-ABS-01 | Corriger : paramètre illisible = 400 | `ConsultationDesAbsencesAdvice` (400 `BAD_REQUEST`, `MethodArgumentTypeMismatchException`) |
| KB-ABS-02 | Corriger : négatifs = 400 ; plafonner `size` | VO `Pagination` : `page`/`size` < 0 = 400 ; **`size` plafonnée à 100** (la taille effective est renvoyée) ; `size = 0` conservé |
| KB-ABS-03 | Corriger : absence inconnue / d'un autre centre = 404 | `AbsenceIntrouvable` -> 404 `ABSENCE_INTROUVABLE` |
| KB-ABS-04 | Écarter les absences annulées ou rattrapées | `SeancesDeRattrapage.exigerRattrapable` -> **422 `ABSENCE_NON_MODIFIABLE`** (code des écritures, RG-ABS-003) ; justifiée / non justifiée / à qualifier restent rattrapables (B7) |
| KB-ABS-05 | Corriger avant A1 (garde de centre) | `SeanceRestController` : `CenterAccessGuard.requireCenter` sur `/dashboard`, `/dashboard/details`, `/dashboard/export` (403) |
| KB-ABS-07 | Corriger avant A1 (k-anonymat des `stats`) | `DirectionAbsencesQueryService` : « masquer par centre seulement » (décision après revue) : centre de 1 à 4 absences masqué (chiffres dérivés `null`) **et exclu du total, des motifs consolidés et du mensuel** ; `centresMasques` publié |
| Fuseau | Pas dans A1 (A2) | jour UTC conservé (`Clock.systemUTC()`), goldens inchangés |
| `SeanceRestController` | Pas de seam (A4) | aucun seam ; fichier ajouté à `covered.json` pour la seule garde de centre |

## État au 2026-10-22 : `built`

### Réalisation

**Contexte `absences-patients`** (`backend/contexts/absences-patients`, module Maven `contexts-absences-patients`, ADR 0038) :

- domaine : `Identifiants` (`CentreId`, `PatientId`, `AbsenceId`), `StatutAbsence`, `MotifAbsence`, `SourceAbsence`, `AbsenceSuivie` (modèle de
  lecture), `FiltreDesAbsences`, `Pagination` / `PaginationInvalide`, `SemaineDuPlanning`, `DelaiDeQualification`, `SeancesDeRattrapage`,
  `SyntheseDesAbsences`, `SeanceRealisee`, `NomDuPatient`, `PageDeResultats`, `AbsenceListee`, `AbsenceIntrouvable`, `AbsenceNonModifiable` ;
- application : `ListerLesAbsences`, `SuivreLaSemaine`, `ObtenirLaSynthese`, `ListerLesSeancesDeRattrapage`, ports sortants `LectureDesAbsences`,
  `SeancesRealisees`, `SessionCourante` ;
- infrastructure : `persistence/LectureDesAbsencesJdbc` (table `absence_patient` seule), `acl/legacyseance/SeancesRealiseesJdbc`,
  `acl/legacypatient/NomsDesPatientsJdbc` (nom du patient, port `NomsDesPatients`, ex-jointure), `acl/legacyidentite/SessionLegacy` ;
- interface : `AbsencesControleur` (+ `AbsenceReponse`, `PageDAbsencesReponse`, `SuiviDeLaSemaineReponse`, `SyntheseReponse`,
  `ConsultationDesAbsencesAdvice`) ; composition : `AbsencesPatientsContexteConfiguration` (beans préfixés `absencesPatients`).

**Retiré du legacy** : routes `lister`, `semaine`, `synthese`, `seancesDeRattrapage` d'`AbsencePatientRestController` (+ `SyntheseResponse`) ;
`AbsencePatientService.lister` / `semaine` / `seancesDeRattrapage` (+ `SuiviSemaine`, `AbsenceSemaine`) ; `AbsencePatientRepositoryPort.findBetween` ;
`AbsenceDonneesPort.seancesRealisees` / `datesSeancesRealisees` (+ `SeanceRealisee`) et leurs adaptateurs. **Conservés pour A2/A3** :
`AbsencePatientService.synthese` (contrôle de nuit et notification), `findPaged` (réconciliation), `datesRattrapage` (rattrapage).

**Correctifs séparés** (livrés avant la slice) : `SeanceRestController` (garde de centre, constructeur + `CenterAccessGuard`), `DirectionAbsencesQueryService`
(`AbsencesStats` à champs nullables), frontend `direction-api.service.ts` (types nullables) et `direction-dashboard.component.html` (`headcount`, tiret).

### Parité

Les 29 tests de caractérisation A1 tournent contre le nouveau code. **Goldens inchangés (sauf écarts décidés)** : vérifié par comparaison de
l'arborescence avant / après (`diff -r` : seuls des fichiers `*_nouveau_code` ajoutés, aucun golden conservé réécrit). **20 goldens
`*_nouveau_code`** remplacent 20 goldens caducs (la comparaison `diff -r` a été refaite après la reprise du masquage Direction : seuls les goldens Direction ont changé) :

- A1 (13) : `liste/parametre_{statut_inconnu,statut_minuscule,motif_inconnu,patient_non_uuid,date_illisible,centre_non_uuid}`, `liste/pagination`
  (page=-1, size=-1 : 400 ; size=1000 : taille 100 ; page/size non numériques : 400) ; `seances-rattrapage/absence_inconnue`,
  `absence_d_un_autre_centre` (404), `identifiant_non_uuid` (400), `absence_rattrapee_ne_propose_pas_sa_propre_date`,
  `absence_annulee_est_proposee_comme_une_autre` (422) ; `semaine-et-synthese/semaine_date_illisible` (400). La réservation d'une séance par
  une rattrapée et la non-réservation par une annulée restent verrouillées par `absence_a_qualifier_du_patient_avec_seances` (inchangé).
- Correctifs KB-05/07 (7) : `lectures-publiees/direction_absences_{fevrier_avril,mars_seul,un_jour,periode_sans_absence}_nouveau_code` (stats masquées, centre masqué
  exclu du total : p. ex. février-avril, total 19 -> 18 absences, valorisation 1 910 -> 1 860, `centresMasques: 1` ; `periode_sans_absence` : seul `centresMasques: 0` s'ajoute),
  `tableau_de_bord_centre_b_lu_par_l_administrateur_du_centre_a_nouveau_code`, `details_centre_b_lus_par_l_administrateur_du_centre_a_nouveau_code`,
  `export_centre_b_lu_par_l_administrateur_du_centre_a_nouveau_code` (403).
- Filet F5 (2 caducs + 1 ajouté) : `facturation/pilotage/lectures-direction/absences_part_du_ca_fevrier_2026` -> `_nouveau_code` (2 absences : masqué, exclu du total) ;
  `absences_societe_sans_centre` -> `_nouveau_code` (seul `centresMasques: 0` s'ajoute) ;
  `absences_part_du_ca_au_dela_du_seuil` ajouté (5 absences comptabilisées : part du CA 10,41 % = 250,00 / 2 400,50, vérifiée à la main ; effectifs par statut < 5 masqués).

### Tests

| Niveau | Tests |
|---|---|
| Domaine (`RegleDeSuiviDesAbsencesTest`) ; ACL de session (`SessionLegacyTest`) | 21 ; 5 |
| Application (`ConsultationDuSuiviTest`, double `SuiviEnMemoire`) | 12 |
| Contrat des lectures : double en mémoire / H2 / **PostgreSQL 16** (`LecturesDuSuiviContrat`, y compris `NomsDesPatients`) | 12 / 12 / 13 |
| ArchUnit du module (`ArchitectureHexagonaleTest`) + `FrontieresDesContextesTest` (règle ajoutée dans `platform`) | 9 + 1 |
| Caractérisation / parité HTTP (5 classes) | 29 (+ 1 test F5 étendu d'un second scénario) |
| Tests legacy adaptés | `AbsencePatientIntegrationTest` (lectures retirées ; tableau de bord avec session du centre ; stats masquées), `AbsencePatientRestControllerTest` (2 tests de lecture retirés), `SeanceRestControllerTest`, `SeanceScanValidationControllerTest` (constructeur) |
| Frontend | `direction-dashboard.component.spec.ts` : 2 tests (masqué, au-delà du seuil) ; 64 tests direction verts |

**Résultats (2026-10-22)** : `./mvnw -o test` complet vert (reactor entier : 1856 tests dans `platform` dont 4 ignorés, 64 dans
`contexts/absences-patients`, facturation, comptabilité, GMAO verts), `check-arch.ts` 0 violation ; rejeu PostgreSQL 16
(`bash backend/scripts/absences-pg16-rejeu.sh 55448`, cluster jetable, port non standard, auto-détruit) : `LecturesDuSuiviPostgresContractIT` 12/12 ;
frontend : 64 tests du dossier `direction` verts (dont 2 ajoutés). Deux builds Maven n'ont jamais été lancés en parallèle.

### Mesures

- **Mutation PIT 1.30.0** (`bash backend/scripts/pit-a1-module.sh`, `domain.*` et `application.*`, tests unitaires, cas d'usage et contrat contre le
  double) : **54 mutants tués sur 55 (98 %)**, force des tests 98 %, couverture de lignes 92 % (97/105). Le survivant est équivalent
  (valeur de retour d'un utilitaire de validation d'identifiant, non utilisée). Cible ≥ 70 % atteinte.
- **Mutation du filet HTTP** (`pit-a1-consultation.sh`, 66 %) : cible historique, classes legacy retirées ; non rejouée.

### Écarts de comportement (nouveau code)

1. Paramètres illisibles de `/absences-patients/**` : 500 -> **400** `BAD_REQUEST` ; `page`/`size` négatifs : 500 -> **400** ; `size` > 100 : plafonnée à 100.
2. Absence inconnue ou d'un autre centre (séances de rattrapage) : 422 -> **404** `ABSENCE_INTROUVABLE`.
3. Séances de rattrapage d'une absence rattrapée ou annulée : 200 (liste) -> **422** `ABSENCE_NON_MODIFIABLE`.
4. Tableau de bord Séance, détails, export : centre autre que celui de la session : 200 -> **403** `ACCESS_DENIED`.
5. Direction (décision « masquer par centre seulement », après la revue) : un centre de 1 à 4 absences comptabilisées est masqué dans tous ses chiffres
   d'absence (nombre, répartition par statut, valorisation HT/TTC, taux, part du CA) ; chaque effectif par statut < 5 est masqué ; **le centre masqué est exclu du
   total, des motifs consolidés et du mensuel** (`centresMasques` + note à l'écran), donc la différence total - centres ne le redonne plus. Le total ne porte donc
   que sur les centres publiés (ses séances et son CA aussi). Limite : un effectif par statut masqué d'un centre publié se déduit du nombre d'absences du centre.

### Points à valider

1. **Direction** : le total qui n'inclut plus les centres masqués (point 5), la limite connue et l'affichage « < 5 » / « — » / note des centres masqués.
2. **Code 422 `ABSENCE_NON_MODIFIABLE`** pour les séances de rattrapage d'une absence rattrapée/annulée (alternative : 409).
3. **Plafond 100** de `size` (aligné sur les tailles proposées par l'interface).
4. `SeanceRestController` : garde de centre posée sur 3 routes ; `/seances/calendar` et les autres routes à `centerId` libre restent sans garde (hors périmètre).
5. Documentation fonctionnelle / client à régénérer à partir du référentiel (règles RG-ABS-053, RG-DIR-012, RG-SEA-051 nouvelles ; RG-ABS-021/023/050 amendées).

### Plan de bascule

Refonte sur place (ADR 0024) : mêmes URL, pas de façade ni de flag ; retour arrière = commit précédent (aucune migration).
**Frontend** : aucune modification nécessaire pour les 400/404/422 (le store lit déjà `err.error.code` ; `ABSENCE_INTROUVABLE` et
`ABSENCE_NON_MODIFIABLE` sont des codes connus ; les actions ne sont offertes que sur une absence modifiable ; les tailles de page proposées sont ≤ 100).
Les stats de la Direction peuvent être `null` : types et gabarit adaptés.
**Déploiement** : aucun contrôle de données (lectures seules) ; vérifier à la première lecture que le tableau de bord de la Direction affiche « < 5 » pour un petit centre.

### Risques et dette acceptés

- **Minuit UTC** : les goldens à dates relatives (`<J-n>`) calculent « aujourd'hui » dans le test et dans le code par deux horloges UTC distinctes ; une
  exécution à cheval sur minuit UTC peut échouer. Accepté (pas de refonte) ; une horloge injectable arrive avec A2.
- **Duplication de la règle du délai de qualification** (3 jours) entre le legacy (`AbsencePatientService.synthese`, contrôle de nuit) et `DelaiDeQualification` :
  acceptée jusqu'à A3, qui retire la synthèse du legacy.
- **`SessionLegacy`** : une copie par contexte (Facturation, Comptabilité, Absences), chacune liée au port de son contexte ; les unifier casserait les
  frontières. Un test unitaire couvre celle des Absences (`SessionLegacyTest`) ; la suppression viendra avec l'extraction du contexte Identité et accès.
- Filet de caractérisation et module non versionnés (revue : commiter d'abord le filet vert sur le legacy) : décision de commit laissée à l'utilisateur.

### Reste à faire (slices suivantes)

A2 cycle de vie et valorisation (fuseau du centre, ACL `AbsencesDesPatientsLegacy`), A3 détection de nuit (retire `synthese` et `findPaged` du legacy), A4 lectures
publiées (`SeanceRestController`, Direction par un port publié ; alignement du taux d'absentéisme, KB-ABS-06), A5 retrait du legacy, A6 clôture mensuelle.
