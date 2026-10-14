---
status: superseded by 0024
date: 2026-10-09
decision-makers: [architecte, direction technique, administrateur]
context-scope: global
research: []
---
# 0017 — Router les slices par une façade interne (filtre + forward) pilotée par centre, URLs inchangées

## Contexte et problème
Les 49 entrypoints GMAO sont sous `/api/v1/gmao/**` (contrôleurs `platform/.../web/rest/gmao/*`), appelés par `frontend/.../gmao-api.service.ts`. Le legacy est gelé (règle 20-legacy-safety) : on ne peut ni retirer ni conditionner ses contrôleurs. Chaque centre est un tenant (`RoleScopeFilter`, garde de centre) ; la bascule doit être réversible en un geste. Deux contrôleurs sur la même route empêcheraient le démarrage.

## Options considérées
1. Reverse proxy (nginx/gateway) devant le monolithe — pour : externe au code ; contre : ne connaît pas le centre de session/JWT, nouveau composant d'infra, une seule JVM de toute façon.
2. Nouvelles URLs + frontend modifié — contre : double travail, pas de bascule par cohorte, rollback = redéploiement front.
3. **Filtre applicatif après la chaîne de sécurité : lit la table de routage, `forward` vers `/_strangler/<contexte>/**` (contrôleurs du nouveau contexte), sinon laisse passer vers le legacy** — pour : même URL, auth/centre/habilitations déjà résolus, cohorte par centre, rollback par une ligne de table.
4. Branch-by-abstraction dans le legacy — contre : édite du legacy sans filet.

## Décision
Option 3. Module `backend/contexts/facade` (hors slice métier, livré avec la slice 1) : `RoutageStranglerFilter` (ordre après Spring Security), table `facade.routage(slice, centre_id|*, mode off|shadow|on, palier, maj_par, maj_le)` + `facade.routage_journal` (chaque changement), cache 5 s. Chaque réponse porte `X-Route: legacy|nouveau`. Le mode `shadow` sert le legacy et rejoue en copie asynchrone (cf. plan shadow de chaque slice). Les contrôleurs `/_strangler/**` ne sont accessibles qu'en `DispatcherType.FORWARD`. Écritures routées **par centre** (un centre = un propriétaire de données, ADR 0018), lectures éventuellement par %.

## Conséquences
+ frontend, RoleScopeFilter et sécurité inchangés ; kill-switch global `hemodialyse.strangler.enabled=false`.
− un hop de forward (< 1 ms) ; table de routage à protéger (ADMIN société) ; nouvelle couche `facade` à déclarer dans `.ddd/arch-rules.json`.
⚠ Accords requis (câblage, règle 2b) : `SecurityConfig` (autoriser FORWARD sur `/_strangler/**`), `backend/pom.xml` (module), `backend/platform/pom.xml` (dépendance), enregistrement du filtre. Surveillé : métrique `strangler_route_total{slice,route}` et alerte sur `X-Route` inattendu.

## Révision
Façade retirée (filtre, table, `/_strangler`) quand la dernière slice du dernier contexte est à 100 % + observation ; sinon revue à chaque fin de slice.

## Amendement 2026-10-10 — filtre remplacé par un HandlerMapping (revue B1)
Un filtre servlet d'ordre 0 forwardait avant les filtres legacy d'audit, de licence et de périmètre de rôle. La façade est désormais un `HandlerMapping` du DispatcherServlet (`RoutageStranglerMapping`) : même `forward` vers `/_strangler/**`, mais exécuté après toute la chaîne de filtres. La décision suit le centre propriétaire du plan pour les accès par identifiant (revue B2).

## Amendement 2026-10-10 — table de routage runtime livrée (revue M4)
La table de routage vit dans le schéma `gmao` (migration additive V37 : `gmao.routage`, `gmao.routage_journal`) et non dans un schéma `facade` : la façade est dans le module du contexte, aucun module `facade` n'a été créé. Écarts assumés par rapport à la décision initiale : une ligne **par centre uniquement** (pas de ligne joker `*` : aucune ligne = tout au legacy, rien n'est actif par défaut) ; modes `OFF`, `ON` et `RETOUR_LEGACY` (le `shadow` reste à livrer avec le shadow run de la slice) ; colonne `gele` pour le gel d'écriture (503 + `Retry-After`, lectures non gelées). Source par défaut `hemodialyse.strangler.source=table`, cache 5 s invalidé localement à chaque changement, **double garde conservée** (`hemodialyse.strangler.enabled=false` par défaut ET ligne `ON`). Pilotage par `BasculerUnCentre` : gel -> drainage de l'outbox/projection -> réconciliation nouveau/legacy -> bascule -> dégel, exposé en API SUPERADMIN `/api/v1/supervision/strangler/{tranche}/centres/**` (auditée) ; procédure SQL de secours dans la fiche G1. Règle des événements en attente : aucune projection vers le legacy pour un centre `RETOUR_LEGACY`. Table à protéger : seuls SUPERADMIN (API) et DBA (SQL).
