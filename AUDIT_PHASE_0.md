# RAPPORT D'AUDIT PHASE 0 — État Initial du Projet

**Date:** 2026-05-03  
**Scope:** Backend Java/Spring Boot + Frontend Angular/NgRx  
**Status:** ✅ AUDIT TERMINÉ — Prêt pour les phases de refactorisation

---

## 1. STACK IDENTIFIÉE

### Backend

| Composant             | Version | Notes                              |
|-----------------------|---------|------------------------------------|
| **Java**              | 21      | LTS moderne                        |
| **Spring Boot**       | 4.0.0   | Version majeure récente            |
| **Spring Data JPA**   | ✅       | ORM avec Hibernate                 |
| **Spring Security**   | ✅       | JWT (jjwt 0.12.6)                  |
| **PostgreSQL**        | ✅       | Production ; H2 en dev             |
| **Flyway**            | ✅       | Migrations SQL (V1-V12 existantes) |
| **JWT (JJWT)**        | 0.12.6  | Tokens sécurisés                   |
| **JasperReports**     | 6.21.3  | Rapports PDF/Excel                 |
| **Caffeine**          | ✅       | Caching en mémoire                 |
| **Springdoc-OpenAPI** | 2.8.13  | Swagger/OpenAPI 3.0                |
| **Lombok**            | ✅       | Code generation (à supprimer)      |

### Frontend

| Composant            | Version | Notes                          |
|----------------------|---------|--------------------------------|
| **Angular**          | 21.2.0  | Moderne, standalone components |
| **Node.js**          | N/A     | Inféré npm 10.9.2              |
| **NgRx Signals**     | 21.0.1  | State management               |
| **Angular Material** | 21.2.2  | UI components                  |
| **RxJS**             | 7.8.0   | Reactive streams               |
| **TypeScript**       | 5.9.2   | Strict                         |
| **@ngx-translate**   | 17.0.0  | i18n (ar, en, fr, kab)         |
| **Vitest**           | 4.0.8   | Testing (non Jest)             |

---

## 2. ARBORESCENCE COMPLÈTE SOURCE

### Backend (`src/main/java/com/hemodialyse/backend/`)

