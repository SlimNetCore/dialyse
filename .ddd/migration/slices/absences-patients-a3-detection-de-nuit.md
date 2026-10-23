---
status: built
date: 2026-10-22
slice: absences-patients-a3-detection-de-nuit
order: 3 (A1 à A6, voir `.ddd/tactical/absences-patients.md` § 10)
sources: [.ddd/tactical/absences-patients.md (§ 6, 8, 10, 12, décisions 2026-10-21), .ddd/safety-net/absences-patients-a3-detection-de-nuit.md, known-bugs.md (KB-ABS-22 à 32), ADR 0024, 0038, 0039, 0040, docs/reference/regles-de-gestion/06-absences-patients.md]
---

> **Refonte sur place (ADR 0024)** : même table `absence_patient`, mêmes URLs, mêmes noms JSON ; **aucune migration** (l'index
> `seances (center_id, patient_id, date_seance)` existe depuis V31). Pas de façade, de flag, de projection ni de plan de bascule par paliers ;
> retour arrière = commit précédent. Seul interrupteur existant : `app.absences.startup-catchup` (rattrapage au démarrage), inchangé.

# A3 — Détection de nuit des absences de patients

**Comportement observable** : cron 02:30 (réconciliation puis détection des 7 derniers jours terminés, notification), rattrapage au démarrage
(31 jours, sans notification), `POST /api/v1/absences-patients/rattrapage-detection`.

## Réalisation

- **domaine** : `AbsencePatient.detecter | reconciliable | reconcilier`, `PeriodeDeDetection` (VO), `DateOuverte.siOuverte`, port `MoisDAbsencesOuverts`,
  événements `AbsenceDetectee`, `AbsenceReconciliee`, `AbsencesRepository.detecteesAQualifierEntre` (curseur).
- **application** : `DetectionAbsences`, `ReconciliationAbsences`, `NotificationDesAbsencesAQualifier` (politiques), `ControlerLesAbsences` (nuit, démarrage),
  `DetecterUnePeriodePassee`, `BilanDuControle`, `HorlogeDesCentres`, ports `PatientsAttendus`, `CentresActifs`, `FuseauxDesCentres`,
  `LectureDesAbsences.nombreAQualifierEntre`. A1/A2 passent de `Clock` à `HorlogeDesCentres` (fuseau du centre).
