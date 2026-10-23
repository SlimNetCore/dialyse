---
status: built
date: 2026-10-23
slice: absences-patients-a6-cloture-mensuelle
order: 6 (A1 à A6, voir `.ddd/tactical/absences-patients.md` § 10)
sources: [.ddd/safety-net/absences-patients-a6-cloture-mensuelle.md, .ddd/tactical/absences-patients.md (MoisAbsences, A18), ADR 0009, 0013, 0024, 0037, 0042, slice F7 (.ddd/migration/slices/facturation-f7-cloture-mensuelle.md), known-bugs.md (KB-ABS-37 à 40), docs/reference/regles-de-gestion/06-absences-patients.md (RG-ABS-070 à 078)]
---

> **Nouvelle fonctionnalité, refonte sur place (ADR 0024)** : mêmes tables d'absences et mêmes URLs ; **migration
additive V44** (deux tables nouvelles, aucune ligne), pas de façade, de flag, de projection ni de
> plan de bascule par paliers. Retour arrière = commit précédent (les tables V44 restent, sans effet). Aucun seam, aucun
> fichier legacy modifié (le legacy d'absences n'existe plus depuis A4).

# A6 — Clôture mensuelle des absences

## Décisions de l'utilisateur (2026-10-23) et réalisation

| # | Décision                                                                                                                                                                                 | Réalisation                                                                                                                                                                                                                                   |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `ADMIN` seul clôture et rouvre ; motif de réouverture obligatoire ≤ 500 caractères ; clôture sans motif ; audit et événements comme F7                                                   | `MoisDAbsencesControleur` (`@PreAuthorize`), `MoisAbsences.rouvrir` ; journal SQL ; `AuditRequestFilter` trace tout `POST` ; événements `MoisDAbsencesCloture` / `MoisDAbsencesRouvert` (`PublicationDesMoisDAbsencesSpring`, sans écouteur)  |
| 2 | Clôture sans condition mais avertissement (à qualifier et en retard) ; ni mois en cours ni futur (fuseau du centre) ; pas de jours de grâce                                              | `CloturerLeMoisDAbsences` : refus 422 `MOIS_D_ABSENCES_NON_TERMINE` avant tout verrou ; comptes lus sous verrou via `LectureDesAbsences.nombreAQualifierEntre`                                                                                |
| 3 | Lien Facturation : avertissement seulement ; réouverture des absences non refusée quand la facturation est close                                                                         | champ additif `absencesDuMoisNonCloses` de la réponse de clôture de facturation (port publié `ClotureDesAbsences`, adapté par `ClotureDesAbsencesPourLaFacturation`) ; message i18n ; `RouvrirLeMoisDAbsences` ne consulte pas la Facturation |
| 4 | Détection de nuit / `POST /rattrapage-detection` : jours des mois clos ignorés, `joursIgnores` exposé (KB-ABS-37), notification sur les mois ouverts                                     | `DetectionAbsences`, `ReconciliationAbsences` (relecture du mois sous verrou), `DetectionControleur.RattrapageReponse.joursIgnores` (absent si vide), `NotificationDesAbsencesAQualifier` (inchangée : lit `moisClos`)                        |
| 5 | Rattrapage : mois de l'absence ET de la date de rattrapage ouverts ; qualifier/annuler/déclarer exigent un mois ouvert (422 + code stable) ; réinclusion refusée ; exclusion en rollback | `GardeDesMoisDAbsences` dans `DeclarerUneAbsence`, `QualifierUneAbsence`, `RattraperUneAbsence` (deux mois, ordre croissant), `AnnulerUneAbsence`, `RetirerLAbsenceDeLExclusion` ; code unique `MOIS_D_ABSENCES_CLOS` (422, propriété `mois`) |
| 6 | Remplacer `TousLesMoisSontOuverts` par `mois_absences` ; V44 ; `FOR UPDATE` ; 409 sur course ; `lock_timeout` comme A2                                                                   | `MoisAbsencesJdbc` (`MoisAbsencesRepository extends MoisDAbsencesOuverts`), `RefusDeVerrou` (partagé avec `AbsencesJdbc`), V44 + `schema.sql` ; `UniteDeTravailSpring` inchangée (5 s)                                                        |
| 7 | Écarts = goldens `*_nouveau_code` ; ancien comportement dans `_ecarts-legacy`                                                                                                            | voir « Écarts de parité »                                                                                                                                                                                                                     |