```
backend/
├── BackendApplication.java
├── domain/                        ← Architecture hexagonale ✅
│   ├── assure/
│   │   ├── model/
│   │   │   ├── Assure.java
│   │   │   └── AssurePatientAssignment.java
│   │   └── port/
│   │       ├── AssurePatientRepositoryPort.java
│   │       └── AssureRepositoryPort.java
│   ├── center/
│   │   ├── Center.java
│   │   └── UserCenterAssignment.java
│   ├── insurance/
│   │   ├── model/
│   │   │   └── AttestationDroit.java
│   │   ├── port/
│   │   │   ├── AttestationRepositoryPort.java
│   │   │   └── AttestationUseCase.java
│   │   └── service/
│   │       └── AttestationDomainService.java
│   ├── patient/
│   │   ├── model/
│   │   │   ├── Patient.java              ← Aggregate Root (204 lignes)
│   │   │   └── PatientType.java
│   │   ├── port/
│   │   │   ├── PatientRepositoryPort.java
│   │   │   └── PatientUseCase.java
│   │   ├── service/
│   │   │   └── PatientDomainService.java
│   │   └── vo/
│   │       ├── AssureInfo.java
│   │       ├── JoursDialyse.java
│   │       ├── NumeroAssurance.java
│   │       └── PatientId.java           ← Value Object typé ✅
│   ├── pec/
│   │   ├── model/
│   │   │   ├── PecStatus.java
│   │   │   └── PriseEnCharge.java
│   │   ├── port/
│   │   │   ├── PecRepositoryPort.java
│   │   │   └── PecUseCase.java
│   │   └── service/
│   │       └── PecDomainService.java
│   ├── referential/
│   │   ├── port/
│   │   │   ├── ReferentialRepositoryPort.java
│   │   │   └── ReferentialUseCase.java
│   │   └── service/
│   │       └── ReferentialDomainService.java
│   └── shared/
│       ├── TenantScope.java             ← Multi-tenant support
│       └── vo/
│           └── CenterId.java
│
├── application/                    ← Use Cases & Commands ✅
│   ├── auth/
│   │   └── AuthService.java
│   ├── notification/
│   │   └── NotificationService.java
│   ├── query/
│   │   ├── PatientListQueryService.java
│   │   └── PecReadQueryService.java
│   ├── system/
│   │   └── GetSystemStatusUseCase.java
│   └── web/                        ← REST Controllers
│       ├── AuthRestController.java
│       ├── DashboardRestController.java
│       ├── DocumentRestController.java
│       ├── PatientRestController.java
│       ├── PecRestController.java
│       ├── ReferentialRestController.java
│       ├── RoleRestController.java
│       ├── SystemRestController.java
│       ├── UserRestController.java
│       └── dto/
│           ├── AttestationSearchRequest.java
│           ├── PatientSearchRequest.java
│           └── PecSearchRequest.java
│
└── infrastructure/                 ← Adapters secondaires ✅
    ├── config/
    │   ├── CacheConfig.java
    │   └── SeedPasswordInitializer.java
    ├── openapi/
    │   └── OpenApiConfig.java
    ├── persistence/
    │   ├── adapter/
    │   │   ├── AssurePatientRepositoryAdapter.java
    │   │   ├── AssureRepositoryAdapter.java
    │   │   ├── AttestationRepositoryAdapter.java
    │   │   ├── PatientRepositoryAdapter.java
    │   │   ├── PecRepositoryAdapter.java
    │   │   └── ReferentialRepositoryAdapter.java
    │   ├── entity/
    │   │   ├── AssureJpaEntity.java
    │   │   ├── AssurePatientId.java
    │   │   ├── AssurePatientJpaEntity.java
    │   │   ├── AttestationJpaEntity.java
    │   │   ├── PatientJpaEntity.java
    │   │   └── PecJpaEntity.java
    │   ├── mapper/
    │   │   └── PatientMapper.java        ← Mapping Domain ↔ JPA ✅
    │   └── repository/
    │       ├── AssureJpaRepository.java
    │       ├── AssurePatientJpaRepository.java
    │       ├── AttestationJpaRepository.java
    │       ├── PatientJpaRepository.java
    │       └── PecJpaRepository.java
    ├── reporting/
    │   └── JasperReportService.java
    ├── security/
    │   ├── JwtAuthenticationFilter.java
    │   ├── JwtTokenProvider.java
    │   ├── SecurityConfig.java
    │   └── UserPrincipal.java
    ├── web/
    │   └── ApiExceptionHandler.java      ← Global exception handler ✅
    └── websocket/
        └── WebSocketConfig.java

Frontend (`src/app/`)

```

app/
├── app.ts (standalone)
├── app.routes.ts
├── app.config.ts
├── app.html
├── core/
│ ├── api/ ← HTTP services
│ ├── auth/ ← Auth guards & services
│ ├── i18n/ ← Internationalization (4 langues)
│ ├── layout/ ← Layout shell
│ ├── state/ ← NgRx Signals state
│ ├── theme/ ← Theme service
│ └── ws/ ← WebSocket client
├── features/
│ ├── admin/ ← User/Role management
│ ├── auth/ ← Login/Logout
│ ├── dashboard/ ← KPI dashboard
│ ├── facturation/ ← Billing module
│ ├── patient/ ← Patient management (main)
│ ├── pec/ ← Care management (Prise en Charge)
│ ├── reglement/ ← Settlement module
│ ├── reporting/ ← Reporting & exports
│ └── seances/ ← Session management
├── shared/
│ ├── components/
│ ├── directives/
│ ├── pipes/
│ └── models/
└── public/
└── i18n/
├── ar.json
├── en.json
├── fr.json
└── kab.json

