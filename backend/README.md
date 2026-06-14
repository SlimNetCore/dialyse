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

## Comptes de demo (dev)

Ces comptes sont charges via `db/seed.sql` puis hashes au demarrage par
`SeedPasswordInitializer`.

| Profil     | Username            | Mot de passe    | Centre          |
|------------|---------------------|-----------------|-----------------|
| Admin      | `admin`             | `admin$$2026dz` | Annaba + Rouiba |
| Medecin    | `medecin`           | `medecin123`    | Annaba          |
| Infirmier  | `infirmier-annaba`  | `infirmier123`  | Annaba          |
| Infirmier  | `infirmier-rouiba`  | `infirmier123`  | Rouiba          |
| Secretaire | `secretaire-annaba` | `secretaire123` | Annaba          |
| Secretaire | `secretaire-rouiba` | `secretaire123` | Rouiba          |

## Modes de cache

### Mode actuel (par defaut): Caffeine local

- Le backend utilise un cache local en memoire (`spring.cache.type=caffeine`).
- Les TTL sont configures via:
    - `CACHE_TTL_REFERENTIALS` (defaut `PT6H`)
    - `CACHE_TTL_PATIENT_DETAIL` (defaut `PT15M`)
    - `CACHE_TTL_PATIENT_LIST` (defaut `PT3M`)
    - `CACHE_TTL_PATIENT_COUNT` (defaut `PT3M`)

```powershell
Set-Location "C:\Users\TS-CONSULT\WebstormProjects\Hemodialyse\backend"
.\mvnw.cmd spring-boot:run
```

### Mode futur: Redis

- Redis est prevu ulterieurement, mais n'est pas actif dans la configuration actuelle.
- Quand vous serez pret, il faudra reintroduire la dependance Redis et sa configuration Spring cache associee.

```powershell
# Exemple TTL local personnalise
$env:CACHE_TTL_PATIENT_DETAIL="PT10M"
$env:CACHE_TTL_PATIENT_LIST="PT2M"
.\mvnw.cmd spring-boot:run
```

