---
slice: seance-se1-creation
status: planned
date: 2026-10-29
order: 1
context: seance
sources: [.ddd/tactical/seance.md, ADR 0002, 0006, 0062, 0074]
---

# SE1 — Création de séance (conditionnée par PEC valide)

> Statut vrai au 2026-11-02 : `planned` (la mention `built` posée par 2978e639 était fausse). Module gelé (`backend/contexts/seance/GEL.md`, ADR 0074) : persistance sur colonnes inexistantes (`pec_id`, `type_seance`), statuts sans `FACTUREE`/`ABSENT`, aucune parité. Reprise sur place : mêmes tables, mêmes URL, statuts du LEGACY ; filet de création et d'unicité à écrire d'abord. Ordre de reprise de la Séance : dernier.

Agrégat `Seance` (root). Cycle `CREE -> VALIDEE -> SIGNEE`. Création conditionnée par une PEC valide (port
`PECRepository` Patient & couverture).

## Cas d'usage

`CreerSeance`, `ConsulterSeance`, `ListerSeancesDuJour`.

## Entrées legacy

`bc-seance` (Seance), `SeanceApplicationService`, `SeanceRestController`.

## Prérequis

Patient & couverture doit exposer `PECRepository` (ADR 0062). Organisation disponible.

## Données

Table `seances` ; aucune migration. centerId obligatoire. Statut `ABSENT` supprimé (ADR 0002).

> Mise à jour 2026-11-07 : le statut `ABSENT` reste LISIBLE (reliquat, ADR 0024 : statuts du legacy), le module ancien est retiré et `contexts/seance` reconstruit (ADR 0088). Décisions, résidus et plan de retrait : `.ddd/architecture/adr/0088-seance-se1-reprise-sur-place-de-la-creation-du-scan-et-de-la-consultation.md`. État pipeline : `characterized` (jamais `built` avant la session B).

## Avancement (session A faite le 2026-11-07 ; session B restante)

