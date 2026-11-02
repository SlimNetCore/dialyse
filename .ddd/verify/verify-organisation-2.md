---
status: proposed
date: 2026-11-02
scope: contexte organisation (O1–O5), 2e vérification
sources: [check-arch.ts, state.ts show, git b365a63a^..HEAD (47a9cb1b, afa99250, 4591ac67), git show b365a63a^ (SocieteRestController, ReferentialRestController, ReferentialAdminRestController, RoleScopeFilter), .ddd/verify/verify-organisation.md]
reviewer: relecteur indépendant (lecture seule, aucun build ni test exécuté)
---

# Vérification n° 2 — contexte Organisation (O1–O5)

Aucun test n'a été lancé. Les « verts » cités viennent des fiches et n'ont **pas été reproduits**.
`check-arch.ts` : 0 violation (0 en baseline, 0 nouvelle).

## 1. Suivi des constats de la vérification n° 1
| Constat n° 1 | État | Preuve |
|---|---|---|
| B1 écritures ouvertes | **corrigé** | `@PreAuthorize` sur chaque écriture : `CentresControleur.java:40-72`, `ReferentielsControleur.java:39,57-136`, `SallesControleur.java:32,39`, `CreneauxControleur.java:39,48`. Garde de centre : `SessionLegacy.java:50-71` |
| B2 schéma prod absent | **corrigé** | `V51__organisation_salle_ouverte_machine_protocole.sql` (additive, `IF NOT EXISTS`) ; colonnes identiques à `schema.sql:29-90` ; aucun doublon V50/V51/V52 |
| B3 legacy supprimé sans filet | **corrigé en partie** | Filet présent (goldens `characterization/organisation/**`). Origine legacy des goldens **non vérifiable** (cf. M2) |
| M dernier centre sans verrou | **corrigé** | `OrganisationService.java:99-122` (verrou avant relecture) ; `VerrouDeLaSocieteJdbc` (FOR UPDATE, 55P03 → 409) ; `UniteDeTravailSpring.java:46` |
| M règles dans le service | **corrigé** | `Centre.desactiver(centresActifs)` et `transfererVers(...)`, setter supprimé. L'unicité des codes reste dans l'application, ce qui est acceptable (elle exige une requête) |
| M entités en JSON | **corrigé** | `ReponsesDuReferentiel` (DTO) dans tous les contrôleurs `/organisation` |
| M `TableDeLOrganisationTest` vide de sens | **corrigé** | Scan de platform et de tous les contextes ; 30 lecteurs datés ; test des entrées mortes (`:136-146`). Écarts résiduels : m4 |
| M O4 faux | **corrigé** | Fiche O4 `planned`, constat repris dans l'ADR 0070 |
| M DELETE sans `center_id` | **corrigé** | `MachinesJdbc.java:39`, `ProtocolesJdbc.java:39` ; médecin : suppression logique (`MedecinsJdbc.java:35`, filtré par centre dans `ReferentielsOrganisationService.java:51`) |
| M fiche O5 corrompue | **corrigé** | 0 caractère de contrôle dans les fiches ; statut `in-progress` cohérent avec l'état |
| m horloge et UUID | **corrigé en partie** | `Clock` et générateur injectés (`OrganisationConfiguration.java:93,98`). Reste `SocietesJdbc.java:34`, qui remplace un `created_at` NULL par `now()` |
| m correspondance par sous-chaîne | **corrigé en partie** | Exceptions typées pour société, centre et logo. Reste `ErreursDeLOrganisationAdvice.java:79-81` (« inconnu »/« introuvable ») pour salle, médecin, machine et protocole |
| m tri `ORDER BY code` | **décidé** | KB-ORG-05 (date litigieuse, M1) |
| m glossaire `proposed` | **non corrigé** | `glossaries/organisation.md:11-16` |
| nit fichier vide | **non corrigé** | `EquipementHttpCharacterizationIntegrationTest.java` (racine, 0 octet, suivi par git) |

