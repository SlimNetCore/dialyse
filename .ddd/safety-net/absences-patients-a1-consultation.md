---
status: proposed
date: 2026-10-22
slice: absences-patients-a1-consultation
sources: [.ddd/tactical/absences-patients.md § 10 (A1), § 11, § 12, décisions du 2026-10-21, .ddd/strategic/glossaries/absences-patients.md, safety-net facturation-f5 (méthode, harnais), lecture de AbsencePatientRestController, AbsencePatientService, AbsencePatientJdbcAdapter, AbsenceDonneesJdbcAdapter, DirectionAbsencesQueryService, SeanceRestController (dashboard, details), RoleScopeFilter, CenterAccessGuard]
---
# Filet de caractérisation — A1 consultation des absences de patients (refonte sur place, ADR 0024)

Lectures seules : aucune route de A1 n'écrit. Les écritures (déclarer, qualifier, rattraper, annuler, détecter, réconcilier) relèvent de A2/A3 et **ne sont pas verrouillées ici**.

## Périmètre exact
| Route | Classes legacy | Verrouillé |
|---|---|---|
| `GET /api/v1/absences-patients` (liste paginée : `centerId`, `statut`, `motif`, `patientId`, `from`, `to`, `page`, `size`) | `AbsencePatientRestController.lister` → `AbsencePatientService.lister` → `AbsencePatientJdbcAdapter.findPaged` (LEFT JOIN `patients`) | oui (7 tests) |
| `GET .../semaine?date` | `semaine` → `PlanningSemaineService.debutSemaine` (dimanche), `findBetween`, `AbsenceDonneesJdbcAdapter.seancesRealisees` | oui (7 tests, avec la synthèse) |
| `GET .../synthese` | `synthese` → `countAQualifier`, `countEnRetard` | oui |
| `GET .../{id}/seances-rattrapage` | `seancesDeRattrapage` → `findById`, `datesRattrapage`, `datesSeancesRealisees` | oui (4 tests) |
| Profils et garde de centre des 4 routes | `@PreAuthorize`, `RoleScopeFilter`, `CenterAccessGuard` | oui (2 tests, matrice 11 profils x 4 routes + anonyme) |
| `GET /api/v1/direction/absences` (parties non financières : statuts, motifs, masquage, mensuel, taux ; la part du CA est celle de F5) | `DirectionAbsencesQueryService` (lit `absence_patient` et `seances`) | oui |
| `GET /api/v1/seances/dashboard`, `/dashboard/export` (CSV), `/dashboard/details?kind=absence|presence` (lignes 794 et 974) | `SeanceRestController` | partie « absences » seulement |
Hors périmètre : `/seances/calendar` (ne lit pas `absence_patient`), `POST /rattrapage-detection`, `GET /mois` (n'existent pas encore), détection de nuit.

## Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security et `RoleScopeFilter` réels, **H2**), fixtures SQL à valeurs maîtrisées. **Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.**
- Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/absencespatients/consultation/` (`ConsultationAbsencesCharacterizationSupport` + 5 classes) ; `GoldenMaster` de G1 réutilisé.
- Goldens : `backend/platform/src/test/resources/characterization/absences-patients/consultation/{liste,semaine-et-synthese,seances-rattrapage,autorisations,lectures-publiees}/*.approved.json` (66 fichiers).
- **Dates (aucune date réelle, aucune dépendance au jour de la semaine d'exécution)** : (1) alias relatifs au jour d'exécution UTC, `<J>`, `<J-4>`, `<J+2>` (horloge du legacy : `Clock.systemUTC`), pour le retard de qualification, la plage des séances de rattrapage et la semaine par défaut ; (2) alias `<ANCRE+n>` pour les routes qui ont besoin d'un jour de semaine connu ou d'un mois entier : l'ancre est le **dimanche 2026-03-01**, fixe et passé ; le champ `weekday` des détails et le début de semaine sont donc déterministes. Les paramètres `year=2026&month=3` (et 2, 5) sont des entrées, pas des dates d'exécution ; `periodStart` des mois 2 et 5 apparaît en clair dans deux goldens. Vérifié : le jour a changé entre la génération (21/10) et la 1re exécution de contrôle (22/10), goldens inchangés.
- Identifiants : tous nommés (`<ABS_nn>`, `<P_AMRANI>`, `<CENTRE_A>`, `<SEANCE_nn>`), aucun `<UUID_GENERE_n>` hors des identifiants volontairement inconnus ; horodatages -> `<HORODATAGE>` ; `generatedAt` masqué.
- Sans `ORDER BY` complet côté legacy : l'ordre des séances réalisées d'un même jour (`semaine`) n'est pas verrouillé ; les jeux de données n'ont aucun ex aequo sur cette liste.

## Scénarios (29 tests, 5 classes)
| Classe | Tests | Scénarios |
|---|---|---|
| `ListeDesAbsencesCharacterizationIntegrationTest` | 7 | 12 absences du centre A (tous statuts, motifs, sources, bornes J-3/J-4, date future, prénom blanc, patient absent, patient d'un autre centre) : tri date décroissante puis id, nom du patient ; centre B et centre vide ; 22 combinaisons de filtres (statut, motif, patient, `from`/`to` inclusifs, `from > to`, filtres vides, combinaisons incohérentes) ; pagination (défaut, découpage, hors bornes, `size` 0 / 1000 / -1, `page` -1, non numériques) ; 6 paramètres illisibles ; garde de centre ; indicateur `enRetard` |
| `SuiviDeLaSemaineEtSyntheseCharacterizationIntegrationTest` | 7 | semaine dimanche -> samedi (3 jours demandés, même résultat), semaines voisines disjointes, centre B isolé, centre vide, sans `date` (jour UTC), `date` illisible, centre étranger ; absences annulées exclues, rattrapées incluses, séances `CREE`/`ABSENT` exclues, doublons de séance fusionnés ; synthèse (6 à qualifier / 3 en retard), centres B et vide, borne J-3 / J-4 |
| `SeancesDeRattrapageCharacterizationIntegrationTest` | 4 | séances proposées (après la séance manquée, jusqu'à aujourd'hui inclus, hors `CREE`/`ABSENT`, ordre croissant) ; séance déjà utilisée par une rattrapée exclue ; annulée avec `date_rattrapage` ne réserve pas ; absence du jour / future / sans séance ; inconnue, autre centre, id non UUID, centre étranger |
| `AutorisationsDesLecturesDAbsencesCharacterizationIntegrationTest` | 2 | 11 profils + anonyme x 4 routes ; formes des refus (`ROLE_SCOPE`, `ACCESS_DENIED`, anonyme vide) |
| `LecturesDeAbsencesParLaDirectionEtLaSeanceCharacterizationIntegrationTest` | 9 | Direction (fév.-avr. 2026 : statuts, motifs, masquage < 5, mensuel, taux, valorisation hors rattrapées ; mars seul ; un jour ; période vide ; défaut annuel) ; tableau de bord Séance (centres A et B, mois vide, février, centre étranger sans garde, CSV, paramètres invalides) ; détails absence/présence (tri, patient inconnu écarté, type normalisé, type inconnu/absent) ; profils |

## Mesures
- **Suite** : 3 exécutions consécutives vertes, **29/29** (la 1re en ordre par défaut, les 2 autres avec `MethodOrderer$Random`), 0 flaky. ≈ 1 min par exécution (≈ 40 s de démarrage Spring).
- **Mutation PIT 1.30.0** (`backend/scripts/pit-a1-consultation.sh`, `--mutators DEFAULTS`, tests = les 5 classes) sur `AbsencePatientJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `DirectionAbsencesQueryService` : **101 mutants, 67 tués (66 %)**, force des tests **89 %** (67 / 75 mutants couverts), couverture de lignes des classes 80 %. 26 « sans couverture » : `save` / `findByPatientAndDate` / `timestamp` (écritures) et `AbsenceDonneesJdbcAdapter` (détection : `patientsAttendusSansSeance`, `seanceRealisee`, `periodeFacturee`, `nomPatient`, `centresActifs`), tous relevant de A2/A3. 8 survivants, tous équivalents ou couverts ailleurs : gardes `n == null` de `COUNT(*)` (`countAQualifier`, `countEnRetard`, `findPaged` : jamais nul), `nomComplet` prénom nul (colonne `NOT NULL`), gardes de période et `Map.forEach` du CA de `DirectionAbsencesQueryService.absences` (validés par les goldens F5 `LecturesFinancieresDirection…`, hors cible de ce run).
- **Couverture de branches** : JaCoCo n'est pas configuré (comme G1 à F7). Exercés par un scénario nommé : chaque filtre de `findPaged`, chaque borne de `countEnRetard` et de `seancesDeRattrapage`, la semaine (bornes incluses/exclues), les deux requêtes `absence_patient` du `SeanceRestController`, les trois branches de l'agrégation Direction (statut, motif, masquage).

## Seams
Aucun appliqué ni nécessaire ; `seamAllowed` inchangé (vide). Un seam d'horloge pour `AbsencePatientRestController` / `SeanceRestController` (jour UTC) permettrait de supprimer la dépendance au jour d'exécution : non demandé (alias relatifs suffisants).

## Fichiers protégés (`covered.json`, ajoutés)
`AbsencePatientRestController`, `AbsencePatientService`, `AbsencePatientJdbcAdapter`, `domain/absence/port/{AbsencePatientRepositoryPort,AbsenceDonneesPort}`, `domain/absence/model/{AbsenceFiltre,AbsenceLigne}`. Déjà couverts par des slices précédentes : `DirectionAbsencesQueryService`, `AbsenceDonneesJdbcAdapter`.
**Couverture partielle (honnêteté)** : seules les routes de lecture sont verrouillées ; le contrôleur et le service contiennent aussi les écritures (A2/A3), non verrouillées ; toute modification d'une écriture dans ces fichiers doit attendre A2/A3. `SeanceRestController` **n'est pas ajouté** (fichier de 1 000+ lignes dont seules deux requêtes sur `absence_patient` sont verrouillées ; décision pour A4). Non ajoutés non plus : `AbsencePatient`, `ValeurAbsence`, enums (hydratation seulement ; verrouillés par A2).

## Après la slice A1 (2026-10-22)
`SeanceRestController` a été ajouté à `covered.json` pour le seul correctif KB-ABS-05 (garde de centre sur trois routes), sur décision de l'utilisateur ; la couverture reste partielle (deux requêtes `absence_patient` et la garde), le fichier est repris par A4. 19 goldens `*_nouveau_code` remplacent les goldens caducs (liste dans `.ddd/migration/slices/absences-patients-a1-consultation.md`).

## Bugs connus
KB-ABS-01 à 09 (`known-bugs.md`) : paramètres illisibles en 500, pagination sans plafond et négative en 500, 422 au lieu de 404, séances de rattrapage sans contrôle de statut, aucune garde de centre sur le tableau de bord de la Séance, tableau de bord / détails / Direction qui comptent différemment, **k-anonymat non appliqué aux `stats` de la Direction**, fuseau UTC, observations de lecture.

## Non verrouillé (limites)
- Toutes les **écritures** et la détection / réconciliation de nuit (A2, A3), `POST /rattrapage-detection`, notifications.
- Rendu du tableau de bord Séance **hors absences** (séances prévues avec programme de dialyse non vide, jours fermés, âge, sexe : le jeu n'a aucun jour programmé, donc `expectedSeances = 0`), exports PDF et XLSX, `/seances/calendar`.
- Parties financières de `/direction/absences` (`caHt`, `partCaHt` = null ici, vérifiées par F5) ; masquage de la valorisation d'un motif au-delà du jeu.
- Format de date côté client (`yyyy-MM-dd` uniquement), `Instant` (`declareeLe`, `qualifieeLe`) : seule la présence d'un horodatage ISO est verrouillée (valeur masquée).
- **PostgreSQL** : ordre des UUID à égalité de date, `LIMIT/OFFSET` négatifs, `EXTRACT` du mensuel, échelle des `NUMERIC` : non testés (H2 seulement ; rejeu PG 16 à prévoir).
- Performance, concurrence (lecture pendant une écriture), front (`absences-patients` du planning, tableau de bord) : non observés.
- Fuseau du serveur : les alias relatifs supposent `LocalDate.now(UTC)` du test = celui du code ; un test lancé à cheval sur minuit UTC peut diverger.

## Lecteurs de `absence_patient` (liste exacte)
| Lecteur | Filet |
|---|---|
| `AbsencePatientJdbcAdapter` (`findPaged`, `findBetween`, `findById`, `findByPatientAndDate`, `datesRattrapage`, `countAQualifier`, `countEnRetard`) | A1 (sauf `findByPatientAndDate`, `save` : écritures A2) |
| `DirectionAbsencesQueryService` (agrégat par statut et motif, mensuel) | A1 (+ F5 financier) |
| `SeanceRestController.buildSeanceDashboard` (l.794), `buildSeanceDashboardDetails` (l.974) | A1 |
| `AbsencesDesPatientsLegacy` (ACL Facturation : `declarer`, `retirer`) | F-slices (`PeriodeFactureeAbsences…`, exclusion) ; A2 |
| `FacturationRestController` / `AbsenceDonneesJdbcAdapter.periodeFacturee` (lit `factures`) | A2/A3 |

## Questions de décision pour la conception sur place de A1
1. **Où vit la lecture ?** Proposition : read model `LectureDesAbsences` du contexte `absences-patients` (port de lecture `liste`, `semaine`, `synthese`, `seancesDeRattrapage`), même URL, même table, jointures SQL sur `patients` / `seances` isolées dans un adaptateur ; le contrôleur de lecture passe dans le contexte.
2. **Codes HTTP des paramètres illisibles (KB-ABS-01/02/03)** : reproduire le 500 (parité stricte du golden) ou passer à 400 / 404 dès A1 (écart décidé, goldens `_nouveau_code`) ? Plafond de `size` ?
3. **KB-ABS-07 (k-anonymat des `stats` de la Direction)** et **KB-ABS-05 (garde de centre du tableau de bord de la Séance)** : à corriger (A4) ou à reproduire ?
4. **KB-ABS-04** : la liste des séances de rattrapage doit-elle écarter une absence annulée / non modifiable (cohérence avec B1 et la décision 5) ?
5. **Fuseau (décision 7)** : la semaine par défaut et `enRetard` passent-ils au fuseau du centre dès A1 (écart de comportement aux alentours de minuit) ou en A2 avec le reste ?
6. **`SeanceRestController`** : ajouter le fichier à `covered.json` pour A4 (protection large mais partielle) ou extraire d'abord les deux requêtes derrière un port par seam mécanique (accord requis) ?
