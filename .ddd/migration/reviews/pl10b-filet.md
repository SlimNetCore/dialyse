---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B5/PL10), pl10a-filet.md, known-bugs.md (KB-DIR-*, KB-PL9-*, KB-PL10a-*), PD1/PD3/PD6, DirectionDashboardRestController
---

# PL10b - filet de caracterisation du tableau de bord de la Direction

Aucun code de production modifie, seamAllowed = []. Rien commite. `covered.json` inchange : controleur, `DatesIllisiblesDeLaDirectionAdvice`, `DirectionAccessGuard`, `AnonymityPolicy` et les 4 `Direction*QueryService` y figurent deja (guard-legacy non bloquant).

## Perimetre reel (9 routes, pas 5+5)
`DirectionDashboardRestController` (`/api/v1/direction`, `@PreAuthorize("hasRole('DIRECTION')")`, 184 l.) : `GET /societe`, `/overview`, `/breakdown` (servies par `TableauDeBordDeLaDirection`, PD1/PD4), `/indicators` (`IndicateursDeLaDirection`, PD6/DM8), `/alerts/history` (`AlertesDeLaDirection`, PD3), puis `/gmao`, `/stock-groupes`, `/capacite`, `/absences` (services de lecture restes en platform : `DirectionGmao/StockGroupes/Capacite/AbsencesQueryService`). `/report` est deja au Pilotage (PL9). Le controleur ne porte que le jeton + `DirectionAccessGuard.requireSociete()` ; `Cache-Control: no-store` partout. Le plus d'ecart : 9 > 5 routes.

**Decoupage propose** : PL10b1 = controleur + garde + advice pour les 5 routes deja servies par le Pilotage (`societe, overview, breakdown, indicators, alerts/history`) ; PL10b2 = les 4 routes de lecture (`gmao, stock-groupes, capacite, absences`) avec leurs services, qui se confondent avec PL10c (vers GMAO/Planification, Stock, Absences). Le filet couvre les deux.

## Ajout : `LeTableauDeBordDeLaDirectionPl10bCharacterizationIntegrationTest` (8 tests, 7 goldens approuves un a un depuis les .received)
Classe : `backend/platform/src/test/java/.../characterization/pilotagedirection/pl10b/`. Goldens : `backend/platform/src/test/resources/characterization/pilotagedirection/pl10b/tableau_de_bord/<test>/`. Harnais PD (H2, horloge du Pilotage figee 23:30 UTC = 00:30 Alger le 2026-11-11).

| Famille | Golden |
|---|---|
| 9 routes x 11 profils + DIRECTION portant un centre, 8 cumuls (DIRECTION+ADMIN/SUPERADMIN/MEDECIN/INFIRMIER/SECRETAIRE, SUPERADMIN sans societe, roles minuscules/sans prefixe), 10 gardes de societe (autre societe, inconnu, inactif, detache, societe inactive/blanche/illisible, id illisible = 400, societe sans centre = 200), anonyme (403), parametre illisible x MEDECIN/anonyme/societe inactive | `garde_roles_et_jetons` |
| 23 formes de `from`/`to` sur 7 routes (debut>fin 422, 5 ans, vide, `2026-1-1`, sans tirets, heure, `Z`, an 0001/999999/9999, chiffres arabes, parametre double, casse, parasites) ; valeurs par defaut des 5 lectures (jour du Pilotage ou de la JVM) ; societeId/centreId ignores | `formes_de_periode_et_valeurs_par_defaut` |
| `limit` de l'historique : vide, 199-201, bornes int, hex, `+7`, espaces, arabe-indien, double ; 210 alertes | `limit_de_l_historique` |
| Societe sans centre : corps complets des 9 routes ; 60 centres de plus (62), temps < 20 s | `sans_centre_et_volume` |
| Cloisonnement entre societes (9 routes) ; seuil d'anonymat route par route, centre B a 3, 4, 5, 6 patients | `cloisonnement_et_seuil` |
| Corps complets des 5 lectures sur le jeu complet PD | `corps_du_jeu_complet` |
| Audit : 9 GET non suivis, aucune ligne de journal | `audit` |
| HEAD/POST/PUT/PATCH/DELETE/OPTIONS x 9 routes, POST anonyme/ADMIN, 6 `Accept` x 9, 18 chemins voisins, corps sur GET, en-tete X-Societe-Id | `methodes_chemins_et_accept` |