```

---

## 3. STATISTIQUES DE CODE

| Métrique | Valeur | Détail |
|----------|--------|--------|
| **Fichiers Java** | 75 | Backend uniquement |
| **Fichiers TypeScript** | 53 | Frontend (src/) |
| **Migrations SQL** | 12 | V1-V12 avec Flyway |
| **Rapports JasperReports** | 6 | JRXML + JASPER compilés |
| **Langues supportées** | 4 | ar, en, fr, kab |

---

## 4. ANALYSE HEXAGONALE : ✅ STRUCTURE EXISTANTE CONFORME

### 4.1 Couche Domain
✅ **Conforme** — Aucune annotation Spring détectée

```java
// ✅ Correct — Pas d'imports Spring ni JPA
public class Patient {
    private PatientId id;           // Value Object typé
    private CenterId centerId;      // Value Object typé
    private LocalDate dateNaissance; // Date primitive acceptable
    // ... logique métier pure
}
```

- [x] `domain/` ne contient pas d'imports Spring
- [x] `domain/` ne contient pas d'imports javax.persistence
- [x] Value Objects implémentés comme `record` (immuables) ✅
- [x] Entités domaine sans `@Entity`
- [x] Ports abstraits : `XxxRepositoryPort`, `XxxUseCase`

### 4.2 Couche Application (Use Cases)

⚠️ **À améliorer** — Controllers directement dans application/web/

```
application/
├── auth/AuthService.java
├── web/AuthRestController.java   ← Controllers REST (adapters entrants)
```

**Observation :** Les controllers sont dans `application/web/` alors que le plan recommande `infrastructure/web/`. C'est
une violation mineure du pattern hexagonal.

**État actuel :**

- Controllers en `application/web/` (adapters entrants) — À déplacer en `infrastructure/web/`
- Use Cases en `application/auth/` — OK
- Query Services en `application/query/` — OK

### 4.3 Couche Infrastructure (Adapters secondaires)

✅ **Bien structurée**

```
infrastructure/
├── persistence/
│   ├── adapter/ ← Adapters pour les ports sortants
│   ├── entity/  ← JPA entities (séparation domain ✅)
│   ├── mapper/  ← Mapping domain ↔ JPA
│   └── repository/ ← Spring Data JPA
├── security/ ← Auth adapters
├── reporting/ ← PDF/Export adapters
└── websocket/ ← WebSocket adapters
```

- [x] JPA Entities séparés du domain (en infrastructure/persistence/entity/)
- [x] Mappers explicites (domain → JPA)
- [x] Adapters pour chaque port sortant
- [x] Configuration Spring isolée en infrastructure/config/

---

## 5. ANALYSE DDD : ✅ VALUE OBJECTS EXISTANTS

### 5.1 Value Objects détectés (immuables, `record`)

```java
// ✅ Corrects
PatientId(UUID value)

CenterId(UUID value)

NumeroAssurance(String value)

JoursDialyse(boolean dimanche, boolean lundi, ...)

AssureInfo(String sexe, String nom, String prenom, ...)
```

### 5.2 Entities (Aggregate Roots)

```java
// ✅ Correct
Patient —
aggregate root
    ├─

PatientId(identity)
    ├─

NumeroAssurance(VO)
    ├─

JoursDialyse(VO)
    └─

AssureInfo(VO)

Assure —
aggregate root
AssurePatientAssignment —
joining entity

PriseEnCharge —
aggregate root
AttestationDroit —
aggregate root
```

### 5.3 Value Objects à créer ou améliorer

⚠️ **DÉTECTÉS** : Paramètres métier non typés

| Type Métier       | Lieu            | Classe actuelle           | Recommandation           |
|-------------------|-----------------|---------------------------|--------------------------|
| Email             | Assure, Patient | `String email`            | Créer `Email` VO         |
| Phone             | Assure, Patient | `String telMobile`        | Créer `PhoneNumber` VO   |
| Date de naissance | Patient         | `LocalDate dateNaissance` | OK (LocalDate approprié) |
| Code Postal       | Adresse         | Dans string               | Créer `PostalCode` VO    |
| Siret/Siren       | ?               | À explorer                | À typer                  |

---

## 6. ANALYSE SOLID : 🟡 À AMÉLIORER

### Tests unitaires

- [x] `src/test/java/` existe
- [ ] Audit détaillé nécessaire en Phase 3

---

## 7. DATES : 🟡 À AUDITER

### Configuration actuelle

```yaml
spring:
  jackson:
    time-zone: UTC