## 2. Contrôles demandés
**Sécurité.**
- `/api/v1/societes/**` : le legacy imposait SUPERADMIN sur toute la classe (`SocieteRestController:49` @ b365a63a^). La matrice golden couvre 14 routes × 11 profils + anonyme et ne présente aucun écart.
- `/api/v1/admin/referentials/**` : le legacy imposait ADMIN ou SUPERADMIN avec `CenterAccessGuard` (`ReferentialAdminRestController:49`). Route non modifiée. Le SUPERADMIN y reçoit en réalité 403 `ROLE_SCOPE` (`o2-administration/matrice-des-droits.approved.json:10`).
- `GET /referentials/*` : pas de garde dans le legacy (`ReferentialRestController:24-95`). Le nouveau code ajoute la garde du jeton, ce qui fait passer 12 cellules de 200 à 403 pour « ADMIN sans centre ». C'est le seul écart de la matrice (diff vérifié).
- `RoleScopeFilter` : l'ajout de `/api/v1/organisation` (`:43`) ne concerne que les comptes SUPERADMIN, y compris les comptes multi-rôles (union). Les autres rôles ne sont pas touchés (`:129-132`) et aucune fuite inter-centres n'a été trouvée.
- Le corps de la requête n'est désérialisé qu'après `@PreAuthorize` (`CorpsJson`). Les octets, eux, sont lus avant.
- Cache : `CacheEvict` est présent sur les sauvegardes (`SallesJdbc:31`, `CreneauxJdbc:31`, `MedecinsJdbc:34`) ; l'admin générique vide aussi le cache après le commit, par préfixe de centre (`ReferentialCacheEvictor:41-50`). Voir m2.

**Parité.**
- Les 11 goldens `_nouveau_code` correspondent exactement aux écarts décidés :
  - garde de centre : 3 goldens + la matrice ;
  - tri par code : 2 ;
  - 400 sur un UUID illisible : 3 ;
  - tri des médecins par nom : 1 ;
  - suppression logique des médecins : 1.
- J'ai comparé chaque golden avec son archive `_ecarts-legacy` (diff). Les différences se limitent à l'écart annoncé.
- Aucun indice d'approbation en lot. En revanche, rien ne permet de prouver que les goldens legacy ont bien été produits sur le legacy (M2).

**Schéma.** V51 et V52 sont additives et idempotentes, avec `DEFAULT TRUE` (les salles restent ouvertes et les médecins actifs). Elles sont cohérentes avec H2 et ne créent aucun doublon de version.

**Concurrence.** Le verrou de la société est pris avant la relecture des centres actifs, dans la même transaction. `lock_timeout` vaut 5 s et son dépassement renvoie 409 `ORGANISATION_ECRITURE_CONCURRENTE`. Résidus en m5.

**Tests dépendant de l'horloge.** Aucun. Les dates des tests unitaires sont fixes (`Clock.fixed`) et les contrats JDBC n'en vérifient aucune.

