---
slice: seance-se5-forfait-surcharge
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se5-forfait-surcharge.md, .ddd/tactical/seance.md, ADR 0002, 0062]
---

# Filet de caractérisation — SE5 Forfait surchargé

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances` (colonnes `forfait_override_*`), plus état du
référentiel `forfait`. Aucun mock du code caractérisé. Le surchargement d'un forfait de séance passe
par `SeanceRestController` (`/api/v1/seances/{id}/forfait`), `SeanceApplicationService` et
`SeanceDomainService.updateForfait`, avec isolation multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/bc-seance/src/test/java/.../domain/seance/service/SeanceDomainServiceTest`
  (`updateForfait_should_store_override_snapshot_when_seance_not_facturee`,
  `updateForfait_should_fail_when_seance_is_facturee`) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest`
  (`updateForfait_should_return_updated_forfait_payload`) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceDetailsIntegrationTest`
  (`updateForfait_should_persist_override_and_return_it_in_details`,
  `updateForfait_should_be_refused_to_a_nurse_and_leave_the_forfait_unchanged`) ;
- `backend/contexts/facturation/src/test/java/.../contrat/SeancesPostgresContractIT`
  (auteur et instant de la surcharge persistés) ;
- `backend/contexts/facturation/.../domain/simulation/PolitiqueDeForfaitAppliqueTest`
  (priorité surcharge > PEC effective > PEC demandée, RG-FAC-021).

## Scénarios verrouillés (multi-centre)

- Surcharger le forfait d'une séance non facturée capture un snapshot immuable (id, code, libellé,
  prix, auteur, instant) sur la séance (`forfait_override_*`).
- Refus de surcharge sur une séance `FACTUREE` (`IllegalStateException`) ; le forfait reste inchangé.
- Seul un `ADMIN` (ou médecin selon rôle) peut surcharger ; un `INFIRMIER` reçoit 403 et le forfait
  n'est pas modifié.
- Le forfait surchargé est prioritaire sur le forfait de la PEC (effective puis demandée) dans le
  calcul du forfait appliqué (RG-FAC-021) ; sans surcharge ni PEC, « Forfait non defini » à 0.
- Un forfait inconnu ou d'un autre centre ne peut pas être posé comme surcharge (isolation centre).
- La surcharge est idempotente en lecture : le premier auteur et le premier instant sont conservés
  lors d'une nouvelle pose (selon règle de mise à jour en cours).
- Isolation inter-centres : une séance du centre A n'est ni surchargeable ni consultable depuis le
  centre B ; `centerId` systématiquement propagé dans `SeanceUseCase`/`SeanceRepositoryPort`.
- Éviction synchrone des caches liés à la séance sur surcharge, clé incluant le `centerId`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceDomainServiceTest`,
`SeanceRestControllerTest` et `SeanceDetailsIntegrationTest`. Aucun mock du code caractérisé.
Exécution répétée sans flakiness détectée.

## Points d'attention

- La surcharge ne change pas le statut de la séance ; elle impose le forfait utilisé pour la
  facturation (RG-FAC-021).
- Le VO `ForfaitApplique` et l'événement `ForfaitDeSeanceSurchargé` ne sont pas encore matérialisés
  : l'état vit sur `Seance` (`forfait_override_*`), à refactorer en SE5.
  - Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
  - Les règles de gestion associées : RG-SEA-033, RG-FAC-021 (voir
  `docs/reference/regles-de-gestion/07-seances.md` et `12-facturation-reglements-comptabilite.md`).