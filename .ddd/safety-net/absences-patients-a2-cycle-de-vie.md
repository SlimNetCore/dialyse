---
status: proposed
date: 2026-10-22
slice: absences-patients-a2-cycle-de-vie
sources: [.ddd/tactical/absences-patients.md § 8, 10 (A2), 11, 12 et décisions du 2026-10-21, .ddd/safety-net/absences-patients-a1-consultation.md (méthode, harnais), lecture de AbsencePatientRestController, AbsencePatientService, AbsencePatient, ValeurAbsence, ValorisationAbsenceService, AbsencePatientJdbcAdapter, AbsenceDonneesJdbcAdapter, ForfaitAbsenceJdbcAdapter, AbsencesDesPatientsLegacy, ExclureUneSeance, ReinclureUneSeance, ApiExceptionHandler, NotificationService]
---
# Filet de caractérisation — A2 cycle de vie et valorisation des absences (refonte sur place, ADR 0024)

Écritures de `/api/v1/absences-patients` et effets de l'ACL `AbsencesDesPatientsLegacy` côté Facturation. Les lectures (A1) et la détection / réconciliation de nuit (A3) ne sont pas dans ce filet.

## Périmètre exact
| Route / entrée | Classes legacy | Verrouillé |
|---|---|---|
| `POST /` (déclarer) | `AbsencePatientRestController.declarer` → `AbsencePatientService.declarer` → `AbsencePatient.declaree`, `ValorisationAbsenceService`, `ForfaitAbsenceJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `AbsencePatientJdbcAdapter.save` | oui |
| `PUT /{id}/qualification`, `/rattrapage`, `/annulation` | idem → `charger`, `exigerDroitDeCorrection`, `AbsencePatient.qualifier / rattraper / annuler` | oui |
| Exclusion et réinclusion d'une séance (`POST /api/v1/facturation/preview/seances/{id}/exclude`, `/include`) vues côté absence | `AbsencesDesPatientsLegacy.declarer / retirer` | oui (le recalcul de l'aperçu et les refus propres à la Facturation restent à F2) |
| Effets sur les lectures A1 (liste, synthèse, séances de rattrapage) | contexte `absences-patients` | oui (cycle complet) |
| Diffusion temps réel `SAISIE_INFIRMIER / ABSENCE` | `SaisieInfirmierNotifier`, `NotificationService` | oui (capturée sur le canal du courtier STOMP) |

## Point d'observation et harnais
HTTP via MockMvc, contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security et `RoleScopeFilter` réels, **H2**), fixtures SQL ; **état de la table `absence_patient`** (toutes colonnes) et des séances relevé après chaque scénario ; canal `brokerChannel` écouté par un intercepteur (sans mock). **Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.**
- Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/absencespatients/cyclevie/` (`CycleDeVieAbsencesCharacterizationSupport` + 8 classes). Le Support A1 étant package-private, sa méthode (alias, scrubber, `GoldenMaster` de G1 réutilisé) est reprise dans un Support dédié.
- Goldens : `backend/platform/src/test/resources/characterization/absences-patients/cycle-de-vie/{declaration,valorisation,qualification,rattrapage,annulation,autorisations,exclusion-facturation,effets-sur-les-lectures}/*.approved.json` (101 fichiers).
- **Dates** : toutes relatives au jour UTC d'exécution (horloge du legacy `Clock.systemUTC`), alias `<J>`, `<J-4>`, `<J+2>` ; jamais de date réelle, aucune dépendance au jour de la semaine (la route `/semaine`, qui en dépend, n'est pas rejouée ici) ; une exécution lancée à moins de deux minutes de minuit UTC attend minuit. Les horodatages (y compris ceux des fixtures, avec ou sans secondes et quel que soit le fuseau) → `<HORODATAGE>`.
- **Identifiants** : tous nommés (`<P_AMRANI>`, `<U_ADMIN>`, `<ABS_FIXTURE_nn>` pour les fixtures, `<ABS_n>` pour les absences créées par le code, dans l'ordre de création) ; messages techniques (JDK, base) masqués.
- **Concurrence** : deux POST simultanés du même patient et du même jour, 5 manches ; golden limité aux invariants stables (une seule création, une seule ligne), la réponse du perdant est contrainte à l'ensemble {422 `ABSENCE_EXISTANTE`, 500 `DATA_ACCESS_ERROR`} (KB-ABS-11).

## Scénarios (84 tests, 8 classes)
| Classe | Tests | Scénarios |
|---|---|---|
| `DeclarationDUneAbsenceCharacterizationIntegrationTest` | 18 | sans motif / avec motif (qualifiée d'emblée, qualificateur = déclarant) ; 9 motifs ; commentaire (rogné, blanc, sans motif perdu, `AUTRE`) ; 1000 / 1001 caractères ; bornes J+1, J, J-60, J-61, J-400 ; séance réalisée (6 statuts, autre patient, autre centre, autre jour) ; unicité patient / jour même annulée ; priorité de l'absence existante ; ordre des contrôles ; bornes de la période facturée (inclusives, autre patient, autre centre) ; corps invalides (12 formes) ; 415 ; isolation des centres ; diffusion temps réel ; déclaration simultanée |
| `ValorisationFigeDesAbsencesCharacterizationIntegrationTest` | 13 | formule TTC et arrondis HALF_UP (11 couples) ; sans prise en charge ; sans règle de TVA ; règle exonérée ; type de prestation exact ; taux selon la date (bornes) ; taux d'un autre centre ; forfait effectif / demandé ; plusieurs prises en charge ; repli sans condition de date ; bornes de période ; valeur figée après changement de forfait, de PEC, de TVA et à chaque étape ; ancienne valorisation conservée |
| `QualificationDUneAbsenceCharacterizationIntegrationTest` | 12 | nominale ; 4 profils ; requalification (commentaire, droit de correction, cumul MEDECIN + INFIRMIER) ; `AUTRE` ; commentaire écrasé ; rattrapée / annulée non modifiables ; refus complets ; motif obligatoire / illisible ; période facturée ; autre centre ; réponse (jamais en retard, sans nom) ; diffusion |
| `RattrapageDUneAbsenceCharacterizationIntegrationTest` | 12 | nominale ; 4 profils sur absence qualifiée ; séance réalisée de même patient et centre (6 cas) ; statuts VALIDEE / SIGNEE / FACTUREE ; bornes de date (même jour, avant, futur, aujourd'hui) ; unicité de la séance et libération après annulation ; ordre des contrôles ; refus complets ; corps invalides ; période facturée ; autre centre ; diffusion |
| `AnnulationDUneAbsenceCharacterizationIntegrationTest` | 8 | à qualifier par 4 profils ; déjà qualifiée (droit de correction) ; rattrapée (KB-ABS-10) ; annulation définitive ; commentaire obligatoire / écrasé / 1001 caractères ; période facturée ; autre centre ; diffusion |
| `AutorisationsDesEcrituresDAbsencesCharacterizationIntegrationTest` | 4 | 11 profils + anonyme × 4 routes (statut HTTP et état de la ligne) ; corrections d'une absence qualifiée × profils ; garde de centre (`centerId` étranger, illisible) ; formes des refus |
| `ExclusionEtReinclusionDeLaFacturationCharacterizationIntegrationTest` | 13 | exclusion nominale (absence DECLAREE qualifiée, auteur, valeur, séance `ABSENT`, lien) ; motifs ; forfait surchargé de la séance (B9) ; refus (absence existante ou annulée, période facturée, délai 60 jours) sans effet ; réinclusion (suppression, ré-exclusion) ; réinclusion après requalification / rattrapage / annulation (B11) ; ligne déjà supprimée ; lectures A1 ; absence de diffusion |
| `EffetsDesEcrituresSurLesLecturesCharacterizationIntegrationTest` | 4 | cycle complet vu par la liste, la synthèse et les séances de rattrapage ; liste après déclarations ; isolation des centres ; traces (auteur / instant) de chaque étape |

## Mesures
- **Suite** : 3 exécutions consécutives vertes, **84/84** (la 1re en ordre par défaut, les 2 autres avec `MethodOrderer$Random` et `ClassOrderer$Random`), 0 flaky. ≈ 1 min par exécution (≈ 35 s de démarrage Spring).
- **Mutation PIT 1.30.0** (`backend/scripts/pit-a2-cycle-de-vie.sh`, `--mutators DEFAULTS`, tests = les 8 classes) sur `AbsencePatientService`, `AbsencePatient`, `ValeurAbsence`, `ValorisationAbsenceService`, `AbsencePatientRestController`, `AbsencePatientJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `ForfaitAbsenceJdbcAdapter`, `AbsencesDesPatientsLegacy`, enums (méthodes de détection / réconciliation / lecture exclues : A1 et A3) : **145 mutants, 125 tués (86 %), 13 survivants, 7 sans couverture**, force des tests **91 %**, couverture de lignes des classes mutées 74 %.
  - 13 survivants, **tous inatteignables ou équivalents par HTTP** : gardes `null` du constructeur d'`AbsencePatient` (5) et de `declaree`, `qualifier`, `rattraper`, `annuler` (4) (le contrôleur les précède : `@NotNull` / `@NotBlank`), gardes de signe de `ValeurAbsence` (3, valeurs issues de `setScale` et de colonnes non négatives), valeur du `nomPatient` (l'appelant ne teste que sa présence).
  - 7 sans couverture : lambdas de `findPaged`, `centresActifs`, `patientsAttendusSansSeance` (A1 / A3) et `StatutAbsence.comptabilisee` (non utilisée par les écritures).
- **Couverture de branches** : JaCoCo n'est pas configuré (comme en A1). Exercés par un scénario nommé : chaque contrôle de `declarer`, `charger`, `rattraper`, `qualifier`, `annuler` et du domaine, chaque branche de choix du forfait (effectif, demandé, repli, ignoré), du taux (exonéré, absent, hors période, autre type, autre centre), `retirer` et `declarer` de l'ACL.

## Seams
Aucun appliqué ni nécessaire ; `seamAllowed` inchangé (vide). Une horloge injectable (`Clock`) dans `AbsencePatientService` / le contrôleur permettrait d'observer le fuseau (B8) et la déclaration du jour juste après minuit : non demandé (la décision 7 impose de toute façon un `Horloge` au nouveau code).

## Fichiers protégés (`covered.json`, ajoutés)
`AbsencePatient`, `ValeurAbsence`, `MotifAbsence`, `StatutAbsence`, `SourceAbsence`, `ValorisationAbsenceService`, `ForfaitAbsencePort`, `ForfaitAbsenceJdbcAdapter`, `AbsencesDesPatientsLegacy`. Déjà couverts : `AbsencePatientRestController`, `AbsencePatientService`, `AbsencePatientJdbcAdapter`, `AbsenceDonneesJdbcAdapter` (A1).
**Couverture partielle (honnêteté)** : `AbsencePatientService`, `AbsencePatientJdbcAdapter`, `AbsenceDonneesJdbcAdapter` et `AbsencePatient` contiennent aussi la détection, la réconciliation, `rattraperPeriode`, `AbsencePatient.detectee` et `centresActifs` (A3), **non verrouillés** ; toute modification de ces méthodes attend A3. `AbsencePatientRestController.rattraperDetection` (POST `/rattrapage-detection`) non verrouillé non plus.

## Bugs connus
KB-ABS-10 à 21 (`known-bugs.md`) : annulation d'une rattrapée, déclaration simultanée sans verrou (500 majoritaire), commentaire perdu sans motif, rattrapage sans droit de correction, commentaire écrasé, forme des réponses d'écriture (`tauxTva: 0`, `patientNom` nul), règles de choix du forfait, entrées illisibles en 500 / 415 absent, ordre des contrôles, exclusion sans diffusion temps réel, réinclusion qui supprime l'absence évoluée.

## Non verrouillé (limites)
- Détection et réconciliation de nuit, `POST /rattrapage-detection`, notification `ABSENCES_A_QUALIFIER` (A3).
- **Écritures concurrentes sur une même absence** (dernière écriture gagne) et **double usage d'une séance de rattrapage** par deux absences : non reproductibles de façon stable sans seam (le test de course ne verrouille que la déclaration).
- Fuseau (B8) : jours relatifs au jour UTC ; l'effet « minuit - 1 h Algérie » n'est pas observé.
- Journal d'audit (`AuditRequestFilter` n'est pas dans le MockMvc), rafraîchissement temps réel côté client, front (`features/absences`, `planning`, `infirmier`).
- `/semaine` après écriture (semaine dimanche-samedi dépendante du jour d'exécution ; verrouillée par les goldens ancrés d'A1), pilotage Direction et tableau de bord Séance après écriture (lisent la même table ; verrouillés par A1 et F5).
- **PostgreSQL** : comportement des violations de contrainte (code et message du 500), échelle des `NUMERIC`, ordre des UUID : non testés (H2 seulement ; rejeu PG 16 à prévoir).
- Valeurs de `forfait.prix` à plus de 2 décimales (colonne `DECIMAL(10,2)`) : l'arrondi HT n'est donc pas observable.
- Messages d'erreur du JDK / de la base masqués ; corps non JSON → seul le statut 500 est verrouillé.

## Questions de décision pour la conception sur place de A2
1. **Gel de la valorisation (décision 4)** : confirmé par le filet (aucune requalification, rattrapage, annulation ni changement de forfait / PEC / TVA ne recalcule). Le nouveau code doit-il en plus **refuser** toute écriture de valeur, ou seulement ne pas la recalculer ?
2. **KB-ABS-16** : reproduire le repli « PEC la plus récente portant un forfait, sans condition de date » et le forfait effectif prioritaire même hors période (une absence peut être valorisée par un forfait d'une période qui ne la couvre pas) ? ou valoriser à zéro / refuser ?
3. **KB-ABS-15 / TVA exonérée** : le nouveau code répond-il `0` ou `0.00` ? Et quel taux pour une règle exonérée (0 % ici, taux plein en Facturation, KB-CPT-19) ?
4. **Corrections de cycle de vie (décision 5)** : (a) annulation d'une rattrapée refusée (ADMIN et MEDECIN y arrivent aujourd'hui) — quel code de refus : `ABSENCE_NON_MODIFIABLE` ; la date de rattrapage doit-elle être **conservée** ou effacée si on autorise un jour l'annulation ? (c) le rattrapage exige le droit de correction : pour **toute** absence ou seulement pour une absence déjà qualifiée (un SECRETAIRE / INFIRMIER pourrait-il encore rattraper une absence « à qualifier ») ?
5. **Verrous et 409 (décision 6)** : la déclaration simultanée perdante doit-elle répondre 409 (au lieu de 500 / 422), et le 409 s'applique-t-il aussi quand le doublon est une absence **annulée** (aujourd'hui 422 `ABSENCE_EXISTANTE`) ? Que répond-on à une écriture concurrente sur une même absence : 409 avec relecture ?
6. **Unicité du rattrapage (décision 6)** : contrainte unique `(centre, patient, date_rattrapage)` — attention aux absences **annulées qui gardent leur `date_rattrapage`** (KB-ABS-10) : la contrainte doit viser les seules `RATTRAPEE`, ou la date doit être effacée à l'annulation ; la requête de vérification de production doit donc filtrer sur `statut = 'RATTRAPEE'`.
7. **KB-ABS-12** : un commentaire fourni sans motif à la déclaration doit-il être conservé (changement) ou refusé ?
8. **KB-ABS-17 / 18** : conserve-t-on les 500 sur entrées illisibles et l'ordre actuel des contrôles pour la parité, ou aligne-t-on dès A2 (400 / 415 / 404 comme en A1) ?
9. **KB-ABS-19 / 20** : l'exclusion de la Facturation doit-elle diffuser la saisie temps réel ? la réinclusion doit-elle encore supprimer une absence déjà **requalifiée, rattrapée ou annulée** (ADR 0031 l'impose pour l'absence « d'exclusion », mais l'utilisateur a pu la travailler) ?
10. **Fuseau (décision 7)** : fuseau du centre dès A2 ; le filet ne peut pas l'observer (jours UTC) : les goldens `*_nouveau_code` de la déclaration du jour juste après minuit seront écrits avec le nouveau code et une horloge de test.
