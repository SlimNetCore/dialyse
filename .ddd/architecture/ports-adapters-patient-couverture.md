---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/patient-couverture.md §7, .ddd/architecture/ports-adapters.md, ADR 0004, ADR 0064]
---

# Ports & adapters — contexte `patient-couverture`

## Ports entrants (driving)

| Port (use case)                                                                                                                                                                                                | Adapter entrant                    | Techno                                                            | Test                            |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|-------------------------------------------------------------------|---------------------------------|
| Use cases Patient/Assure/PEC/Attestation (`AdmettrePatient`, `TransfererPatient`, `DeclarerSortiePatient`, `DeclarerGreffe`, `AffecterAssurePrincipal`, `ValiderPEC`, `CloturerPEC`, `EnregistrerAttestation`) | `interfaces/http` controleurs REST | Spring webmvc, Spring Security (centre de session, habilitations) | slice MockMvc + caracterisation |
| Queries `DetailPatient`, `DroitsPatient`, `ListePatients` (pagination)                                                                                                                                         | `interfaces/http`                  | idem                                                              | integration                     |
| Ecouteur `organisation.v1` (lecture centres)                                                                                                                                                                   | `interfaces/consumers`             | `@ApplicationModuleListener` (Modulith)                           | integration                     |

## Ports sortants (driven)

| Port (nom metier)                                                                 | Couche      | Adapter                                                                        | Techno                                                        | Test                                                            |
|-----------------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------|
| `PatientRepository`, `AssureRepository`, `PECRepository`, `AttestationRepository` | domain      | `infrastructure/persistence/*JpaRepositoryAdapter` + entites `*Jpa` + mappers  | JPA/Hibernate, schema `patient_couverture`, version optimiste | contrat (fake in-memory -> adapter JPA sur Testcontainers PG16) |
| `OrganisationsPublishedLanguage` (lecture centre cible)                           | application | `infrastructure/acl/legacyorganisation`                                        | Java (ACL)                                                    | traduction table-driven                                         |
| `PublicateurDEvenements`                                                          | application | `infrastructure/evenements` -> `ApplicationEventPublisher` (registre Modulith) | Spring Modulith 2.0.x                                         | integration (`PublishedEvents`)                                 |
| `JournalDesMessagesTraites` (idempotence)                                         | application | table `patient_couverture.message_traite`                                      | JPA                                                           | contrat                                                         |
| `UniteDeTravail` (transaction)                                                    | application | gestion transactionnelle Spring                                                | spring-tx                                                     | integration                                                     |
| `Horloge`, `GenerateurIdentifiants`                                               | application | horloge systeme / UUID                                                         | JDK                                                           | fake deterministe                                               |
| `Habilitations` (Identite & acces)                                                | application | lecture du contexte de securite                                                | Spring Security                                               | slice securite                                                  |

## Published language `patientcouverture.v1` (outbox)

| Evenement                                                            | Producteur  | Consommateurs                                                                                           | Livraison                                               |
|----------------------------------------------------------------------|-------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `PatientAdmis`, `PatientTransfere`, `PatientSorti`, `GreffeDeclaree` | Patient     | Planification, Seance, Absences patients, Facturation                                                   | registre Modulith, at-least-once, idempotence `eventId` |
| `AssurePrincipalAffecte`                                             | Assure      | Facturation (payeur), Simulation                                                                        | idem                                                    |
| `PECCreee`, `PECValidee`, `PECCloturee`                              | PEC         | Seance (creation conditionnee), Facturation (priorite/fin de validite), Absences patients (reinclusion) | idem                                                    |
| `AttestationEnregistree`                                             | Attestation | Patient & couverture (lecture uniquement)                                                               | idem                                                    |

Regles : payloads en types JDK (UUID, String, BigDecimal, Instant) — pas de VO internes ; nouvelle version `v2` a cote
de `v1`, jamais de modification d'un contrat publie.

## Port publie expose aux autres contextes

- `PECRepository` (lecture PEC active par patient) : port publie consomme par Seance (creation conditionnee par PEC
  valide). |
  <|end|>assistant<|channel|>analysis<|message|>Now git add files.<|end|><|start|>-start to=shell<|channel|>analysis <
  |constrain|>xml<|message|><rfl:parameter name="command">
  cd "C:\Users\TS-CONSULT\WebstormProjects\Hemodialyse" ; git add .ddd/architecture/layout-patient-couverture.md
  .ddd/architecture/ports-adapters-patient-couverture.md
  .ddd/architecture/adr/0064-patient-couverture-contexte-unique-module-contexts-patient-couverture.md
  .ddd/arch-rules.json .ddd/state.json backend/pom.xml
  backend/platform/src/test/java/com/hemodialyse/backend/architecture/FrontieresDesContextesTest.java
  backend/contexts/patient-couverture ; git status --short | Select-Object -First 30