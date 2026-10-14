---
topic: Spring Boot 4 - migration, web, data, transactions, tests
date: 2026-10-09
source: web (non vérifié par context7 - clé API invalide)
libraryId: n/a (github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide ; docs.spring.io)
---
## Réponses clés
- Versions Maven Central (2026-10-09) : 4.0.8 (ligne 4.0), 4.1.1 (ligne 4.1), 4.2.0-M2 (milestone). Choisir une version GA, ne pas prendre le `release` tag = milestone.
- Baseline : Spring Framework 7, Jakarta EE 11, Servlet 6.1, Java 17+ (Java 21 OK).
- Starters modularisés `spring-boot-starter-<techno>` ; Flyway/Liquibase exigent leur starter dédié ; starters de test dédiés (`spring-boot-starter-security-test` pour @WithMockUser). Ponts temporaires : `spring-boot-starter-classic`, `spring-boot-starter-test-classic`.
- Jackson 3 : packages `tools.jackson` (sauf `jackson-annotations`) ; `@JsonComponent`->`@JacksonComponent`, `@JsonMixin`->`@JacksonMixin` ; stop-gap `spring-boot-jackson2`.
- Tests : `@MockBean`/`@SpyBean` supprimés -> `@MockitoBean`/`@MockitoSpyBean`. `@SpringBootTest` ne fournit plus MockMvc/TestRestTemplate : ajouter `@AutoConfigureMockMvc` / `@AutoConfigureTestRestTemplate` / `@AutoConfigureRestTestClient`.
- Propriété renommée : `spring.persistence.exceptiontranslation.enabled` (ex `spring.dao.exceptiontranslation.enabled`).
- `hibernate-jpamodelgen` -> `hibernate-processor`. Probes liveness/readiness activées par défaut. JSpecify nullability ajoutée.
- `HttpMessageConverters` déprécié (-> `ClientHttpMessageConvertersCustomizer`/`ServerHttpMessageConvertersCustomizer`).

## Écarts vs pratiques courantes / pièges
- Imports Jackson et @MockBean cassent à la compilation en migrant depuis Boot 3.
- Spring Retry n'est plus géré par Boot (version à fixer).
- Les 24 derniers Ko du guide (offset 100000) n'ont pas été lus.

## Questions ouvertes
- Transactions/Spring Data JPA/JDBC : aucun changement spécifique relevé dans la source lue ; non vérifié (Hibernate 7 à confirmer pour la ligne de version utilisée).
- Version Boot réellement utilisée par le projet (lire pom parent).
