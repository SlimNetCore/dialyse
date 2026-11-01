---
slice: seance-se1-creation
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se1-creation.md, .ddd/tactical/seance.md, ADR 0002, 0006, 0062]
---

# Filet de caractérisation — SE1 Création de séance (conditionnée par PEC valide)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances`. Aucun mock du code caractérisé. Les lectures/écritures
passent par `SeanceRestController`, `SeanceApplicationService` et `SeanceUseCase`, avec isolation multi-centre par
`center_id`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest` (contrôleur REST, `create`,
  `createFromQr`, rôles, multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceScanIntegrationTest` (scan QR, création/validation,
  multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceDetailsIntegrationTest` (détail de séance,
  multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceListIntegrationTest` (liste paginée, multi-centre) ;
- `backend/bc-seance/src/test/java/.../domain/seance/service/SeanceDomainServiceTest` (logique domaine).

## Scénarios verrouillés (multi-centre)

- Création d'une séance (`POST /api/v1/seances`) rattachée à un `center_id` et à un patient valide ; statut initial
  `CREE`.
- Création conditionnée par une PEC valide (port `PECRepository` Patient & couverture) ; refus sans PEC valide.
- Scan QR : création ou retour de la séance du jour ; statut `CREE` pour secrétaire, validation directe pour
  infirmier/admin.
- Isolation inter-centres : une séance du centre A n'apparaît pas pour le centre B ; `centerId` systématiquement propagé
  dans `SeanceUseCase`/`SeanceRepositoryPort`.
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}` ; filtrage par dates/statuts.
- Cache (le cas échéant) invalidé par `center_id` ; pas de fuite inter-centres.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceRestControllerTest` et
`SeanceScanIntegrationTest`. Aucun mock du code caractérisé.

## Points d'attention

- Statut `ABSENT` supprimé (ADR 0002) : plus de création de séance au statut `ABSENT`.
- Le cycle `CREE -> VALIDEE -> SIGNEE` ; la création ne valide pas.
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-SEA-0xx (voir `docs/reference/regles-de-gestion/`).