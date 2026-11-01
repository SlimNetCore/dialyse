---
slice: dossier-medical-dm4-serologie
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/dossier-medical-dm4-serologie.md, .ddd/tactical/dossier-medical.md, ADR 0065]
---

# Filet de caractérisation — DM4 Sérologie (détection positive)

## Point d'observation

Tests unitaires HTTP sur `SerologieRestController` (dépendances mockées, pas de Spring Context complet) + test unitaire
`SerologieDomainService` existant (logique domaine, statuts, transitions). Isolation multi-centre par `center_id` sur
toutes les lectures/écritures. Aucun mock du code caractérisé.

Tests :

- `backend/platform/src/test/java/.../infrastructure/web/rest/SerologieRestControllerTest` (REST, multi-centre,
  pagination) ;
- `backend/bc-medical/src/test/java/.../domain/medical/serologie/service/SerologieDomainServiceTest` (logique domaine,
  invariants) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SerologiePlacementHookTest` (hook Planification, isolement
  machine).

## Scénarios verrouillés (multi-centre)

- Création d'une sérologie (`POST`) rattachée à un `center_id` et à un patient valide ; le `centerId` provient du corps
  de
  requête et est confronté au centre de session par `CenterAccessGuard` (refus `AccessDeniedException` si mismatch).
- Résultat `POSITIF` sans `conduiteATenir` rejeté par l'agrégat (`SEROLOGIE_CONDUITE_REQUISE`) — invariant de sécurité
  pour l'isolement machine en centre d'hémodialyse.
- Doublon même marqueur + même date de prélèvement rejeté (`existsByPatientIdAndMarqueurAndDatePrelevement`).
- Bandeau synthèse : dernier résultat connu par marqueur (`listDerniersResultatsByPatient`).
- Transitions : `corrigerResultat` émet un `SerologiePositiveDetectee` quand le résultat devient positif.
- Isolation inter-centres : une sérologie du centre A n'apparaît pas pour le centre B ; `centerId` systématiquement
  propagé dans `SerologieUseCase`/`SerologieRepositoryPort`.
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}` ; `size` bornée (MAX 200), `page` plancher à 0.
- Fallback : si `centerId` n'est pas fourni, le centre de session est utilisé ; sinon refus inter-centres.
- Suppression (`DELETE`) filtrée par `center_id` via `deleteByIdAndPatientIdAndCenterId`.

## Stabilité

Suite exécutée sans Spring Context (contrôleurs avec fakes) + JUnit 5 sur le domaine pur. Isolation centre vérifiée par
les tests REST et domaine. Aucun mock du code caractérisé.

## Points d'attention

- Statuts : `POSITIF`, `NEGATIF`, `DOUTEUX`, `EN_COURS` ; un résultat positif impose une conduite à tenir documentée.
- Marqueurs : `VIH_AC`, `AG_HBS`, `AC_HBS`, `AC_HBC`, `AC_VHC`, `ARN_VHC`, `TPHA`, `CMV_IGG`, `CMV_IGM`, `TOXO_IGG`,
  `TOXO_IGM`, `EBV_IGG`, `EBV_IGM`.
- Événement publié : `SerologiePositiveDetectee` (consommé par Planification — isolement machine, hors périmètre DM4).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-DM-0xx (voir `docs/reference/regles-de-gestion/`).
- Couverture `centerId` obligatoire sur toute lecture/écriture (règle multi-centre §2).
