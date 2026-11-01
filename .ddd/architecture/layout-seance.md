# Layout contexte `seance` (ADR 0062)

- Module Maven `contexts-seance`, package racine `com.hemodialyse.backend.contexts.seance`.
- Couches : `domain/` (Seance, ConsommableDeclare, DerogationRegularisation, VO, events, ports), `application/` (use
  cases, ports sortants), `infrastructure/` (persistence JPA, acl Stock/Planification/Patient, evenements Modulith),
  `interfaces/` (http, consumers), `publishedlanguage/` (seance.v1), `configuration/` (SeanceConfiguration, toujours
  active).
- Refactoring sur place, memes tables (`seances`, `seance_consommables`, `derogation_regularisation`) et memes URLs
  `/api/v1/seances/**`.
- Dependances modules : `contexts-shared-kernel`, `contexts-stock`, `contexts-planification` (published language
  uniquement).
- Pas de Timefold ; Modulith events-api pour l'ecouteur `FacturationValidee` (verrou FACTUREE).
- Caches `ref.seances.du.jour`, `ref.seances.validees.non.signees` (TTL PT3M, cle centerId).