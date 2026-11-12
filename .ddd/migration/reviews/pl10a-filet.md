---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B5/PL10a), pl9-filet.md, known-bugs.md (KB-DIR-*, KB-PL9-*), PD2 (instantanes)
---

# PL10a - filet de caracterisation des instantanes mensuels de la Direction

Aucun code de production modifie, seamAllowed = []. Rien commite. `covered.json` inchange : `DirectionSnapshotRestController` y figure deja (guard-legacy non bloquant).

## Perimetre reel
Il ne reste que **3 routes** en platform dans `DirectionSnapshotRestController` (`/api/v1/direction/snapshots`, `@PreAuthorize("hasRole('DIRECTION')")`, ~63 l.) : `GET /snapshots` (liste), `GET /snapshots/{mois}` (lecture), `POST /snapshots/{mois}` (capture idempotente). La 4e route de l'inventaire, `GET /snapshots/{mois}/report`, est deja servie par le Pilotage depuis PL9 (ADR 0148). Les services (`InstantanesMensuelsDeLaDirection`, cas d'usage, `InstantanesMensuelsJdbc`, `MasqueDesTotauxFiges`, `SeuilDAnonymat`) sont deja dans `contexts/pilotage-direction` (PD2, ADR 0103) ; la garde `DirectionAccessGuard` et la tache planifiee restent a migrer/brancher. Table : `direction_snapshot` (societe_id, mois VARCHAR(7), payload, generated_at ; unique (societe_id, mois)). Audit : `AuditActionResolver` (POST suivi `DIRECTION_CREATION`).

