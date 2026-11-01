---
slice: seance-se2-validation-signature
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se2-validation-signature.md, .ddd/tactical/seance.md, ADR 0002, 0006, 0062]
---

# Filet de caractérisation — SE2 Validation et signature

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances`. Aucun mock du code caractérisé. Les transitions
`CREE -> VALIDEE -> SIGNEE` passent par `SeanceRestController`, `SeanceApplicationService` et
`SeanceDomainService` (`validerParInfirmier`, `signerParMedecin`, `deverrouillerPourRegularisation`),
avec isolation multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest` (validation, signature, rôles,
  multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceScanIntegrationTest` (scan QR : validation directe,
  multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceDetailsIntegrationTest` (détail de séance
  validée/signée, multi-centre) ;
- `backend/bc-seance/src/test/java/.../domain/seance/service/SeanceDomainServiceTest` (logique domaine
  validation/signature) ;
- `backend/bc-seance/src/test/java/.../domain/seance/model/SeanceDeverrouillageTest` (déverrouillage pour
  régularisation).

## Scénarios verrouillés (multi-centre)

- Validation d'une séance au statut `CREE` par un infirmier : passage à `VALIDEE`, `validatedAt` et
  `signedByInfirmierAt` renseignés, place mémorisée.
- Validation idempotente : une séance déjà `VALIDEE` ou `SIGNEE` ne change pas d'état (retour anticipé).
- Refus de validation pour une séance verrouillée pour facturation (`SeanceFactureeNonModifiableException`).
- Refus de validation hors statut `CREE` (autre que `FACTUREE`) : `IllegalStateException`.
- Signature médicale d'une séance `VALIDEE` par un médecin : passage à `SIGNEE`, `signedByMedecinAt` renseigné.
- Refus de signature si la séance n'est pas `VALIDEE` (pas encore validée) ou déjà signée.
- Refus de signature pour une séance verrouillée pour facturation.
- Cycle sans retour : `CREE -> VALIDEE -> SIGNEE` ; aucune transition inverse.
- Déverrouillage pour régularisation d'une séance d'un jour passé restée `CREE` par un admin : idempotent ;
  refusé pour une séance du jour/à venir, non `CREE`, ou verrouillée pour facturation.
- Isolation inter-centres : une séance du centre A n'est ni validable ni signable depuis le centre B ;
  `centerId` systématiquement propagé dans `SeanceUseCase`/`SeanceRepositoryPort`.
- Éviction synchrone du cache `ref.seances.du.jour` et `ref.seances.validees.non.signees` sur validation/signature,
  clé incluant le `centerId`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceRestControllerTest` et
`SeanceScanIntegrationTest`. Aucun mock du code caractérisé. Exécution répétée 3 fois sans flakiness détectée.

## Points d'attention

- `validerParInfirmier` est idempotent pour `VALIDEE`/`SIGNEE` (retour anticipé sans erreur) — comportement legacy
  conservé.
- Validation le jour même sinon déverrouillage admin (7j, RG-SEA-046) ; le déverrouillage ne change pas le statut,
  il autorise la validation ultérieure.
- Une séance verrouillée pour facturation est immuable (RG-SEA-002) : toute validation/signature/modification est
  refusée.
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-SEA-001, RG-SEA-002, RG-SEA-046 (voir `docs/reference/regles-de-gestion/`).