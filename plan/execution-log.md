# Journal d execution du plan

## 2026-03-14 - Demarrage

### Sprint 0
- [x] Structure `docs/` creee
- [x] Glossaire metier cree (`docs/domain/glossary.md`)
- [x] Context map cree (`docs/architecture/context-map.md`)
- [x] ADR-001 cree (`docs/architecture/adrs/ADR-001-single-db-multicentre.md`)
- [x] ADR-002 cree (`docs/architecture/adrs/ADR-002-modulith-hexagonal.md`)
- [x] Backlog MVP initial cree (`docs/backlog/mvp-backlog.md`)
- [x] DoD cree (`docs/governance/definition-of-done.md`)
- [x] Dossiers `frontend/`, `backend/`, `infra/` initialises

### Sprint 1 (bootstrap)
- [x] Fichier `docker-compose.yml` cree (PostgreSQL)
- [x] Fichier `.gitignore` initialise
- [x] Note infra creee (`infra/README.md`)
- [x] Backend Spring Boot 4 initialise (`backend/`)
- [x] OpenAPI/Swagger configure (`backend/pom.xml`, `backend/src/main/java/.../OpenApiConfig.java`)
- [x] JWT resource server configure (`backend/src/main/java/.../SecurityConfig.java`)
- [x] Endpoint de smoke test ajoute (`GET /api/v1/system/ping`)
- [x] Base migration multi-centre initiale (`backend/src/main/resources/db/migration/V1__init_multicentre.sql`)
- [x] Front Angular Material + Signal Store initialise (`frontend/src/app/core/state/app-shell.store.ts`)
- [x] Selection de centre dans shell front (`frontend/src/app/app.html`)
- [x] Tests backend executes (`./mvnw.cmd -q test`)
- [x] Tests frontend executes (`npm test -- --watch=false`)

### Sprint 2 (en cours)
- [x] Fix local H2 runtime (`pom.xml` + `application.yml`) et activation console H2
- [x] Ajout migration metier MVP (`V2__create_mvp_patient.sql`)
- [x] Ajout migration type patient (`V3__patient_type_and_constraints.sql`)
- [x] Ajout domaine PEC (`PriseEnCharge`, `PecStatus`, `PriseEnChargeRepository`)
- [x] Ajout service/controleur PEC (`PecService`, `PecController`)
- [x] Ajout test integration PEC (`PecServiceIntegrationTest`)
- [x] Refonte UI shell front avec theme apaisant domaine hemodialyse
- [x] Branchement front sur APIs patient/PEC (`BackendApiService`, actions UI connectees)
- [x] Validation metier vacancier/non-vacancier + attestation (`PatientService`, `PecService`)
- [x] Gestion erreurs API RFC7807 (`ApiExceptionHandler`)

### Sprint 3 (demarrage)
- [x] Extraction composants front dedies (`PatientFormComponent`, `PecWorkflowComponent`)
- [x] Integration de composants dans `app.html` / `app.ts`
- [x] Creation fenetre d'authentification (`LoginComponent`)
- [x] Endpoint backend de connexion (`POST /api/v1/auth/login`)
- [x] Session front (token JWT + interceptor HTTP)
- [x] Passage login -> interface de creation patient apres succes

### Prochaine action
- [ ] Sprint 3: renforcer controles JWT/roles (claims + policies fines)
- [ ] Sprint 3: finaliser schema attestation/assurance et tests de non-regression multi-centre
- [ ] Sprint 3: remplacer les boutons demo par formulaires metier complets patient/PEC

## 2026-06-14 - Reprise migration frontend Angular 22

### Verification de l etat Angular

- [x] Verification dependances Angular 22 (`frontend/package.json`)
- [x] Verification configuration Angular 22 (`frontend/angular.json`)
- [x] Verification TypeScript 6 et mode strict (`frontend/tsconfig.json`)

### Verification technique executee

- [x] Build frontend: `npm run build` (OK)
- [x] Tests frontend: `npm test -- --watch=false` (4 tests OK)
- [x] Verification migrations restantes: `npx ng update` (aucune migration restante)

### Etat

- [x] Migration Angular 22 confirmee operationnelle
- [ ] Prochaine etape recommandee: traiter les avertissements de budget SCSS (
  `frontend/src/app/core/layout/shell.component.ts`, `frontend/src/app/features/patient/patient-list.component.ts`)