## API (noms exacts)

| Route                                                                                                                                | Rôles                                 | Réponse                                                                                                                                   | Refus                                                                                     |
|--------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `GET /api/v1/absences-patients/mois?annee=AAAA[&centerId]`                                                                           | ADMIN, SECRETAIRE, MEDECIN, INFIRMIER | 200 `{centerId, annee, mois:[12 × {mois, statut:"OUVERT"\|"CLOTURE", cloturePar, clotureLe, rouvertPar, rouvertLe, motifDeReouverture}]}` | 400 `ANNEE_INVALIDE` ; 403 centre                                                         |
| `POST /api/v1/absences-patients/mois/{AAAA-MM}/cloturer[?centerId]`                                                                  | ADMIN                                 | 200 `{centerId, mois, statut:"CLOTURE", cloturePar, clotureLe, absencesAQualifierRestantes, absencesEnRetard}`                            | 400 `MOIS_INVALIDE` ; 409 `MOIS_D_ABSENCES_DEJA_CLOS` ; 422 `MOIS_D_ABSENCES_NON_TERMINE` |
| `POST /api/v1/absences-patients/mois/{AAAA-MM}/rouvrir[?centerId]` corps `{motif}`                                                   | ADMIN                                 | 200 `{centerId, mois, statut:"OUVERT"}`                                                                                                   | 400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE`, `MOIS_INVALIDE` ; 409 `MOIS_D_ABSENCES_NON_CLOS`  |
| écritures d'absence (`POST /`, `PUT /{id}/qualification`, `/rattrapage`, `/annulation`) ; exclusion et réinclusion de la Facturation | inchangés                             | inchangées                                                                                                                                | **422 `MOIS_D_ABSENCES_CLOS`** (propriété `mois`)                                         |
| `POST /api/v1/absences-patients/rattrapage-detection`                                                                                | ADMIN                                 | + `joursIgnores:[dates]` (seulement s'il n'est pas vide)                                                                                  | —                                                                                         |
| `POST /api/v1/facturation/periodes/{mois}/cloturer`                                                                                  | ADMIN                                 | + `absencesDuMoisNonCloses: boolean` (avertissement)                                                                                      | inchangés                                                                                 |

## Fichiers (backend `contexts/absences-patients`, sauf mention)

- **Domaine** : `MoisAbsences`, `EntreeDuJournalDuMois`, `MoisAbsencesRepository`, `MoisDAbsencesOuverts` (+
  `estOuvertSousVerrou`), refus `MoisDAbsencesClos`, `MoisDAbsencesDejaClos`, `MoisDAbsencesNonClos`,
  `MoisDAbsencesNonTermine`, `MotifDeReouvertureObligatoire`. `DateOuverte.siOuverte` lit le mois sous verrou.
- **Application** : `CloturerLeMoisDAbsences`, `RouvrirLeMoisDAbsences`, `ConsulterLesMoisDAbsences`,
  `GardeDesMoisDAbsences`, port `PublicationDesMoisDAbsences` ; cinq cas d'usage d'écriture reçoivent la garde.
- **Infrastructure** : `persistence/MoisAbsencesJdbc`, `persistence/RefusDeVerrou`,
  `messaging/PublicationDesMoisDAbsencesSpring` ; supprimé : `mois/TousLesMoisSontOuverts`.
- **Interfaces** : `http/MoisDAbsencesControleur`, `http/RequeteInvalide`, refus dans `ErreursMetierDesAbsencesAdvice`,
  `facturation/ClotureDesAbsencesPourLaFacturation`.
- **Langage publié** : `MoisDAbsencesCloture`, `MoisDAbsencesRouvert`. **Facturation** (modifiée minimalement) :
  `publishedlanguage/ClotureDesAbsences`, `application/cloture/AbsencesDuMois`, ACL
  `infrastructure/acl/absences/AbsencesDuMoisPubliees`, `CloturerLeMois` (+ résultat), `ClotureReponse`.
- **Plateforme** : `db/migration/V44__absences_patients_cloture_mensuelle.sql`, `db/schema.sql`. **Scripts** :
  `backend/scripts/absences-a6-pg16-rejeu.sh`, `backend/scripts/pit-a6-module.sh`.
- **Frontend** (`frontend/src/app/features/absences`) : `mois-d-absences.component.{ts,html,css}` (écran de gestion des
  mois, intégré à l'écran des absences), `mois-d-absences.store.ts`, `mois-d-absences.util.ts` ;
  `absence-patient-api.service.ts` (3 routes, `joursIgnores`) ; lignes d'absence d'un mois clos sans action (cadenas) ;
  avertissement `FACTURATION.PERIODES.AVERTISSEMENT_ABSENCES` dans l'écran de Facturation (`facturation.store.ts`,
  `facturation-workspace.component.html`, `facturation-errors.ts`) ; clés i18n fr/en/ar/kab.

## Écarts de parité (goldens `*_nouveau_code`)

| Golden                                                                                              | Écart                                                                                                                                                              |
|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `cycle-de-vie/mois-ouvert-a6/cloture_de_facturation_avec_absence_a_qualifier_nouveau_code`          | champ additif `absencesDuMoisNonCloses` (vrai) ; ancien : `cycle-de-vie/_ecarts-legacy/mois-ouvert-a6/cloture_de_facturation_avec_absence_a_qualifier.legacy.json` |
| `facturation/cloture/nouveau_code/cloture_nominale_avertissement`, `cloture_sans_seance_avec_corps` | même champ additif (goldens F7 mis à jour, diff = une ligne)                                                                                                       |
| nouveaux : `cycle-de-vie/cloture-mensuelle-a6/*` (9) et `detection/mois-clos-a6/*` (4)              | comportement nouveau, pas de legacy                                                                                                                                |

Les sept autres scénarios du filet A6 (mois ouverts) passent sans modification de golden.

## Tests et mesures

| Niveau                                                                                                                                                                                                         | Tests                                   |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| Domaine `MoisAbsencesTest`                                                                                                                                                                                     | 11                                      |
| Application `MoisDAbsencesCloTest` (clôture, avertissement, mois non terminé, réouverture, écritures refusées, rattrapage à deux mois, exclusion/réinclusion) + 2 cas de course dans `ControleDesAbsencesTest` | 22 + 2                                  |
| Contrat `MoisAbsencesContrat` : double en mémoire / H2 / **PostgreSQL 16**                                                                                                                                     | 7 / 9 / 11                              |
| Concurrence `MoisAbsencesConcurrentContrat` : H2 / **PostgreSQL 16** (+ `lock_timeout` => `EcritureConcurrente`)                                                                                               | 4 / 5                                   |
| HTTP (Spring complet, H2) : `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest`, `DetectionSurUnMoisDAbsencesCloNouveauCodeIntegrationTest`                                                                | 9 + 5                                   |
| Facturation : avertissement (`CloturerEtRouvrirLeMoisTest`)                                                                                                                                                    | 1                                       |
| ArchUnit du module (9), `FrontieresDesContextesTest`, `TableDesAbsencesDePatientsTest`, `ReglesDeGestionDocumentationTest`, `check-arch.ts`                                                                    | verts, 0 violation                      |
| Frontend (vitest/jsdom, sans serveur) : `mois-d-absences.util.spec.ts`, `mois-d-absences.store.spec.ts`, spec du store de Facturation                                                                          | 12 nouveaux ; 57 verts avec les voisins |

- **Preuve du verrou** : retirer `FOR UPDATE` de `MoisAbsencesJdbc.verrouiller` fait échouer deux des quatre tests de
  concurrence H2 (essai du 2026-10-23, fichier restauré).
- **PIT** (`bash backend/scripts/pit-a6-module.sh`, `domain.*` et `application.*`) : 315/322 mutants tués (98 %), force
  des tests 98 % ; le mutant sans couverture restant et les six survivants sont antérieurs à A6 (A1 à A3).
- **PostgreSQL 16 jetable** (`bash backend/scripts/absences-a6-pg16-rejeu.sh 55456`) : V44 additive et idempotente,
  tables vides, 3 contraintes ; contrats A6 (16) et non-régression A1 à A4 (57) verts, code de sortie 0.
- **Build complet** `./mvnw -o test` : BUILD SUCCESS, 1997 tests de la plateforme (4 ignorés, préexistants), aucun
  échec.
- **Frontend** : `ng build --configuration development` sans erreur ni avertissement ; `ng test --watch=false` limité
  aux spécifications des absences et du store de Facturation. Aucun `ng serve`, aucun Playwright.

## Plan de bascule

Pas de bascule par paliers (ADR 0024). Déploiement : appliquer V44 (additive), contrôler `verifications-production.md` §
27 (27.2 : tables vides), puis **avant la première clôture d'un mois** exécuter 27.1 et qualifier
les absences en retard. Aucune clôture n'est faite par le déploiement. Retour arrière : commit précédent ; si des mois
ont été clos, les rouvrir (motif) avant, ou ignorer les tables.

## À valider

1. ✅ 2026-10-23 (ADR 0042 accepté) — Lecture de l'état des mois ouverte aux quatre profils d'accès (F7 : ADMIN seul) ;
   motif et identifiants réservés aux ADMIN.
2. ✅ 2026-10-23 (ADR 0042 accepté) — Un seul code `MOIS_D_ABSENCES_CLOS` pour toutes les écritures, la réinclusion et
   l'exclusion de la Facturation ; message générique « rouvrez d'abord le mois ».
3. La première clôture d'un mois fige les absences encore à qualifier (rouvrir exige un motif) ; la recette § 27.1 sert
   à les repérer avant (requête complétée d'une colonne
   `cloturable`, rejouée en local le 2026-10-23 ; **résultat de production à rapporter**).
4. Traductions arabe et kabyle : relecture technique faite le 2026-10-23 (`ar.json` : 3 clés ; `kab.json` : 6 clés, dont
   le `HINT` F7) ; **relecture par des locuteurs natifs
   toujours requise**. Constat hors A6 : en kabyle, « tamettant » (absence) signifie couramment « la mort » et «
   inelmaden » « les élèves » (`ABSENCES.TITLE`) ; tout le vocabulaire
   kabyle des absences est à faire revoir.
5. Glossaire : quatre termes `proposed` (mois terminé, avertissement de clôture, journal du mois d'absences, mois
   d'absences clos) ; questions ouvertes dans le glossaire.
6. ✅ 2026-10-23 — Documentation client régénérée (`docs/client/documentation-fonctionnelle.{html,pdf,docx}`, 568
   règles ; RG-ABS-070 à 078 et RG-FAC-040 présents).
7. Modèle tactique (`.ddd/tactical/absences-patients.md`) mis à jour le 23/10 : décision 3 du 21/10 remplacée (ADR
   0042), Q1-Q4 tranchées, canvas `MoisAbsences` et A18 alignés.

## Corrections de revue (`.ddd/verify/review-absences-a6.md`)

Modèle tactique ; détection (verrou de mois indisponible = échec journalisé, non plus « déclaration concurrente ») ;
`GET /mois` : motif et identifiants réservés aux ADMIN ; notification : un seul COUNT groupé ; code de verrou
documenté ; imports ; mémoïsation de `estPostgreSql()`.
