---
status: proposed
date: 2026-10-09
decision-makers: [architecte, direction technique]
context-scope: global
research: [.ddd/research/spring-modulith-events-outbox.md]
---
# 0013 — Utiliser le registre de publication d'événements Spring Modulith (JDBC) comme outbox intra-applicatif, sans broker

## Contexte et problème
GMAO publie `gmao.v1` (`ÉquipementMisHorsService`, `RemisEnService`, `Réformé`, `Enregistré`, `InterventionTerminée`) at-least-once ; la politique P1 doit tourner dans une transaction séparée (fin de la transaction multi-agrégats). Tous les consommateurs (Planification legacy via ACL, Pilotage, Notifications) sont dans la même JVM (ADR 0001).

## Options considérées
1. Outbox maison (table + relais planifié) — contre : code d'infrastructure à maintenir.
2. **Registre Modulith** (`spring-modulith-starter-jdbc`, BOM 2.0.x aligné sur Boot 4.0) + `@ApplicationModuleListener` (async, `REQUIRES_NEW`) — pour : écrit dans la transaction métier, rejeu des publications incomplètes ; contre : pas d'ordre garanti, purge à gérer.
3. Broker (Kafka/AMQP) + externalisation — contre : aucune frontière de processus aujourd'hui ; ajoutable plus tard (`@Externalized`, mode outbox).

## Décision
Option 2. Réglages : schéma géré par Flyway (`schema-initialization.enabled=false`), `completion-mode=DELETE`, `republish-outstanding-events-on-restart=true`, staleness monitor activé. Consommateurs **idempotents** (port `JournalDesMessagesTraités`, clé `eventId`) ; ordre par `equipementId` non garanti => le consommateur compare `depuis`.

## Conséquences
+ pas de nouvelle infrastructure ; extraction future vers broker sans changer le domaine.
− la classe principale legacy n'est pas modulithique : seul le registre est utilisé, pas `verify()` (cf. ADR 0015).
⚠ fonctionnement du registre sans `@Modulithic` et sérialisation Jackson 3 des événements : test pilote obligatoire en slice 1 ; échec => repli option 1 (nouvel ADR).
## Résultat du pilote (slice G1, 2026-10-09) — ⚠ levé, option 2 confirmée
Test `RegistreDEvenementsTest` (module `contexts-gmao`, Boot 4.0.8, Spring Modulith 2.0.8, starter **JDBC**, H2 mode PostgreSQL) : le registre fonctionne **sans `@Modulithic` ni détection de modules** ; sérialisation/désérialisation **Jackson 3** correcte pour des événements en records contenant des identifiants typés (records à un champ) ; un événement publié dans une transaction annulée n'est jamais livré ; un listener en échec laisse la publication dans `event_publication`, relue puis rejouée par `IncompleteEventPublications.resubmitIncompletePublications(...)` (équivalent d'un redémarrage). Constats à retenir :
- **Le starter JPA est à éviter** : l'entité du registre génère `serialized_event VARCHAR(255)` sous `ddl-auto` (événement refusé dès ~350 caractères). Le starter JDBC avec le schéma officiel (`VARCHAR(4000)` / `TEXT`) est conservé ; migration Flyway **V35** (copie du schéma PostgreSQL v2). `spring.modulith.events.jdbc.schema-initialization.enabled=true` par défaut dans `gmao-defaults.properties` (idempotent, pour H2/dev), à passer à `false` quand Flyway est appliqué partout.
- `@ApplicationModuleListener` exige `@EnableAsync` (activé par `GmaoConfiguration` ; le legacy n'utilise aucun `@Async`, effet global nul aujourd'hui).
- Un bean listener est proxifié : ne jamais lire ses champs directement dans les tests.
- Tailles : un événement de plan ≈ 360 caractères JSON ; surveiller `VARCHAR(4000)` en H2 pour les futurs événements riches.
- Repli (non déclenché) : option 1 (outbox maison) ; aucun ADR de remplacement nécessaire.

## Révision
Premier contexte déployé hors JVM, ou volume > 10⁵ événements/jour.
