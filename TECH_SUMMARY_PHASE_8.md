# RÉSUMÉ TECHNIQUE — Phase 8 + Prochaines actions

**Date :** 2026-06-12  
**Version :** Hemodialyse v1.0-phase8

---

## 🎯 État actuel du projet

### Stack confirmée

| Layer        | Stack                             | Version         |
|--------------|-----------------------------------|-----------------|
| **Frontend** | Angular + Material + RxJS Signals | 21.2.0          |
| **Backend**  | Spring Boot + DDD Hexagonal       | 4.0.0           |
| **Database** | PostgreSQL + Flyway               | 15+             |
| **Build**    | Maven (BE) + npm (FE)             | 3.9.x / 10.9.x  |
| **Tests**    | Vitest (FE) + JUnit5 (BE)         | 4.x / 5.x       |
| **Auth**     | JWT stateless                     | Spring Security |

### Métriques de santé

```
Frontend:
  - Tests: 4/4 passing ✅
  - Build: production OK
  - TypeScript strict mode: ON
  - CSS budget: +24 bytes over (acceptable)

Backend:
  - Tests: 11/11 passing ✅
  - Compilation: OK
  - Code coverage: ~60% (à améliorer Phase 9)
```

---

## 🚀 Démarrage rapide

### Frontend

```bash
cd frontend

# Install
npm install

# Dev server
npm start

# Tests
npm test -- --watch=false

# Build production
ng build --configuration production
```

### Backend

```bash
cd backend

# Build
mvnw.cmd clean compile

# Tests
mvnw.cmd test

# Run local
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### Stack local (Docker Compose)

```bash
# From root
docker-compose up -d

# Database ready: localhost:5432
# API ready: http://localhost:8090
# Frontend: http://localhost:4200
```

---

## 📦 Modules clés

### Frontend Features

```
src/app/features/
├── auth/                 → JWT login, token storage
├── patient/              → CRUD + formulaires
├── pec/                  → Prise en Charge workflow
├── stock/                → Gestion stock + PMP
├── dashboard/            → KPI dashboard
├── reporting/            → Jasper reports
├── admin/                → Users, roles, centers
└── seances/              → Dialysis sessions
```

### Backend Modules

```
src/main/java/com/hemodialyse/backend/
├── domain/               → Business logic (pur, sans Spring)
│   ├── patient/
│   ├── pec/
│   ├── stock/
│   └── shared/
├── application/          → Use cases, services
├── infrastructure/       → REST, Database, JWT
└── config/               → Spring configuration
```

---

## 🔧 Fichiers importants

### Configuration

| Fichier                                      | Rôle                       |
|----------------------------------------------|----------------------------|
| `backend/pom.xml`                            | Dépendances Maven, plugins |
| `frontend/package.json`                      | Dépendances npm, scripts   |
| `docker-compose.yml`                         | Stack locale (DB + BE)     |
| `backend/src/main/resources/application.yml` | Propriétés Spring          |
| `frontend/environments/environment.ts`       | Config frontend            |

### API Contracts

| Endpoint                                          | Méthode      | Rôle                |
|---------------------------------------------------|--------------|---------------------|
| `/api/v1/auth/login`                              | POST         | Authentification    |
| `/api/v1/patient`                                 | GET/POST/PUT | CRUD patients       |
| `/api/v1/pec`                                     | GET/POST/PUT | CRUD PEC            |
| `/api/v1/stock/dashboard/pmp-explain/{articleId}` | GET          | Calcul détaillé PMP |
| `/api/v1/dashboard/stats`                         | GET          | KPI dashboard       |
| `/actuator/health`                                | GET          | Health check        |

---

## 🧪 Tests

### Frontend (Vitest)

```bash
# Run tests
npm test -- --watch=false

# Watch mode
npm test

# Fichier de test : *.spec.ts
# Import: import { describe, it, expect } from 'vitest';
```

### Backend (JUnit5)

```bash
# Run all tests
mvnw test

