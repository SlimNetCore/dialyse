---
status: built (build complet -o test -fae vert, 3 passes ciblees vertes dont random, check-arch 0 ; docs a jour ; HTML client non regenere ; pas de commit)
date: 2026-11-12
sources: pl10b-filet.md, pl10a-realisation.md, ADR 0150, known-bugs.md (KB-PL10b-1..6)
---

# PL10b1 - realisation (rapport partiel)

## Fait
- Pilotage : `TableauDeBordDeLaDirectionControleur` (`interfaces/http`) sert `GET /api/v1/direction/{societe, overview, breakdown, indicators, alerts/history}` ; `produces` JSON (406), `SessionDeLaDirection` (garde AVANT les parametres), dates ISO strictes (parametre repete = 400, vide = periode par defaut), `limit` entier ASCII (vide/repete/hex/+7/espaces/arabe-indien = 400).
- Platform : `DirectionDashboardRestController` reduit a `/gmao, /stock-groupes, /capacite, /absences` (PL10b2/PL10c) ; `DatesIllisiblesDeLaDirectionAdvice` : 404/406 etendus aux 5 routes, les 5 routes retirees des tableaux 400/403 ; `AuditActionResolver` : `DIRECTION_TABLEAU_DE_BORD_CONSULTATION` (5 GET) ; `FrontieresDesContextesTest` (+2 regles, retour interdit).
- Goldens PL10b (5 relus un par un, `*_nouveau_code.approved.json`, anciens dans `_ecarts-legacy/*_avant_pl10b1.legacy.json`) ; 3 goldens inchanges.

## Ecarts constates (tous = decisions de l'utilisateur)
403 avant 400 (societe inactive + from=hier sur 3 routes) · 406 (pdf/html/charabia/XML) et 404 (chemins voisins) · journal des 5 routes (200/400/403) · limit strict · `from` double = 400 (decision « repete = 400 » etendue aux dates) · espace initial encode reste 400.

## A reprendre pour PL10b2 (memes decisions, routes gmao/stock-groupes/capacite/absences)
KB-1 (403 avant 400 sur capacite/stock-groupes/absences et societe inactive), KB-2, KB-3, KB-4 (fin = jour d'Alger), KB-5 (seuil unique PD5-R9, a confirmer metiers/DPO <= 2026-12-15), KB-6.

## Reste
Build complet, 3 passes ciblees, check-arch, butoirs, referentiel/annexe/glossaire/covered.json, ADR 0151, KB « CORRIGE ».
