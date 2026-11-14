---
status: phase 1 realisee (filet), phase 2 en attente d'inscription des fichiers dans covered.json
date: 2026-11-14
sources: ADR 0024, 0107, 0140 D7, 0157-0161, `inventaire-platform-restant-v2.md` ligne 12 (decision D6), `TableDeLOrganisationTest`, `rp3c-realisation.md`
---

# IDN-lecture - phase 1 : filet de caracterisation

## Perimetre (6 entrees de `TableDeLOrganisationTest`, butoir 2027-03-31, aucun report)
| Entree | Lectures de `centers` / `societes` |
|---|---|
| `AuthService` | nom du centre (login, rebuild), societes proposees (actives avec >= 1 centre actif), centres d'une societe, centres accessibles (SUPERADMIN : tous ; sinon centres actifs de la societe du centre courant), controle centre/societe actifs, raison sociale + actif de la societe (session direction) |
| `DirectionAccountService` | existence de la societe (`COUNT societes`) |
| `SocieteAdminAccountService` | existence de la societe, nom + appartenance d'un centre a la societe, liste des administrateurs avec noms de centres (jointure), centres d'un compte dans la societe |
| `UserRestController` | `GET /users/{id}` : centres du compte avec nom (les listes passent par JPA `CenterJpaEntity`, hors garde) |
| `DevSuperAdminBootstrapInitializer` | `SELECT id FROM centers` (tous les centres, actifs ou non) |
| `SessionLegacy` (pilotage) | habilitation : compte actif + rattache + societe active |

## Filet livre (nouveaux fichiers de test uniquement)
Package `backend/platform/src/test/java/com/hemodialyse/backend/characterization/identite/idnlecture/` (6 fichiers dont le support), goldens `backend/platform/src/test/resources/characterization/identite/idn-lecture/**` (19 goldens approuves un par un depuis `.received`, jamais `-Dcharacterization.approve`).

| Classe | Tests | Goldens | Comportement verrouille |
|---|---|---|---|
| `ConnexionEtSelection...` | 5 | annuaires_publics_de_connexion, connexion_a_un_centre, session_de_centre, connexion_sans_centre, centres_accessibles | annuaires, 13 refus de login (ordre des controles), `/me` + refresh, direction/proprietaire, centres accessibles |
| `UtilisateursEtRattachements...` | 3 | listes, fiche, rattachements | listes/recherche avec noms de centres, fiche (SQL), remplacement des centres |
| `ComptesDeSociete...` | 5 | administrateurs_listes/creation/modification/activation, direction_comptes | listes + centres de la seule societe, CENTRE_HORS_SOCIETE, SOCIETE_INTROUVABLE (controlee en premier), revocation des jetons |
| `AmorcageDeDeveloppement...` | 4 | sans_effet, premier_demarrage, rejeu, compte_existant | desactive par defaut, cles reelles, creation idempotente, TOUS les centres rattaches (role SUPERADMIN sauvegarde/restaure) |
| `SessionDeLaDirection...` | 2 | habilitation, session | matrice directe de `SessionLegacy` (complete PL10b) |

Total : 19 tests. Trois passes ciblees `-Dtest=*IdnLecture*` : vertes. Mots de passe : fictifs de test uniquement ; jetons, hash et cookies jamais enregistres (noms de cookies seulement) ; aucune comparaison d'horodatages.

## Constats (reproduits, non corriges ; a confirmer metiers/DPO <= 2026-12-15)
1. `GET /auth/societes/{id}/centres` avec un identifiant illisible : 500 `INTERNAL_ERROR` (attendu : 400). Hors perimetre.
2. Routes `/societes/{id}/admin-accounts` et `/direction-accounts` : la validation `@Valid` precede `@PreAuthorize` : un ADMIN/MEDECIN avec corps invalide recoit 400 avant 403 (golden `administrateurs_creation`, `direction_comptes`). Contraire a la regle 403 avant 400 ; reproduit en phase 1 (aucun changement de comportement sans decision).
3. Le login n'echo pas la societe choisie (`societeId`/`societeName` null en session de centre).
4. Limite du filet : la suppression d'un compte portant des roles repose sur les `ON DELETE CASCADE` PostgreSQL absents du schema H2 ; seul un compte sans role est caracterise.

## Fichiers legacy a modifier en phase 2 : etat de `covered.json`
| Fichier (chemin relatif) | Dans covered.json ? | Couvert par le filet |
|---|---|---|
| `backend/platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionAccountService.java` | oui | comptes (direction_comptes) |
| `backend/platform/src/main/java/com/hemodialyse/backend/application/direction/SocieteAdminAccountService.java` | oui | comptes (4 goldens administrateurs_*) |
| `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/UserRestController.java` | oui | utilisateurs (3 goldens) |
| `backend/platform/src/main/java/com/hemodialyse/backend/application/auth/AuthService.java` | NON : a inscrire | connexion (5 goldens) |
| `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/config/DevSuperAdminBootstrapInitializer.java` | NON : a inscrire | bootstrap (4 goldens) |
| `backend/contexts/pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/infrastructure/acl/legacyidentite/SessionLegacy.java` | NON : a inscrire | pilotage (2 goldens) + PL10b |

## Plan de phase 2 (apres inscription)
- Ports : `AnnuaireDesCentres` fournit ids (actifs/tous) et fuseau ; `AnnuaireDesSocietes` fournit societes actives, societe d'un centre, societe avec ses centres (nom, actif) et raison sociale. Manquent : nom d'un centre par identifiant, centre actif par identifiant, tous les centres avec nom (liste SUPERADMIN), existence d'une societe (`societe(id).isPresent()` suffit). Option A : composer (`societeDuCentre` + `societe(id).centres`, tri en Java, ecart de collation a verifier) ; option B (preferee si l'utilisateur l'approuve) : etendre le langage publie par des lectures minimales (`nomDuCentre`, `tousAvecNom`). A trancher dans l'ADR 0174.
- Chaque site : remplacer le SELECT par l'annuaire injecte, goldens inchanges (parite), tri par nom conserve. `SessionLegacy` : `s.actif` via `societesActives().contains` (garde le SQL `app_user`/`app_user_societe` qui reste a Identite et acces).
- Sortie : supprimer les 6 entrees de `TableDeLOrganisationTest` (critere d'avancement : 0), ArchUnit, `check-arch.ts`, KB.

## Verification de phase 1
Build complet detache (`mvnw.cmd -o test -fae -q`) : 3550 tests, 1 echec connu et non touche (`InventairePl7...feuille_de_comptage`, date) ; aucun autre `[ERROR]`. Un premier build avait revele une pollution de la base partagee par le test d'amorcage (role/compte SUPERADMIN preexistants supprimes puis non rendus, `InitialSetupIntegrationTest` en echec) : corrige (sauvegarde avant retrait, restitution a la fin), puis trois passes ciblees vertes (`*IdnLecture*` + `InitialSetupIntegrationTest`) et build complet relance.
Commande Maven : `cmd.exe //c "<chemin absolu>\mvnw.cmd ..."` (le `mvnw.cmd` relatif n'est pas reconnu sous Git Bash).
Fichiers de production modifies : aucun. `covered.json`, `.ddd/state.json`, `seamAllowed` : non touches.
