---
topic: Testcontainers PostgreSQL avec Spring Boot 4
date: 2026-10-09
source: web (docs.spring.io/spring-boot testcontainers ; Maven Central) - non vérifié par context7
libraryId: org.testcontainers 2.0.5 ; Spring Boot doc 4.1.1
---
## Réponses clés
- Testcontainers 2.x (2.0.5 dernier) : JUnit 4 supprimé ; `PostgreSQLContainer` est dans `org.testcontainers.postgresql` (plus `org.testcontainers.containers`) ; non générique.
- Dépendances de test : `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, module postgresql.
- `@ServiceConnection` sur un champ `static` `PostgreSQLContainer` : crée `JdbcConnectionDetails` (et R2DBC), remplace `spring.datasource.*`. Alternative : `@DynamicPropertySource`.
- Piège cache de contexte : le champ static arrêté par l'extension JUnit peut casser un ApplicationContext mis en cache ; déclarer le conteneur en `@Bean` (avec `@ServiceConnection`) ou `@ImportTestcontainers`.

## Extraits
```java
@Testcontainers @SpringBootTest
class PgIT {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
}
```

## Écarts / pièges
- CONTRADICTION artefact : la doc Boot cite `org.testcontainers:postgresql` ; Maven Central expose `org.testcontainers:testcontainers-postgresql` 2.0.5 (renommage 2.x probable). Vérifier la résolution dans le BOM Boot (`mvn dependency:tree`) avant de figer.
- Aligner l'image sur la version PostgreSQL de production.
- Docker requis sur le poste/CI.

## Questions ouvertes
- Version PostgreSQL de prod ; réutilisation de conteneur (singleton) pour la vitesse.