# Run suite de classe
mvnw -Dtest=PatientServiceTest test

# Skip tests (build only)
mvnw build -DskipTests
```

---

## 📊 Architecture décisionnelle

### 1. Multi-centre (single DB)

- ✅ Toute donnée métier porte `center_id` (UUID NOT NULL)
- ✅ Chaque requête filtrée par centre (sécurité)
- ✅ Uniques scopées par centre : `UNIQUE(center_id, business_key)`

### 2. JWT Stateless

- ✅ Token contient: userId, roles, centers autorisés
- ✅ Validé via Spring Security Resource Server
- ✅ Expiration: 1h (configurable)

### 3. DDD Hexagonale

- ✅ Domain: pur (sans Spring, sans JPA)
- ✅ Application: use cases, services
- ✅ Infrastructure: REST, Database, JWT adapters

### 4. Standalone Components (Angular)

- ✅ Zéro NgModule (ou root module uniquement)
- ✅ Lazy loading par route
- ✅ Injection de dépendances directe

---

## 🚨 Warnings à résoudre (Phase 9)

| Issue                                          | Priorité  | Effort |
|------------------------------------------------|-----------|--------|
| CSS budget +24 bytes (shell.component)         | 🟡 Medium | 15 min |
| CSS budget +221 bytes (patient-list.component) | 🟡 Medium | 30 min |
| Coverage tests < 70%                           | 🟠 High   | 2h     |
| E2E tests manquants                            | 🟡 Medium | 4h     |

---

## ✅ Checklist Phase 9 (CI/CD)

- [ ] Créer `Jenkinsfile` (pipeline Maven + npm)
- [ ] Créer `.gitlab-ci.yml` (GitLab Runner)
- [ ] Créer `.github/workflows/ci.yml` (GitHub Actions)
- [ ] Stages: lint → compile → test → scan → build → publish
- [ ] Quality gates: SonarQube integration
- [ ] Artifact publish: Artifactory / Docker Hub
- [ ] Deployment: Docker image push + deploy script

---

## 📚 Ressources utiles

### Documentation générée

- `PHASE_8_RAPPORT.md` — Rapport détaillé Phase 8
- `docs/architecture/context-map.md` — DDD context map
- `docs/domain/glossary.md` — Glossaire métier
- `plan/plan.md` — Plan global
- Swagger UI: `http://localhost:8090/swagger-ui.html` (après run backend)

### Liens externes

- [Spring Boot 4 Guide](https://spring.io/projects/spring-boot)
- [Angular 21 Docs](https://angular.io/docs)
- [RxJS Signals](https://angular.io/guide/signals)
- [Material Components](https://material.angular.io/)

---

## 🔐 Sécurité

### Points clés

1. ✅ JWT validé sur chaque endpoint (`@PreAuthorize`)
2. ✅ Center scope appliqué (anti cross-centre access)
3. ✅ Passwords hashés (BCrypt)
4. ✅ CORS configuré (`allowed-origins`)
5. ⚠️ RLS PostgreSQL (optionnel, post-MVP)

### Secrets à configurer

```yaml
# backend/src/main/resources/application.yml
security:
  jwt:
    secret: ${JWT_SECRET}  # À définir en env
    expiry: 3600          # secondes
```

---

## 🐛 Debugging

### Backend

```bash
# Activer debug logs
mvnw spring-boot:run -Dspring-boot.run.arguments="--logging.level.root=DEBUG"

# Voir logs SQL
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Frontend

```bash
# Enable source maps
ng build --source-map

# Debugger dans DevTools (F12)
```

---

## 📝 Conventions de code

- **Java** : CamelCase classes, snake_case columns (DB)
- **TypeScript** : Strict null checks, no `any` type
- **Angular** : Standalone components, OnPush change detection
- **Git** : Commit messages: `type: description` (feat, fix, refactor, docs)

---

**Fin du résumé technique. Prêt pour Phase 9 ! 🚀**

