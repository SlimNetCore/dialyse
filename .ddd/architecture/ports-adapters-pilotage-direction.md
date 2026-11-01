---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/pilotage-direction.md, .ddd/architecture/ports-adapters.md, ADR 0009, ADR 0013, ADR 0066]
---

# Ports & adapters — contexte `pilotage-direction`

## Ports entrants (driving)

| Port (use case)                                                                                                                   | Adapter entrant                    | Techno                                                                      | Test                                      |
|-----------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-----------------------------------------------------------------------------|-------------------------------------------|
| Use cases `CapturerInstantaneMensuel`, `GenererAlerteDirection`, `MarquerAlerteLue`, `ExporterSynthese`, `ConsulterTableauDeBord` | `interfaces/http` contrôleurs REST | Spring webmvc, Spring Security (centre de session, habilitations DIRECTION) | slice MockMvc + caractérisation           |
| Capture mensuelle automatique (fin de mois)                                                                                       | `interfaces/jobs`                  | `@Scheduled(cron, zone)`                                                    | application (fakes) + intégration         |
| Écouteurs des événements amont (`FacturationValidee`, `SeanceValidee`, `AbsenceDetectee`)                                         | `interfaces/consumers`             | `@ApplicationModuleListener` (Modulith)                                     | application (fake) + intégration registre |

## Ports sortants (driven)

| Port (nom métier)                                                                                                                                 | Couche      | Adapter                                                                       | Techno                                              | Test                                                           |
|---------------------------------------------------------------------------------------------------------------------------------------------------|-------------|-------------------------------------------------------------------------------|-----------------------------------------------------|----------------------------------------------------------------|
| `InstantaneRepository`, `AlerteRepository`, `ExportRepository`                                                                                    | domain      | `infrastructure/persistence/*JpaRepositoryAdapter` + entités `*Jpa` + mappers | JPA/Hibernate, schéma `pilotage`, version optimiste | contrat (fake in-memory ↔ adapter JPA sur Testcontainers PG16) |
| `TableauDeBordRepository` (read model)                                                                                                            | application | read model SQL                                                                | JPA/SQL natif                                       | intégration Testcontainers                                     |
| `FacturationPublishedLanguage` (verrou mois clôture)                                                                                              | application | `infrastructure/acl/legacyfacturation` (ou `facturation.publishedlanguage`)   | Java                                                | traduction table-driven                                        |
| Langages publiés des contextes amont (Seance, Absences patients, Stock, GMAO, Planification, Patient & couverture, Dossier médical, Organisation) | application | `infrastructure/acl/<contexte>`                                               | Java                                                | contrat                                                        |
| `PublicateurDEvenements`                                                                                                                          | application | `infrastructure/evenements` → `ApplicationEventPublisher` (registre Modulith) | Spring Modulith 2.0.x                               | intégration (`PublishedEvents`)                                |
| `JournalDesMessagesTraites` (idempotence)                                                                                                         | application | table `pilotage.message_traite`                                               | JPA                                                 | contrat                                                        |
| `UniteDeTravail` (transaction)                                                                                                                    | application | gestion transactionnelle Spring                                               | spring-tx                                           | intégration                                                    |
| `Horloge`, `GenerateurIdentifiants`                                                                                                               | application | horloge système / UUID                                                        | JDK                                                 | fake déterministe                                              |
| `ModeleDocumentPrinter` (export PDF/HTML)                                                                                                         | application | service générique d'impression                                                | Java                                                | intégration                                                    |

## Published language `pilotagedirection.v1` (outbox)

| Événement                                                                    | Producteur                                                     | Consommateurs           | Livraison                                                          |
|------------------------------------------------------------------------------|----------------------------------------------------------------|-------------------------|--------------------------------------------------------------------|
| `InstantaneMensuelCapture`, `AlerteDirectionGeneree`, `ExportSyntheseGenere` | traducteur domaine → `publishedlanguage/v1` dans l'application | Reporting, UI direction | registre Modulith, at-least-once, idempotence `eventId` (ADR 0013) |

Règles : payloads en types JDK (UUID, String, BigDecimal, Instant, Mois) — pas de VO internes ; nouvelle version `v2` à
côté de `v1`.

## Notes

- Refactoring sur place : mêmes tables et mêmes URLs que le legacy (`platform/application/direction`), un seul
  propriétaire, pas de façade.
- Conformiste en lecture : aucune écriture dans les autres contextes (R4). Le contexte consomme leurs événements
  publiés et expose ses propres indicateurs.