---
status: approved
date: 2026-11-11
sources: ADR 0133 (S6a), backend/platform/src/main/java/.../infrastructure/migration/**, web/rest/MigrationRestController.java
---

# Filet S6a : outil de reprise historique (patients, assures, affectations, soldes d'ouverture)

Niveau : HTTP MockMvc (securite reelle + RoleScopeFilter, Spring complet, H2) sur `/api/v1/admin/migration/**`, plus etat des tables relu en SQL (`patients`, `assure`, `assure_patient`, `migration_id_map`, `migration_batch`, `migration_entity_run`, `factures*`). Risque eleve (production, ecritures de masse) : le filet compare l'etat de base, pas seulement les codes HTTP. Aucun seam, aucun mock, `seamAllowed` = [].

Fichiers : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/migration/Historique*` ; goldens `backend/platform/src/test/resources/characterization/migration/historique/*.approved.json` (18).

## Scenarios (18 goldens, 18 tests)
| Classe | Golden | Comportement verrouille |
|---|---|---|
| Assures | assures_verification_import_et_rejeu | verification sans ecriture, import, rejeu (UPDATED), non-effacement des valeurs vides, journal des executions |
| Assures | assures_refus_tout_ou_rien | doublons dans le fichier, autre centre, formats invalides, identifiant d'origine deja repris : aucune ecriture |
| Assures | assures_rapprochement_d_un_assure_existant | MATCHED_EXISTING, valeurs conservees |
| Patients | patients_assure_lui_meme_et_ayant_droit | creation, affectation principale, rejeu |
| Patients | patients_references_aux_referentiels | toutes les colonnes ecrites, resolution salle/creneau/medecin/transporteurs/payeur, references introuvables |
| Patients | patients_incoherences_refusees | dates, doublons fichier, numero/code deja pris |
| Patients | patients_avertissements_et_rapprochement_par_numero | POSSIBLE_DUPLICATE, MATCHED_EXISTING, EVENT_DATE_MISSING |
| Patients | patients_formats_invalides_et_assures_manquants | erreurs de format ligne par ligne, assure obligatoire/introuvable/autre centre |
| Patients | patients_correspondance_de_valeur_et_synonymes | correspondance de valeurs, synonymes, formats de date |
| Patients | patients_rejeu_sans_effacement_et_changement_d_assure | rejeu, changement d'assure principal, patient supprime puis rejoue |
| Patients | patients_isolement_des_centres | aucun rapprochement inter-centres |
| Affectations/soldes | affectations_controles_import_et_rejeu | controles, import, rejeu, rapprochement d'historique |
| Affectations/soldes | soldes_ouverture_controles_et_remplacement | controles, creation, remplacement au rejeu, facture soldee ignoree |
| Droits/annulation | droits_et_garde_de_centre | roles, centre, lot d'un autre centre, SUPERADMIN |
| Droits/annulation | annulation_supprime_ce_que_le_lot_a_cree | rollback (`MigrationRollbackJdbcAdapter`) patients/assures/affectations/factures, conserve les UPDATED, reprise apres annulation |
| Droits/annulation | annulation_refusee_si_donnees_utilisees | blocages, lot annule/inconnu |
| Droits/annulation | fichier_et_lot_erreurs_generales | fichier vide, en-tetes seuls, colonnes absentes/ignorees, donnee inconnue, 5001 lignes, lot cloture/deja ouvert |
| Droits/annulation | correspondances_de_valeurs_et_modeles | erreurs de correspondance, modeles CSV |

Le nominal des soldes (factures/lignes/reglements) et la colonne generateur restent verrouilles par `FacturesDeRepriseCharacterizationIntegrationTest` et `PatientsGenerateursMigrationCharacterizationIntegrationTest` (non dupliques).

## Non couvert (et pourquoi)
- Entites d'historique (attestations, prises en charge, dossiers, antecedents, serologies, abords, analyses, seances) : hors perimetre S6 (tables non liees a bc-patient/bc-assure) ; `MigrationHistoryIntegrationTest` les couvre sans golden.
- `GET /batches/{id}` (journal des executions) : non deterministe en cas d'egalite d'`executed_at` (KB-S6a-7) ; le journal est verrouille par SQL (`executions()`).
- Fichiers Excel (.xlsx) : lecteur `TabularFileReader` partage, non modifie par S6.
- Charge/volume reel, concurrence (deux imports simultanes), PostgreSQL (verrouille sur H2 uniquement).
- Valeurs `false` vs `NULL` des booleens de `patients` : le releve omet les booleens a faux et `nombre_enfants = 0` (lisibilite) ; une colonne qui deviendrait true/non nulle est detectee.
- Le filet ne verrouille pas `MigrationEntity` (colonnes d'historique non couvertes).

## Execution
18 tests, 4 classes, 1 contexte Spring (meme configuration que les autres tests `@SpringBootTest(MOCK)` + profil `test`), ~35 s dont ~33 s de demarrage du contexte. 3 passes dont `-Dsurefire.runOrder=random` : vertes, 0 flaky.
Non-determinisme normalise : UUID generes (alias par cle metier), horodatages, code patient genere `PAT-xxxxxxxx`, cles JSON triees. Caches applicatifs vides en `@BeforeEach/@AfterEach` (fixtures SQL).

## Bugs releves
KB-S6a-1 a KB-S6a-7 dans `known-bugs.md` (a trancher pour la reecriture : reproduire ou corriger).
