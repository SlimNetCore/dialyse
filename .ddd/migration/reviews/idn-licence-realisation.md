---
status: S-DDL1 livree ; IDN-licence phases 1 (filet) et 2 (refactoring sur place, ADR 0175 accepted) livrees ; non commitee
date: 2026-11-14
sources: ADR 0024, 0174, 0175, `inventaire-platform-restant-v2.md` lignes 13 et 16, `idn-lecture-realisation.md`, `TableDeLOrganisationTest`
---

# S-DDL1 (livree)
`backend/platform/src/main/resources/db/schema.sql` : `societes`, `centers`, `license`, `app_role`, `app_user`, `app_user_role`, `app_user_center` (ordre des FK : societes, centers, app_role, app_user, puis les deux rattachements), identiques a Flyway V1 + V12 + V46 + V56 ; aucune migration Flyway modifiee. `app_role`/`app_user` existaient avec un DDL divergent jamais applique (largeurs, UNIQUE inexistants en production) : corriges. Test : `SchemaH2DeLIdentiteEtDeLOrganisationTest` (signature des colonnes sur base vierge, FK/unicites/defauts) ; compare une fois au DDL genere par Hibernate : identique. Ajout de phase 2 : `ALTER TABLE app_user_center ... ADD CONSTRAINT IF NOT EXISTS` (FK vers `centers`, que Hibernate ne cree plus depuis que le rattachement est une collection de valeurs).

# IDN-licence phase 1 : filet (10 tests, 10 goldens approuves un par un)
Package `backend/platform/src/test/java/com/hemodialyse/backend/characterization/identite/idnlicence/`, goldens `.../resources/characterization/identite/idn-licence/**`.

| Classe | Tests | Goldens | Comportement verrouille |
|---|---|---|---|
| `EmissionDesLicences...` | 4 | par_centre, acces_par_centre, par_societe, refus_par_societe | emission par centre/societe, code du centre dans le jeton, centres actifs par nom, selection, atomicite, refus, acces |
| `ListeStatutEtActivation...` | 4 | liste, statut, activation, verification_en_ligne | liste (nom du centre, societe, ordre), statut, activation, revocation, autorite |
| `AmorcageDesLicencesDeDeveloppement...` | 1 | developpement | une licence par centre non servi (tous centres), rejeu |
| `VerificationEnLigneDUneInstanceCliente_...` | 1 | instance_cliente | contexte distinct, autorite = socket locale du test : periode de grace, revocation a distance, echec reseau ignore |

Normalisations : cle de licence `<CLE_SIGNEE>`, jti jamais enregistre, horodatages `<HORODATAGE>`, durees `<N> milliseconds` ; aucun scenario ne compare deux horodatages.

# IDN-licence phase 2 : refactoring sur place (livre)
| Couche | Fichiers |
|---|---|
| Langage publie (Organisation) | `AnnuaireDesCentres.codeDuCentre`, `AnnuaireDesSocietes.centresAvecLeurSociete` (+ `CentreRattache`), adapters JDBC, faux en memoire, 2 tests de contrat (14 verts) |
| Consommateurs | `LicenseService`, `LicenseRestController`, `DevLicenseBootstrapInitializer` (annuaires injectes) ; `AppUserJpaEntity.centers` -> `Set<UUID> centerIds` (`@ElementCollection` sur `app_user_center`) ; `UserSpecifications` (filtre par identifiant ; filtre par nom : identifiants resolus par `tousAvecNom` avec la semantique `LIKE`) ; `UserRestController` (noms lus une fois par requete) |
| Supprimes | `CenterJpaEntity`, `SocieteJpaEntity`, `CenterJpaRepository`, `SocieteJpaRepository` |
| Ecarts corriges | `LicenseRestController.activate` (refus -> 400 avec motif), `DatesIllisiblesDeLaDirectionAdvice` (emission : 403 avant 400, puis 400) |
| Garde | `TableDeLOrganisationTest.ENTITES_JPA_CONNUES` vide, aucun report ; les deux fichiers d'entite absents verifies |

Ecritures sur `centers`/`societes` dans platform (Grep) : aucune (toutes dans `CentresJdbc`/`SocietesJdbc`).

## Parite
10 goldens de licence : 7 inchanges, 3 changes volontairement (`emission/acces_par_centre`, `emission/refus_par_societe`, `licence/activation`) -> `*_nouveau_code.approved.json`, anciens archives en `_ecarts-legacy/**/*_avant_idn_licence.legacy.json` (diff verifie ligne a ligne : 500 -> 400/403 uniquement). 19 goldens IDN-lecture inchanges. Un premier essai avait perdu la FK `app_user_center -> centers` (le rattachement a un centre inconnu reussissait) : retablie dans `schema.sql`.
KB : KB-LIC-1 et 2 CORRIGES (2026-11-14) ; KB-LIC-3 (aucun controle d'etat du centre/societe a l'emission par centre) et KB-LIC-4 (`authority/verify` 403 sans session) REPRODUITS, a confirmer metiers/DPO <= 2026-12-15 ; restent reproduits en 500 : `authority/verify` sans `jti`, ADMIN sans centre sur `/activate`.

# Verification
Passes ciblees (`*IdnLicence*`, `*IdnLecture*`, `SchemaH2DeLIdentite*`, `TableDeLOrganisationTest`, `LicenseSocieteIntegrationTest`, `InitialSetupIntegrationTest`, `Annuaires*`) : normale, aleatoire, normale : 50 tests, 0 echec chacune. `check-arch.ts` : 0 violation. Build complet detache (`mvnw.cmd -o test -fae -q`) : 3562 tests ; 2 echecs : l'echec connu non touche (`InventairePl7...feuille_de_comptage`, date) et `ReglesDeGestionDocumentationTest` (identifiant RG-SEC-038 en double, ma regle renommee RG-SEC-067) ; corrige puis le test relance : vert. Aucun autre `[ERROR]`, processus java termine ; `*.received.json` et `backend/.ddd` supprimes. `covered.json`, `.ddd/state.json`, `seamAllowed` : non touches par moi (les 5 fichiers ont ete inscrits par l'utilisateur) ; la suppression des 4 fichiers d'entite/depot a ete faite sur instruction.
