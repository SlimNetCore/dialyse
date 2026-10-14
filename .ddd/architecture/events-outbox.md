---
status: proposed
date: 2026-10-09
sources: [.ddd/tactical/gmao.md §7, ADR 0013, .ddd/research/spring-modulith-events-outbox.md]
---
# Événements & outbox

## Flux (une commande GMAO)
1. Use case : `UniteDeTravail.executer(...)` → charge l'agrégat → méthode d'intention → l'agrégat collecte ses événements de domaine.
2. `Repository.enregistrer(agrégat)` (adapter JPA) : persiste l'état + journal des statuts (R8) dans la même transaction.
3. `PublicateurDEvenements.publier(événements)` (même transaction) : événements de domaine **et** événements d'intégration `gmao.v1` traduits par l'application → `ApplicationEventPublisher` → le registre Modulith écrit une entrée par listener (outbox).
4. Commit → listeners `@ApplicationModuleListener` (async, transaction propre) : P1 (`Equipement.constaterEtat`), P2, ACL legacy Planification, Audit ; entrée marquée complétée (puis supprimée, `completion-mode=DELETE`) si succès.
5. Échec → entrée incomplète ; rejeu au redémarrage + resoumission planifiée des `FailedEventPublications` ; staleness monitor alerte.

## Garanties & règles
| Sujet | Règle |
|---|---|
| Livraison | at-least-once ; aucun ordre garanti |
| Idempotence | `eventId` (UUID) dans chaque événement ; consommateur enregistre `(consommateur, eventId)` dans `JournalDesMessagesTraites` dans sa transaction |
| Ordre | consommateur compare `depuis` (instant) pour un même `equipementId` ; ignore un événement plus ancien que le dernier appliqué |
| P1 | idempotente par `interventionId + jalon` (tactical §6) ; ignore un équipement réformé (Q3 tactique) |
| Schéma | table du registre créée par migration Flyway (pas d'initialisation auto) |
| Versionnement | `publishedlanguage/v1` figé ; changement incompatible = `v2` publié en parallèle |
| Sérialisation | Jackson 3 (Boot 4) ; événements = records à types JDK/VO simples — vérifier au pilote |

## Questions ouvertes
1. Événements de domaine internes persistés dans le registre (pour P1/P2) ou seulement les événements d'intégration + appel direct ? Proposé : tous les événements ayant un listener transactionnel passent par le registre.
2. Sémantique de `EquipementMisHorsService` (Q1 tactique) : impacte le traducteur, pas l'infrastructure.
