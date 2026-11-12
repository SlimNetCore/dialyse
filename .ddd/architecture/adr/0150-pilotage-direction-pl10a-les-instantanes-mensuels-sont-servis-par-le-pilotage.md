---
status: accepted
date: 2026-11-12
sources: pl10a-filet.md, pl10a-realisation.md, ADR 0103, ADR 0105, ADR 0148, known-bugs.md (KB-PL10a-1..5)
---

# ADR 0150 - PL10a : les trois routes des instantanes mensuels sont servies par le Pilotage direction

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, tables, codes. `GET /api/v1/direction/snapshots`, `GET /snapshots/{mois}` et `POST /snapshots/{mois}` restaient dans `DirectionSnapshotRestController` (platform) ; la route `/report` est servie par le Pilotage depuis PL9.

## Decisions
1. `InstantanesDeLaDirectionControleur` (`contexts/pilotage-direction/interfaces/http`) remplace le contrôleur de la plateforme (supprimé ; règle ArchUnit contre son retour). Session par `SessionDeLaDirection` (ACL `SessionLegacy`, créée en PL9).
2. `DirectionAccessGuard` RESTE en plateforme : encore utilisée par `DirectionDashboardRestController` et `WebSocketAuthentication` (ADR 0105 pt 4). Elle disparaîtra avec Identité et accès.
3. Écarts décidés par l'utilisateur (le 2026-11-12), tous CORRIGÉS : KB-PL10a-1 (chemins voisins 404, Accept incompatible 406, message de jeton sans détail technique comme KB-PL9-5), KB-PL10a-2 (contenu vide, `{}`, non objet ou non fini : 422 `INSTANTANE_ILLISIBLE`), KB-PL10a-3 (liste et lecture journalisées, action `DIRECTION_INSTANTANE_CONSULTATION`), KB-PL10a-4 (lignes dont le mois est illisible ignorées dans la liste, jamais supprimées ; la capture n'écrit que des mois `AAAA-MM`), KB-PL10a-5 (la relecture masque les effectifs de centre de 1 à 4 ; seuil unique `SeuilDAnonymat`, PD5-R9 ; le `seuilAnonymat` stocké n'est plus relu).
4. Horodatage de capture : horloge injectée du Pilotage (`InstantanesMensuels.enregistrer(instantane, genereLe)`) au lieu de `CURRENT_TIMESTAMP` (KB-DIR-06 d). Même instant en production, testable. Conséquence : le rapport mensuel imprimé affiche l'heure d'Alger de l'horloge du Pilotage.
5. Cumul DIRECTION + ADMIN/SUPERADMIN/MEDECIN : REPRODUIT (à confirmer par les métiers).

## Questions ouvertes
- KB-PL10a-4 : la décision parle de « 400 si invalide » à la capture ; le mois invalide du chemin répond toujours 422 `MOIS_INVALIDE` (comportement PD2 conservé, aucune contrainte `CHECK` ajoutée en base). À trancher (400 ou 422).
- KB-PL10a-5 : seuil unique à confirmer par les métiers et le DPO au plus tard le 2026-12-15.
- Cumul de rôles : à confirmer au plus tard le 2026-12-15.
