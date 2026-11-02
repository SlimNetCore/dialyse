---
status: characterized (filet vert sur le LEGACY, rien de commité)
date: 2026-11-02
sources: .ddd/migration/slices/patient-couverture-pc1-identite-mouvements.md, backend/contexts/patient-couverture/GEL.md (ADR 0073), ADR 0005/0024/0052/0061, docs/reference/regles-de-gestion/04-patients-assures-pec.md, known-bugs.md KB-PAT-01 à 18
---

# Filet de caractérisation : patient-couverture-pc1-identite-mouvements

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` reste vide). Module parallèle `contexts/patient-couverture` gelé (ADR 0073) : non concerné, la référence est le legacy (`bc-patient`, `PatientRestController`, `PatientApplicationService`, `MouvementPatientJdbcAdapter`...).

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL (`patients`, `mouvement_patient`, `attestation_droit`, `assure`, `assure_patient`), lecteurs consommateurs (`PatientsAttendus` d'Absences, tableau de bord Séance). Aucun mock. Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/patientcouverture/pc1/PatientCouvertureCharacterizationSupport.java` (au-dessus de `LiberationCharacterizationSupport`, donc de P3 à P7 : décor, dates nommées, diffusions, audit, garde de minuit du fuseau du centre). Un sous-dossier de goldens par scénario : `backend/platform/src/test/resources/characterization/patientcouverture/pc1/<dossier>/<scénario tronqué à 36 caractères>/` (128 fichiers).

Commande : `cd backend; ./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.patientcouverture.pc1.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`). Ne jamais lancer deux builds Maven en parallèle.

## Déjà verrouillé ailleurs (non rejoué)

