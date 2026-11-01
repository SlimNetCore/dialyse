# Ports & adapters — contexte `organisation` (ADR 0063)

## Ports entrants (driving)

| Port (use case)                                                                                                                                       | Adapter entrant                    | Techno                                                      | Test                                      |
|-------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-------------------------------------------------------------|-------------------------------------------|
| `CreerCentreUseCase`, `ModifierParametresCentreUseCase`, `TransfererCentreUseCase`, `OuvrirSalleUseCase`, `FermerSalleUseCase`, `CreerCreneauUseCase` | `interfaces/http` controleurs REST | Spring webmvc + Security (centre de session, habilitations) | MockMvc + goldens HTTP                    |
| Queries `ListeCentres`, `ListeSalles`, `CapacitesCentres`                                                                                             | `interfaces/http`                  | Spring webmvc                                               | integration Testcontainers                |
| Handler de publication des evenements de domaine                                                                                                      | `interfaces/consumers`             | `@ApplicationModuleListener` (Modulith)                     | application (fake) + integration registre |

## Ports sortants (driven)

| Port (nom metier)                                                               | Couche      | Adapter                                                                        | Techno                           | Test                                               |
|---------------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------|----------------------------------|----------------------------------------------------|
| `SocieteRepository`, `CentreRepository`, `SalleRepository`, `CreneauRepository` | domain      | `infrastructure/persistence/*JpaRepositoryAdapter` + `*Jpa` + mappers          | JPA/Hibernate, version optimiste | contrat fake in-memory <-> JPA Testcontainers PG16 |
| `PublicateurDEvenements` (Outbox)                                               | application | `infrastructure/evenements` -> `ApplicationEventPublisher` (registre Modulith) | Spring Modulith 2.0.x            | integration (`PublishedEvents`)                    |
| `JournalDesMessagesTraites` (idempotence)                                       | application | table `organisation.message_traite`                                            | JPA                              | contrat                                            |
| `UniteDeTravail`, `Horloge`, `GenerateurIdentifiants`, `Habilitations`          | application | `transaction/`, `horloge/`, `identifiants/`, ACL identite                      | spring-tx, JDK, Spring config    | fakes deterministes                                |
| `LectureCentres`, `LectureSalles`, `LectureCapacitesCentres`                    | application | read models SQL                                                                | JPA/SQL natif                    | integration Testcontainers                         |
| ACL GMAO (invalider salle)                                                      | application | `infrastructure/acl/gmao`                                                      | Java (published language)        | contrat                                            |
| ACL Planification (migration placements)                                        | application | `infrastructure/acl/planification`                                             | Java (published language)        | contrat                                            |

## Published language `organisation.v1` (outbox)

| Evenement                                                                                                  | Producteur                                   | Consommateurs                                             | Livraison                                                        |
|------------------------------------------------------------------------------------------------------------|----------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------|
| `CentreCreee`, `CentreTransfere`, `SalleOuverte`, `SalleFermee`, `CreneauCree`, `ParametresCentreModifies` | traducteur domaine -> `publishedlanguage/v1` | Planification, Stock, Seance, Facturation, GMAO, Pilotage | registre Modulith, at-least-once, idempotence eventId (ADR 0013) |

Regles : payloads en types JDK (UUID, String, BigDecimal, Instant) ; nouvelle version `v2` a cote de `v1`.