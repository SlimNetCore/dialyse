---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décisions du 2026-11-10, « à confirmer métiers <= 2026-12-15 »), architecte]
context-scope: patient-couverture (payeurs, catégories de transport : contrôleurs de lecture), platform (advice des erreurs de requête de l'administration générique)
related: ADR 0126 (RF3a), 0128 (RF3c), 0123 (RF1c), 0125 (RF2c), 0119 (advice 400) ; fiche `.ddd/migration/slices/patient-couverture-rf3-payeurs.md` ; known-bugs KB-RF3-01 à 03, KB-RF1-01, KB-RF2-01
---

# 0129 - RF3d : 400 sur les entrées illisibles des payeurs, étendu à TOUTE l'administration générique

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles. Un seul écart de comportement DÉCIDÉ par l'utilisateur (modèle RF1c / RF2c), sans compte d'usage modifié ni SQL modifié.

## Décisions
| N° | Décision | Réalisation |
|---|---|---|
| 1 | KB-RF3-01 et 02 : 400 `BAD_REQUEST` (au lieu de 500) sur `/api/v1/admin/referentials/{caisses,agences,centres-payeurs}` : corps non JSON ou absent, `centerId` du corps ou de la requête, identifiant de ressource, `page` illisibles | `DatesIllisiblesDeLaDirectionAdvice` (platform) |
| 2 | Extension à TOUS les autres référentiels de l'administration générique (créneaux, salles, médecins, générateurs, et tout référentiel futur) : résiduel RF1-R4 clos | motif de route `/api/v1/admin/referentials(/|$)` au lieu de `(transporteurs|forfaits)` |
| 3 | KB-RF3-03 : `centerId` ABSENT de `/api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` et de `/api/v1/referentials/categories-transport` : 400 | `PayeursControleur` et `TransportsControleur` : paramètre facultatif puis `IllegalArgumentException` déjà gérée en 400 par leur advice ; les doublons `/api/v1/organisation/referentiels/...` (`centreId` facultatif par conception) répondaient déjà 400 sur un identifiant illisible |
| 4 | Le refus de droits (403) passe AVANT le 400 sur toute route d'administration | même évaluation `@PreAuthorize` que RF1c ; goldens « secretaire, corps illisible » |

## Périmètre et résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF3d-R1 | `GET /api/v1/referentials/{salles,creneaux,medecins,generateurs,...}` (lectures communes servies par Organisation) sans `centerId` répond toujours 500 (golden `organisation/o3-referentiels/salles-sans-centre`) | hors décision (seuls les payeurs et `categories-transport` sont visés) ; à décider avec l'Organisation |
| RF3d-R2 | Import (`/{kind}/import`) : partie `file` absente non couverte par un golden | hors périmètre du filet |

## Preuves
- Rouge vérifié AVANT le code : nouvelle classe de filet (2 tests, `admin-generique`) et scénario « secretaire, corps illisible » ; les `.received` montraient 500 partout.
- 9 goldens existants re-approuvés en `_nouveau_code` à la main (relecture ligne à ligne du `.received`), anciens archivés dans `_ecarts-legacy/**/*_avant_rf3d.legacy.json` : `rf1/lectures-communes/categories-de-transport`, `rf3/lecture/listes`, `rf3/lectures-communes/lectures`, `rf3/saisie-agences/creations`, `rf3/saisie-caisses/{creations,creations-gardees,modifications,suppressions}`, `rf3/saisie-centres-payeurs/creations` ; 2 goldens nouveaux (`rf3/admin-generique/refus-de-forme`, `lectures-communes`). AUCUN golden d'un autre contexte n'a changé (Organisation, Séance SE7e : verts, inchangés).
- Rejeu PostgreSQL 16 (`backend/scripts/patient-couverture-rf3-pg16-rejeu.sh`, port 55801) : vert, contrat des payeurs 20 tests.

## Conséquences
- Les clients qui testaient un 500 sur ces entrées (aucun connu) reçoivent un 400. Aucune donnée en jeu : pas de requête de détection en production.
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier avant le 2026-12-15.
- Étendre aux lectures communes d'Organisation (RF3d-R1) ?
