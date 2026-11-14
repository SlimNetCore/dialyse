---
status: S-DDL1 livree ; IDN-licence phase 1 (filet) livree ; phase 2 de production BLOQUEE par guard-legacy (en attente de l'utilisateur) ; non commitee
date: 2026-11-14
sources: ADR 0024, 0174, 0175 (propose), `inventaire-platform-restant-v2.md` lignes 13 et 16, `idn-lecture-realisation.md`, `TableDeLOrganisationTest`
---

# S-DDL1 (livree)
`backend/platform/src/main/resources/db/schema.sql` : `societes`, `centers`, `license`, `app_role`, `app_user`, `app_user_role`, `app_user_center` (ordre des FK : societes, centers, app_role, app_user, puis les deux rattachements), identiques a Flyway V1 + V12 + V46 + V56. `app_role`/`app_user` existaient avec un DDL divergent jamais applique (largeurs, UNIQUE inexistants en production) : corriges. Test : `SchemaH2DeLIdentiteEtDeLOrganisationTest` (2 tests : signature des colonnes sur base vierge, FK/unicites/defauts). Verifie une fois contre le DDL genere par Hibernate : identique.

# IDN-licence phase 1 : filet (livre, 10 tests, 10 goldens approuves un par un)
Package `backend/platform/src/test/java/com/hemodialyse/backend/characterization/identite/idnlicence/` (support + 4 classes), goldens `backend/platform/src/test/resources/characterization/identite/idn-licence/**`.

| Classe | Tests | Goldens | Comportement verrouille |
|---|---|---|---|
| `EmissionDesLicences...` | 4 | par_centre, acces_par_centre, par_societe, refus_par_societe | emission par centre (code du centre dans le jeton, centre desactive / societe desactivee acceptes, centre inconnu 400), par societe (centres actifs par nom, selection, atomicite, doublon, refus), acces |
| `ListeStatutEtActivation...` | 4 | liste, statut, activation, verification_en_ligne | liste (nom du centre, societe, centre supprime, ordre), statut (postes occupes, revocation, alteration, expiree), activation (ADMIN lie a son centre, SUPERADMIN, refus), autorite |
| `AmorcageDesLicencesDeDeveloppement...` | 1 | developpement | une licence par centre non servi (tous centres), rejeu sans doublon, revoquee conservee, illisible reemise |
| `VerificationEnLigneDUneInstanceCliente_...` | 1 | instance_cliente | contexte Spring distinct (cle publique seule, URL d'autorite = socket locale du test, aucun reseau) : periode de grace, revocation a distance, echec reseau ignore |

Normalisations : cle de licence toujours `<CLE_SIGNEE>`, `jti` jamais enregistre (UUID normalise), horodatages `<HORODATAGE>`, durees des messages de la bibliotheque de jetons `<N> milliseconds`, dates des scenarios constantes (2026-01-01 / 2099-12-31) sauf une date relative a `now - 1 jour` (jamais enregistree) ; aucun scenario ne compare deux horodatages. Cas deja couverts par IDN-lecture : centres accessibles, rattachements, listes d'utilisateurs avec noms de centres et filtre par nom de centre (`utilisateurs/*`).

Defauts reproduits (KB-LIC-1 a 4, a confirmer metiers/DPO <= 2026-12-15) : activation refusee = 500 (message masque) ; dates illisibles = 500 pour tous les roles ; aucun controle de l'etat du centre/societe a l'emission par centre ; `authority/verify` 403 sans session.

# Langage publie etendu (livre, dans `newCodeRoot`)
`AnnuaireDesCentres.codeDuCentre`, `AnnuaireDesSocietes.centresAvecLeurSociete` (+ record `CentreRattache`) ; adapters JDBC, faux en memoire, 2 tests de contrat de plus (14 verts) ; fakes de Seance et Patient-couverture completes.

# Phase 2 de production : BLOQUEE (guard-legacy) - approbation de l'utilisateur requise
Fichiers a modifier ou supprimer, absents de `covered.json` (rien n'a ete inscrit, `seamAllowed` reste []) :
1. `backend/platform/src/main/java/com/hemodialyse/backend/application/license/LicenseService.java` (filet : emission, activation, statut, amorcage)
2. `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/LicenseRestController.java` (filet : liste, emission par societe)
3. `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/config/DevLicenseBootstrapInitializer.java` (filet : amorcage)
4. `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/entity/AppUserJpaEntity.java` (filet : utilisateurs/listes, fiche, rattachements)
5. `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/spec/UserSpecifications.java` (filet : utilisateurs/listes)
6. A supprimer : `.../persistence/entity/CenterJpaEntity.java`, `.../persistence/entity/SocieteJpaEntity.java`, `.../persistence/repository/CenterJpaRepository.java`, `.../persistence/repository/SocieteJpaRepository.java`
Deja couvert (modifiable) : `UserRestController.java` (changera avec `AppUserJpaEntity`). `LicenseOnlineCheckJob` et `LicenseEnforcementFilter` : aucun changement. Tests a faire evoluer apres : `TableDeLOrganisationTest.ENTITES_JPA_CONNUES` (vider), le reste du filet reste inchange (parite attendue 10/10 + 19/19).

Plan (ADR 0175 propose) : voir l'ADR ; aucune ecriture sur `centers`/`societes` ne subsiste dans platform (inventaire par Grep : toutes dans `CentresJdbc`/`SocietesJdbc` d'Organisation).

# Verification
Passes ciblees `*IdnLicence*` + `SchemaH2DeLIdentite*` : normale, aleatoire verts (10 + 2 tests) ; `Annuaires*`, `JourDuCentrePublieTest`, `HorlogeDesCentresPubliesTest` verts ; `check-arch.ts` : 0 violation. Build complet detache (`mvnw.cmd -o test -fae -q`, apres S-DDL1 + filet + langage publie, avant la phase 2) : 3562 tests, 1 echec connu et non touche (`InventairePl7...feuille_de_comptage`, date), aucun autre `[ERROR]`, processus java termine ; `*.received.json` et `backend/.ddd` supprimes. Le build complet de la phase 2 reste a faire apres deblocage.
Fichiers de production modifies : aucun (hors `schema.sql` et le langage publie d'Organisation). `covered.json`, `.ddd/state.json`, `seamAllowed` : non touches.
