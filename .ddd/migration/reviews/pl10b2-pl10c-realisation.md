---
status: built, un seul point bloqué (WebSocketAuthentication, guard-legacy) ; build complet voir ci-dessous ; pas de commit
date: 2026-11-12
sources: pl10b1-realisation.md, pl10b-filet.md, ADR 0105, 0150, 0151, 0152, known-bugs.md (KB-PL10b-1..6)
---

# PL10b2 + PL10c - réalisation

## Fait
- Pilotage : 4 cas d'usage (`ConsulterLaGmaoDesCentres`, `ConsulterLeStockDesGroupes`, `ConsulterLaCapaciteDesCentres`, `ConsulterLesAbsencesDesCentres`), 4 ports (`GmaoDesCentres`, `StockDesGroupes`, `CapaciteDesCentres`, `AbsencesDesCentres`), 4 ACL par langage publié (`acl/gmao`, `acl/stock`, `acl/planification`, `acl/absencespatients`), `LecturesDeLaDirectionControleur`, `AccesALaDirection` (langage publié, implémenté par `SessionLegacy`), pom (+gmao, planification, absences-patients), composition.
- Plateforme supprimée : `DirectionDashboardRestController`, `Direction{Gmao,StockGroupes,Capacite,Absences}QueryService`, `AnonymityPolicy`, `ValorisationArticlesService` (cache repris par l'ACL du Stock, même nom de cache) ; `AuditActionResolver` (+4 routes), `DatesIllisiblesDeLaDirectionAdvice` (plus aucune route de la Direction ; 404/406 étendus), `StockEvenementsListener` (nom du cache).
- Gardes : `TableDuPilotageTest` devenue permanente (3 comptes, aucune date), `TableDesSeancesTest` (-1 entrée), 3 autres gardes du 2026-12-15 sans entrée concernée, aucune date repoussée ; `FrontieresDesContextesTest` : règles « ne reviennent pas ».
- Goldens PL10b : 4 relus un par un depuis `.received` (audit, formes de période, méthodes/chemins/Accept, garde/rôles), anciens dans `_ecarts-legacy/*_avant_pl10b2.legacy.json` ; 4 inchangés (`cloisonnement_et_seuil`, `corps_du_jeu_complet`, `sans_centre_et_volume`, `limit`).

## Écarts (tous = décisions de l'utilisateur)
403 avant 400 (3 routes + société inactive) · 406 (pdf, html, charabia, XML) et 404 chemin voisin · journal des 4 routes (200/403) · `from` double = 400 · fin par défaut = jour d'Alger · seuil unique (valeur 5, aucun chiffre ne change). Cumul de rôles reproduit.

## Reste (état exact pour reprise)
1. (FAIT après déblocage) `WebSocketAuthentication` utilise `AccesALaDirection` ; `WebSocketAuthenticationTest` adapté ; `DirectionAccessGuard` supprimée, entrée de `TableDeLOrganisationTest` retirée (8 -> 7), règle ArchUnit anti-retour ajoutée.
2. Confirmation métiers/DPO du seuil unique et du cumul de rôles (avant le 2026-12-15).
3. HTML client de la doc fonctionnelle non régénéré.
