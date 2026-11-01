# Layout contexte `organisation` (ADR 0063)

- Module Maven `contexts-organisation`, package racine `com.hemodialyse.backend.contexts.organisation`.
- Couches : `domain/` (Societe, Centre, Salle, Creneau, VO, events, ports), `application/` (use cases, ports sortants),
  `infrastructure/` (persistence JPA/JDBC, acl legacy, evenements Modulith), `interfaces/` (http, consumers),
  `publishedlanguage/` (organisation.v1), `configuration/` (OrganisationConfiguration, toujours active).
- Refactoring sur place, memes tables (centres, salles, creneaux, positions de creneau, medecins, machines, protocoles)
  et memes URLs `/api/v1/organisation/**` (a confirmer a la slice 1).
- Dependances modules : `contexts-shared-kernel` uniquement (CentreId, SocieteId). Les contextes aval consomment
  `organisation.v1` par ACL.
- Pas de Timefold ; Modulith events-api pour la publication des evenements de domaine.
- Caches `ref.centres.actifs`, `ref.salles.du.centre`, `ref.parametres.centre` (TTL PT3M/PT15M, cle centerId/societeId).