## 3. Nouveaux constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy/traçabilité | **Décisions de l'utilisateur datées du lendemain des commits.** KB-ORG-02/03/05, la suppression logique et l'ouverture SUPERADMIN sont « décidées le 2026-11-03 ». Or les commits datent du 2026-11-02 (16:33 et 17:26), comme aujourd'hui. `seams.md` écrit même « décision du 2026-11-03 : FERMÉ le 2026-11-02 ». L'ADR 0070 reste `proposed` tout en se disant « décision de l'utilisateur ». Aucun ADR ne couvre la garde de centre (KB-ORG-05) ni le 400 (KB-ORG-02), qui sont pourtant des corrections de bugs legacy | `known-bugs.md:1305-1308` ; `seams.md:200` ; `adr/0070…md:3,44` ; `V52__…sql:1` ; `organisation-retroactif.md:30-35` | Faire confirmer chaque décision par l'utilisateur et la dater au jour réel ; passer l'ADR 0070 en `accepted` ; écrire un ADR court « écarts de parité Organisation » (KB-ORG-02/03/05 et suppression logique) |
| major | tests/legacy | **Origine legacy des goldens non vérifiable.** Le worktree `b365a63a^` a été supprimé ; il n'existe ni script de rejeu legacy ni journal ; les goldens legacy sont entrés dans le même commit que les correctifs de parité (afa99250). Le seul script présent rejoue le nouveau code sur PostgreSQL 16 | `organisation-retroactif.md:9-12` ; `backend/scripts/` (seul `organisation-pg16-rejeu.sh`) ; `git show --stat afa99250` | Ajouter un script reproductible (`git worktree add b365a63a^` → copie du filet → `mvn verify` → diff des goldens) et le faire exécuter une fois par l'utilisateur |
| major | archi/sécurité | **Élargissement des droits du SUPERADMIN par rapport au legacy, sur des routes sans consommateur.** Dans le legacy, le SUPERADMIN ne pouvait écrire aucun référentiel de centre (403 `ROLE_SCOPE`). Il peut désormais créer, modifier ou supprimer les médecins, machines, protocoles, salles et créneaux de **n'importe quel centre** : le centre vient du corps ou du paramètre (`SessionLegacy.java:57-58`). Le frontend n'appelle aucune route `/api/v1/organisation` : `OrganisationApiService` n'est importé par aucun composant | `SessionLegacy.java:57-63` ; `ReferentielsControleur.java:39` ; `o2-administration/matrice-des-droits.approved.json:10` ; `frontend/src/app/core/api/organisation-api.service.ts` (aucun importeur) | Limiter le SUPERADMIN aux routes `centres` (comme `/societes`), ou faire acter explicitement l'écriture transverse dans l'ADR ; à défaut, désactiver les routes sans consommateur |
| major | docs | **Règles de gestion et documentation client non mises à jour.** Garde de centre sur `/referentials`, 400 sur UUID illisible, médecins désactivés et triés par nom, 409 en écriture concurrente : `03-organisation.md` n'a pas été modifié depuis le 2026-10-04, et aucun commit Organisation ne régénère `docs/client` | `git log -- docs/reference/regles-de-gestion/03-organisation.md` → 34956190 (2026-10-04) | Ajouter des RG-ORG pour chaque écart, régénérer la documentation fonctionnelle et client |
| minor | legacy | §36 contredit les décisions : il annonce « identifiant illisible : 500 comme le legacy (KB-ORG-02, à décider) » alors que le 400 est décidé, et son titre ne mentionne que V51 | `verifications-production.md:1602,1624-1625` | Corriger le §36 (400, V51 + V52) |
| minor | archi | Le cache des générateurs (`ref.generateurs`) n'est pas invalidé par `SallesJdbc.sauvegarder`, alors que l'admin générique l'invalide. `@CacheEvict` s'exécute avant le commit, si bien qu'une lecture concurrente peut remettre l'ancienne valeur en cache | `SallesJdbc.java:31` contre `ReferentialAdminTables.java:38` | Invalider après le commit (même mécanisme que `ReferentialCacheEvictor`) et inclure `ref.generateurs` |
| minor | stratégique | Fermer une salle (O2) est sans effet : aucun lecteur en dehors du contexte ne filtre sur `salle.ouverte`, et la planification lit toutes les salles | `LectureDesRessourcesLegacy.java:48`, `SallesDuCentreJdbc.java:30` | Préciser la portée de « Salle fermée » au glossaire et la faire consommer en O4, ou retirer la route |
| minor | legacy | `TableDeLOrganisationTest` donne le même motif générique aux 30 entrées, y compris à un écrivain (l'admin générique). Elle exclut les `/entity/` (JPA `SocieteJpaEntity`/`CenterJpaEntity`) et ne voit pas le SQL dynamique | `TableDeLOrganisationTest.java:37,64,95` | Motif propre à chaque entrée ; traiter les entités JPA à part |
| minor | tactique | Concurrence résiduelle : la société cible d'un transfert n'est pas verrouillée (transfert possible vers une société désactivée en parallèle). `centers.code` n'a pas de contrainte d'unicité en base, et l'unicité est vérifiée par lecture puis écriture. `SET LOCAL` est posé à l'entrée de l'unité et non « au premier verrou » comme l'affirme la javadoc | `OrganisationService.java:111-118,133-135` ; `V1__baseline_schema.sql:1986` (unicité sur `societes` seulement) ; `UniteDeTravailSpring.java:15,46` | Verrouiller les deux sociétés dans l'ordre des identifiants ; index unique sur `centers(code)` après le contrôle SELECT |
| minor | archi | Le 400 renvoie le message technique (« Invalid UUID string: pas-un-uuid ») | `o1-societes/detail-identifiant-illisible_nouveau_code.approved.json` | Message métier fixe |
| minor | stratégique | Glossaire : Médecin, Machine, Protocole, Écriture concurrente et Verrou de la société sont absents (0 occurrence) ; les 6 termes existants sont encore `proposed` | `glossaries/organisation.md` | Ajouter ces termes en `proposed` et les faire valider |
| minor | docs | La roadmap affiche O1–O4 `planned` alors que l'état indique `in-progress`, et O5 en est absente | `roadmap.md:290-293` | Aligner |
| nit | legacy | `KB-ORG-04` : le harnais trie les centres (`scrubTrie`), ce qui masque l'ordre réel renvoyé | `known-bugs.md:1307` | Acceptable ; le documenter dans la RG |

Non trouvé : aucune violation `check-arch`, aucune entrée morte dans `covered.json` (129), `seamAllowed` vide (seams fermés), aucun test dépendant de l'horloge.

## 4. État réel proposé (à poser par l'utilisateur)
| Slice | Actuel | Proposé | Condition |
|---|---|---|---|
| O1 sociétés/centres | in-progress | `built` | après confirmation datée des décisions (M1) et rejeu legacy reproductible (M2) ; sinon rester `in-progress` |
| O2 salles/créneaux | in-progress | `in-progress` | routes `/organisation/salles` sans consommateur et fermeture sans effet ; parité de l'admin générique acquise |
| O3 référentiels | in-progress | `built` | mêmes conditions que O1 + décision sur l'écriture transverse du SUPERADMIN |
| O4 lectures publiées | planned | `planned` | 30 lecteurs SQL directs |
| O5 retrait `bc-referential` | in-progress | `in-progress` | ADR 0070 `proposed`, entités JPA restantes |

## 5. Contrôles de production (ordre, SELECT uniquement, sur une copie d'abord)
1. ORG-1, ORG-2, ORG-10 : `salle.ouverte`, `machine`/`protocole` et `medecin.actif` présents après V51/V52, **avant** le déploiement du code.
2. ORG-8 : aucune société sans centre actif (sinon l'invariant bloque toute opération sur ses centres).
3. ORG-5/ORG-6 : aucun médecin, salle ou créneau orphelin de centre.
4. Nouveau : `SELECT u.id FROM … WHERE rôle ADMIN AND center_id IS NULL` (comptes qui perdent `/referentials/*` : 200 → 403).
5. ORG-7 + `SELECT code, count(*) FROM centers GROUP BY code HAVING count(*)>1` (doublons de codes de centre).
6. Après déploiement, avec des comptes de test : `/referentials/salles?centerId=<autre>` → 403 ; ADMIN d'un autre centre sur `POST /organisation/referentiels/medecins` → 403 ; SUPERADMIN sur `/admin/referentials` → 403 `ROLE_SCOPE` (inchangé).
7. Écrans référentiels (médecins, salles) : même nombre de lignes qu'avant ; médecins triés par nom ; médecin supprimé = `actif=FALSE` (ORG-10).
8. Caches : modifier une salle dans l'admin, puis relire `/referentials/salles` et `/generateurs`.
9. Logo et pied de page des documents ; connexion → choix de la société et du centre.
10. Journaux : aucune 409 `ORGANISATION_ECRITURE_CONCURRENTE` inattendue la première semaine.

## Verdict : **pass-with-actions**
Les 3 blockers de la vérification n° 1 sont levés (sécurité, schéma, filet). Il reste des majors de traçabilité et de documentation, ainsi qu'un élargissement de droits à arbitrer.

Top 3 actions :
1. Faire confirmer et dater correctement par l'utilisateur les décisions KB-ORG-02/03/05, la suppression logique et l'ouverture SUPERADMIN ; ADR 0070 `accepted` et ADR des écarts.
2. Rendre le rejeu legacy reproductible (script `worktree b365a63a^`) et le faire exécuter une fois.
3. Arbitrer l'écriture transverse du SUPERADMIN sur les référentiels (routes sans consommateur frontend), puis mettre à jour `03-organisation.md`, le §36 et la documentation client.
