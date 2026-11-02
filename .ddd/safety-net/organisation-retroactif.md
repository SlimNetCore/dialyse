---
status: draft
date: 2026-11-02
sources: [git b365a63a^ (SocieteRestController, ReferentialRestController, ReferentialAdminRestController, CenterAccessGuard), verify-organisation.md]
---

# Organisation : filet rétroactif

Le legacy d'Organisation a été supprimé sans filet (commit b365a63a). Le filet a été écrit APRÈS COUP sur un worktree jetable de
`b365a63a^` (supprimé ensuite), les goldens LEGACY y ont été produits puis relus un à un, puis copiés dans l'arbre courant et rejoués sur
le nouveau code. Rien n'a été approuvé en lot côté nouveau code : chaque écart est soit corrigé (parité), soit décidé par l'utilisateur
(goldens `_nouveau_code` + archive `_ecarts-legacy`), soit listé ci-dessous (scénario désactivé).

## Filet (backend/platform/src/test)
| Fichier | Contenu | Goldens |
|---|---|---|
| `characterization/organisation/OrganisationCharacterizationSupport` | harnais (11 profils, fixtures, normalisation UUID/horodatage, tri des centres) | - |
| `SocietesCharacterizationIntegrationTest` | `/api/v1/societes/**` : matrice 14 routes x 11 profils + anonyme, lectures, création et validations, unicité des codes, invariant du dernier centre actif, transfert, logos | `o1-societes/` (54) |
| `ReferentielsCharacterizationIntegrationTest` | `GET /api/v1/referentials/*` : matrice 13 routes x 11 profils + anonyme, contenu et ordre, garde de centre, paramètres | `o3-referentiels/` (14 + `_ecarts-legacy`) |
| `AdministrationDesReferentielsCharacterizationIntegrationTest` | `/api/v1/admin/referentials` (salles, créneaux, médecins) : droits, garde de centre, CRUD | `o2-administration/` (14) |
| `organisation/AutorisationsDeLOrganisationIntegrationTest` | routes `/api/v1/organisation/**` : 33 routes x 11 profils + anonyme, autre centre, 403 avant corps, DTO, désactivation d'un médecin, coexistence des deux familles d'URLs | - |

Aucune date réelle, aucun jour de mois fixe, aucun minuit : aucune date n'intervient dans ces scénarios.

## Parité : résultat
- Rejouée sur le nouveau code : 22 scénarios, tous actifs et verts (plus de scénario désactivé).
- Bugs du nouveau code corrigés pour rétablir la parité : codes d'erreur 422 `SOCIETE_INTROUVABLE` / `CENTRE_INTROUVABLE` (le nouveau code
  répondait 404 `NOT_FOUND`), 404 sans corps pour société ou logo absent, suppression d'un logo absent = 204 (le nouveau code répondait 404).
  Exceptions typées `SocieteIntrouvable`, `CentreIntrouvable`, `LogoIntrouvable` (plus de correspondance par sous-chaîne pour ces cas).
- Écarts DÉCIDÉS par l'utilisateur (2026-11-02), goldens `_nouveau_code`, ancien comportement dans `o3-referentiels/_ecarts-legacy` :
  garde de centre sur `/referentials/*` (3 goldens + 12 cellules de la matrice : ADMIN sans centre 200 -> 403), tri par code des salles et
  positions (2 goldens).
- Écarts décidés le 2026-11-02, goldens `_nouveau_code` relus un par un, legacy archivé (`o1-societes`, `o2-administration`,
  `o3-referentiels/_ecarts-legacy`) : KB-ORG-02 (UUID illisible : 400, 3 goldens), KB-ORG-03 (médecins triés par nom, 1 golden) et
  suppression logique des médecins dans l'admin générique (`medecin-lignes-en-base`). Comportement douteux reproduit : KB-ORG-01.
