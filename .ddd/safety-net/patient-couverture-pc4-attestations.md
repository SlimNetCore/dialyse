---
slice: patient-couverture-pc4-attestations
status: characterized (filet vert sur le code actuel, rien de commité)
date: 2026-11-04
sources: .ddd/migration/slices/patient-couverture-pc4-attestations.md, .ddd/tactical/patient-couverture.md, ADR 0024/0064/0073/0076/0077, docs/reference/regles-de-gestion/04-patients-assures-pec.md, known-bugs.md KB-PAT-38 à 48
---

# Filet de caractérisation : patient-couverture-pc4-attestations

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Remplace l'ancienne note (2026-11-01, tests unitaires `bc-insurance` seulement). Référence : les quatre routes d'attestation de `PecRestController` (`/api/v1/pec/attestations*`), `AttestationDomainService`/`AttestationDroit`, `AttestationRepositoryAdapter` (cache), `PecReadQueryService`/`AttestationSpecifications` (recherche), `AttestationJpaEntity`/`AttestationJpaRepository`, et leurs lecteurs.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL de `attestation_droit` (jointure `patients`), diffusions (canal du courtier), notifications durables, audit ; les lecteurs sont appelés tels que câblés : `SeanceBillingEligibilityPort` (bean), `POST /api/v1/seances`, `GET /pec/{id}/session-allowed`, `POST /pec`, `POST/PUT /patients` (fiche), `/api/v1/dashboard/stats`. Aucun mock. Harnais : `pc4/AttestationCharacterizationSupport` (au-dessus de `CouvertureCharacterizationSupport`). Goldens : `src/test/resources/characterization/patientcouverture/pc4/**`.

## Déjà verrouillé avant (non rejoué)

