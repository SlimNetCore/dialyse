---
status: built
date: 2026-10-23
slice: absences-patients-a4-lectures-publiees
order: 4 (A1 à A6, voir `.ddd/tactical/absences-patients.md` § 10)
sources: [.ddd/safety-net/absences-patients-a4-lectures-publiees.md, known-bugs.md (KB-ABS-33 à 36), seams.md, ADR 0024, 0041, docs/reference/regles-de-gestion/06-absences-patients.md (RG-ABS-060 à 062)]
---

> **Refonte sur place (ADR 0024)** : même table `absence_patient`, mêmes URLs, mêmes noms JSON ; aucune migration, pas de façade ni de flag.
> Retour arrière = commit précédent.

# A4 — Lectures publiées des absences pour le pilotage (et fin du legacy, A5 soldé)

## Réalisation

- **publishedlanguage** : `AbsencesDuPilotage` (`aujourdhui`, `nombreHorsAnnulees`, `horsAnnulees`, `cumulParCentre`, `mensuel`).
- **infrastructure** : `persistence/AbsencesDuPilotageJdbc` (4 SQL repris à l'identique ; fuseau et horloge injectés) ; bean dans `AbsencesPatientsContexteConfiguration`.
- **Seam (accordé le 23/10, `seams.md`, `seamAllowed`)** : paramètre de constructeur `AbsencesDuPilotage` dans `SeanceRestController` (nombre du mois ; branche absence du détail) et
  `DirectionAbsencesQueryService` (cumul par centre-statut-motif ; mensuel). Les décisions KB-ABS-33 à 36 ont ajouté dans les mêmes fichiers : « aujourd'hui » du pilotage, borne des absences et fin de période (tableau de bord et détail),
  validation du `format` d'export. Nouveau fichier `platform/.../infrastructure/web/DatesIllisiblesDeLaDirectionAdvice` (400 limité à `GET /api/v1/direction/absences`). Les tests de `SeanceRestController` (2 classes) passent le nouveau paramètre.

## Écarts de parité (décisions du 23/10, goldens `*_nouveau_code`, ancien comportement dans `consultation/_ecarts-legacy/lectures-publiees-a4/*.legacy.json`)

| Golden | Décision |
|---|---|
| `direction_controle_de_la_periode_nouveau_code` (date illisible : 400, était 500) | KB-ABS-33 corrigé |
| `tableau_de_bord_mois_futur_nouveau_code`, `details_absences_mois_futur_nouveau_code` (0 absence, `periodEnd = periodStart`, plus de valeur volatile) | KB-ABS-35 corrigé |
| `export_formats_nouveau_code` (`format=txt` : 400) | KB-ABS-36 corrigé |
| `direction_periode_future_nouveau_code` (Direction bornée à aujourd'hui quel que soit `to`, ancien comportement : `direction_periode_future.legacy.json`) | KB-ABS-35 étendu à la Direction (décision 23/10) |
| KB-ABS-34 : « aujourd'hui » au fuseau du centre ; test A1 `sans_dates_la_direction_prend_l_annee_en_cours_jusqu_a_aujourd_hui_du_fuseau_du_centre` encadré par deux lectures (plus fragile à minuit) | pas de golden (valeur volatile) |

## Tests et mesures

| Niveau | Tests |
|---|---|
| Contrat `AbsencesDuPilotageContrat` (6) : double en mémoire / H2 (PostgreSQL) / **PostgreSQL 16** | 6 + 6 + 6 |
| Fuseau de `aujourdhui()` (`AujourdhuiDuPilotageTest`) | 2 |
| Filet A4 (7 tests, 9 goldens dont 4 `_nouveau_code`) et A1 lectures publiées | verts hors écarts décidés |
| ArchUnit du module (9), `check-arch.ts` | 0 violation |
| `TableDesAbsencesDePatientsTest` (aucun source hors contexte ne cite `absence_patient`), `FrontieresDesContextesTest` | verts |

Rejeu PostgreSQL 16 jetable (`bash backend/scripts/absences-a4-pg16-rejeu.sh 55470`) : 57/57. Build complet `./mvnw -o test` : BUILD SUCCESS, 1971 tests plateforme verts (4 ignorés, préexistants).

## État des lieux A5 (retrait final du legacy)

Soldé en grande partie par A3 (voir `absences-patients-a3-detection-de-nuit.md`) : après A4, `grep absence_patient` dans `platform/src/main`, `shared-kernel` et `bc-*` ne trouve plus rien
(hors migrations et `schema.sql`). Plus de contrôleur, service, port ni adaptateur d'absence de patient dans le legacy. Reliquat : (1) la table et ses migrations (propriété du contexte, à garder) ;
(2) les tests de caractérisation A1 à A4 restent comme filet de non-régression (ne sont supprimés qu'avec le legacy qu'ils décrivent : à décider, ils testent maintenant le nouveau code) ; (3) `TousLesMoisSontOuverts` (A6) ;
(4) `AbsenceInfirmier` / `TypeAbsence` (absences d'infirmiers) sont un autre contexte (Infirmiers), hors périmètre ; (5) documentation client à régénérer (RG-ABS-060 à 062).

## Points à valider

1. Retouches ponctuelles hors du strict remplacement SQL dans `SeanceRestController` / `DirectionAbsencesQueryService` (conséquence des décisions KB-ABS-34, 35, 36) et nouveau fichier `DatesIllisiblesDeLaDirectionAdvice` classé en `seamAllowed`.
2. KB-ABS-35 va plus loin que le mois futur : dans le mois en cours, les absences datées après aujourd'hui ne sont plus comptées (tableau de bord, détail, exports **et Direction**, quel que soit `to`). Test unitaire à port simulé `BorneDesAbsencesDuTableauDeBordTest` (3) ; recette SELECT-only : `verifications-production.md` § 26.
3. `format=` vide ne change pas : Spring le remplace par la valeur par défaut (csv), comme le paramètre absent (cas ajouté au golden `export_formats_nouveau_code`). Un format inconnu autre est refusé en 400, y compris pour d'anciens clients qui envoyaient une valeur libre.
4. Le conseil de 400 ne couvre que la route `/api/v1/direction/absences` (reconnue par son chemin) ; toute autre route délègue à `ApiExceptionHandler` (500 inchangé, cas ajouté au golden). Il est sorti de `acl/` : `infrastructure/web/DatesIllisiblesDeLaDirectionAdvice`.
5. Glossaire : « absences du pilotage », « aujourd'hui du pilotage » (proposed).
