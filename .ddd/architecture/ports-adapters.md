---
status: proposed
date: 2026-10-09
sources: [.ddd/tactical/gmao.md §8, .ddd/architecture/layout.md, ADR 0012/0013/0014]
---
# Ports & adapters — par contexte

## GMAO (`backend/contexts/gmao`) — seul contexte modélisé

### Ports entrants (driving)
| Port (use case) | Adapter entrant | Techno | Test |
|---|---|---|---|
| Use cases Équipement / Intervention / Intervenant / Plan / Pièce jointe (`tactical/gmao.md` §8) | `interfaces/http` contrôleurs REST | Spring webmvc, Spring Security (centre de session, habilitations) | slice MockMvc (`@AutoConfigureMockMvc`) + suite de caractérisation (ApprovalTests) |
| Requêtes FicheÉquipement, Indicateurs, Chronologie, Retards, Tableau de bord | `interfaces/http` | idem | intégration Testcontainers + parité |
| Politique P1 `AppliquerLEtatConstate` | `interfaces/consumers` | `@ApplicationModuleListener` (Modulith) | application (fake) + intégration registre |
| Politique P2 `PropositionDeReforme` | `interfaces/consumers` | idem | application (fake `Notifications`) |

### Ports sortants (driven)
| Port (nom métier) | Couche | Adapter | Techno | Test |
|---|---|---|---|---|
| `EquipementRepository`, `InterventionRepository`, `IntervenantRepository`, `PlanMaintenanceRepository`, `PieceJointeRepository` | domain | `infrastructure/persistence/*JpaRepositoryAdapter` + entités `*Jpa` + mappers | JPA/Hibernate, schéma `gmao`, version optimiste | **contrat** (même suite : fake in-memory ↔ adapter JPA sur Testcontainers PG16) |
| `CodesEquipementDuCentre` (R7) | domain | requête sur `gmao.equipement` + contrainte unique | JPA/SQL | contrat + test de contrainte |
| Journal des statuts (R8, append-only) | — (écrit par l'adapter `EquipementRepository` depuis les événements, même transaction) | `infrastructure/persistence` | JPA | intégration |
| `LectureHistoriqueStatuts`, `LectureCouts`, `LectureInterventions` | application | read models SQL | JPA/SQL natif | intégration Testcontainers |
| `PublicateurDEvenements` | application | `infrastructure/evenements` → `ApplicationEventPublisher` (registre Modulith) | Spring Modulith 2.0.x | intégration (`PublishedEvents`) |
| `JournalDesMessagesTraites` (idempotence P1, consommateurs) | application | table `gmao.message_traite` | JPA | contrat |
| `UniteDeTravail` (transaction) | application | gestion transactionnelle Spring | spring-tx | intégration |
| `Horloge`, `GenerateurIdentifiants` | application | horloge système / UUID | JDK | fake déterministe |
| `Habilitations` (Identité & accès) | application | lecture du contexte de sécurité | Spring Security | slice sécurité |
| `Notifications` (générique) | application | `infrastructure/acl/legacynotifications` → service de notifications legacy | ACL | traduction table-driven |
| `ParametresGmao` (`SeuilDeReforme`) | application | propriété `hemodialyse.gmao.reforme-seuil-ratio` | Spring config | unitaire |
| `StockageDesPiecesJointes` | application | stockage binaire (même stockage que legacy, à confirmer) | JPA/BYTEA ou fichiers | contrat |
| Alerte Planification (cible) | platform | listener d'événements GMAO → `GenerateurIndisponibleService.signaler` (voir « Réalisé ») | Modulith listener | test d'intégration |

### Réalisé en refactoring sur place (G1 plans, G2 intervenants, G3 équipements ; ADR 0024)
Mêmes tables et mêmes URLs que le legacy (`gmao_plans_maintenance`, `gmao_intervenants`, `gmao_equipements`, historique des statuts), un seul propriétaire, pas de façade, routage, projection ni rattrapage.
| Port (couche) | Adapter | Notes |
|---|---|---|
| `PlanMaintenanceRepository`, `IntervenantRepository`, `EquipementRepository` (domain) | `infrastructure/persistence/*RepositoryJpa` (+ `*Jpa`, `*SpringData`, `*Mapper`) | mêmes colonnes que le legacy ; pas de colonne de version ; contrat partagé avec les fakes `doubles/*EnMemoire` |
| `LecturePlans`, `LectureIntervenants`, `LectureEquipements` (application, read models) | `Lecture*Jpa` | listes exclues de la suppression logique, `parId` non (KB-GMAO-17) |
| `PublicateurDEvenements` | `infrastructure/evenements/PublicateurSpring` | registre Modulith JDBC (migration V35) ; consommateur minimal `JournalDesEvenements` |
| `UniteDeTravail`, `Horloge`, `GenerateurIdentifiants` | `UniteDeTravailSpring`, `HorlogeSysteme`, `IdentifiantsAleatoires` | |
| `SessionCourante` (application) | `infrastructure/acl/legacyidentite/SessionLegacy` | lit `getCenterId()` / `getId()` du principal par réflexion (aucune dépendance Maven vers `platform`) |
| `EquipementExiste` (application) | `EquipementRepository` (G3 propriétaire de la table) | KB-34 : équipement supprimé logiquement trouvé |
| `CapaciteDesSalles` (application/equipement) | `platform/infrastructure/acl/gmao/CapaciteDesSallesLegacy` | capacité du planning des salles |
| `EtatEquipementPort` (publishedlanguage) | implémenté par `ConstaterEtat` | utilisé par G4 (clôture d'intervention) |
| `GenerateursDuCentre` (publishedlanguage, G6 temps 2) | `GenerateursDuCentreJpa` (lecture de `gmao_equipements`) | utilisé par la reprise des patients via l'ACL `GenerateursDeRepriseGmao` (port `GenerateursDeReprise` de `bc-migration`) ; contrat dans `PilotageContrat` |
| Écouteur d'événements | `platform/infrastructure/acl/gmao/GmaoEvenementsPlanificationListener` | alerte Planification (`GenerateurIndisponibleService.signaler`) ; l'éviction du cache `ref.generateurs` est un `@CacheEvict` synchrone dans `EquipementControleur` |
| Adapters entrants HTTP | `interfaces/http/{PlanMaintenance,Intervenant,Equipement}Controleur` | `@RestController` ordinaires, mêmes URLs et mêmes `@PreAuthorize` (sauf KB-32) |

### Published language `gmao.v1` (outbox)
| Événement | Producteur | Consommateurs | Livraison |
|---|---|---|---|
| `EquipementMisHorsService`, `EquipementRemisEnService`, `EquipementReforme`, `EquipementEnregistre`, `InterventionTerminee` | traducteur domaine → `publishedlanguage/v1` dans l'application | ACL Planification (legacy), Pilotage, Notifications | registre Modulith, at-least-once, idempotence `eventId` (ADR 0013) |

Décision actuelle (refactoring sur place, G3) : événements non versionnés, les événements de domaine sont les événements publiés ; l'écouteur de platform dépend des classes d'événements ; à versionner quand un 2e consommateur ou une séparation de service apparaîtra. Le tableau ci-dessus est la cible.

Règles : payloads en types JDK (UUID, String, BigDecimal, Instant) — pas de VO internes ; nouvelle version `v2` à côté de `v1`, jamais de modification d'un contrat publié.

## Questions ouvertes
1. Stockage des pièces jointes : BYTEA en base (comme legacy ?) ou système de fichiers/objet — à vérifier lors de la caractérisation.
2. `Notifications` et `Habilitations` restent dans `platform` : ACL dédiées confirmées ou appel via un port générique commun ?