| Filet | Verrouille |
|---|---|
| `characterization/patientcouverture/*` (17 goldens, PC2 assuré principal, PC3 PEC) | affecter/modifier assuré et affectation (ADMIN), liste des assurés, PEC |
| Planification P5 `placement/*` | `POST`/`PUT /patients` : 8 règles de place, refus 422 `PLACEMENT_*`, centre du corps (403), matrice 4 routes x 11 profils + anonyme (liste blanche de rôles de l'écriture de fiche), diffusions `PATIENT_*`, audit, verrou de centre |
| Planification P7 `liberation/*` | sortie sans date refusée (422 `PATIENT_DATE_EVENEMENT_REQUISE`), libération à l'enregistrement et la nuit, mouvements `PLACE_LIBEREE`, matrice des rôles de `GET /mouvements-patients` |
| Absences A3 | détection de nuit via `PatientsAttendus` |

## Scénarios ajoutés (56, 6 classes, 0 flaky)

| Classe | Nb | Verrouille |
|---|---|---|
| `CreationDeLaFichePatient` | 15 | création nominale (réponse, fiche, mouvement `ADMISSION`, attestation), code `PAT-xxxxxxxx` unique, valeurs par défaut, champs primitifs absents/nuls (500, KB-PAT-01), champs obligatoires (nom, prénom, sexe, dates, numéro, centre, attestation) sans écriture, unicité du numéro par centre, homonymes acceptés, états et dates à la création (19 cas), attestation (vacancier, dates), assuré tiers, formats téléphone/courriel, dates de naissance/admission, corps invalides, garde de centre, effets sur fiche/recherche/mouvements/résumé (cache) |
| `ModificationEtEtatsDeLaFichePatient` | 11 | modification nominale, numéro d'assurance ignoré (KB-PAT-03), patient inconnu, **matrice de 27 transitions d'état** (type de mouvement, date d'effet, état précédent), sorties refusées (sans date, avant admission, bornes), état absent du corps, reprise `PERMANENT`, écritures simultanées, attestation (existante, jamais dupliquée, historique), bascule de qualité d'assuré |
| `ConsultationEtRechercheDesPatients` | 15 | fiche complète (entité de domaine), assuré tiers, générateur du parc, refus de lecture, ligne héritée aux colonnes nulles, recherche (forme, pages, bornes, 14 colonnes x 4 sens de tri, 34 filtres, dates d'admission inclusives et inversées, facturable via PEC validée), résumé et détail du mois (mois masqué), paramètres invalides, cache |
| `MouvementsDesPatients` | 8 | liste complète des 8 types, ordre (date d'effet, création, id), filtres cumulatifs (29), pagination bornée, mouvements orphelins, libellés et nom, type inconnu en base, centre de la session |
| `AutorisationsEtCentresDeLaFichePatient` | 4 | matrices 11 profils + anonyme de 6 lectures et de 3 écritures d'assuré, **absence de garde de centre** des lectures et des écritures d'assuré (KB-PAT-06/07), garde de la fiche |
| `LecteursDeLaFichePatient` | 3 | patients attendus d'Absences jour par jour selon l'état et la date d'événement (14 patients, J-2 à J+2), admission future, tableau de bord Séance (mois courant et lointain) |

## Non-déterminisme

Dates par alias relatifs nommés (`<J-400>`, `<J+5>`, `<J-20000>`...), jamais de date réelle ni de jour de mois (échec du harnais si une date non nommée reste dans un golden). Mois courant, année, premier et dernier jour du mois masqués (`<MOIS_COURANT>`, `<MOIS>`, `-JJ`). Événements à `J+100`/`J-100` pour rester hors des bornes du mois. Code patient aléatoire masqué ; identifiants de fiche nommés. Attente de minuit du fuseau du centre (Africa/Algiers, héritée de P3) et scénario ignoré si le jour de la JVM diffère du jour du centre (les routes d'assuré lisent `LocalDate.now()`). Ordre alphabétique : interclassement H2.

## Stabilité

Trois exécutions des 56 scénarios : ordre par défaut, puis deux ordres aléatoires : 0 échec, 0 flaky. Suite voisine (patientcouverture, planification placement et liberation, 172 tests) verte en ordre aléatoire avec PC1.

## Couverture et mutation

Pas de JaCoCo ni de PIT dans la chaîne Maven hors ligne : aucune mesure de branches ni de mutants. Couverture raisonnée : toutes les routes de `PatientRestController` (sauf la liste héritée d'`affecter` déjà en PC2) et de `MouvementPatientRestController` sont exercées ; branches de `FinOccupation.verifierDate` (3 issues), `MouvementsPatient.changementEtat` (aucun mouvement, 5 types) et `TypeMouvementPatient.pourEtat` (8 valeurs), `PatientDomainService` (unicité, vacancier, hydratation), `PatientApplicationService` (attestation, assuré, état, reprise). 16 fichiers ajoutés à `covered.json` (tous exercés par au moins un scénario).

## Limites (NON verrouillé)

- PostgreSQL (H2 seulement) : interclassement, types, verrous réels ; vraie concurrence (une seule paire de `PUT` simultanés, résultat borné) ; volumétrie.
- Direction (`DirectionDashboardQueryService`, `DirectionBreakdownQueryService`) : effectif par centre non rejoué (centre société à monter), voir filet Direction. Séance : seul le tableau de bord ; `SeancePlanningJdbcAdapter`, `SeanceBillingEligibilityJdbcAdapter`, `PecValidationJdbc`, facturation : filets Séance/Facturation.
- Diffusions et audit de la fiche : P5. Notifications durables `PATIENT_*` : non observées en base. Filtres des colonnes de référentiel de la recherche (médecin, créneau, transporteur, forfait) : non rejoués (`PatientReferenceLookup`).
- Photo (`photoBase64`) volumineuse, pièces jointes, `assureHistoryJson` : valeurs libres non éprouvées.
- Fuseau : bascule de minuit non rejouée (scénarios ignorés ou attente) ; `LocalDate.now()` de la JVM des routes d'assuré (KB-PAT-17).
- Bascule des lectures SQL hors module : liste blanche ci-dessous, sans test d'architecture associé (à écrire à PC5).

## Liste blanche datée 2026-11-02 des lectures et écritures SQL hors module (25 fichiers de `src/main`)

Relevé : `grep -E "(FROM|JOIN|INTO|UPDATE) (patients|mouvement_patient|assure|assure_patient|prise_en_charge|attestation_droit)"` sur `backend/*/src/main` et `backend/contexts/*/src/main`, hors `contexts/patient-couverture`. Les cinq premières lignes sont dans le périmètre legacy patient lui-même (lecteurs historiques de la fiche) ; les autres sont des lecteurs d'autres contextes.

| Fichier (sous `backend/`) | Table(s) | Accès |
|---|---|---|
| `platform/.../persistence/adapter/MouvementPatientJdbcAdapter.java` | `mouvement_patient`, `patients` | écrit (INSERT) et lit (propriétaire legacy) |
| `platform/.../application/query/PatientSummaryQueryService.java` | `patients` | lit (propriétaire legacy) |
| `platform/.../persistence/adapter/migration/MigrationRollbackJdbcAdapter.java` | `patients`, `assure`, `assure_patient` | lit (outil de migration) |
| `platform/.../web/rest/DashboardRestController.java` | `patients`, `attestation_droit`, `prise_en_charge` | lit |
| `platform/.../web/rest/SeanceRestController.java` | `patients`, `prise_en_charge` | lit (tableau de bord, journal) |
| `platform/.../application/direction/DirectionDashboardQueryService.java` | `patients` | lit |
| `platform/.../application/direction/DirectionBreakdownQueryService.java` | `patients` | lit |
| `platform/.../persistence/adapter/SeancePlanningJdbcAdapter.java` | `patients` | lit |
| `platform/.../persistence/adapter/SeanceBillingEligibilityJdbcAdapter.java` | `attestation_droit`, `prise_en_charge` | lit |
| `contexts/seance/.../persistence/PecValidationJdbc.java` | `prise_en_charge` | lit |
| `contexts/facturation/.../acl/legacyseance/SeancesFacturablesJdbc.java` | `patients`, `prise_en_charge` | lit |
| `contexts/facturation/.../persistence/SituationsDesFacturesJdbc.java` | `patients` | lit |
| `contexts/absences-patients/.../acl/legacypatient/PatientsDuCentreJdbc.java` | `patients` | lit |
| `contexts/absences-patients/.../acl/legacypatient/NomsDesPatientsJdbc.java` | `patients` | lit |
| `contexts/absences-patients/.../acl/legacycouverture/ForfaitsDesPatientsJdbc.java` | `prise_en_charge` | lit |
| `contexts/planification/.../acl/legacypatient/ContraintesDesPatientsJdbc.java` | `patients` | lit |
| `contexts/planification/.../acl/legacypatient/NomsDesPatientsJdbc.java` | `patients` | lit |
| `contexts/planification/.../acl/legacypatient/PatientsDuCentreJdbc.java` | `patients` | lit |
| `contexts/planification/.../acl/legacypatient/PlanningDesPlacesDuCentreJdbc.java` | `patients` | lit |
| `contexts/planification/.../acl/legacypatient/SejoursDesPatientsJdbc.java` | `patients` | lit |
| `contexts/planification/.../acl/legacypatient/MouvementsDesPatientsJdbc.java` | `mouvement_patient` | **écrit** (INSERT `PLACE_LIBEREE`) et lit |
| `contexts/planification/.../acl/legacypatient/PlacesDuCentreJdbc.java` | `patients` | **écrit** (UPDATE des colonnes de place) et lit |
| `contexts/planification/.../acl/legacypatient/PlacesTenuesJdbc.java` | `patients` | **écrit** (UPDATE des places tenues) et lit |
| `contexts/planification/.../acl/legacyseance/SeancesRealiseesJdbc.java` | `patients` (jointure) | lit |
| `contexts/planification/.../persistence/LectureDesPreferencesJdbc.java` | `patients` | lit |

Écrivains hors module : les trois ACL de Planification ci-dessus (colonnes de place, mouvement `PLACE_LIBEREE`) ; la migration (`MigrationRollbackJdbcAdapter`, `PatientMigrator`, `AffectationMigrator`) supprime/écrit en bloc. Références Java hors SQL aux types du patient (`domain.patient.*`, `PatientJpa*`, à inspecter en PC5 : non vérifiées une à une) : `PecReadQueryService`, `PecDomainService`, `FhirExportService`, `SaisieInfirmierNotifier`, `SeanceApplicationService`, `SeanceDomainService`, `SeanceDetails`, `EffectifSql`, `GreffePdfService`, `KdigoGreffeQueryService`, `CahierDialyseReportService`, `LiberationALEnregistrementDeLaFiche`, `PatientMapper`, `PatientMigrator`, `OpeningBalanceMigrator`, `AffectationMigrator`.

## Comportements suspects

`.ddd/safety-net/known-bugs.md` : KB-PAT-01 à KB-PAT-18 (dont l'absence de garde de centre des lectures et des écritures d'assuré : KB-PAT-06/07).

## Questions de décision pour l'utilisateur (PC1)

1. Codes HTTP : 500 devenant 400 pour les corps/paramètres illisibles et les booleens absents (KB-PAT-01/02) ; 404 pour patient inconnu (KB-PAT-18) ; erreurs de validation en liste (`@Valid` sur `CreatePatientRequest`).
2. Unicité : numéro d'assurance insensible à la casse et aux espaces ? contrôle aussi à la modification ? contrainte en base après un contrôle de doublons (KB-PAT-03) ; doublon d'identité (nom, prénom, naissance) à signaler ou refuser ?
3. États : liste fermée des états (refuser `BIDON`) ? transitions permises depuis `DECEDE`, `GREFFE`, `GUERRI` ? date de fin de séjour obligatoire pour `OCCASIONNEL`/`VACANCIER_*` ? mouvement à chaque re-saisie de date (KB-PAT-04/05).
4. Droits et centres : garde de centre sur toutes les lectures et écritures d'assuré (403), refus de l'ADMIN sans centre, du rôle inconnu et de « aucun rôle » sur les écritures d'assuré (KB-PAT-06/07).
5. Fuseau : jour du centre pour l'affectation d'assuré, la reprise, l'admission future (KB-PAT-17).
6. Vocabulaire : états du legacy (`TRANSFERE`, `GUERRI`) et types de mouvement (`TRANSFERT`, `GUERISON`) conservés, ou termes du glossaire ?
7. Cohérence de dates (naissance future, antérieure à l'admission, admission future) : refuser ? (KB-PAT-08). Ligne héritée à colonnes nulles : migration de remplissage ou tolérance du `PUT` (KB-PAT-09).
8. Réponse de la fiche : DTO au lieu de l'entité de domaine, sans photo ni JSON bruts (KB-PAT-12).
