---
status: accepted
date: 2026-11-12
sources: pl10b-filet.md, pl10b1-realisation.md, ADR 0102, ADR 0104, ADR 0105, ADR 0106, ADR 0150, known-bugs.md (KB-PL10b-1..6)
---

# ADR 0151 - PL10b1 : cinq routes du tableau de bord de la Direction sont servies par le Pilotage direction

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, tables, codes. `DirectionDashboardRestController` (platform) hébergeait neuf routes ; cinq sont déjà calculées par le Pilotage (`societe`, `overview`, `breakdown`, `indicators`, `alerts/history`), quatre lisent d'autres contextes (`gmao`, `stock-groupes`, `capacite`, `absences` : PL10b2/PL10c).

## Decisions
1. `TableauDeBordDeLaDirectionControleur` (`contexts/pilotage-direction/interfaces/http`) sert les cinq routes avec `SessionDeLaDirection` (ACL `SessionLegacy`) ; le contrôleur de la plateforme ne garde que les quatre autres, sans changer leurs URL (règle ArchUnit : il ne dépend plus du Pilotage).
2. `DirectionAccessGuard` RESTE en plateforme (`WebSocketAuthentication`, ADR 0105, et les quatre routes restantes).
3. Écarts décidés par l'utilisateur (2026-11-12), CORRIGÉS sur les cinq routes : KB-PL10b-1 (garde avant l'analyse des paramètres), KB-PL10b-2 (consultation journalisée `DIRECTION_TABLEAU_DE_BORD_CONSULTATION`), KB-PL10b-3 (404 chemins voisins, 406 `Accept` hors JSON, XML refusé), KB-PL10b-6 (`limit` décimal ASCII ; vide ou répété = 400 ; dates répétées = 400). KB-PL10b-4 : fin par défaut = jour d'Alger (horloge du Pilotage), déjà le cas. KB-PL10b-5 : seuil unique du Pilotage (PD5-R9).
4. Cumul DIRECTION + ADMIN/SUPERADMIN/MEDECIN/INFIRMIER/SECRETAIRE : REPRODUIT (à confirmer).
5. Pour PL10b2, les mêmes décisions s'appliquent aux quatre routes restantes (403 avant 400, journal, 404/406, jour d'Alger, seuil unique, analyse stricte).

## Questions ouvertes
- Dates `from`/`to` vides : toujours ignorées (période par défaut) ; la décision « vide = 400 » visait les paramètres numériques. À confirmer.
- Seuil unique (KB-PL10b-5) et cumul de rôles : à confirmer métiers/DPO au plus tard le 2026-12-15.
