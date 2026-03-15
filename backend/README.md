# Backend - Sprint 1 bootstrap

Spring Boot 4 backend initialise pour demarrer le projet DDD + hexagonal.

## Inclus dans ce bootstrap
- Securite JWT (resource server) avec endpoint public `/api/v1/system/ping`
- OpenAPI/Swagger UI
- PostgreSQL + Flyway
- Base multi-centre initiale (`centers`, `user_center_assignment`)

## Lancer en local
```bash
./mvnw spring-boot:run
```

## Tester
```bash
./mvnw test
```

## Endpoints utiles
- `GET /api/v1/system/ping`
- `GET /actuator/health`
- `GET /swagger-ui.html`

