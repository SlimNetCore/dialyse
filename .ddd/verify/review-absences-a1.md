---
status: proposed
date: 2026-10-22
scope: slice absences-patients-a1-consultation + correctifs KB-ABS-05 / KB-ABS-07 (non commités, branche feat/gmao-ddd-sur-place)
sources: [git status/diff, check-arch.ts, state.ts show, scan.ts --incremental, .ddd/migration/slices/absences-patients-a1-consultation.md, ADR 0038, known-bugs.md, glossaries/absences-patients.md, code cité ci-dessous]
---

# Revue indépendante : A1 Consultation des absences + KB-ABS-05/07

## Contrôles automatiques
- `check-arch.ts` : 0 violation (0 baseline, 0 nouvelle).
- `scan.ts --incremental` : 0 cycle entre modules. Le scan a réécrit `.ddd/scan/{files,graph}.json` et `summary.md` (effet du contrôle demandé).
- `state.ts show` : slice `built` ; contexte `absences-patients` toujours `modeled` (Facturation et GMAO sont `architected`).
- Aucun build ni test Maven lancé (consigne). Les résultats cités dans la fiche de slice n'ont pas été rejoués.

## Écarts décidés : vérifiés conformes
| Écart | Preuve |
|---|---|
| 400 sur paramètre illisible (4 routes seulement) | `ConsultationDesAbsencesAdvice.java:22,33-41` (`assignableTypes = AbsencesControleur`) |
| `page`/`size` < 0 → 400 ; `size` plafonnée à 100 ; `size = 0` conservé | `Pagination.java:16-20` |
| Absence inconnue ou d'un autre centre → 404 | `ListerLesSeancesDeRattrapage.java:36`, `LectureDesAbsencesJdbc.java:122` (filtre `center_id`) |
| KB-04 : rattrapée/annulée → 422 `ABSENCE_NON_MODIFIABLE` | `StatutAbsence.java:26-28`, cohérent avec le legacy `AbsencePatient.exigerModifiable` (bc-seance `AbsencePatient.java:177-180`) |
| KB-05 : garde de centre sur `/dashboard`, `/details`, `/export` | `SeanceRestController.java:648,658,668` |
| KB-07 : stats masquées de 1 à 4 | `DirectionAbsencesQueryService.java:68-74` |
| Parité des lectures : mêmes requêtes SQL, même calcul du retard (`d+3 < J` ⇔ `d < J-3`), même tri | `LectureDesAbsencesJdbc.java:85-131` vs l'ancien `AbsencePatientJdbcAdapter` ; `DelaiDeQualification.java` ; `SeancesRealiseesJdbc.java:20` = `SEANCE_REALISEE` du legacy |
| Frontières | domaine et application sans import de framework ; module sans dépendance Maven vers le legacy ; règle ArchUnit `le_legacy_n_utilise_pas_le_contexte_des_absences_de_patients` |
| Consommateurs de `AbsencesStats` (`long` → `Long`) | aucun autre consommateur backend ; front adapté (`direction-dashboard.component.html:600,608,636-642`) |

