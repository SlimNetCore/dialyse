---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/dossier-medical.md, .ddd/architecture/ports-adapters.md, ADR 0007, ADR 0065]
---

# Ports & adapters — contexte `dossier-medical`

## Ports entrants (driving)

| Port (use case)                                                                                                                       | Adapter entrant                                       | Techno                                                                                 | Test                             |
|---------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|----------------------------------------------------------------------------------------|----------------------------------|
| Use cases Constante / Prescription / DemandeExamen / Serologie / Antecedent / Allergie / ObservationBiologique / BilanPreGreffe       | `interfaces/http` contrôleurs REST                    | Spring webmvc, Spring Security (centre de session, habilitations « données de santé ») | slice MockMvc + caractérisation  |
| Événements cliniques publiés (constant enregistrée, prescription signée, résultat reçu, sérologie positive, bilan pré-greffe complet) | `interfaces/consumers` (si consommés par le contexte) | `@ApplicationModuleListener` (Modulith)                                                | application (fake) + intégration |

## Ports sortants (driven)

| Port (nom métier)                                                                                                                                                                                              | Couche             | Adapter                                                                       | Techno                                             | Test                                                           |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|-------------------------------------------------------------------------------|----------------------------------------------------|----------------------------------------------------------------|
| `ConstanteRepository`, `PrescriptionRepository`, `DemandeExamenRepository`, `SerologieRepository`, `AntecedentRepository`, `AllergieRepository`, `ObservationBiologiqueRepository`, `BilanPreGreffeRepository` | domain             | `infrastructure/persistence/*JpaRepositoryAdapter` + entités `*Jpa` + mappers | JPA/Hibernate, schéma `medical`, version optimiste | contrat (fake in-memory ↔ adapter JPA sur Testcontainers PG16) |
| `RisquesInfectieux`, `AbordsVasculaires` (ports publiés)                                                                                                                                                       | domain/application | implémentés par les services de domaine                                       | Java pur                                           | contrat                                                        |
| `PatientsPublies`, `SeancesPubliees`                                                                                                                                                                           | application        | `infrastructure/acl/legacypatient`, `infrastructure/acl/legacyseance`         | JDBC                                               | traduction table-driven                                        |
| `OrganisationsPubliees` (lecture centres)                                                                                                                                                                      | application        | `infrastructure/acl/legacyorganisation`                                       | JDBC                                               | traduction table-driven                                        |
| `PublicateurDEvenements`                                                                                                                                                                                       | application        | `infrastructure/evenements` → `ApplicationEventPublisher` (registre Modulith) | Spring Modulith 2.0.x                              | intégration (`PublishedEvents`)                                |
| `JournalDesMessagesTraites` (idempotence)                                                                                                                                                                      | application        | table `medical.message_traite`                                                | JPA                                                | contrat                                                        |
| `UniteDeTravail` (transaction)                                                                                                                                                                                 | application        | gestion transactionnelle Spring                                               | spring-tx                                          | intégration                                                    |
| `Horloge`, `GenerateurIdentifiants`                                                                                                                                                                            | application        | horloge système / UUID                                                        | JDK                                                | fake déterministe                                              |

## Published language `dossiermedical.v1` (outbox)

| Événement                                                                                                                                                              | Producteur                                                     | Consommateurs                                | Livraison                                                          |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|----------------------------------------------|--------------------------------------------------------------------|
| `ConstanteEnregistree`, `PrescriptionSignee`, `PrescriptionAnnulee`, `DemandeExamenEmise`, `ResultatExamenRecu`, `SerologiePositiveDetectee`, `BilanPreGreffeComplete` | traducteur domaine → `publishedlanguage/v1` dans l'application | Seance, Facturation, Planification, Pilotage | registre Modulith, at-least-once, idempotence `eventId` (ADR 0013) |

Règles : payloads en types JDK (UUID, String, BigDecimal, Instant) — pas de VO internes ; nouvelle version `v2` à côté
de `v1`.

## Notes

- Refactoring sur place : mêmes tables et mêmes URLs que le legacy (`bc-medical`), un seul propriétaire, pas de façade.
- Les agrégats et use cases sont à implémenter lors des slices dédiés ; le squelette ne contient aucun code métier.