---
slice: organisation-o5-retrait-bc-referential
status: in-progress
date: 2026-11-03
order: 5
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063, ADR 0070, .ddd/verify/verify-organisation.md, .ddd/safety-net/organisation-retroactif.md, .ddd/migration/roadmap.md]
---

# O5 - Retrait de `bc-referential` (bascule Organisation O1-O4)

Fiche réécrite le 2026-11-03 (l'ancienne version était contradictoire et contenait des caractères corrompus). État réel : le module
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

## Décisions de l'utilisateur (2026-11-03)

1. **Préfixe `/api/v1/organisation/**` ouvert au SUPERADMIN** dans `RoleScopeFilter` (seam inscrit puis fermé). Conséquence : NOUVELLES URLs
   publiques, écart avec « mêmes URLs » de l'ADR 0024. Les URLs historiques `/api/v1/societes/**` coexistent et renvoient le même contenu
   (test `AutorisationsDeLOrganisationIntegrationTest`). Restent servies sous les anciennes URLs : `/api/v1/societes/**`,
   `/api/v1/referentials/*`, `/api/v1/admin/referentials/**`, `/api/v1/admin/migration/**`.
   Le frontend `organisation-api.service.ts` ne manipule que `/organisation/societes` : désormais atteignable par un SUPERADMIN.
2. **Garde de centre sur `GET /api/v1/referentials/*`** : le centre est celui du jeton (403 sinon). Écart décidé, goldens
   `_nouveau_code`, ancien comportement archivé dans `_ecarts-legacy`.
3. Rôles ADMIN/SUPERADMIN pour machines et protocoles : validés.
4. Médecins : suppression LOGIQUE (désactivation, `medecin.actif`, migration V52), car référencés par `patients.medecin_traitant_id`.
5. Tri par `code` de `/referentials/salles` et `/positions` : accepté (le legacy ne triait pas).

## Écarts de parité restants (à décider par l'utilisateur, aucun golden `_nouveau_code` approuvé)

| N° | Écart | Legacy | Nouveau code | Scénario (désactivé) |
|---|---|---|---|---|
| KB-ORG-02 | identifiant ou paramètre UUID illisible | 500 `INTERNAL_ERROR` | 400 `BAD_REQUEST` | `identifiant_de_societe_illisible_ecart_a_decider`, `identifiants_illisibles_ecart_a_decider` |
| KB-ORG-03 | ordre des médecins de `/referentials/medecins` | ordre physique de la table | tri par nom (ou liste paginée) | `ordre_des_medecins_ecart_a_decider` |

Comportements douteux du legacy reproduits tels quels : KB-ORG-01 (500 pour un corps absent ou illisible sur `POST /societes`),
KB-ORG-04 (ordre des centres d'une société non déterministe : le filet trie `centres` par code).

## Reste à faire

- [ ] Décider KB-ORG-02 et KB-ORG-03, puis approuver (ou non) les goldens correspondants.
- [ ] Slice O4 (lectures publiées `organisation.v1`) : aucun consommateur ; `TableDeLOrganisationTest` liste 29 lecteurs SQL directs
      (liste blanche datée 2026-11-02, un lecteur migré fait échouer le test tant que l'entrée n'est pas retirée).
- [ ] Référentiels orphelins (payeurs, agences, caisses, transporteurs, forfaits) : propriétaires à décider.
- [ ] `SocieteJpaEntity` et `CenterJpaEntity` (auth, licence) conservées, hors périmètre.
- [ ] Cache `ref.*` : invalider à la bascule (clés incluant `centerId`).
- [ ] Étendre le filet rétroactif aux logos en documents (`ModeleDocumentPrinter`) et à l'import CSV de l'admin générique.

## Données

Mêmes tables (`societes`, `centers`, `salle`, `position_creneau`, `medecin`) ; migrations additives V51 et V52 (voir
`.ddd/migration/verifications-production.md` section 36).

## Retrait du legacy

Fait (b365a63a). Aucun retour arrière par flag n'est prévu (refactoring sur place, ADR 0024) ; le retour arrière est un revert Git.
