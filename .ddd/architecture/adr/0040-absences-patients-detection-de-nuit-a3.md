---
status: proposed
date: 2026-10-22
deciders: utilisateur (décisions du 2026-10-21 et du 2026-10-22) ; réalise le modèle tactique `.ddd/tactical/absences-patients.md` (slice A3)
---

# ADR 0040 — Absences de patients : détection de nuit (A3), écarts corrigés, fuseau du centre et notification unique

## Contexte

A1 (ADR 0038) a déplacé la consultation, A2 (ADR 0039) le cycle de vie. A3 déplace le dernier comportement automatique : le contrôle
de nuit (cron 02:30), le rattrapage au démarrage, `POST /rattrapage-detection` et la notification `ABSENCES_A_QUALIFIER`. Le filet (55
tests, `.ddd/safety-net/absences-patients-a3-detection-de-nuit.md`) a révélé onze comportements suspects (KB-ABS-22 à 32).

## Décision

1. **Politiques dans `contexts/absences-patients`** (refonte sur place, ADR 0024 : mêmes table, URL et JSON ; pas de façade) :
   `DetectionAbsences`, `ReconciliationAbsences`, `NotificationDesAbsencesAQualifier`, composées par les cas d'usage
   `ControlerLesAbsences` (nuit 7 jours, démarrage 31 jours) et `DetecterUnePeriodePassee`. Domaine : `AbsencePatient.detecter`,
   `reconciliable`, `reconcilier`, VO `PeriodeDeDetection`, `DateOuverte.siOuverte`, port `MoisDAbsencesOuverts`. Ports : `PatientsAttendus`
   (ACL SQL), `CentresActifs`, `FuseauxDesCentres`, langage publié `NotificationsDuCentre` (fourni par la plateforme).
2. **Un seul déclencheur serveur** (02:30 au fuseau par défaut) ; le cas d'usage calcule « aujourd'hui » et la fenêtre de chaque centre à son
   fuseau (`HorlogeDesCentres`). Le centre n'a pas de colonne de fuseau : tous suivent `app.absences.fuseau-par-defaut` (Africa/Algiers) ; aucune
   migration. Les lectures A1 (`/semaine`, `enRetard`, séances de rattrapage, synthèse) et les contrôles de date d'A2 (déclaration du jour,
   rattrapage) utilisent la même horloge.
3. **Isolation par absence** : une absence = une transaction (`UniteDeTravail`) ; un incident (valorisation refusée, base) est capturé dans le
   `BilanDuControle`, journalisé par l'adaptateur entrant, et n'arrête ni son centre ni les autres ; le centre est notifié quand même.
   Une déclaration simultanée du même jour (409) vaut « l'absence existe » : pas d'échec.
4. **Réconciliation corrigée** (KB-22, 23) : seules les absences **détectées** « à qualifier » **hors période facturée** sont annulées (relues sous
   verrou) ; lecture par **curseur d'identifiant** (`detecteesAQualifierEntre`), tout en un passage (plus de page sautée).
5. **Patient attendu** (KB-24) : `TRANSFERE`, `GUERRI`, `DECEDE`, `GREFFE` excluent le jour de l'événement (uniformisé). **Sans date d'événement, un sorti n'est jamais attendu** (legacy conservé, revue du 22/10) : l'utilisateur a choisi de bloquer la sortie sans date dans le contexte Patient et couverture (hors A3, prérequis noté dans `roadmap.md`) ; la liste des cas existants est le § 25.2 de `verifications-production.md`. La requête exclut aussi les jours qui ont déjà une absence (le contrôle `existe` reste dans la transaction).
6. **Notification dédoublonnée** (KB-25) : une seule notification par centre et par jour du centre, à **identifiant déterministe** (UUID v3 de type, centre et jour) passé à `NotificationService` (surcharge ajoutée avec `idImpose`, fichier ajouté à `covered.json` pour cette seule surcharge) : sûr en multi-instance. Comptes = **tous** les mois d'absences ouverts, sans limite de fenêtre (port `MoisDAbsencesOuverts.moisClos`). Le port `NotificationsDuCentre` reste dans `publishedlanguage/` avec des `UUID` : port requis fourni par la plateforme, comme `SignalementDesSaisies` (A2), à reclasser quand les notifications deviendront un contexte.
7. **Centres inactifs non contrôlés** (KB-26). **Rattrapage de détection** (KB-28, 29) : entrées illisibles 400 (415 non JSON), fin ramenée à hier
   signalée par les champs additifs `fin` et `finRamenee` (présents seulement dans ce cas), `echecs` seulement si des absences ont été en incident ;
   durée maximale de 366 jours inchangée.
8. **Mois clos** : le contrôle saute (et journalise dans le bilan) un jour dont le mois d'absences est clos, via le port `MoisDAbsencesOuverts`.
   A6 n'existe pas : l'adaptateur `TousLesMoisSontOuverts` déclare tout ouvert ; A6 le remplacera sans toucher aux politiques.
9. **Performance** : une requête « patients attendus » par jour et par centre (inchangée) ; pas d'anti-jointure sur la fenêtre ni d'écriture par
   lot (l'isolement par absence prime). L'index `seances (center_id, patient_id, date_seance)` existe (V31) : aucune migration.
10. **Code legacy retiré** : `AbsencePatientService`, `AbsencePatientScheduler`, `AbsencePatientRestController`, ports `AbsenceDonneesPort`,
    `AbsencePatientRepositoryPort`, `ForfaitAbsencePort`, adaptateurs JDBC, `ValorisationAbsenceService`, tout `bc-seance/.../domain/absence/**`
    (devenu inconstructible après le retrait de `detectee`), le seam `saveIfAQualifier` (`seams.md`).

## Conséquences

- Les goldens `*_nouveau_code` (12) remplacent les goldens caducs ; le comportement legacy exact est gardé dans `_ecarts-legacy/*.legacy.json`.
- Effets visibles : une notification de moins par nuit ; plus de
  contrôle des centres inactifs ; la semaine du planning et les dates du jour changent une heure plus tôt (Alger, UTC+1).
- Une absence en incident est retentée chaque nuit pendant 7 jours, journalisée au niveau ERROR avec un compteur par centre, puis relancée par `POST /rattrapage-detection` une fois la cause corrigée (choix simple, pas de nouvelle alerte).
- Mesures : `.ddd/migration/slices/absences-patients-a3-detection-de-nuit.md`.
