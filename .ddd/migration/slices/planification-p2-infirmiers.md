---
slice: planification-p2-infirmiers
status: built
date: 2026-10-26
order: 2
context: planification
sources: [.ddd/tactical/planification.md (P13, canvas Infirmier), .ddd/safety-net/planification-p2-infirmiers.md, RG-INF-001 à 004, 010 à 012, ADR 0024, 0043, 0049, 0055, 0056, known-bugs.md KB-PLN-12 à 24]
---

# P2 — Référentiel des infirmiers et de leurs comptes (9 routes)

Refonte sur place (ADR 0024) : pas de façade, de routage, de projection ni de métrique ; mêmes tables, mêmes URLs. **Aucun drapeau** : le nouveau code sert les 9 routes directement et l'ancien est supprimé ; **aucune migration** ; le retour arrière est celui du déploiement. Les 3 routes d'affectations d'`InfirmierRestController` (P3) restent dans le legacy, intactes.

## Fait
| Couche | Contenu (module `backend/contexts/planification`) |
|---|---|
| domaine | agrégat `Infirmier` (racine, immuable : `enregistrer`, `modifier`, `desactiver`, `reactiver(EtatDuCompte)`, `lierCompte`, `delierCompte(EtatDuCompte)`, `delierAvantLaSuppressionDuCompte`, `suivreLeCompte`) ; VO `Matricule` (majuscules, ≤ 50), `IdentiteDeLInfirmier` (nom, prénom, téléphone bornés), `Qualification`, `AdresseEmail`, `IdentifiantDeConnexion`, `EtatDuCompte`, `CompteId` ; service `MatriculeUniqueDansLeCentre` ; ports `InfirmiersRepository`, `VerrouDuReferentielDesInfirmiers` |
| application | 11 cas d'usage (`ListerLesInfirmiers`, `EnregistrerUnInfirmier`, `ModifierUnInfirmier`, `DesactiverUnInfirmier`, `ReactiverUnInfirmier`, `ListerLesComptesLiables`, `LierUnCompte`, `CreerUnCompte`, `DelierLeCompte`, `SuivreLEtatDuCompte`, `DelierAvantLaSuppressionDuCompte`) ; read model `FicheDetaillee`/`FichesDetaillees` ; ports `ComptesUtilisateurs`, `RoulementsDesInfirmiers` (lecture P3), `GenerateurDeMotDePasse`, `NouveauxIdentifiantsDInfirmier` |
| infrastructure | `InfirmiersJdbc`, `VerrouDuReferentielDesInfirmiersJdbc`, ACL `legacyidentite/ComptesUtilisateursJdbc` (+ verrou consultatif des identifiants sur PostgreSQL), ACL `legacyroulement/RoulementsDesInfirmiersJdbc`, `GenerateurDeMotDePasseTemporaire`, `NouveauxIdentifiantsAleatoires` |
| interfaces | `InfirmiersControleur` (9 routes, corps lu après l'autorisation : `CorpsJson`), advices P1 étendus (400/415/422/409), `publie/FichesDesInfirmiersPourLeLegacy` |
| langage publié | `FichesDesInfirmiers` (écriture : `suivreLEtatDuCompte(compte, actif, centre)`, `delierAvantLaSuppressionDuCompte(compte)` → `CompteLieAUneFicheActive`) |
| legacy | supprimés : `CompteInfirmierService`, `GenerateurMotDePasseTemporaire`, `InfirmierModelTest`, `CompteInfirmierIntegrationTest`, `InfirmierCompteRestControllersTest`, `GenerateurMotDePasseTemporaireTest`, `pit-p2-infirmiers.sh` ; réduits : `InfirmierRestController` (3 routes d'affectations), `InfirmierService` (détail en lecture pour « Mon planning »), `ComptesInfirmierPort`/adaptateur, `InfirmierRepositoryPort`/adaptateur, `Infirmier` (record en lecture seule) ; édités avec accord : `UserRestController` (port publié, transactions), `AuditActionResolver`, `AuditRequestFilter` |
| frontend | i18n fr/en/ar/kab : `INFIRMIER.ERR.COMPTE_DELIAISON_INCOHERENTE`, `INFIRMIER.ERR.PLANIFICATION_ECRITURE_CONCURRENTE`, `ADMIN.USERS.DELETE_BLOCKED_ACTIVE_NURSE`, `ADMIN.USERS.DELETE_ERROR` ; `userDeleteErrorKey` (la suppression d'un utilisateur affiche désormais son échec au lieu de « supprimé ») |

## Preuves
- Module : 150 tests (domaine 17 + 18 de P1, cas d'usage 18 + 16, contrat double 9 + 8, contrat JDBC H2 17 + 22, publié 2 + 3, mot de passe 2, ArchUnit 11 sans `failOnEmptyShould`) ; `check-arch.ts` : 0 violation.
- PostgreSQL 16 jetable : `backend/scripts/planification-p2-pg16-rejeu.sh <port != 5432>` — constats Flyway (unicité `(center_id, matricule)`, `ux_infirmier_user`, 0 doublon de casse), contrat JDBC 19 tests (+ contrat P1 23) dont `lock_timeout` → 409 et insertion d'une fiche pendant que le verrou du centre est tenu (`FOR NO KEY UPDATE`) ; vert.
- Parité : 50 scénarios de caractérisation (48 + 2 ajoutés) exécutés contre le nouveau code : tous verts ; 42 goldens sur 194 changent par décision (voir écarts), ancien comportement dans `src/test/resources/characterization/planification/p2-infirmiers/_ecarts-legacy`.
- Mutation : `backend/scripts/pit-p2-module.sh` (domaine + application du module, P1 + P2) : 228 mutants, 99 % tués (survivants : retour de `Identifiants.requis`, retours ignorés sans effet).
- Audit : `AuditDesInfirmiersCharacterizationIntegrationTest` (7 libellés métier + identifiant de la fiche créée, refus tracés avec leur statut).
- Frontend : `infirmier-stores.spec.ts` + `user-list.store.spec.ts` (20 tests verts, codes et messages fr/en/ar/kab). Build complet : voir état final.

## Écarts de parité décidés (26/10/2026)
KB-PLN-12/13/20 (400, 415, 403 avant le corps, tailles, e-mail, pagination, plafond 100), 15 (borné au centre), 16 (422 si fiche active, une transaction), 17 (libellés d'audit), 18 (409 + verrou), 19 (matricule en majuscules), 21 (RG-INF-004 corrigée), 22 (états incohérents refusés), 23 (une transaction) ; **14 reproduit « à décider »** ; 24 nouveau (pas d'unicité de `username` en PostgreSQL). Détail : `known-bugs.md`.

## Reste à faire / points ouverts
- **Décidé (revue du 2026-10-26)** : un compte ne se lie plus qu'à un seul centre (422 `COMPTE_LIE_A_UN_AUTRE_CENTRE`) ; les comptes déjà multi-centres sont à traiter à la main avant déploiement (§ 33, requête n° 5). Ordre global des verrous centre → fiches → `app_user` → verrou des identifiants (ADR 0056) ; `createUser` legacy prend le verrou des identifiants ; les 409 de l'administration des utilisateurs sont rendus par `EcritureConcurrenteDesFichesAdvice`. Éditions de l'audit ratifiées par écrit par l'utilisateur (2026-10-26, `seams.md`).
- **Dette** : la règle « le compte suit l'état de la fiche » reste dans les cas d'usage, pas dans l'agrégat (ADR 0056 § Dette).
- **À décider** : index unique `lower(username)` (KB-PLN-24) et index unique fonctionnel du matricule (ADR 0056, après le contrôle n° 1 de `verifications-production.md` § 33) ; synchronisation des noms fiche/compte (KB-PLN-14).
- L'ACL `legacyinfirmier/InfirmiersDuCentreJdbc` (P1, situation d'un infirmier) lit désormais une table du même contexte : à remplacer par `InfirmiersRepository` à la prochaine passe (sans changement de comportement).
- `InfirmierService`, `ComptesInfirmierPort`, `ComptesInfirmierJdbcAdapter` (lecture du détail d'une fiche pour « Mon planning ») disparaissent avec P3 ; `RoulementsDesInfirmiers` (lecture de `infirmier_affectation`) disparaît quand P3 possède cette table.
- Événements de domaine (`InfirmierEnregistre`…) : aucun consommateur, non introduits.
- Glossaire : 10 termes `proposed` à valider. ADR 0056 `proposed`.

## Plan de bascule et retour arrière
Pas de bascule progressive (ADR 0024) : déploiement unique. Contrôles avant mise en production : `verifications-production.md` § 33 (SELECT seulement, notamment doublons de casse du matricule et doublons d'identifiants de connexion). Surveillance : actions d'audit `INFIRMIER_*`, statuts 400/409/422 sur `/api/v1/infirmiers/**` et 422 sur `DELETE /api/v1/users/{id}`. Retour arrière : redéploiement de la version précédente (aucune migration ; les matricules en majuscules restent valides pour l'ancien code).

## Seam
Édition minimale du legacy autorisée par l'utilisateur (`UserRestController`, audit) : inscrite dans `.ddd/safety-net/seams.md` ; **fermée** à la fin de la slice ; `seamAllowed` vide.
