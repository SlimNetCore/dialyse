---
status: proposed
date: 2026-11-13
sources: pl11-analyse.md (2, 3, 4), pl11-decisions.md (D3-D10), pl10b-filet.md, known-bugs.md (KB-PL11-1..13)
---

# PL11-0 : filet complementaire de l'administration generique des referentiels (salles, creneaux, medecins)

Tests seuls, aucun code de production modifie, seamAllowed = []. Rien commite. Au point HTTP (MockMvc, H2, profil `test`), harnais `OrganisationCharacterizationSupport` reutilise ; 1 seul contexte Spring demarre pour les 4 classes (+ la classe o2 existante).

## Classes creees (`backend/platform/src/test/java/com/hemodialyse/backend/characterization/organisation/pl11/`)
| Classe | Tests | Goldens (dossier `.../resources/characterization/organisation/<dossier>`) |
|---|---|---|
| `Pl11FiletSupport` (base : journal numerote, fixtures d'usage, filtre d'audit branche, nettoyage) | - | - |
| `SallesPl11...IntegrationTest` | 11 | `pl11-0-salles` : refus d'usage (patient, equipement, 3 usages, usage hors centre, affectation infirmier, mouvement, calendrier, seance), unicite, validations (25 cas), remplacement complet (capacite effacee, ouverte conservee), recherche/pagination, cache salles et generateurs, import, modele, erreur du controle d'usage, audit |
| `CreneauxPl11...IntegrationTest` | 8 | `pl11-0-creneaux` : cycle de vie (contenu), refus d'usage, unicite/validations, recherche, cache positions, import, modele, audit |
| `MedecinsPl11...IntegrationTest` | 12 | `pl11-0-medecins` : creation/modification, unicite nom+prenom, validations, refus d'usage (patient, prescription, hors centre), desactivation d'un medecin utilise (route Organisation), recreation/reimport d'un desactive, recherche, import complement, modele, cache (2 strategies), unicite Organisation vs admin, audit |
| `TransversePl11...IntegrationTest` | 6 | `pl11-0-transverse` : descripteurs, matrice de droits complementaire (14 routes x 17 profils), garde de centre (21 cas), formes illisibles (42 cas), referentiel inconnu/retire, audit des refus |

37 tests, 37 goldens `*.approved.json`, relus (statuts et corps clefs) puis copies depuis les `.received` (jamais `-Dcharacterization.approve=true`). Aucun `.received` restant.

## Execution
3 passes (la 3e en `-Dsurefire.runOrder=random`) avec la classe o2 `AdministrationDesReferentielsCharacterizationIntegrationTest` : 42 tests, 0 echec, 0 flaky, ~58 s par passe (1 contexte Spring). Deux flakys corriges en cours de route (ordre de deux homonymes, contenu zip du modele xlsx), un code d'equipement aleatoire remplace par un compteur. Journaux : `backend/pl11-0-final1..3.log`. Commande : `locked.ts maven -- <abs>\backend\mvnw.cmd -f backend/pom.xml -o -pl platform -am test -Dtest=*Pl11CharacterizationIntegrationTest,AdministrationDesReferentielsCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`.

## Constats verrouilles (KB-PL11-n, known-bugs.md, non corriges)
1 usages incomplets (salle ET creneau : infirmier, mouvement, calendrier, seance supprimes sans refus, orphelins) ; 2 usage compte hors centre ; 3 erreur du controle d'usage = suppression autorisee (table renommee en H2, 204 malgre l'equipement) ; 4 Organisation desactive un medecin utilise (200) alors que l'admin refuse 422 ; 5 aucune unicite cote Organisation (2 homonymes) ; 6 medecin desactive invisible : recreation et reimport creent un doublon, l'ancien n'est jamais reactive, PUT/DELETE d'un desactive = 422 introuvable ; 7 `ouverte` jamais touchee par l'admin ; 8 PUT sans capacite l'efface, et l'import sans colonnes facultatives remet capacite a NULL et isolement a NON ; 9 cache medecins vide pour tous les centres cote Organisation, pour le seul centre cote admin ; 12 formes illisibles en 500 (405, 415, 406, import sans fichier, texte brut) ; 13 SUPERADMIN refuse 403 `ROLE_SCOPE` sur toutes les routes (RoleScopeFilter) malgre `hasAnyRole('ADMIN','SUPERADMIN')`, et audit : ecritures tracees (`ADMIN_CREATION/MODIFICATION/SUPPRESSION`, entite `admin`, `entity_id` = slug pour creation/import, refus 403/422 traces, lectures non tracees).
KB-PL11-10 et 11 : non exerces ici (modele de domaine, Javadoc) ; `ordonnances.medecin_id` (varchar) non compte : non teste (pas de table d'ordonnance amorcee).

## Limites
- H2 seulement : tri/collation, rollback apres `DataAccessException` (PostgreSQL abort la transaction) non prouves sur PG16.
- Le test d'erreur du controle d'usage renomme temporairement `gmao_equipements` (restauration en `finally`) ; il suppose un contexte H2 non partage en parallele.
- Contenu xlsx du modele non verrouille (zip horodate) : statut et type seulement.
- Pas d'ordre garanti entre deux homonymes : le total seul est verrouille.
- `covered.json` : fichiers deja inscrits (controleur, service, adaptateur, tables, domaine) ; aucun ajout necessaire.

## Decisions a demander (apparues pendant le filet)
1. KB-PL11-13 : SUPERADMIN est bloque par `RoleScopeFilter` sur l'admin generique : la doc dit ADMIN/SUPERADMIN. Reproduire (refus) ou ouvrir ? 
2. KB-PL11-12 : 405/415/406 en 500 : corriger en PL11-1 (comme KB-PL10b-3) ?
3. Audit : enregistrer l'identifiant de l'entite creee et le type de referentiel (aujourd'hui `admin` + slug) ?