## Constats
| Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| **major** (violation de l'objectif annoncé) | stratégique / sécurité des données | **KB-ABS-07 n'est pas corrigé : le masquage reste contournable par différence.** (a) La ligne `total` est publiée exacte dès qu'elle atteint 5, alors qu'un centre est masqué. On a donc : centre B = total − centre A = 19 − 18 = **1 absence** et 1910 − 1860 = **50,00 HT**. Ce sont exactement les valeurs que KB-07 voulait cacher. (b) Pour un centre ≥ 5, les effectifs par statut ne sont pas masqués individuellement. Ils révèlent des motifs masqués : `nbAQualifier = 2` ↔ motif `NON_QUALIFIE` masqué, `nbNonJustifiees = 2` ↔ motif `NON_JUSTIFIEE` masqué. (c) Le `mensuel` masqué se déduit aussi : février = 19 − 11 − 6 = 2. RG-DIR-012 et known-bugs affirment « corrigé » et « non contournable ». | `DirectionAbsencesQueryService.java:72-74` (sous-effectifs non masqués si ≥ 5), `:154` (`stats(total,…)` sans masquage complémentaire) ; golden `direction_absences_fevrier_avril_nouveau_code.approved.json:37-45,59-60,94,136-144` ; `13-direction.md:39` | Passer KB-ABS-07 à « corrigé en partie » dans `known-bugs.md:43` et `13-direction.md:34-39`. Faire décider la direction : masquer aussi chaque sous-effectif < 5 ; masquer le total si un seul centre est masqué (masquage complémentaire), ou n'afficher le total que si au moins 2 centres sont masqués ou aucun. Ajouter un golden « différence total − centres ». |
| minor | archi / sécurité | `SessionLegacy` réimplémente `CenterAccessGuard` par réflexion. Elle accepte tout principal qui expose `getCenterId` (le legacy exige `instanceof UserPrincipal`). Le cast `(String)` lèverait un 500 si le type changeait. Elle est copiée dans 3 contextes, sans test unitaire, et hors du périmètre PIT. | `SessionLegacy.java:23-37,52-53` vs `CenterAccessGuard.java:39-41` ; copies dans `contexts/facturation/.../SessionLegacy.java` et `contexts/comptabilite/.../SessionLegacy.java` | Suggestion : un adaptateur `SessionCourante` dans `platform` (composition root) qui délègue à `CenterAccessGuard` ; à défaut, un test de contrat partagé des 3 copies. |
| minor | archi | Le persistence adapter lit la table `patients` (contexte Patient) par jointure, hors `acl/`, alors que `seances` passe par `acl/legacyseance`. | `LectureDesAbsencesJdbc.java:89-90` | Documenter dans ADR 0038 (lecture partagée temporaire) ou passer par un port `NomsDesPatients` dans `acl/`. |
| minor | stratégique (langage) | Termes du code absents du glossaire validé : `AbsenceSuivie`, `AbsenceListee`, `SemaineDuPlanning` / « suivi de la semaine », `DelaiDeQualification`, `SeancesDeRattrapage` (liste proposée). | `.ddd/strategic/glossaries/absences-patients.md` (29 lignes, aucune occurrence) | Les ajouter avec le statut `proposed` (règle 02) et les mentionner dans les questions ouvertes. |
| minor | legacy / process | ADR 0038 a le statut `accepted` alors que la fiche liste encore des points à valider (portée du masquage, 422 ou 409, plafond 100). L'état du contexte reste `modeled`. | `0038-…md:2` ; fiche `absences-patients-a1-consultation.md:116-122` ; `state.ts show` | Mettre l'ADR en `proposed` jusqu'à la validation, ou sortir les 3 points en ADR/questions séparées. Mettre l'état du contexte à jour via `state.ts set`. |
| minor | tests / legacy | Le filet A1 (tests de caractérisation et 66 goldens) et le module ne sont pas versionnés. Les goldens legacy caducs sont remplacés par des `*_nouveau_code` (les versions legacy de 19 goldens n'existent dans aucun commit). Le « vert sur legacy avant » et le `diff -r` annoncé ne sont donc pas vérifiables. | `git status` : `?? backend/platform/src/test/.../absencespatients/`, `?? .../characterization/absences-patients/` | Commiter d'abord le filet vert sur le legacy (commit séparé), puis la slice. |
| minor | tactique | La règle du délai de qualification est dupliquée entre le legacy (synthèse du contrôle de nuit) et `DelaiDeQualification`, avec un risque de divergence jusqu'à A3. | `AbsencePatientService.java:68-71` vs `DelaiDeQualification.java` | Accepté jusqu'à A3 ; l'inscrire dans « Reste à faire » A3. |
| nit | tests | Les goldens à dates relatives (`<J-n>`) calculent « aujourd'hui » dans le test et dans le code par deux horloges UTC distinctes. Le test peut échouer s'il passe minuit UTC. | `ConsultationAbsencesCharacterizationSupport.java:121-128` ; `AbsencesPatientsContexteConfiguration.java:45-61` (`Clock.systemUTC()`) | Accepté, documenté ; ou une horloge injectable en A2. |
| nit | code mort | `import java.util.List` inutilisé. Le paramètre `patientNom` de `AbsenceResponse.de` est toujours `null`. | `AbsencePatientRestController.java:29,93-129,155` | Nettoyer à A2. |
| nit | langage | `jourDuPatient` renvoie une liste de jours. | `SeancesRealisees.java:24` | `joursDuPatient`. |

Aucun autre changement de comportement relevé sur les lectures A1. Le front reste compatible (tailles ≤ 100, codes d'erreur connus).

## Verdict : **pass-with-actions**

### Top 3 actions
1. KB-ABS-07 : requalifier en « corrigé en partie » (known-bugs, RG-DIR-012, fiche § Écarts point 5). Faire décider le masquage complémentaire : total et sous-effectifs. Ajouter le golden de différence.
2. Commiter le filet de caractérisation A1 vert sur le legacy avant le commit de la slice, pour la traçabilité de la parité.
3. Compléter le glossaire (`proposed`) et repasser ADR 0038 en `proposed` tant que les 3 points ne sont pas validés.
