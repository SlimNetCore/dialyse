---
slice: seance-se4-derogation-regularisation
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se4-derogation-regularisation.md, .ddd/tactical/seance.md, ADR 0002, 0006, 0062]
---

# Filet de caractérisation — SE4 Dérégation de régularisation

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances`. Aucun mock du code caractérisé. Le déverrouillage
pour régularisation passe par `SeanceRestController` (`/api/v1/seances/{id}/deverrouiller-regularisation`),
`SeanceApplicationService` et `SeanceDomainService`/`Seance.deverrouillerPourRegularisation`, avec isolation
multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/bc-seance/src/test/java/.../domain/seance/model/SeanceDeverrouillageTest` (déverrouillage pour
  régularisation, idempotence, refus < 7j, non `CREE`, sans date) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceDeverrouillageIntegrationTest` (flux admin/nurse,
  rôles, isolation des listes `deverrouillee`) ;
- `backend/platform/src/test/java/.../application/seance/SeanceRegularisationServiceIntegrationTest` (rappel des séances
  à régulariser).

## Scénarios verrouillés (multi-centre)

- Une séance passée restée `CREE` ne peut être validée par un infirmier tant qu'elle n'est pas déverrouillée
  par un administrateur (422) ; après déverrouillage, validation possible.
- Seul un `ADMIN` peut déverrouiller (403 pour `INFIRMIER`, `SECRETAIRE`, `MEDECIN`).
- Une séance du jour ou à venir ne peut pas être déverrouillée (422) ; l'infirmier la valide librement.
- Un administrateur peut valider une séance oubliée sans déverrouillage préalable.
- Le déverrouillage est idempotent : le premier auteur et la première date sont conservés.
- Refus de déverrouillage pour une séance déjà validée/signée ou verrouillée pour facturation.
- Une séance sans date ne peut pas être déverrouillée.
- La liste infirmier ne montre que les séances déverrouillées ; la liste administrateur les montre toutes (filtre
  `deverrouillee`).
- Isolation inter-centres : une séance du centre A n'est ni déverrouillable ni validable depuis le centre B ;
  `centerId` systématiquement propagé dans `SeanceUseCase`/`SeanceRepositoryPort`.
- Éviction synchrone des caches `ref.seances.du.jour` / `ref.seances.validees.non.signees` sur déverrouillage,
  clé incluant le `centerId`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceDeverrouillageIntegrationTest`.
Aucun mock du code caractérisé. Exécution répétée sans flakiness détectée.

## Points d'attention

- Le déverrouillage ne change pas le statut de la séance ; il autorise seulement la validation ultérieure.
- L'agrégat satellite `DerogationRegularisation` n'est pas encore matérialisé : l'état vit sur `Seance`
  (`regularisationDeverrouilleeAt`, `regularisationDeverrouilleeBy`), à refactorer en SE4.
- Règle des 7 derniers jours (RG-SEA-046) portée par `SeanceDomainService`/`RegleRegularisation`.
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-SEA-002, RG-SEA-046 (voir `docs/reference/regles-de-gestion/07-seances.md`).
