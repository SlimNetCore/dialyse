---
slice: seance-se6-verrou-facturation
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se6-verrou-facturation.md, .ddd/tactical/seance.md, ADR 0002, 0062]
---

# Filet de caractérisation — SE6 Verrou facturation (réception FacturationValidée)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances` (colonne `statut = 'FACTUREE'`). Aucun mock du code
caractérisé. Le verrou est posé lors de la validation d'une facturation : les séances de la période passent à
`FACTUREE` et deviennent immuables ; l'isolation multi-centre est assurée par `center_id`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest` (refus 422
  `SEANCE_FACTUREE_NON_MODIFIABLE` sur modification d'une séance `FACTUREE`) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceDetailsIntegrationTest` (détail d'une séance
  facturée, multi-centre) ;
- `backend/bc-seance/src/test/java/.../domain/seance/service/SeanceDomainServiceTest`
  (`should_reject_modification_when_verrouillee_facturation`, etc.) ;
- `backend/bc-seance/src/test/java/.../domain/seance/service/VoletMedicalDomainServiceTest` et
  `VoletParamedicalDomainServiceTest` (refus sur séance `FACTUREE`) ;
- `backend/bc-seance/src/test/java/.../domain/seance/model/SuppressionSeanceTest` (`SEANCE_FACTUREE_NON_SUPPRIMABLE`) ;
-
`backend/platform/src/test/java/.../characterization/facturation/validation/EvenementEtComptabiliteCharacterizationIntegrationTest`
(validation de facturation → séances `FACTUREE` + événement `FacturationValidée`) ;
- `backend/contexts/facturation/...` tests de validation/émission (période, idempotence, multi-centre).

## Scénarios verrouillés (multi-centre)

- Réception de `FacturationValidée` pour une période/centre : les séances concernées passent au statut `FACTUREE`
  rattachées à leur facture.
- Une séance `FACTUREE` est immuable : toute modification (date, forfait, validation, signature, consommables, volets
  médical/paramédical) est refusée par 422 `SEANCE_FACTUREE_NON_MODIFIABLE`.
- La suppression d'une séance `FACTUREE` est refusée par 422 `SEANCE_FACTUREE_NON_SUPPRIMABLE`.
- Idempotence : la revalidation d'une même période ne repose pas le verrou une seconde fois de manière observable
  (journal `JournalDesMessagesTraites`).
- Isolation inter-centres : une facturation validée dans le centre A ne verrouille que les séances du centre A ; le
  `centerId` est systématiquement propagé dans l'écouteur et le repository.
- Cache : éviction synchrone des caches séance sur verrouillage, clé incluant le `centerId`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceRestControllerTest` et
`SeanceDetailsIntegrationTest`. Aucun mock du code caractérisé. Exécution répétée sans flakiness détectée.

## Points d'attention

- Le statut `FACTUREE` est un verrou interne posé sur réception de `FacturationValidée` (ADR 0002) ; il n'est pas
  positionnable manuellement depuis l'écran séance.
- La suppression garde son code dédié `SEANCE_FACTUREE_NON_SUPPRIMABLE` (distinct de la modification).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-SEA-002, RG-SEA-046, RG-FAC-010 (voir
  `docs/reference/regles-de-gestion/07-seances.md` et `12-facturation-reglements-comptabilite.md`).