### Fait en session A (étapes 1 à 3, rien de routé, aucun contrôleur ni golden touché)
| Couche | Contenu (`backend/contexts/seance`) | Tests |
|---|---|---|
| Domaine | `Seance` (création, hors planning, restitution + `Jalons`), `StatutDeSeance` (5 statuts du legacy), `DerogationHorsPlanning`, `MotifHorsPlanning`, `SituationDuPlanning`, `ProgrammationDuPatient`, `CauseDeNonFacturation`, `CodeScanne` (KB-SEA-06), `RechercheDeSeances`, `Pagination`, refus typés (`SeanceRefusee`+`MotifDeRefus`, `SaisieInvalide`, `PatientIntrouvable`, `SeanceIntrouvable`, `SeanceDuJourAmbigue`, `EcritureConcurrente`, `SeanceIllisible`), port `Seances` | 40 (domaine) |
| Application | `CreerUneSeance`, `ScannerUnPatient` (+ `ResoudreLePatientScanne`), `ConsulterUneSeance`, `ListerLesSeances` ; ports : `SessionCourante`, `UniteDeTravail`, `HorlogeDuCentre`, `GenerateurDIdentifiants`, `PatientsDeLaSeance`, `PatientsScannables`, `PlanningDesPatients`, `EligibiliteALaFacturation`, `ValidationDeSeance`, `DiffusionsDeSeance`, `GenerateursDesPatients`, `VoletsDeLaSeance`, `ForfaitsDeSeance`, `ConsommationsDeLaSeance`, `HistoriqueDesSeances` | 48 (fakes en mémoire, dont atomicité du scan) |
| Infrastructure | `SeancesJdbc` (table `seances` seule, 9 colonnes écrites, verrou consultatif du jour), `UniteDeTravailSpring`, `GenerateurDIdentifiantsAleatoires`, ACL `SessionLegacy`, `EligibiliteParLaCouverture`, `ConsommationsDeLaSeanceDuStock` | 9 (ACL table-driven) + contrat `Seances` : mémoire 11, H2 14, PostgreSQL 16 16 (script) |
| Câblage | `SeanceConfiguration` (adapters seulement, AUCUN cas d'usage exposé), `backend/pom.xml`, `platform/pom.xml`, `Dockerfile`, `ArchitectureHexagonaleTest` (10 règles), `check-arch.ts` 0 violation, `FrontieresDesContextesTest` 18 vertes | 10 |
| Script | `backend/scripts/seance-se1-pg16-rejeu.sh 55501` (PostgreSQL 16 jetable, mot de passe par `SEANCE_PG16_PASSWORD`) : 28 colonnes de `seances`, aucune unicité inventée, requêtes de contrôle, contrat PG 16/16 verts | |

### Fait en session B1 (2026-11-07 ; NON routé, état pipeline toujours `characterized`)
| Couche | Contenu | Tests |
|---|---|---|
| Langage publié (`seance.publishedlanguage`, types primitifs/records) | `PlanningFourni`, `FichesFournies`, `GenerateursFournis`, `VoletsFournis`, `ForfaitsFournis`, `HistoriqueFourni`, `ValidationFournie`, `DiffusionsFournies` : un port par source legacy (précédent dossier-medical, AUCUNE exception dans `FrontieresDesContextesTest`) | |
| ACL du module (`infrastructure/acl/legacyseance/`) | `PlanningDesPatientsParLeLegacy`, `PatientsParLeLegacy` (patients de la séance + scannables), `GenerateursParLeLegacy`, `VoletsParLeLegacy`, `ForfaitsParLeLegacy`, `HistoriqueParLeLegacy` (ignore et journalise un statut illisible), `ValidationParLeLegacy` (relit la séance), `DiffusionsParLeLegacy` | 7 (table-driven) |
| Plateforme (`platform/.../infrastructure/acl/seance/`, `*Legacy`, retrait avec SE2/SE7) | `PlanningFourniLegacy` (enveloppe `SeancePlanningPort`), `FichesFournieLegacy` (`PatientRepositoryPort`), `GenerateursFournisLegacy` (`gmao_equipements`), `VoletsFournisLegacy`, `ForfaitsFournisLegacy` (SQL de `loadCurrentForfait` repris), `HistoriqueFourniLegacy` (jointure SQL, statuts illisibles exclus et comptés dans un WARN), `ValidationFournieLegacy` (`SeanceUseCase.validate(..., List.of())`), `DiffusionsFournieesLegacy` | couverts par la parité (B2) |
| Câblage | `SeanceConfiguration` : 8 ACL + les 4 cas d'usage | |
| Interface | `SeancesControleur` (4 routes, droits du legacy, clés JSON du legacy), `ErreursDeSeanceAdvice` (table des refus, 403 avant 400), `DroitsDeLaRoute`, DTO `CreationDeSeanceDemande`, `ScanDeSeanceDemande` | 8 (MockMvc autonome) |
| Garde | `TableDuPatientCouvertureTest` : 2 jalons nommés datés (`HistoriqueFourniLegacy`, `ForfaitsFournisLegacy`, revue 2026-12-15) | |

Non routé : `SeancesControleur` et `ErreursDeSeanceAdvice` sont conditionnés par `seance.se1.controleur-actif=true` (absent par défaut). Les 4 handlers legacy répondent encore ; activer la propriété ferait échouer le démarrage (mapping ambigu). B2 retire les handlers ET la condition dans le même changement. `AuditSansTrace` n'est pas créé : le legacy audite toujours le POST et le scan (KB-SEA-08), le mécanisme actuel (`AuditActionResolver` par chemin) reste valable.

### Reste à faire : B2 (parité et routage) puis B3 (docs et clôture)
- **B2** : contrat HTTP complet contre les goldens (rejouer les 31 scénarios avec le nouveau contrôleur, `*_nouveau_code` pour KB-SEA-01/02/04/06/07, legacy archivé octet pour octet dans `_ecarts-legacy`) ; contrat de port `HistoriqueDesSeances` et `ForfaitsDeSeance` mémoire/H2/PG16 ; retrait des 4 handlers et des résidus (seam AVANT l'édition) ; suppression de la condition `seance.se1.controleur-actif` ; verrou ArchUnit contre de nouveaux appelants des écritures legacy résiduelles ; `SeancePlanningJdbcAdapter` et `SeancePlanningPort` RESTENT (utilisés par `PlanningFourniLegacy`) : l'ADR 0088 est à corriger sur ce point.
- **B3** : RG-SEA et annexe de traçabilité, glossaire `proposed`, `verifications-production.md`, ADR 0088, bilan nominatif des résidus (revue 2026-12-15), `state.ts set slices.seance-se1-creation built`.

### Plan initial de la session B (historique)
1. **Adapters de la plateforme pour les ports restants** (ACL temporaires, `infrastructure/acl/seance/`, retrait avec SE2/SE7) : `PlanningDesPatients` (enveloppe `SeancePlanningJdbcAdapter`, traduire `SituationPlanning` vers `SituationDuPlanning`), `PatientsDeLaSeance` et `PatientsScannables` (sur `PatientRepositoryPort` : `findById`, `findByCodePatient`, `findByNumeroAssurance`, `findAllByCenter`, avec le numéro d'assurance de l'assuré), `GenerateursDesPatients` (`gmao_equipements`, `deleted_at IS NULL`), `VoletsDeLaSeance` (volets paramédical et médical), `ForfaitsDeSeance` (surcharge de `seances` puis PEC couvrant le jour puis repli, SQL de `loadCurrentForfait`), `HistoriqueDesSeances` (JPA Criteria de `SeanceRepositoryAdapter.search` ou jointure SQL ; ignorer et journaliser une ligne de statut illisible, KB-SEA-07), `ValidationDeSeance` (appelle `SeanceUseCase.validate(..., List.of())` et relit), `DiffusionsDeSeance` (`notifySeanceCreated`, `notifySaisieInfirmier`, `notifySeanceValidated`). Contrat de port identique mémoire/H2/PG16 pour `HistoriqueDesSeances` et `ForfaitsDeSeance` (le fake et l'adapter réel).
2. **Frontière** : ces adapters implémentent des ports de `seance.application` depuis `platform` : `FrontieresDesContextesTest.le_legacy_n_utilise_la_seance_que_par_son_langage_publie` les refuserait. Choisir (ADR 0088 §3) : exception NOMMÉE et DATÉE (revue 2026-12-15) dans la règle, ou ports déplacés dans `publishedlanguage` avec types propres. Trancher et consigner.
3. **Câblage** des 4 cas d'usage dans `SeanceConfiguration` (aujourd'hui seuls les adapters le sont).
4. **Adapters entrants** (`interfaces/http`) : `POST /api/v1/seances`, `POST /api/v1/seances/scan`, `GET /api/v1/seances/{id}`, `GET /api/v1/seances`, DROITS du legacy (`ADMIN, INFIRMIER` pour le POST ; `ADMIN, INFIRMIER, SECRETAIRE` pour le scan ; `ADMIN, INFIRMIER, SECRETAIRE, MEDECIN` pour le détail ; `ADMIN, INFIRMIER, MEDECIN, SECRETAIRE` pour la liste), 403 AVANT 400 (centre du corps contrôlé avant la lecture du corps : KB-SEA-04), format de réponse et clés JSON du legacy (`buildSeanceCreationPayload`, détail, liste), mapping d'erreurs : `SeanceRefusee` 422 + `MotifDeRefus.code()`, `SaisieInvalide` 400, `PatientIntrouvable` 404 `PATIENT_INTROUVABLE`, `SeanceIntrouvable` 400 (golden `detail_refus`), `SeanceDuJourAmbigue` 409 `SEANCE_DU_JOUR_AMBIGUE`, `EcritureConcurrente` 409, `SeanceIllisible` 400 sans nom de classe, `AccesRefuse` 403. Corps JSON : `precisionHorsPlanning` > 255 en 400 (`VALIDATION_ERROR` du golden `precision de 256`).
5. **Audit** : reproduire `SEANCES_CREATION` pour le POST et le scan seulement (KB-SEA-08, y compris refus 403/422/500 devenus 409/400) ; vérifier le mécanisme actuel avant de retirer les handlers.
6. **Parité** : rejouer les 31 scénarios des goldens `characterization/seance/se1/` contre le nouveau code (routage par retrait des 4 handlers, ADR 0024, pas de façade), écarts attendus à consigner : KB-SEA-01 (jour du centre pour le scan), 02 (409), 04 (400/403), 06 (404 et QR de ponctuation), 07 (liste). Chaque écart : décision tracée dans l'ADR 0088 après accord de l'utilisateur.
7. **Retrait** des quatre handlers de `SeanceRestController` et de leurs aides, de `create`, `createFromQr`, `scanAndValidate`, `resolvePatientIdFromQr`, `verifierProgramme`, `getDetails`, `search` (service de domaine, service applicatif, `SeanceUseCase`, adapter) quand plus rien ne les appelle ; garde ArchUnit « plus aucun appelant » (revue 2026-12-15) ; `covered.json` et seams selon `guard-legacy.ts` (seam inscrit AVANT l'édition, refermé après).
8. `state.ts set slices.seance-se1-creation built` seulement après parité verte et `/ddd-verify`. Documentation : référentiel des règles de gestion et doc fonctionnelle à régénérer (mémoire projet).

### Écarts de parité connus (non encore mesurés, à confirmer en session B)
Écarts voulus uniquement (décisions de l'ADR 0088) ; tout autre écart est un défaut du nouveau code.

### Plan de bascule
Refactoring sur place (ADR 0024, mémoire projet) : pas de façade, pas de drapeau, pas de projection ni de métrique ; mêmes tables et mêmes URL ; la bascule est le remplacement des handlers en session B, réversible par retour git. Aucune activation en production par l'assistant.