## Existant non duplique
PD2 (5 tests : cycle de vie/idempotence, mois refuses 60/61 mois et formes 13/00/1, cloisonnement + ancien sans repartition + illisible 422, tache planifiee, relecture masquee d'un ancien instantane) ; PL9 (rapport mensuel) ; `DirectionSnapshotIntegrationTest` (5).

## Ajout : `LesInstantanesDeLaDirectionPl10aCharacterizationIntegrationTest` (7 tests, 6 goldens approuves un a un depuis les .received)
Classe : `backend/platform/src/test/java/.../characterization/pilotagedirection/pl10a/`. Goldens : `backend/platform/src/test/resources/characterization/pilotagedirection/pl10a/instantanes/`. Harnais PD (H2, horloge du Pilotage figee 23:30 UTC = 00:30 Alger).

| Famille | Golden |
|---|---|
| 3 routes x 11 profils (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER, SUPERADMIN = 403 ou `ROLE_SCOPE`), cumuls DIRECTION+ADMIN/SUPERADMIN/MEDECIN (200), roles minuscules/sans prefixe, 10 gardes de societe (autre societe, inconnu, inactif, detache, societe inactive/blanche/illisible, id illisible = 400, societe sans centre), anonyme (403), lignes creees | `garde_roles_et_jetons` |
| 18 formes du mois dans le chemin (espaces, tiret long, chiffres arabes, `.json`, `null`, annee 0000/0001/9999, barre finale = 500, `;` `%00` `%0A` `%2F` = 400), 5 parametres de requete sans effet (societeId, mois, from/to, centreId, pagination), corps de capture ignore | `mois_du_chemin_et_parametres` |
| 20 contenus stockes atypiques (vide, `{}`, `[]`, `null`, nombre, tronque, NaN, BOM, commentaire, texte apres JSON, profondeur 2000, 1e999999, cle dupliquee, centres non objets, effectifs texte/decimaux, centre sous seuil en clair, unicode) sur lecture/capture/liste, contenu jamais reecrit ; gros instantane (3000 centres, 2000 alertes, 24 mois, un centre masque) | `contenus_stockes_atypiques` |
| Seuil d'anonymat a la capture : A/B = 4/0, 4/3, 5/5, 6/5, 6/1 ; totaux supprimes si un centre est masque ; contenu stocke deja masque ; relecture identique apres changement des effectifs | `seuil_d_anonymat_a_la_capture` |
| Instantanes hors bornes deja presents (mois -61, -120, courant, +1, +14 : lisibles, non re-capturables) ; mois non canoniques en base ; tri de la liste ; horodatage (heure de la base, pas l'horloge du Pilotage ; decalage +05:00 conserve) ; volume 74 mois ; 4 captures simultanees (1 ligne, 1 corps) | `hors_bornes_tri_horodatages_concurrence` |
| Audit : GET non suivis, POST suivi (200 x2, 403, 422) | `audit` |
| Methodes, chemins voisins, Accept, Content-Type, HEAD/OPTIONS (Allow trie), corps sur GET, en-tete X-Societe-Id sans effet | `methodes_chemins_et_en_tetes` |

## Execution
3 passes (la 3e `-Dsurefire.runOrder=random`) avec PD2 + PL9 + `DirectionSnapshotIntegrationTest` : 26 tests, 0 echec, 0 flaky (une instabilite corrigee en cours de route : ordre de l'en-tete `Allow`, desormais trie). PL10a ~2 s apres le demarrage ; contexte Spring ~30 s (2 contextes : harnais PD et `DirectionSnapshotIntegrationTest`) ; ~60 s par passe. Journaux : `scratchpad/pl10a-pass1..3.log`.

## Bugs connus (non corriges) : KB-PL10a-1..5 (voir known-bugs.md)
1 chemins voisins et `Accept` incompatible en 500 (dont `/snapshots/<mois>/`) ; 2 contenu vide/non objet servi en 200 tout nul, 1e999999 servi `"Infinity"` ; 3 audit asymetrique (rappels idempotents journalises, lectures non journalisees) ; 4 `mois` sans contrainte de format, tri alphabetique des lignes illisibles ; 5 relecture qui ne masque que les totaux composes (centre isole stocke en clair servi tel quel).

## Limites
- Anonyme : 403 sous MockMvc (pas de point d'entree 401 dans le harnais) ; non comparable a la production.
- `generatedAt` est masque `<HORODATAGE>` par le harnais : seul l'instant (egalite) et la comparaison a l'horloge figee sont verrouilles, pas le format textuel.
- Fuseau de la date de capture : le mois controle suit l'horloge du Pilotage (Alger) ; la bascule UTC/Alger d'un changement de mois n'est pas rejouable (horloge figee en milieu de mois).
- PG16 non execute (H2) ; la tache planifiee reste couverte par PD2 uniquement.
- La concurrence (4 threads) est verrouillee sur H2 ; sur PG16 la course de l'`INSERT` n'est pas observee.

## Decisions a demander
1. 404 propre pour les chemins voisins (dont la barre finale) et 406 pour `Accept` incompatible ; refuser ou servir `application/xml` ? (KB-PL10a-1)
2. Contenu figé vide/`null`/non objet : 422 `INSTANTANE_ILLISIBLE` comme les autres contenus illisibles ? (KB-PL10a-2, KB-PL9-3)
3. Journaliser la liste et la lecture des instantanes (comme le rapport) ; ne journaliser la capture que lorsqu'elle cree ? (KB-PL10a-3)
4. Contrainte de format sur `mois` (CHECK) ou nettoyage a la migration ? (KB-PL10a-4)
5. Relecture : masquer aussi les effectifs de centre de 1 a 4 stockes en clair, lire `seuilAnonymat` stocke, ou conserver (RG-DIR-090) ? Unification du seuil avec PD5-R9. (KB-PL10a-5)
6. Cumul DIRECTION + ADMIN/SUPERADMIN/MEDECIN : voulu ? SUPERADMIN seul est refuse `ROLE_SCOPE` (rappel KB-PL9-5).
7. Horodatage de capture par l'horloge injectee du Pilotage plutot que la base (KB-DIR-06 d) ?
