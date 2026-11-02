---
status: draft
date: 2026-11-02
sources: [git b365a63a^ (SocieteRestController, ReferentialRestController, ReferentialAdminRestController, CenterAccessGuard), verify-organisation.md]
---

# Organisation : filet rétroactif (état au 2026-11-02)

Le legacy d'Organisation a été supprimé sans filet (commit b365a63a). Ce fichier consigne ce qui est fait, ce qui reste (TEMPS 3 non commencé) et les décisions à prendre.

## Droits du legacy reconstitués (`git show b365a63a^`)
| Route legacy | Droit legacy | Garde de centre legacy | Nouveau code (fait) |
|---|---|---|---|
| `/api/v1/societes/**` (sociétés, centres, logos, activer/désactiver, transfert) | `@PreAuthorize("hasRole('SUPERADMIN')")` au niveau de la classe | aucune (transverse) | inchangé : `SocietesControleur` SUPERADMIN (URLs `/societes` et `/organisation/societes`) |
| `GET /api/v1/referentials/*` (salles, positions, médecins, générateurs, caisses, agences, forfaits…) | aucun `@PreAuthorize` ; `SecurityConfig.java:82` : `authenticated()` | **aucune** : le `centerId` du client est cru tel quel (tout utilisateur authentifié lit tout centre) | inchangé et **toujours ouvert** (`ReferentielsLegacyControleur`) : à décider, NE PAS fermer sans accord |
| `/api/v1/admin/referentials/**` (CRUD forfaits, créneaux, salles, médecins… + import CSV) | `hasAnyRole('ADMIN','SUPERADMIN')` | `CenterAccessGuard.requireCenter` (centre du jeton ; SUPERADMIN transverse) | non touché (relocalisé en `platform/infrastructure`) |
| `/api/v1/organisation/**` | **n'existait pas** (routes créées par O1-O5) | — | voir ci-dessous (rapprochement avec le legacy, décision à valider) |

### Droits appliqués aux routes `/api/v1/organisation/**` (routes nouvelles, rapprochées du legacy)
| Routes | Droit | Garde de centre |
|---|---|---|
| `POST/PUT /centres`, `POST /centres/{id}/transferer`, `GET /centres`, `GET /centres/actifs` | SUPERADMIN (comme les centres de `/societes`) | transverse |
| `POST /salles/{id}/ouvrir|fermer`, `POST/PUT /creneaux`, `POST/PUT/DELETE /referentiels/{medecins,machines,protocoles}` | ADMIN ou SUPERADMIN (comme l'administration des référentiels) | `SessionCourante.centre` : centre du jeton, 403 si le centre demandé diffère ; PUT/DELETE par id filtrés sur le centre |
| lectures (`GET` salles, créneaux, référentiels, `centres/{id}/parametres`) | utilisateur authentifié | idem (le legacy `/referentials` n'en avait pas : écart durcissant, uniquement sur les routes nouvelles) |
Le corps JSON n'est lu qu'après les droits (403 avant 400/415, comme P1 à P8). Test : `backend/platform/src/test/java/com/hemodialyse/backend/organisation/AutorisationsDeLOrganisationIntegrationTest.java` (33 routes x 11 profils + anonyme, autre centre, 403 avant corps, suppressions bornées au centre).

## Constats à décider (aucun n'a été « corrigé » en silence)
1. **`RoleScopeFilter` (platform) restreint tout compte SUPERADMIN à `/api/v1/societes|licenses|audit|supervision`.** Conséquence : `/api/v1/organisation/**` (dont `/organisation/societes` utilisé par `organisation-api.service.ts` du frontend) renvoie 403 `ROLE_SCOPE` à un SUPERADMIN. Seules les URLs `/api/v1/societes/**` lui sont ouvertes. Les routes `/organisation/centres` sont donc fermées pour tous (fail-closed). Décision : ajouter `/api/v1/organisation/societes` et `/centres` à la portée du SUPERADMIN, ou garder le frontend sur `/api/v1/societes`.
2. `GET /api/v1/referentials/*` : aucune garde de centre (legacy). Fuite inter-centres possible pour tout authentifié. Décision : ajouter la garde (durcissement) ou conserver.
3. Rôles accordés aux écritures de médecins/machines/protocoles : aucun équivalent legacy pour machines/protocoles ; alignés sur l'administration des référentiels (ADMIN, SUPERADMIN).
4. Suppression physique des médecins (référencés par `patients.medecin_traitant_id`) : conservée ; décision logique/physique à prendre.

## Schéma de production (TEMPS 2, fait)
Migration V51 (additive, idempotente) : `salle.ouverte`, tables `machine`, `protocole`. Rejeu PostgreSQL 16 jetable : `backend/scripts/organisation-pg16-rejeu.sh` (V1..V51 sans doublon, V51 rejouable, contrats JDBC `OrganisationJdbcContrat` verts sur H2 et sur le schéma Flyway réel). Contrôles SELECT : `.ddd/migration/verifications-production.md` § 36.

## Reste à faire (TEMPS 3 et suites, NON FAIT)
- Worktree jetable sur `b365a63a^`, filet HTTP des routes legacy d'Organisation (sociétés, centres, logos, `/referentials/*`, `/admin/referentials`), goldens LEGACY, rejeu sur le nouveau code, liste d'écarts soumise à l'utilisateur (aucun golden `_nouveau_code` approuvé). KB-ORG-xx à ouvrir à ce moment.
- Écart connu : `/referentials/salles` et `/positions` triés par `code` (legacy : non triés).
- Règle « dernier centre actif » sans transaction ni verrou (`OrganisationService.java:81-132`) : verrou de la société + `lock_timeout`, 409.
- Règles dans le service applicatif ; `Centre.setActif` setter ; entités de domaine renvoyées en JSON (`Centre`, `Medecin`, `Machine`, `Protocole`, `Salle`, `Creneau`) -> DTO ; `TableDeLOrganisationTest` vide de sens ; fiche O5 corrompue ; ADR « admin générique et migration relocalisées dans `platform/infrastructure` ».
- Test instable hors périmètre : `ReplanificationDeNuitCharacterizationIntegrationTest.une_proposition_qui_apporte_quelque_chose_...` a échoué une fois en suite complète (deux lignes `calculs` permutées, ordre SQL non déterministe), vert isolé.
