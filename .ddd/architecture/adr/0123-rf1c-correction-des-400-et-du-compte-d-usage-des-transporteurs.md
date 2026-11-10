---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décisions du 2026-11-10, « à confirmer métiers <= 2026-12-15 »), architecte]
context-scope: patient-couverture (transporteurs), platform (advice des erreurs de requête)
related: ADR 0122 (RF1), 0119 (advice 400 de la Direction) ; fiche `.ddd/migration/slices/patient-couverture-rf1-transporteurs.md` ; known-bugs KB-RF1-01 et KB-RF1-03
---

# 0123 - RF1c : 400 sur les entrées illisibles des transporteurs, compte d'usage borné au centre

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles. Deux écarts de comportement DÉCIDÉS par l'utilisateur ; tout le reste de RF1 est inchangé (20 des 27 fichiers golden RF1 inchangés, 7 re-approuvés en `_nouveau_code`).

## Décisions
| N° | Décision | Réalisation |
|---|---|---|
| 1 | KB-RF1-01 : 400 `BAD_REQUEST` (au lieu de 500 `INTERNAL_ERROR`) sur `/api/v1/admin/referentials/transporteurs/**` (corps non JSON, corps absent, `centerId` du corps ou de la requête, identifiant de ressource, `page` illisibles) et sur `/api/v1/referentials/transporteurs` sans `centerId` | `DatesIllisiblesDeLaDirectionAdvice` (même code `BAD_REQUEST`, mêmes libellés « Paramètre illisible : x », « Corps de la requête illisible », « Paramètre obligatoire absent : x ») étendu à la route des transporteurs de l'administration, reconnue par son chemin ; `TransportsControleur` lève l'`IllegalArgumentException` déjà gérée en 400 par `ErreursDesTransportsAdvice` |
| 2 | Le refus de droits (403) passe AVANT le 400 sur la route d'administration des transporteurs (la lecture du corps précède `@PreAuthorize`) | même évaluation `@PreAuthorize` que les statistiques médicales ; golden « secretaire, corps illisible » |
| 3 | KB-RF1-03 : le contrôle d'usage à la suppression ne compte que les patients du centre du transporteur (`patients.center_id`), aller et retour ; 422 `REFERENTIAL_IN_USE` inchangé pour le reste | `CarnetDeTransporteurs.nombreDeReferences(centre, id)` ; contrat unique passé par le double, H2 et PostgreSQL 16 (12 tests) |

## Périmètre et résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF1-R4 | L'advice n'est PAS étendu aux 8 autres référentiels de l'administration générique (forfaits, créneaux, salles, médecins, caisses, agences, centres payeurs, plus tout nouveau) ni à `GET /api/v1/referentials/categories-transport` sans `centerId` (500 conservé) | limité aux transporteurs par prudence ; l'extension changerait des goldens d'autres contextes (RF2, RF3, Organisation) et relève d'une décision par référentiel. La numérotation RF1-R3 était déjà prise (ADR 0122) |

## Preuves
- Rouge vérifié AVANT le code : contrat double et H2, 4 échecs (attendu 4 et 0, obtenu 5 et 1) ; filet RF1, 6 échecs sur 16 tests (500 au lieu de 400 ; 422 au lieu de 204).
- Goldens `_nouveau_code` approuvés à la main (7 fichiers) ; anciens archivés dans `_ecarts-legacy/**/*_avant_rf1c.legacy.json`.
- Rejeu PostgreSQL 16 (`backend/scripts/patient-couverture-rf1-pg16-rejeu.sh`, port 55791) : vert, requête d'usage bornée au centre exécutable sur le schéma réel.

## Conséquences
- Un transporteur d'un centre référencé seulement par des fiches d'un autre centre devient supprimable (les identifiants sont des UUID propres à un centre : cas improbable). Détection en production : `.ddd/migration/verifications-production.md` § 83.
- Les clients qui testaient un 500 sur ces entrées (aucun connu) reçoivent un 400.
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier des deux corrections avant le 2026-12-15.
- Étendre le 400 aux huit autres référentiels et à `categories-transport` (RF1-R4) ?
