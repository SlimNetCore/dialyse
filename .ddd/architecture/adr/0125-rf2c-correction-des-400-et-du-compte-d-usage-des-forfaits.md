---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décisions du 2026-11-10, « à confirmer métiers <= 2026-12-15 »), architecte]
context-scope: patient-couverture (forfaits), platform (advice des erreurs de requête), seance et facturation (signature du port d'usage)
related: ADR 0124 (RF2), 0123 (RF1c, modèle suivi), 0119 (advice 400) ; fiche `.ddd/migration/slices/patient-couverture-rf2-forfaits.md` ; known-bugs KB-RF2-01 et KB-RF2-02
---

# 0125 - RF2c : 400 sur les entrées illisibles des forfaits, compte d'usage des PEC borné au centre

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles. Deux écarts de comportement DÉCIDÉS par l'utilisateur ; tout le reste de RF2 est inchangé (21 des 28 fichiers golden RF2 inchangés, 7 re-approuvés en `_nouveau_code`).

## Décisions
| N° | Décision | Réalisation |
|---|---|---|
| 1 | KB-RF2-01 : 400 `BAD_REQUEST` (au lieu de 500) sur `/api/v1/admin/referentials/forfaits/**` (corps non JSON ou absent, `centerId` du corps ou de la requête, identifiant, `page` illisibles) et sur `/api/v1/referentials/forfaits` sans `centerId` | `DatesIllisiblesDeLaDirectionAdvice` étendu aux forfaits (même code, mêmes libellés que les transporteurs) ; `ForfaitsControleur` lève l'`IllegalArgumentException` déjà gérée en 400 |
| 2 | Le refus de droits (403) passe AVANT le 400 sur la route d'administration | même évaluation `@PreAuthorize` que RF1c ; golden « secretaire, corps illisible » |
| 3 | KB-RF2-02 : le compte des prises en charge à la suppression d'un forfait ne retient que celles du centre du forfait (`prise_en_charge.center_id`), demandé et effectif ; 422 `REFERENTIAL_IN_USE` inchangé pour le reste | `UsageDuForfait.nombreDeReferences(centre, forfait)` ; `UsageDuForfaitParLesPec` borné ; les usages Facturation (lignes de facture) et Séance (séances qui imposent le forfait) reçoivent le centre et l'ignorent : leur compte reste sans centre (KB-SEA-91) |
| 4 | Prix nul / omis (KB-RF2-04) : GARDÉ tel quel | décision de l'utilisateur |

## Périmètre et résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF1-R4 (réduit) | **SOLDÉ le 2026-11-10 par RF3d (ADR 0129)**. Texte d'origine : L'advice n'est étendu ni aux 7 autres référentiels de l'administration générique (créneaux, salles, médecins, caisses, agences, centres payeurs, plus tout nouveau) ni à `categories-transport` | changerait des goldens d'autres contextes ; décision par référentiel |
| RF2c-R1 | Le compte des lignes de facture et des séances n'est pas borné au centre | hors décision ; KB-SEA-91 |

## Preuves
- Rouge vérifié AVANT le code : contrat H2, 2 échecs ; filet RF2, 6 échecs sur 16 tests.
- Goldens `_nouveau_code` approuvés à la main (7 fichiers) ; anciens archivés dans `_ecarts-legacy/**/*_avant_rf2c.legacy.json`.
- Rejeu PostgreSQL 16 (`backend/scripts/patient-couverture-rf2-pg16-rejeu.sh`, port 55793).

## Conséquences
- Un forfait référencé seulement par des PEC d'un autre centre devient supprimable (identifiants UUID propres à un centre : cas improbable). Détection en production : `.ddd/migration/verifications-production.md` § 84.
- Les clients qui testaient un 500 sur ces entrées (aucun connu) reçoivent un 400.
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier avant le 2026-12-15.
- Étendre le 400 aux sept autres référentiels (RF1-R4) ?