Non duplique : PD1 (contenu overview/breakdown/societe, bornes de ces 2 routes, droits), PD3 (limit 0/-5/500/abc, contenu de l'historique), PD6/DM8/DM9 (indicateurs), `DirectionIntegrationTest`, `DatesIllisiblesDeLaDirectionAdviceTest`, filets GMAO/Stock/Planification/Absences (contenu metier de chaque lecture).

## Execution
3 passes : 2 x la classe seule (8 tests) + 1 en `-Dsurefire.runOrder=random` avec PD1, PD3, `DirectionIntegrationTest`, `DatesIllisiblesDeLaDirectionAdviceTest` (43 tests) : 0 echec, 0 flaky. Classe PL10b ~4 s apres le demarrage ; contexte Spring ~30 s (2 contextes : harnais PD et `DirectionIntegrationTest`) ; ~70 s par passe. Journaux : `scratchpad/pl10b-pass1..3.log`.

## Bugs connus (non corriges) : KB-PL10b-1..6 (known-bugs.md)
1 parametre illisible traite avant la garde (400 au lieu de 403 sur /absences /capacite /stock-groupes et societe inactive) ; 2 consultations non journalisees ; 3 chemins voisins et `Accept` incompatible en 500, XML servi ; 4 fin par defaut : jour d'Alger sur 3 routes, jour de la JVM sur 4 ; 5 seuil d'anonymat heterogene entre routes (PD5-R9) ; 6 analyse permissive (hex, `+7`, espaces, arabe-indien) et cumul de roles reproduit.

## Limites
- Les lectures GMAO, Stock, Absences et les indicateurs sont verrouillees en forme et en cloisonnement, pas en valeurs metier : le jeu PD n'a ni pannes, ni groupes d'articles, ni absences ni analyses. Le seuil d'anonymat de ces 4 routes est donc inconclusif (KB-PL10b-5) ; leurs filets dedies restent la reference.
- Anonyme : 403 sous MockMvc (pas de point d'entree 401). `generatedAt` masque. H2 uniquement.
- La fin par defaut des 4 routes JVM est testee par comparaison avec `LocalDate.now()` : fragile seulement si l'execution a lieu le 2026-11-11 (jour d'Alger fige), sans objet apres.
- `DirectionAccessGuard` utilise aussi par `WebSocketAuthentication` (ADR 0105) : le chemin WebSocket n'est pas exerce ici (PD5).

## Decisions a demander
1. Journaliser les consultations du tableau de bord (comme le rapport) ? (KB-PL10b-2)
2. 404 propre pour les chemins voisins et 406 pour `Accept` incompatible ; refuser ou servir XML ? (KB-PL10b-3)
3. 403 avant 400 sur toutes les routes, y compris societe inactive ? (KB-PL10b-1)
4. Fin par defaut unique : jour d'Alger (horloge du Pilotage) partout ? (KB-PL10b-4)
5. Un seul seuil d'anonymat (PD5-R9) a toutes les routes, et le publier partout ? (KB-PL10b-5)
6. Rejeter hex, `+`, espaces, chiffres arabes-indiens, parametres vides ou repetes ? (KB-PL10b-6)
7. Cumul DIRECTION + ADMIN/SUPERADMIN/MEDECIN/INFIRMIER/SECRETAIRE : voulu ? (KB-PL9-5)
8. Decoupage PL10b1 / PL10b2 (= PL10c) accepte ?
