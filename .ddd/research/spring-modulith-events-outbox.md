---
topic: Spring Modulith - @ApplicationModuleListener, event publication registry, outbox / externalisation
date: 2026-10-09
source: web (docs.spring.io/spring-modulith/reference/events.html) - non vérifié par context7
libraryId: org.springframework.modulith (doc 2.1.1 ; stables 2.0.8, 1.4.13 ; preview 2.2.0-M2)
---
## Réponses clés
- `@ApplicationModuleListener` = `@Async` + `@Transactional(REQUIRES_NEW)` + `@TransactionalEventListener` : listener asynchrone, transaction propre, réaction inter-modules.
- Event Publication Registry : une entrée par listener transactionnel, écrite dans la transaction métier d'origine ; marquée complétée seulement si le listener réussit ; sinon reste pour retry. C'est un outbox intra-appli (at-least-once => listeners idempotents).
- Starters : `spring-modulith-starter-jpa` ou `-jdbc` (JDBC crée sa table sauf `spring.modulith.events.jdbc.schema-initialization.enabled=false` ; en prod gérer le schéma via Flyway).
- Republication au redémarrage : `spring.modulith.events.republish-outstanding-events-on-restart=true`.
- États (depuis 2.0) : PUBLISHED, PROCESSING, COMPLETED, FAILED, RESUBMITTED. APIs : `CompletedEventPublications`, `IncompleteEventPublications`, `FailedEventPublications.resubmit(ResubmissionOptions)`.
- `spring.modulith.events.completion-mode` : UPDATE (défaut, nécessite purge), DELETE, ARCHIVE.
- Staleness monitor : `spring.modulith.events.staleness.*` (inactif si durées = 0).
- Externalisation broker : `spring-modulith-events-kafka|amqp|jms|messaging` + `@Externalized("target::#{key}")`. Simple listener async, PAS un vrai outbox.
- Vrai outbox : `spring-modulith-starter-namastack` (SGBD relationnel) ou `-jobrunr` + `spring.modulith.events.externalization.mode=outbox`.
- Test : `@ApplicationModuleTest` + `PublishedEvents`/`AssertablePublishedEvents`.

## Extraits
```java
@ApplicationModuleListener
void on(OrderCompleted event) { /* idempotent */ }
```

## Écarts / pièges
- Mode UPDATE : la table grossit sans purge (`CompletedEventPublications.deleteCompletedPublicationsOlderThan`).
- Pas d'ordre garanti ; au moins une fois.
- Compat Boot 4 : doc ne la précise pas ; hypothèse : Modulith 2.0.x <-> Boot 4.0, 2.1.x <-> Boot 4.1 (non vérifié, utiliser le BOM `spring-modulith-bom` et confirmer sur spring.io).

## Complément 2026-10-09 (source: web docs.spring.io/spring-modulith/reference/{appendix,fundamentals}.html, non vérifié par context7)
- Matrice de compatibilité (appendix) : Modulith 2.0 compilé contre Boot 4.0 ; 1.4 ↔ Boot 3.5. La ligne 2.1 ↔ Boot 4.1 n'apparaît pas dans la page lue (hypothèse). => projet en Boot 4.0.8 : `spring-modulith-bom` 2.0.x.
- Détection des modules : défaut `direct-subpackages` du package de la classe principale ; `spring.modulith.detection-strategy=explicitly-annotated` => seuls les packages annotés `@ApplicationModule` (ou jMolecules `@Module`) sont des modules.
- `@Modulithic(additionalPackages=…, sharedModules=…, systemName=…)` sur la classe principale ; `@ApplicationModule(allowedDependencies="order :: spi", type=OPEN)` ; `@NamedInterface("spi")` sur package-info.
- Modules situés dans d'autres jars : non documenté explicitement ; voie documentée = `ApplicationModuleSourceFactory` déclaré dans `META-INF/spring.factories` (inférence, à prototyper).
- Conséquence projet : la classe principale legacy (`com.hemodialyse.backend`) a des sous-packages par couche (domain/application/infrastructure) => `verify()` en `direct-subpackages` n'a pas de sens ; n'utiliser Modulith que pour le registre d'événements tant que le legacy n'est pas modularisé.

## Questions ouvertes
- Namastack/JobRunr : maturité et besoin réel d'un broker ? Décision par ADR.
- Le registre d'événements fonctionne-t-il sans `@Modulithic` ni détection de modules (starter JDBC seul) ? À confirmer par un test pilote (slice GMAO n°1).
- Sérialisation Jackson 3 des événements de domaine (records + VO) dans le registre : à vérifier par test d'intégration.