- **infrastructure** : ACL `legacyplanning/PatientsAttendusJdbc` (une requête par jour et centre), `legacyorganisation/CentresActifsJdbc`,
  `horloge/FuseauParDefaut`, `mois/TousLesMoisSontOuverts` (provisoire, jusqu'à A6), `AbsencesJdbc.detecteesAQualifierEntre`.
- **interfaces** : `http/DetectionControleur` (+ `EntreesDesAbsencesAdvice` étendu : 400/415), `jobs/ControleDeNuitDesAbsences` (`@Scheduled` + `ApplicationReadyEvent`).
  Langage publié requis : `NotificationsDuCentre` (fourni par `platform/.../acl/absences/NotificationsDuCentreLegacy`, SQL sur le journal des alertes + `NotificationService`).
- **Retiré du legacy** : `AbsencePatientService`, `AbsencePatientScheduler`, `AbsencePatientRestController`, `AbsenceDonneesPort`, `AbsencePatientRepositoryPort`,
  `ForfaitAbsencePort`, `AbsenceDonneesJdbcAdapter`, `AbsencePatientJdbcAdapter`, `ForfaitAbsenceJdbcAdapter`, `ValorisationAbsenceService` (+ bean), `AbsenceFiltre`, `AbsenceLigne`,
  et tout le modèle `bc-seance/.../domain/absence/**` (`AbsencePatient`, `MotifAbsence`, `StatutAbsence`, `SourceAbsence`, `ValeurAbsence`) devenu inconstructible une fois `detectee` retiré ;
  tests legacy associés (`AbsencePatientSchedulerTest`, `AbsencePatientRestControllerTest`, `ValorisationAbsenceServiceTest`, `AbsencePatientTest`, les 11 tests de détection de
  `AbsencePatientIntegrationTest` ; ses 4 tests Direction/Séance sont gardés et créent leurs absences par la nouvelle détection). Seam `saveIfAQualifier` supprimé (`seams.md`, `seamAllowed` vide).
  `DomainServiceConfig` (bean retiré) ajouté à `covered.json` à la demande du hook, comme `NotificationsDuCentreLegacy` : **à valider**.
- **Frontend** : champs additifs du résultat du rattrapage (`fin`, `finRamenee`, `echecs`), deux messages (fr/en/ar/kab), 1 test de store.

## Décisions de l'utilisateur (22/10) et défauts retenus

| Sujet | Réalisation |
|---|---|
| Notification (KB-25) | une seule par centre et par jour du centre, à identifiant déterministe (sûr en multi-instance) ; comptes = toutes les absences à qualifier des mois ouverts, sans limite de fenêtre (révisé à la revue du 22/10) |
| Réconciliation (KB-22, 23) | détectées seulement, hors période facturée ; curseur d'identifiant, tout en un passage |
| Patient attendu (KB-24) | jour de l'événement exclu pour DECEDE/GREFFE/TRANSFERE/GUERRI ; **sans date : jamais attendu (legacy conservé)** en attendant la règle « bloquer la sortie sans date » de Patient et couverture (`roadmap.md`) ; liste en `verifications-production.md` § 25.2 |
| Robustesse (KB-26, 27, 28, 29) | isolement par absence, journalisé, notification quand même ; centres inactifs ignorés ; 400/415 ; `fin`/`finRamenee` signalés ; 366 jours conservés |
| Fuseau (décision 7) | `Africa/Algiers` par défaut (`app.absences.fuseau-par-defaut`), un déclencheur 02:30 ; A1 (`/semaine`, `enRetard`, synthèse, séances de rattrapage) et A2 (déclaration du jour, rattrapage) alignés |
| Mois clos | port `MoisDAbsencesOuverts`, adaptateur provisoire « tout ouvert » (A6) ; le contrôle saute le jour, le journalise dans le bilan, et la notification ne compte pas ce mois |
| Performance | une requête par jour et centre, pas de lot (isolement) ; index V31 présent, pas de migration |

## Parité

Filet de caractérisation : **55 tests** exécutés contre le nouveau code : **44 verts sans changement de golden**, **11 goldens `*_nouveau_code`**
(écarts décidés) ; le comportement legacy des 12 écarts initiaux est conservé dans `.../detection/_ecarts-legacy/<thème>/*.legacy.json` (non rejoué).
Harnais adapté (3 classes de support A1/A2/A3) : « aujourd'hui » = jour du fuseau Africa/Algiers au lieu d'UTC ; le scheduler testé est `ControleDeNuitDesAbsences`.
Filets A1 (27 tests) et A2 (84 tests), `MappingTvaConsommateurs` (lecteur de TVA des absences = port du module) : verts sans changement de golden.

| Golden écarté | Décision |
|---|---|
| `controle-de-nuit/centre_inactif`, `deux_passages`, `echec_au_milieu_d_un_centre`, `limite_du_retard` (`notification_a_qualifier_et_en_retard` est redevenu golden legacy, trace dans `_ecarts-legacy`) | KB-25, 26, 27 |
| `patient-attendu/etat_et_date_d_evenement` (seul le jour de l'événement TRANSFERE/GUERRI change ; les 4 « sans date » reviennent au legacy) | KB-24 |
| `demarrage-et-masse/reconciliation_de_250_absences` | KB-23 |
| `rattrapage-detection/bornes_de_dates`, `corps_invalides`, `garde_de_centre` | KB-28, 29 |
| `reconciliation/absence_declaree_sans_motif_conservee`, `periode_facturee_ignoree` | KB-22 |

## Tests et mesures

| Niveau | Tests |
|---|---|
| Domaine (`PeriodeDeDetectionTest`, `DetectionEtReconciliationDUneAbsenceTest`) | 8 + 6 |
| Application (`ControleDesAbsencesTest` 29, `FuseauDuCentreTest` 3, `BilanDuControleTest` 1) | 33 |
| ACL SQL H2 (`PatientsAttendusJdbcTest` : 42 cas dont états de sortie, séjour limité, fériés, séances, centres actifs) | 42 |
| Contrat : curseur et compte (double / H2 / **PostgreSQL 16**) | +2 absences, +1 lectures, par implémentation |
| Job planifié (annotations cron/fuseau, démarrage désactivable) | 4 |
| ArchUnit du module | 9 (0 violation) ; `check-arch.ts` : 0 violation |

- Rejeu PostgreSQL 16 jetable (`bash backend/scripts/absences-a3-pg16-rejeu.sh 55460`, port != 5432, V1..V43) : **50/50** (absences 14, détection 5 sur les vraies tables, concurrence 16, lectures 15).
- **PIT** (`bash backend/scripts/pit-a3-module.sh`, `domain.*` + `application.*`) : 272 mutants, 265 tués (97 %), force des tests 98 %, lignes 98 %. Survivants : seuil de `Valorisation.depuis`
  et `Identifiants.requis` (équivalents, comme A2), dédoublonnage des jours ignorés (`ReconciliationAbsences`, `BilanDuControle`, couvert ensuite), 1 sans couverture (branche `ouverte.isEmpty()` à mois clos de `DetectionAbsences`, inatteignable : le jour est sauté avant).
- Module `contexts/absences-patients` complet : **315 tests** verts (hors 5 IT PostgreSQL de détection, rejoués par le script). Build complet `./mvnw -o test` : **BUILD SUCCESS**,
  1964 tests plateforme verts (4 ignorés, préexistants), `FrontieresDesContextesTest` et `ReglesDeGestionDocumentationTest` (garde du référentiel) verts. Frontend : 1 test de store ajouté (non exécuté ici, pas de `ng test`/serveur lancé).

## Plan de bascule

Sans objet (refonte sur place). Déploiement : `verifications-production.md` § 25 (25.2 à saisir, le reste informatif). Retour arrière : commit précédent (aucune migration).

## Points à valider

1. **Sorties sans date d'événement** (question ouverte, prérequis de Patient et couverture) : l'utilisateur choisit de **bloquer la sortie sans date** ; règle HORS A3 (`roadmap.md`). En attendant, A3 reproduit le legacy : un sorti sans date n'est jamais attendu, donc ses vraies absences ne sont pas détectées. § 25.2 liste les cas à saisir.
2. **Notification** : une par centre et par jour du centre (clé déterministe), comptes sur tous les mois ouverts. La surcharge de `NotificationService` (idImpose) a imposé de classer ce fichier dans `covered.json` : exception ponctuelle (`seams.md`).
3. **Absence en incident** : retentée chaque nuit pendant 7 jours, journalisée au niveau ERROR avec compteur, puis à relancer par `POST /rattrapage-detection` (la réponse porte `echecs`). Aucune nouvelle alerte.
4. **Fuseau du centre** : pas de colonne (aucune migration) ; tous les centres suivent `app.absences.fuseau-par-defaut`. A2 (déclaration du jour, rattrapage) est aligné lui aussi : entre 00:00 et 01:00 à Alger, la déclaration du jour n'est plus refusée.
5. **Retrait élargi** : tout `bc-seance/.../domain/absence/**` retiré maintenant (au lieu de A5), car inconstructible. `NotificationsDuCentreLegacy` et `NotificationService` (surcharge) dans `covered.json` ; `DomainServiceConfig` en est retiré.
6. **Mois clos** : tant qu'A6 n'existe pas, `TousLesMoisSontOuverts` ; la nuit du 1er au 7, un mois clos ne sera pas complété (à rouvrir), comme décidé.
7. **Centres inactifs** : tout centre `actif = FALSE` n'est plus contrôlé ni notifié (`actif` nul = actif).
8. **Documentation client** à régénérer depuis le référentiel (RG-ABS-040 à 046, 041, 044 amendées ; 058, 059 nouvelles ; RG-PAT-032 précisée).

## Reste à faire

A4 lectures publiées (`AbsencesDuPilotage`, SQL de la Direction et de la Séance), A5 retrait (`AbsencePatientRestController` déjà retiré ; reste `AbsencesDesPatientsLegacy` déjà retiré en A2 : vérifier `FrontieresDesContextesTest`), A6 clôture mensuelle (remplace `TousLesMoisSontOuverts`).