- Filet étendu le 2026-11-02 (legacy rejoué sur un worktree jetable de `b365a63a^`, puis supprimé) : import CSV de l'admin générique (11
  scénarios, parité complète) et logo et pied de page imprimés dans les documents (`LogoDansLesDocumentsCharacterizationIntegrationTest`,
  parité complète). Au total 22 scénarios, tous actifs et verts.

## Droits du legacy reconstitués (`git show b365a63a^`)
| Route legacy | Droit legacy | Garde de centre legacy | Nouveau code |
|---|---|---|---|
| `/api/v1/societes/**` | SUPERADMIN (classe) | aucune (transverse) | inchangé (82 goldens, matrice identique) |
| `GET /api/v1/referentials/*` | authentifié (`SecurityConfig`) | aucune | garde de centre du jeton (écart décidé) |
| `/api/v1/admin/referentials/**` | ADMIN ou SUPERADMIN | `CenterAccessGuard` | non touché |
| `/api/v1/organisation/**` | n'existait pas | - | centres SUPERADMIN ; écritures de salles, créneaux, médecins, machines, protocoles ADMIN (l'annotation reste `ADMIN` ou `SUPERADMIN`, mais le SUPERADMIN est arrêté en amont par le `RoleScopeFilter`) avec garde de centre ; lectures authentifié avec garde |

`RoleScopeFilter` : le SUPERADMIN n'atteint que `/api/v1/organisation/societes` et `/api/v1/organisation/centres` ; le reste de
`/api/v1/organisation` lui répond 403 `ROLE_SCOPE`, comme le legacy (décision du 2026-11-02, revue n° 2 ; deux seams inscrits dans
`seams.md` puis fermés ; ADR 0071).

## Rejeu reproductible du legacy
`backend/scripts/organisation-rejeu-legacy.sh` : crée un worktree jetable HORS du dépôt sur `b365a63a^`, y copie le filet et les goldens
legacy (le golden de chaque écart décidé est remplacé par son archive `_ecarts-legacy`), exécute le filet en mode « vérifier » (jamais
`-Dcharacterization.approve`) et supprime le worktree. Exécuté le 2026-11-02 : 22 scénarios verts contre le legacy réel (92 goldens tels
quels + 11 archives d'écarts décidés) : les goldens legacy sont exacts, aucun n'a été inventé.

## Données et sécurité de production
Migrations V51 (salle.ouverte, machine, protocole) et V52 (medecin.actif) ; rejeu PostgreSQL 16 jetable
`backend/scripts/organisation-pg16-rejeu.sh` (contrats JDBC dont la concurrence du dernier centre actif sur PostgreSQL réel).
Contrôles SELECT : `.ddd/migration/verifications-production.md` section 36.

## Règle « dernier centre actif »
Verrou de la société (`SELECT ... FOR UPDATE`) pris AVANT la lecture des centres actifs, dans une transaction (`UniteDeTravailSpring`,
`SET LOCAL lock_timeout` au premier verrou, 5 s) ; refus de verrou = 409 `ORGANISATION_ECRITURE_CONCURRENTE`. Règle portée par le domaine
(`Centre.desactiver(centresActifs)`, `Centre.transfererVers(cible, centresActifs)`, plus de setter `setActif`). Test de concurrence sur H2
et PostgreSQL 16 : deux désactivations simultanées, une seule passe.

## Reste à faire
- Corps absent ou illisible (KB-ORG-01, 500 aux deux) : non décidé.
- `SocietesJdbc` fabrique `now()` pour un `created_at` NULL à la lecture (mineur, non caractérisé).
- Entités de domaine encore renvoyées en JSON par les lectures communes (`EntreeReferentiel`, `DetailPayeur` : records, rendus corrects).
- O4 `planned` : `organisation.v1` sans consommateur (constat dans la fiche O4 et l'ADR 0070).
- Test instable hors périmètre : `ReplanificationDeNuitCharacterizationIntegrationTest.une_proposition_qui_apporte_quelque_chose_...`
  (deux lignes `calculs` permutées, ordre SQL non déterministe) a échoué une fois en suite complète, vert isolé.
