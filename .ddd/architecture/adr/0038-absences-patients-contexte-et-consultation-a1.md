---
status: proposed
date: 2026-10-22
deciders: utilisateur (décisions du 2026-10-21 et du 2026-10-22) ; réalise le modèle tactique `.ddd/tactical/absences-patients.md` (slice A1)
---

# ADR 0038 — Contexte Absences de patients : module, lecture A1 et écarts corrigés

## Contexte

Le modèle tactique des Absences de patients (approuvé le 2026-10-21) découpe la refonte sur place (ADR 0024) en six slices. A1 est la
consultation : liste paginée, semaine du planning, synthèse, séances de rattrapage. Le filet de caractérisation (29 tests, 66 goldens)
a révélé neuf comportements suspects (KB-ABS-01 à 09). L'utilisateur a décidé, le 2026-10-22, lesquels corriger dans A1, lesquels dans
un correctif séparé et lesquels reporter.

## Décision

1. **Un module Maven par contexte** : `backend/contexts/absences-patients` (`com.hemodialyse.backend.contexts.absencespatients`),
   mêmes couches et mêmes règles ArchUnit que Facturation. Même table `absence_patient`, mêmes URLs `/api/v1/absences-patients/**`,
   aucune migration, aucune façade ni routage ; retour arrière = commit précédent.
2. **Lecture sans agrégat** : A1 ne contient que des lectures. Port `LectureDesAbsences` (modèle de lecture, CQRS léger) et port
   `SeancesRealisees` (ACL SQL sur `seances`, Séance n'étant pas encore un contexte). L'agrégat `AbsencePatient` et la valorisation
   arrivent avec A2 ; le modèle de lecture `AbsenceSuivie` est son précurseur.
3. **Règles dans le domaine** : pagination (`Pagination`), semaine dimanche-samedi (`SemaineDuPlanning`), délai de qualification
   (`DelaiDeQualification`, 3 jours), proposition des séances de rattrapage (`SeancesDeRattrapage`). Le jour courant est celui d'UTC
   (`Clock.systemUTC()`), comme le legacy : le fuseau du centre est reporté à A2.
4. **Écarts de comportement décidés** (goldens `*_nouveau_code`) :
   - KB-ABS-01 : un paramètre illisible donne **400** `BAD_REQUEST` (au lieu de 500) sur les quatre routes ;
   - KB-ABS-02 : `page` ou `size` négatif = 400 ; `size` **plafonnée à 100** (les tailles de l'interface vont jusqu'à 100) ;
   - KB-ABS-03 : absence inconnue ou d'un autre centre = **404** `ABSENCE_INTROUVABLE` (au lieu de 422) ;
   - KB-ABS-04 : les séances de rattrapage d'une absence **rattrapée ou annulée** = **422** `ABSENCE_NON_MODIFIABLE` (code des écritures,
     RG-ABS-003) ; justifiée, non justifiée et à qualifier restent rattrapables (B7 reproduit).
5. **Correctifs séparés, livrés avant A1** (même jour, décision 3 du 2026-10-22) :
   - KB-ABS-05 : garde de centre standard (`CenterAccessGuard`) sur `/seances/dashboard`, `/dashboard/details` et `/dashboard/export` ;
   - KB-ABS-07 : « masquer par centre seulement » (décision après revue) : un centre de 1 à 4 absences est masqué dans tous ses chiffres
     d'absence et **exclu du total, des motifs consolidés et du mensuel** (`centresMasques`), pour que la différence total moins centres
     publiés ne le redonne pas ; chaque effectif par statut < 5 est aussi masqué (limite : déductible du nombre d'absences du centre).
   Statut `proposed` tant que l'utilisateur n'a pas validé les points de la fiche de slice (masquage Direction, 422 contre 409, plafond 100).
5 bis. **Nom du patient** : lu par le port `NomsDesPatients` (ACL `acl/legacypatient`, SQL sur `patients`), non plus par une jointure dans
   l'adaptateur de persistance ; le nom est ajouté à la page par le cas d'usage.
6. **Identité** : `SessionCourante` + ACL `SessionLegacy` (lecture du `UserPrincipal` par réflexion), comme Facturation, tant que le
   contexte Identité et accès n'est pas extrait. Erreurs : `ConsultationDesAbsencesAdvice` limité au contrôleur du module, au format
   `ProblemDetail` commun ; les écritures (encore au legacy) gardent leurs codes jusqu'à A2.
7. **Code legacy retiré** : les quatre routes de lecture de `AbsencePatientRestController`, `AbsencePatientService.{lister,semaine,
   seancesDeRattrapage}`, `AbsencePatientRepositoryPort.findBetween`, `AbsenceDonneesPort.{seancesRealisees,datesSeancesRealisees}`
   et leurs adaptateurs. Restent (jusqu'à A2/A3) : `synthese` (contrôle de nuit), `findPaged` (réconciliation).

## Conséquences

- Contrat : les clients qui lisaient un 500 ou un 422 sur ces cas reçoivent 400/404 ; le front gère déjà ces codes (`absencePatientErrorKey`).
- `SeanceRestController` est ajouté à `covered.json` pour la seule garde de centre (couverture partielle assumée : le fichier est repris
  par A4) ; son constructeur reçoit `CenterAccessGuard`.
- Le jour d'UTC reste la source de vérité de `enRetard` et de la semaine par défaut jusqu'à A2 (écart connu entre minuit et 1 h, heure
  d'Algérie).
- `FrontieresDesContextesTest` interdit au legacy d'utiliser les couches internes du module ; un langage publié viendra avec A2 (déclaration
  de l'exclusion) et A4 (lecture du pilotage).