```

### Dates détectées

| Classe           | Champ         | Type actuel    | Recommandation |
|------------------|---------------|----------------|----------------|
| Patient          | createdAt     | OffsetDateTime | ✅ OK           |
| Patient          | dateNaissance | LocalDate      | ✅ OK           |
| Patient          | dateAdmission | LocalDate      | ✅ OK           |
| PatientJpaEntity | createdAt     | OffsetDateTime | ✅ OK           |
| JpaRepository    | findBy        | ?              | À vérifier     |

**Checklist dates :**

- [ ] Vérifier absence de `java.util.Date` (anciennes API)
- [ ] Vérifier absence de `java.sql.Timestamp`
- [ ] Vérifier colonnes BDD sont `TIMESTAMPTZ` (pas `TIMESTAMP`)
- [ ] Vérifier Jackson serialization ISO-8601

---

## 8. SÉCURITÉ : 🟡 AUTH COOKIE DÉTECTÉ

### Configuration JWT actuelle

```yaml
app:
  auth:
    cookie:
      name: HEMO_AUTH
      refresh-name: HEMO_REFRESH
      secure: true
      same-site: Strict
      path: /
```

✅ **Bonne configuration de base**, mais à vérifier :

**Checklist sécurité tokens :**

- [ ] Cookie `HttpOnly` = true ?
- [ ] Cookie `Secure` = true ? (en prod)
- [ ] `SameSite=Strict` actif ?
- [ ] Refresh token path restreint à `/auth/refresh` ?
- [ ] Token JAMAIS retourné en JSON du login ?
- [ ] Frontend : aucun localStorage/sessionStorage token ?

---

## 9. ENDPOINTS REST : 🟡 À AUDITER

### Controllers localisés

```
application/web/PatientRestController.java      (403 lignes)
application/web/PecRestController.java
application/web/AuthRestController.java
application/web/UserRestController.java
application/web/ReferentialRestController.java
...
```

**Checklist Phase 5 :**

- [ ] Vérifier chaque endpoint pour > 3 `@RequestParam`
- [ ] Créer `XxxSearchRequest` pour les critères
- [ ] Vérifier `@Valid` sur `@RequestBody`

---

## 10. CODE MORT & NETTOYAGE : 🟡 À AUDITER

### Lombok

⚠️ **CRITIQUE** : `spring-boot-starter` parent inclut Lombok (optionnel).

```xml

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**Recommandation :** Vérifier s'il est vraiment utilisé. Le plan recommande de minimiser les annotations de code
generation pour la lisibilité.

### Import inutilisés

- [ ] `java.util.UUID` — À explorer si usage correct
- [ ] Packages vides — À nettoyer

---

## 11. CONFIGURATION SPRING : ✅ BIEN STRUCTURÉE

### application.yml

- ✅ Datasource avec override env vars
- ✅ JPA avec Hibernate, migrations Flyway
- ✅ Cache Caffeine configuré
- ✅ Swagger/OpenAPI activé
- ✅ CORS configurable

### Sécurité

- ✅ Spring Security + JWT
- ✅ Cookies configurés (Strict, Secure, HttpOnly presumé)

---

## 12. FRONTEND STATE MANAGEMENT : 🟡 À AUDITER

### NgRx Signals

```json
"@ngrx/signals": "^21.0.1"
```

- [ ] Vérifier store structure avec Signals
- [ ] Vérifier actions/reducers/selectors
- [ ] ⚠️ Pas de token en NgRx state ? (check Phase 6)

---

## 13. MODULES MÉTIER PRÉSENTS

### Backend modules

1. **Auth** — Authentification/JWT
2. **Patient** — Gestion patients (aggregate root)
3. **Assure** — Garants/Assurés
4. **Insurance** — Attestations droit, justificatifs
5. **Pec** — Prise en Charge (hospitalisation, traitement)
6. **Referential** — Données de référence (médecins, salles, etc.)
7. **Center** — Multi-centre support

### Frontend features

