---
slice: seance-se1-creation
status: characterized
date: 2026-11-07
sources: [.ddd/migration/slices/seance-se1-creation.md, ADR 0024, 0062, 0072, 0074, backend/contexts/seance/GEL.md, known-bugs.md KB-SEA-01 à 10]
---

# Filet SE1 : création de séance (legacy, refactoring sur place)

Remplace le filet du 2026-11-01, qui ne citait que des tests classiques (aucun golden, aucune création vérifiée en base). Le module `backend/contexts/seance` reste GELÉ, hors réacteur Maven (GEL.md, ADR 0074) : constaté, non branché, non touché.

## Périmètre vérifié par lecture du code
- `SeanceRestController` (legacy, 1 315 lignes) : `POST /api/v1/seances` (ADMIN, INFIRMIER), `POST /api/v1/seances/scan` (ADMIN, INFIRMIER, SECRETAIRE), `GET /api/v1/seances/{id}?centerId=` et `GET /api/v1/seances` (ADMIN, INFIRMIER, MEDECIN, SECRETAIRE).
- `SeanceApplicationService` (frontière transactionnelle) / `SeanceDomainService` (`create`, `createFromQr`, `scanAndValidate`, résolution du QR, `verifierProgramme`) / `Seance` / `ProgrammationSeance` ; adaptateurs `SeanceRepositoryAdapter` (table `seances`), `SeancePlanningJdbcAdapter` (jours de dialyse, fermetures, état du patient), `SeanceBillingEligibilityParLaCouverture` (PEC puis attestation, lu depuis Patient et couverture), `JourDuCentreLegacy`, `NotificationService` (temps réel `/topic/center/<id>/events`).
- Aucun événement de domaine ni outbox n'est publié par la création (pas de `publishEvent`) : seules les diffusions temps réel sont observables, et elles sont verrouillées.
- Consommateurs voisins déjà filetés ailleurs, cités et non rejoués : Facturation (`facturation/validation`, `retrait` : séances `FACTUREE`), Stock S6b (`stock/referentiel` : sorties pour séances), Planification P6 (`SeancePlace`, séances réalisées), Absences (rattrapages), Pilotage (`DirectionBreakdownQueryService`), Patient et couverture (PC3 PEC, PC4 attestations). Les fixtures PEC et attestation de ce filet reprennent leur schéma sans le contredire.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL de la table réelle `seances`, diffusions temps réel (canal du courtier), audit (`AuditActionResolver`). **Aucun seam, aucun code de production modifié** (`seamAllowed` vide, `seams.md` inchangé).

