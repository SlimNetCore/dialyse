---
slice: organisation-o5-retrait-bc-referential
status: in-progress
date: 2026-11-02
order: 5
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063, ADR 0070, .ddd/verify/verify-organisation.md, .ddd/safety-net/organisation-retroactif.md, .ddd/migration/roadmap.md]
---

# O5 - Retrait de `bc-referential` (bascule Organisation O1-O4)

Fiche réécrite le 2026-11-02 (l'ancienne version était contradictoire et contenait des caractères corrompus). État réel : le module
`bc-referential` et ses adapters ont été retirés (commit b365a63a) AVANT la caractérisation ; le filet a été reconstitué après coup
(voir `.ddd/safety-net/organisation-retroactif.md`). L'état du pipeline est `in-progress` tant que les écarts listés ci-dessous ne sont pas
décidés et que O4 n'est pas faite.

## Intention

Supprimer le module legacy `bc-referential` (domaine `organisation`, `referential`, `referential/admin`) après bascule de ses consommateurs
vers le contexte `organisation`. Les URLs historiques restent servies (aucune coupure frontend).

## Ce qui est fait

- Sociétés, centres, logos, activer/désactiver, transfert : `SocietesControleur`, servi sous `/api/v1/societes` (historique) et
  `/api/v1/organisation/societes` (canonique), SUPERADMIN, mêmes DTO et mêmes codes d'erreur que le legacy (parité vérifiée par le filet
  rétroactif : 82 goldens, voir ci-dessous).
- Lectures `/api/v1/referentials/*` : `ReferentielsLegacyControleur` (forme `RefItem` historique).
- Routes canoniques `/api/v1/organisation/{centres,salles,creneaux,referentiels}` : droits par route, garde de centre, DTO de réponse.
- Stack legacy supprimée : `SocieteRestController`, `ReferentialRestController`, adapters, DTO, `DomainServiceConfig` (beans société et
  référentiel), packages `domain/organisation` et `domain/referential`, modules `bc-referential` et `bc-migration`.
- Admin générique (`/api/v1/admin/referentials`) et migration de données (`/api/v1/admin/migration`) relocalisés dans
  `platform/.../infrastructure/{referential/admin,migration}` : hébergement documenté par l'ADR 0070 (proposed).
- Schéma de production : migrations V51 (salle.ouverte, machine, protocole) et V52 (medecin.actif), rejeu PostgreSQL 16 jetable.

## Décisions de l'utilisateur (2026-11-02)

1. **SUPERADMIN ouvert puis LIMITÉ au legacy** (`RoleScopeFilter`, deux seams inscrits puis fermés) : il garde
   `/api/v1/organisation/societes` et `/api/v1/organisation/centres` (création, modification, transfert, lecture) ; salles, créneaux, médecins,
   machines, protocoles et référentiels lui répondent 403 `ROLE_SCOPE` comme le legacy (revue n° 2, ADR 0071). Conséquence : NOUVELLES URLs
   publiques `/organisation/societes` et `/organisation/centres`, écart avec « mêmes URLs » de l'ADR 0024. Les URLs historiques `/api/v1/societes/**` coexistent et renvoient le même contenu
   (test `AutorisationsDeLOrganisationIntegrationTest`). Restent servies sous les anciennes URLs : `/api/v1/societes/**`,
   `/api/v1/referentials/*`, `/api/v1/admin/referentials/**`, `/api/v1/admin/migration/**`.
   Le frontend `organisation-api.service.ts` ne manipule que `/organisation/societes` : désormais atteignable par un SUPERADMIN.
2. **Garde de centre sur `GET /api/v1/referentials/*`** : le centre est celui du jeton (403 sinon). Écart décidé, goldens
   `_nouveau_code`, ancien comportement archivé dans `_ecarts-legacy`.
3. Rôle ADMIN pour machines et protocoles : validé (le SUPERADMIN en est exclu, voir 1).
4. Médecins : suppression LOGIQUE (désactivation, `medecin.actif`, migration V52), car référencés par `patients.medecin_traitant_id`.
5. Tri par `code` de `/referentials/salles` et `/positions` : accepté (le legacy ne triait pas).

## Écarts décidés le 2026-11-02 (goldens `_nouveau_code` relus un par un, legacy archivé dans `_ecarts-legacy`)

| N° | Écart | Legacy | Nouveau code |
|---|---|---|---|
| KB-ORG-02 | identifiant ou paramètre UUID illisible | 500 `INTERNAL_ERROR` | 400 `BAD_REQUEST` (décision : garder) |
| KB-ORG-03 | ordre des médecins de `/referentials/medecins` | ordre physique de la table | tri par nom (décision : accepté) |
| KB-ORG-05 | lecture de `/referentials/*` | centre du client cru | garde de centre, 403 |
| (admin) | suppression d'un médecin via `/admin/referentials/medecins` | `DELETE` physique | désactivation (`actif = FALSE`) |
| (tri) | salles et positions | non triées | triées par code |

Comportements douteux du legacy reproduits tels quels : KB-ORG-01 (500 pour un corps absent ou illisible sur `POST /societes`, non
décidé), KB-ORG-04 (ordre des centres d'une société non déterministe : le filet trie `centres` par code). Aucun écart décidé n'attend
plus d'approbation.

## Reste à faire

- [x] KB-ORG-02 et KB-ORG-03 décidés (voir ci-dessus).
- [ ] Slice O4 (lectures publiées `organisation.v1`) : `planned`, constat consigné (aucun consommateur) ; `TableDeLOrganisationTest` liste
      30 lecteurs SQL directs (liste blanche datée 2026-11-02, un lecteur migré fait échouer le test tant que l'entrée n'est pas retirée).
- [ ] Référentiels orphelins (payeurs, agences, caisses, transporteurs, forfaits) : propriétaires à décider.
- [ ] `SocieteJpaEntity` et `CenterJpaEntity` (auth, licence) conservées, hors périmètre.
- [ ] Cache `ref.*` : invalider à la bascule (clés incluant `centerId`).
- [x] Filet étendu au logo et au pied de page imprimés dans les documents (`DocumentIdentityProvider`, 4 goldens) et à l'import CSV de
      l'admin générique (vérification, import, rejeu, refus ; à parité avec le legacy).
- [x] Goldens legacy prouvés exacts : `backend/scripts/organisation-rejeu-legacy.sh` (worktree jetable sur `b365a63a^`, filet en mode
      « vérifier », exécuté le 2026-11-02 : 22 scénarios verts contre le legacy réel).
- [x] Revue n° 2 : caches `ref.salles|positions|medecins|generateurs` invalidés APRÈS commit ; société cible d'un transfert verrouillée
      (ordre des identifiants) ; exceptions typées pour salle, créneau, médecin, machine, protocole (plus de sous-chaîne) ; ADR 0071.
      Documentés sans changement : KB-ORG-06 (`created_at` NULL), KB-ORG-07 (fermer une salle sans effet), KB-ORG-08 (`centers.code` sans
      contrainte d'unicité, contrôle ORG-11, index proposé après contrôle).
- [x] Horloge (`Clock`) et générateur d'identifiants injectés dans `OrganisationService` et `ReferentielsOrganisationService`.

## Données

Mêmes tables (`societes`, `centers`, `salle`, `position_creneau`, `medecin`) ; migrations additives V51 et V52 (voir
`.ddd/migration/verifications-production.md` section 36).

## Retrait du legacy

Fait (b365a63a). Aucun retour arrière par flag n'est prévu (refactoring sur place, ADR 0024) ; le retour arrière est un revert Git.