| Filet | Verrouille |
|---|---|
| PC1 `creation`, `modification` | attestation saisie sur la fiche : obligatoire hors vacancier, jamais dupliquée si identique, fin avant début refusée, identifiant imposé, attestation existante qui suffit, `CouvertureDeLaFicheJdbc` (ACL temporaire) |
| PC3 `creation`, `cycle` | attestation du non-vacancier à la création de PEC et pour `session-allowed` (bornes incluses, inversée, autre centre) via `AttestationsDuPatientJdbc` (ACL temporaire, jour du centre) |
| PC3 `lecteurs` | cause de non-facturabilité (11 cas dont 3 d'attestation) |
| `bc-insurance` JUnit | validation des dates et dépôt en mémoire (pas le comportement HTTP) |

## Scénarios ajoutés (28, 5 classes, `pc4`)

| Classe | Nb | Verrouille |
|---|---|---|
| `CreationDeLAttestation` | 6 | nominale (réponse, ligne, diffusion `ATTESTATION_CREATED`, aucune notification durable) ; 9 bornes de période ; 17 refus de forme ; rattachement non vérifié (patient d'un autre centre, inexistant, vacancier, centre inconnu) ; doublon, chevauchement, inclusion, contiguïté ; identifiants distincts |
| `LectureEtSuppressionDesAttestations` | 5 | liste d'un patient (forme brute, isolement centre/patient, 5 cas) ; 7 refus de forme ; cache (7 étapes) ; suppression (6 cas, diffusions, centre et patient non vérifiés) ; 5 refus de suppression |
| `RechercheDesAttestations` | 6 | forme et tri (`dateFin` décroissant), 7 pages ; attestation orpheline et patient d'un autre centre ; 15 bornes et refus ; 22 filtres texte ; 21 filtres de dates ; isolement |
| `AutorisationsEtCentresDesAttestations` | 4 | matrices 11 profils + `PHARMACIEN`, `DIRECTEUR_ADMINISTRATIF`, rôle personnalisé de 4 routes + anonyme ; absence de garde de centre ; squelette du module non servi, routes voisines |
| `EffetsEtLecteursDesAttestations` | 7 | éligibilité de la séance (19 cas, bornes incluses, vacancier) ; cycle API d'attestation / PEC / `session-allowed` / facturabilité (16 étapes) ; création de séance HTTP (422 / 200) ; attestation de la fiche lue et supprimée par les routes ; compteurs du tableau de bord ; audit et diffusions ; concurrence (créations, suppressions) |

## Non-déterminisme

Dates par alias relatifs nommés (`<J>`, `<J-5>`, `<J+50>`...), jamais de date réelle ni de jour de mois ou de semaine (le harnais échoue si une date non nommée reste dans un golden) ; garde de minuit du fuseau du centre (héritée du harnais Planification). Horodatages de fixtures fixes ; identifiants de fixtures stables et nommés. Ordre de la liste d'un patient (aucun tri) : non verrouillé, les goldens la trient. Suppressions simultanées : seule l'ensemble des issues permises (200 ou 500 `DATA_ACCESS_ERROR`, une au moins réussit) est verrouillé. Tableau de bord : calcule l'échéance au jour de la JVM (KB-PAT-47) : le scénario est ignoré (`Assumptions`) si le jour de la JVM diffère du jour du centre.

## Stabilité

3 exécutions de `characterization/patientcouverture/**` (149 tests : PC1, PC2, PC3 existants + 28 de PC4) : ordre alphabétique, puis deux ordres aléatoires de classes et de méthodes (`-Dsurefire.runOrder=random -Djunit.jupiter.testmethod.order.default=...MethodOrderer$Random`). Résultat : 3 exécutions, 149 tests chacune, 0 échec, 0 erreur, 0 ignoré (y compris le scénario du tableau de bord), 0 flaky. Commande : `cd backend; ./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.patientcouverture.**.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer un test à la fois).

## Couverture et mutation

Pas de JaCoCo ni de PIT sur la zone : aucune mesure de branches ni de mutants. Couverture raisonnée : `AttestationDomainService` (création, refus, liste, suppression), `AttestationDroit` (dates obligatoires, fin avant début), `AttestationRepositoryAdapter` (cache et évictions, sauf `existsValidAt`), `AttestationSpecifications` (chaque filtre et chaque forme de date), `PecReadQueryService` (moitié attestation), `SeanceBillingEligibilityJdbcAdapter` (clause d'attestation), les quatre routes.

## Limites (NON verrouillé)

- `AttestationRepositoryPort.existsValidAt` et son cache `patient.attestation.existsValidAt` : plus aucun appelant (KB-PAT-48), non exercés.
- Ordre de la liste d'un patient (sans tri) ; ordre des ex æquo de la recherche ; ordre de plusieurs PEC créées dans la même milliseconde.
- Expiration (TTL) du cache ; PostgreSQL (`LIKE`, interclassement, verrous, vraie concurrence) : H2 seulement, une paire d'appels.
- `DashboardRestController` : seuls les compteurs d'attestation sont lus (les compteurs de PEC, de patients et le reste relèvent de PC5) ; `DocumentRestController`/`reports/attestation.jrxml`, `liste_attestations.jrxml` (impression), migration historique (`HistoricalRecordJdbcAdapter`, `HistoryRules.Attestations`, `MigrationRollbackJdbcAdapter`), `ReferentialAdminTables` : non rejoués.
- Séance sans `dateSeance` (`LocalDate.now()` de la JVM) : non rejouée ; filets Séance.
- Fuseau : bascule de minuit non rejouée ; jour JVM contre jour du centre non différencié (scénario ignoré si les jours diffèrent).
- Notification durable des attestations : aucune (non durable) ; diffusion de la recherche : aucune.

## Lecteurs et écrivains hors module de `attestation_droit`

Écrivains : `PecRestController`/`AttestationRepositoryAdapter` (routes), `CouvertureDeLaFicheJdbc` (fiche, PC1, ACL temporaire), `HistoricalRecordJdbcAdapter` (migration historique), `MigrationRollbackJdbcAdapter` (suppression), `seed.sql`.
Lecteurs : `SeanceBillingEligibilityJdbcAdapter` (Séance, y compris vacancier), `AttestationsDuPatientJdbc` (PC3, jour du centre, ACL temporaire), `CouvertureDeLaFicheJdbc.attestationEnregistree` (PC1), `PecReadQueryService` (recherche), `DashboardRestController` (compteurs, jour JVM), `DocumentRestController` (impression), `HistoryRules.Attestations` (migration).

## Comportements suspects

`.ddd/safety-net/known-bugs.md` : KB-PAT-38 à 48 (garde de centre absente, rattachement non vérifié, suppression sans contrôle, aucun contrôle de période, 500 sur erreurs de format, audit `PEC_*`, cache de liste, recherche, éligibilité même pour le vacancier, jour JVM, code mort).

## Questions de décision pour l'utilisateur (PC4)

1. Garde de centre : appliquer la décision du 2026-11-02 (403 si `centerId` ≠ centre de session, ADMIN sans centre refusé) aux attestations (KB-PAT-38) ? Qui peut créer/supprimer (ADMIN, SECRETAIRE seulement, ou tout compte comme aujourd'hui) ?
2. Rattachement : refuser une attestation pour un patient inexistant ou d'un autre centre (KB-PAT-39) ? Bornes : début = fin autorisé ? doublons / chevauchements / contiguïté refusés ou fusionnés (KB-PAT-41) ? période échue ou future autorisée à la saisie ? règle R5 (« attestation liée à une PEC active », tactique) : à retenir ou abandonner (jamais implémentée) ?
3. Vacancier : l'attestation reste-t-elle exigée pour facturer un vacancier alors que la saisie l'en dispense (KB-PAT-46, lié à KB-PAT-25) ?
4. Suppression : autorisée si une PEC validée en dépend (KB-PAT-40) ? 404 sur identifiant inconnu et pas de diffusion fantôme ?
5. Fuseau : jour du centre pour les compteurs du tableau de bord et la séance sans date (KB-PAT-47) ; compteurs « valides » / « à échéance non échues » ?
6. Droits et vocabulaire : actions d'audit `ATTESTATION_CREATION`/`ATTESTATION_SUPPRESSION`, pas d'audit de la recherche (KB-PAT-43) ; erreurs 400/404/405/415 propres (KB-PAT-42) ; que faire du squelette mort du module (KB-PAT-48) ?