## Scénarios (31 tests, 31 goldens `src/test/resources/characterization/seance/se1/<dossier>/<scénario>/*.approved.json`)
| Dossier | Classe | Tests | Contenu |
|---|---|---|---|
| creation | `LaCreationDUneSeance…` | 13 | création sans date (jour du centre, statut CREE, rien de validé, diffusions), avec date (passé, futur, borne 2099), PEC et attestation (aucune, non validée, sans attestation, attestation échue, sans forfait, PEC d'un autre centre), bornes de période (effective, demandée, cumul), absence de contrôle du planning, patient inconnu / autre centre / centre de session (SUPERADMIN, sans centre), corps et valeurs (12 formes), absence d'unicité et scan devenu 500, droits par profil (15 profils + anonyme), refus sans écriture, audit, écritures simultanées, lignes illisibles |
| scan | `LeScanDUnPatient…` | 12 | infirmier (crée puis valide, second scan), administrateur, secrétaire (crée seulement), séance déjà présente par statut (6 statuts), 18 formes de QR, hors planning (motif, précision 255 / 256, motif inconnu), autres raisons (sommeil, pas admis, décédé, transféré, vacancier, centre fermé, administrateur), PEC (aucune écriture), droits par profil, cloisonnement et corps, audit, écritures simultanées |
| lecture | `LaConsultationEtLaListeDesSeances…` | 6 | détail d'une séance créée (forfait courant, patient, volets nuls), générateur du patient (maintenance), refus 400 / 403 du détail, liste (défaut, forfait, cloisonnement), filtres (période, statuts, texte, déverrouillage), tris et pagination bornés |

## Déterminisme
Dates fixes `<D+n>` ; jour du centre (`Africa/Algiers`, `<J>`) et jour de la JVM (scan, aussi `<J>`, voir KB-SEA-01) avec garde de minuit sur les deux fuseaux ; identifiants stables par alias ou `<UUID_GENERE_n>` ; horodatages masqués ; ordres de liste déterministes (identifiants stables) ; concurrence verrouillée par invariants uniquement ; données fictives. Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`), jamais `-Dcharacterization.approve=true`.

Exécutions : suite SE1 3 fois consécutives (31 verts), dont une en `-Dsurefire.runOrder=random`, 0 flaky ; suite complète `./mvnw -o test -fae` : 2 933 tests, vert avec `-Dsurefire.rerunFailingTestsCount=2`. Deux exécutions sans relance ont échoué chacune sur un test ANTÉRIEUR et sans lien (DM5 `un_diagnostic_actif_ne_peut_pas_etre_enregistre_deux_fois`, DM3 `le_parcours_nominal_demande_preleve_…`) : la colonne `modification` de leur golden (`updated_at > created_at`) vaut « jamais modifiee » quand les deux horodatages sont égaux à la précision de la base ; le test passe seul. Flaky préexistant du harnais `etatDesDossiers` / `etatDesDemandes` (DM3, DM5), non corrigé ici.

## covered.json
Ajoutés : `SeanceRepositoryAdapter`, `SeanceJpaEntity`, `SeanceJpaRepository`, `SeancePlanningJdbcAdapter`, `CreateSeanceRequest`, `ScanSeanceQrRequest`, `SeanceSearch`, `SeanceStatus`, `DerogationPlanning`, `MotifHorsPlanning`, `SituationPlanning`, `SeanceListItem`, `SeanceDetails`. Déjà présents : `SeanceRestController`, `SeanceApplicationService`, `SeanceDomainService`, `Seance`, `NotificationService`, `JourDuCentreLegacy`, `SeanceBillingEligibilityParLaCouverture`. **Non ajouté** : `ProgrammationSeance` (branches `GREFFE`, `GUERRI` et séjour limité à venir non exercées).

## Limites (non verrouillé)
Couverture de branches non mesurée et aucune mutation exécutée (aucun outil configuré dans le module, PIT documenté dans `.ddd/research/mutation-testing-pit.md`, tests Spring trop lents). H2 seulement : schéma PostgreSQL non rejoué ; `attestation_droit.date_fin` est NOT NULL en H2, l'attestation à fin nulle (traitée par la lecture de la couverture) n'est pas observable ; concurrence par invariants, pas de contrat à deux connexions (KB-SEA-02). Mémorisation de la place (`salle_id`, `creneau_id`, `generateur_id`) à la validation par scan, consommables à la validation, signature médecin, forfait surchargé, déverrouillage, dérogation, date modifiée (`PUT /{id}`), suppression, journal, tableau de bord, calendrier, volets médical et paramédical, scan-validation détaillée : **SE2 à SE7**, non couverts ici. `ProgrammationSeance` : règles `GREFFE`, `GUERRI`, séjour limité à venir. Aucun test de l'horloge à minuit (KB-SEA-01 lu dans le code). Les 18 lecteurs SQL de `seances` (liste blanche SE7) ne sont pas inventoriés ici.

## Questions ouvertes
Reproduire ou corriger KB-SEA-01 à 10, en particulier l'absence d'unicité et le scan en 500 (02), le QR de ponctuation (06, imputation au mauvais patient), le POST sans contrôle du planning (03), les 500 de forme (04) et l'audit (08). Statut `ABSENT` : retrait prévu avec Facturation F2 / Absences (ADR 0002).