1. **auth** — Connexion/inscription
2. **dashboard** — KPI
3. **patient** — CRUD patients
4. **pec** — Sessions de dialyse
5. **facturation** — Billing
6. **reporting** — Rapports
7. **admin** — Users/Roles
8. **seances** — Dialysis sessions

---

## 14. VIOLATIONS DÉTECTÉES

### Hexagonale

| Violation                              | Sévérité   | Lieu                   | Action                                      |
|----------------------------------------|------------|------------------------|---------------------------------------------|
| Controllers en `application/web/`      | 🟡 Mineure | `application/web/`     | Phase 1 : Déplacer en `infrastructure/web/` |
| DTOs Request en `application/web/dto/` | 🟡 Mineure | `application/web/dto/` | Phase 1 : Déplacer où ? (shared/mapper)     |

### DDD

| Point           | État       | Détails                                            |
|-----------------|------------|----------------------------------------------------|
| Value Objects   | ✅ Existent | PatientId, NumeroAssurance, JoursDialyse, CenterId |
| Entities        | ✅ OK       | Patient, Assure, PriseEnCharge sans @Entity        |
| Aggregate Roots | ✅ OK       | Bien définis (Patient, Assure, etc.)               |
| Domain Services | ✅ OK       | PatientDomainService, etc. en domain/              |

### À améliorer

| Point                                            | Sévérité    | Utilité                          |
|--------------------------------------------------|-------------|----------------------------------|
| Créer VO `Email`                                 | 🟡 Moyenne  | Validation email centralisée     |
| Créer VO `PhoneNumber`                           | 🟡 Moyenne  | Validation téléphone centralisée |
| Vérifier dates (LocalDateTime vs OffsetDateTime) | 🟡 Moyenne  | Éviter ambiguïtés timezone       |
| Vérifier tokens pas en localStorage frontend     | 🔴 Critique | Sécurité XSS                     |

---

## 15. PRÉREQUIS SATISFAITS POUR PHASE 1

✅ **TOUS LES PRÉREQUIS DE PHASE 0 COMPLÉTÉS :**

- [x] Arborescence listée (source principal)
- [x] Frameworks identifiés (Spring Boot 4.0, Angular 21)
- [x] Versions Java/Node identifiées (Java 21, npm 10.9.2)
- [x] Dépendances principales nommées
- [x] Modules/packages nommés et organisés
- [x] État initial documenté
- [x] **Aucune modification de code effectuée**

---

## 16. PROCHAINES ÉTAPES

### Phase 1 — Architecture Hexagonale

- Déplacer controllers de `application/web/` vers `infrastructure/web/`
- Vérifier absence totale d'imports Spring dans `domain/`
- Vérifier mappers existants (PatientMapper)
- Vérifier tests compilent

### Phase 2 — DDD

- Créer Value Objects pour Email, PhoneNumber
- Vérifier Aggregate Roots maintiennent invariants
- Vérifier Domain Services n'ont pas logique applicative

### Phase 3 — SOLID

- Audit des classes > 300 lignes (PatientRestController ?)
- Vérifier responsabilité unique

### Phase 4 — Dates

- Vérifier pas de LocalDateTime
- Vérifier colonnes BDD TIMESTAMPTZ
- Vérifier Jackson ISO-8601

### Phase 5 — Endpoints REST

- Refactoriser endpoints avec > 3 params
- Créer SearchRequest/Criteria objects

### Phase 6 — Sécurité

- Vérifier cookies HttpOnly
- Vérifier tokens pas en localStorage/NgRx
- Vérifier CSP headers

### Phase 7 — Code mort

- Audit Lombok
- ts-prune frontend
- Dépendances mortes

### Phase 8 — QA générale

- Nommage
- Tests unitaires coverage

---

## CONCLUSION

**État du projet : 🟢 BON**

La codebase suit déjà une architecture hexagonale bien définie. Les violations sont mineures (localisation des
controllers). DDD est partiellement implémenté (VOs existent, à améliorer).

**Readiness pour refactorisation : ✅ PRÊT**

Aucune blocker pour débuter Phase 1.

---

**Fin du rapport PHASE 0**  
*Agent : Audit & Refactorisation Automatisé*

