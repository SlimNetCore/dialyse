# PLAN D'EXÉCUTION DÉTAILLÉ — Phases 1 à 9

**Status :** 🟢 PHASE 0 TERMINÉE — Prêt pour Phase 1  
**Date de démarrage :** 2026-05-03  
**Agent responsable :** Refactorisation Automatisée

---

## PHASE 1 — Architecture Hexagonale ⏱️ Estimée : 2-3h

### Objectif

Corriger les violations architecturales détectées et assurer une séparation claire entre domaine pur, application, et
infrastructure.

### Violations à corriger

#### 1.1. **Déplacer Controllers** (Violation majeure)

**Lieu :** `/application/web/*RestController.java`  
**Action :** Déplacer vers `/infrastructure/web/rest/`

**Fichiers affectés :**

- AuthRestController.java
- PatientRestController.java
- PecRestController.java
- DashboardRestController.java
- DocumentRestController.java
- ReferentialRestController.java
- RoleRestController.java
- SystemRestController.java
- UserRestController.java

**Process :**

1. Créer `infrastructure/web/rest/` (nouveau dossier)
2. Mv tous les controllers
3. Mettre à jour imports dans les DTOs
4. Vérifier compilation tests

#### 1.2. **Réorganiser DTOs Request** (Violation structurelle)

**Lieu :** `/application/web/dto/`  
**Action :** Déplacer vers `/infrastructure/web/dto/request/`

**Fichiers affectés :**

- AttestationSearchRequest.java
- PatientSearchRequest.java
- PecSearchRequest.java

**Process :**

1. Créer `infrastructure/web/dto/request/`
2. Créer `infrastructure/web/dto/response/` (futur)
3. Mv fichiers
4. Mettre à jour imports

#### 1.3. **Vérifier absence d'imports Spring dans Domain** ✅

**Command de vérification :**

```bash
find backend/src/main/java/com/hemodialyse/backend/domain -name "*.java" -exec grep -l "@Entity\|@Service\|@Repository\|@Component\|@Autowired\|javax.persistence\|org.springframework" {} \;
```

**Résultat attendu :** Aucun fichier retourné

#### 1.4. **Tests après restructuration**

```bash
# Backend
mvn clean test -DskipITs

# Frontend
npm run test
```

**Attendu :** Tous les tests passent

### Artefacts à produire (Phase 1)

1. ✅ Réorganisation des packages complétée
2. ✅ Tous tests compilent et passent
3. ✅ Rapport : "Violations hexagonales corrigées"

---

## PHASE 2 — Domain-Driven Design ⏱️ Estimée : 3-4h

### Objectif

Renforcer le modèle de domaine avec des Value Objects strictement typés et vérifier les Aggregates.

### 2.1 Créer Value Objects manquants

#### Email (VO)

**Créer :** `domain/shared/vo/Email.java`

```java
public record Email(String value) {
    public Email {
        if (value == null || !value.matches("^[\\w.+-]+@[\\w-]+\\.(com|fr|dz|sn|[a-z]{2,})$"))
            throw new InvalidEmailException("Invalid email: " + value);
    }
    public static Email of(String str) { return new Email(str); }
}
```

**Où remplacer :**

- Patient.email → patient.Email? Non, reste String pour compatibilité legacy
- Assure.email → À étudier
- User.email → À étudier

**Décision architecturale :** À valider (peut rester comme validation en controller)

#### PhoneNumber (VO)

**Créer :** `domain/shared/vo/PhoneNumber.java`

```java
public record PhoneNumber(String countryCode, String number) {
    public PhoneNumber {
        if (!number.matches("\\d{7,15}"))
            throw new InvalidPhoneException("Invalid phone number");
    }
}
```

**Où remplacer :**

- Patient.telMobile → PhoneNumber
- Patient.telPersonnel → PhoneNumber
- Patient.telBureau → PhoneNumber
- Assure.tel* → PhoneNumber

#### PostalCode (VO) — Optionnel

Garder comme String avec validation en tant que VO si plusieurs utilisations.

### 2.2 Vérifier Aggregate Roots

**Aggregates à auditer :**

```
Patient (Aggregate Root)
├─ Métadonnées civiles
├─ Métadonnées médicales
└─ Relations: Assure (par ID, pas direct)

Assure (Aggregate Root)
├─ Informations de base
└─ Relations: AssurePatientAssignment (entité jointe)

PriseEnCharge (Aggregate Root)
├─ Sessions de dialyse
└─ État

AttestationDroit (Aggregate Root)
```

**Checklist invariants :**

- [ ] Patient ne peut pas changer son ID
- [ ] Patient.activate() → validate state transition
- [ ] Pec.cancel() → validate state transition
- [ ] Assure.assignPatient() → validate constraints

### 2.3 Domain Services

**Vérifier :**

- [ ] PatientDomainService n'a QUE de la logique métier (pas @Transactional)
- [ ] PecDomainService mêmes règles
- [ ] Pas de dépendances vers repositories (passer en parameter si needed)

### 2.4 Tests DDD

Créer `backend/src/test/java/.../domain/`:

```bash
mkdir -p src/test/java/com/hemodialyse/backend/domain/patient/{model,vo}
```

**Tests unitaires domaine :**

- PatientTest → Patient creation, state transitions
- PatientIdTest → PatientId validation, equality
- NumeroAssuranceTest → Validation, format
- EmailTest (si créé as VO)

### Artefacts à produire (Phase 2)

1. ✅ Value Objects Email, PhoneNumber créés (si décidé)
2. ✅ Invariants des Aggregates validés
3. ✅ Tests unitaires domaine créés (> 50% coverage)
4. ✅ Rapport : "DDD Strengthening completed"

---

## PHASE 3 — SOLID Principles ⏱️ Estimée : 2-3h

### 3.1 Single Responsibility Principle

**Audit (SonarQube ou IntelliJ) :**

```bash
mvn sonar:sonar  # Si SonarQube disponible
```

**Classes > 300 lignes (candidats division) :**

- PatientRestController.java (403 lignes) ← À diviser
- Patient.java (204 lignes) ← OK
- PatientJpaEntity.java (~130 lignes) ← OK

**Action sur PatientRestController :**

1. Extraire méthodes GET → `PatientQueryController.java`
2. Extraire méthodes POST/PUT → `PatientCommandController.java`
3. Ou créer `PatientFacade` avec delégation

### 3.2 Open/Closed Principle

**Chercher :**

```bash
grep -r "switch.*status\|if.*status\|instanceof" backend/src/main/java
```

**À remplacer par polymorphisme :**

- PatientStatus (enum) → Pattern Strategy si comportement complexe
- PecStatus → Même pattern

### 3.3 Liskov Substitution

Vérifier aucune subclass ne lance `UnsupportedOperationException`.

### 3.4 Interface Segregation

Vérifier interfaces < 5 méthodes :

- [ ] PatientUseCase
- [ ] PatientRepositoryPort
- [ ] AuthService

### 3.5 Dependency Inversion

**Vérifier :**

- [ ] Injections = interfaces, pas classes concrètes
- [ ] PatientService inject PatientRepositoryPort, pas adapter direct

### Artefacts à produire (Phase 3)

1. ✅ Audit SOLID report
2. ✅ PatientRestController refactorisé
3. ✅ Aucune classe > 300 lignes (exception: entités complexes)
4. ✅ Rapport : "SOLID principles applied"

---

## PHASE 4 — Gestion des Dates ⏱️ Estimée : 1-2h

### 4.1 Audit complet

```bash
# Chercher java.util.Date
grep -r "java\.util\.Date\|java\.sql\.Timestamp" backend/src/main/java

# Chercher LocalDateTime (ambigü)
grep -r "LocalDateTime" backend/src/main/java
```

### 4.2 Vérifier colonnes BDD

**Migration Flyway :**

```bash
grep -r "TIMESTAMP\|TIMESTAMPTZ\|DATE" backend/src/main/resources/db/migration/
```

**À vérifier dans chaque migration :**

- `created_at TIMESTAMPTZ` ✅
- `updated_at TIMESTAMPTZ` ✅
- `date_naissance DATE` ✅
- Pas de `TIMESTAMP` simple (ambigu sans TZ)

### 4.3 Configuration Jackson

Vérifier `application.yml` :

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false  # ✅ ISO-8601
    time-zone: UTC                      # ✅ présent
```

### 4.4 Tests

```java
@Test
void testCreatedAtSerialization() {
    Patient p = new Patient();
    p.setCreatedAt(OffsetDateTime.of(2026, 5, 3, 10, 30, 0, 0, ZoneOffset.UTC));
    
    String json = objectMapper.writeValueAsString(p);
    assertTrue(json.contains("2026-05-03T10:30:00Z"));
}
```

### Artefacts à produire (Phase 4)

1. ✅ Aucun java.util.Date ni LocalDateTime ambigu
2. ✅ Tests de sérialisation JSON
3. ✅ Rapport : "Dates management standardized"

---

## PHASE 5 — Endpoints REST ⏱️ Estimée : 3-4h

### 5.1 Audit paramètres

```bash
grep -n "@RequestParam" backend/src/main/java/com/hemodialyse/backend/application/web/*.java
```

**Endpoints avec > 3 @RequestParam :**

- PatientRestController.search() ← À refactoriser
- PecRestController.search() ← À refactoriser

### 5.2 Refactoriser EndPoints

**Avant :**

```java

@GetMapping("/patients")
public Page<PatientDto> search(
        @RequestParam String nom,
        @RequestParam String prenom,
        @RequestParam String email,
        @RequestParam UserStatus status,
        @RequestParam int page
) {
}
```

**Après :**

```java
@PostMapping("/patients/search")
public Page<PatientDto> search(@RequestBody @Valid PatientSearchCriteria criteria) {}

public record PatientSearchCriteria(
    @NotBlank String nom,
    String prenom,
    @Email String email,
    UserStatus status,
    @Min(0) int page,
    @Min(1) @Max(100) int size
) {}
```

### 5.3 Valider tous les @RequestBody

```bash
grep -B3 "@RequestBody" backend/src/main/java/com/hemodialyse/backend/application/web/*.java | grep -v "@Valid"
```

**Action :** Ajouter `@Valid` manquant

### 5.4 Pathvariables typées

```bash
grep "@PathVariable String" backend/src/main/java
```

**À remplacer :**

- `@PathVariable String centerId` → `@PathVariable UUID centerId`
- `@PathVariable String patientId` → `@PathVariable UUID patientId`

### 5.5 GlobalExceptionHandler

Vérifier `ApiExceptionHandler.java` gère :

- [ ] MethodArgumentNotValidException → 400 + liste erreurs
- [ ] EntityNotFoundException → 404
- [ ] Exception générique → 500 (no stack trace!)

### Artefacts à produire (Phase 5)

1. ✅ Tous endpoints refactorisés
2. ✅ DTOs SearchCriteria créés
3. ✅ @Valid ajoutés
4. ✅ Exception handler complet
5. ✅ Rapport : "REST endpoints refactored"

---

## PHASE 6 — Sécurité (CRITIQUE) ⏱️ Estimée : 2-3h

### 6.1 Tokens Cookies

**Checklist :**

- [x] HttpOnly = true
- [x] Secure = true
- [x] SameSite = Strict
- [ ] Refresh token path = `/auth/refresh` (à corriger)
- [x] Token jamais en body login response

**Action :**

```java
// Amélioration
if(refreshToken){
refreshCookie =

buildRefreshCookie(refreshCookieName, refreshToken, refreshCookieMaxAgeSec)
        .

path("/auth/refresh")  // ← Restreindre
        .

build();
}
```

### 6.2 CSRF Protection

Vérifier avec SameSite=Strict (natif cross-site protection).

Si besoin CSRF token explicite (Si SameSite=Lax) :

```java
http.csrf(csrf ->csrf
        .

csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        );
```

### 6.3 Content Security Policy

Ajouter header CSP en SecurityConfig :

```java
http.headers(headers ->headers
        .

contentSecurityPolicy(csp ->csp.

policyDirectives(
        "default-src 'self'; "+
                "script-src 'self'; "+
                "style-src 'self' 'unsafe-inline'; "+
                "img-src 'self' data: https:; "+
                "connect-src 'self' http://localhost:4200; "+
                "frame-ancestors 'none'"
))
        );
```

### 6.4 Headers de sécurité

```java
http.headers(headers ->headers
        .

xssProtection()
    .

and()
    .

contentTypeOptions()
    .

and()
    .

referrerPolicy(referrer ->referrer.

policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
        .

and()
    .

frameOptions().

deny()
);
```

### 6.5 Validation d'entrée

Checklist :

- [x] @Valid présent sur @RequestBody
- [x] Annotations de validation (@NotNull, @Email, @Size, etc.)
- [ ] Messages de validation personnalisés (à ajouter)

### 6.6 Exception Handler sécurisé

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} from {}", request.getRequestURI(), 
        getClientIp(request), ex);
    // Jamais ex.getMessage() en réponse
    return ResponseEntity.status(500)
        .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
}
```

### 6.7 Dépendances vulnérables

```bash
mvn dependency:check
npm audit
```

**À faire :** Corriger toutes les CVE HIGH/CRITICAL

### Artefacts à produire (Phase 6)

1. ✅ Refresh token path restreint
2. ✅ CSP headers ajoutés
3. ✅ Exception handler sécurisé
4. ✅ Validation injection: mvn check + npm audit
5. ✅ Rapport : "Security hardening completed"

---

## PHASE 7 — Code Mort ⏱️ Estimée : 2h

### 7.1 Backend — Analyse code mort

```bash
# Utiliser IntelliJ : Analyze → Run Inspection by Name → Unused declarations
# Ou SonarQube
```

**Checklist :**

- [ ] Supprimer @Deprecated sans utilisation
- [ ] Supprimer variables non lues
- [ ] Supprimer méthodes privées non appelées
- [ ] Supprimer DTOs orphelins
- [ ] Supprimer imports inutilisés

### 7.2 Frontend — ts-prune

```bash
cd frontend
npx ts-prune
```

**Checklist :**

- [ ] Supprimer exports non utilisés
- [ ] Supprimer composants orphelins
- [ ] Supprimer services non injectés
- [ ] Supprimer actions/reducers NgRx non référencés
- [ ] Supprimer console.log de debug

### 7.3 Lombok — Audit

```bash
grep -r "@Data\|@Getter\|@Setter" backend/src/main/java
```

**Décision :**

- Garder si très répandu (simplifie classe)
- Supprimer si peu utilisé (favorise lisibilité)

### 7.4 application.yml — Propriétés orphelines

```bash
grep "app:" backend/src/main/resources/application.yml | while read line; do
    key=$(echo "$line" | cut -d: -f1)
    grep -r "$key" backend/src/main/java || echo "ORPHAN: $key"
done
```

### Artefacts à produire (Phase 7)

1. ✅ Aucun export TypeScript non utilisé
2. ✅ Aucun code commenté
3. ✅ Rapport : "Dead code eliminated"

---

## PHASE 8 — Qualité générale ⏱️ Estimée : 2h

### 8.1 Nommage

**Conventions à vérifier :**

| Type            | Pattern           | Exemple                        |
|-----------------|-------------------|--------------------------------|
| Use Case        | Verbe + "UseCase" | CreatePatientUseCase           |
| VO              | Substantif        | Email, Money, PatientId        |
| Boolean method  | is/has/can/should | isActive(), hasRole()          |
| Mutation method | Intention métier  | activate(), cancel()           |
| Event           | Passé composé     | PatientCreated, OrderCancelled |

### 8.2 Tests couverture

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

**Cibles :**

- Domain: > 80% coverage
- Application: > 70% coverage
- Infrastructure: > 50% coverage

### 8.3 Documentation

**Checklist :**

- [ ] Chaque classe a un javadoc court
- [ ] Chaque enum explique ses valeurs
- [ ] Ports (interfaces) expliquent contrat

### 8.4 Lint Frontend

```bash
npm run lint
npx prettier --write src/
```

### Artefacts à produire (Phase 8)

1. ✅ Nommage cohérent appliqué
2. ✅ Coverage > 70% domain
3. ✅ Lint frontend 0 warnings
4. ✅ Rapport : "Code quality standards met"

---

## PHASE 9 — Livrable Final ⏱️ Estimée : 1h

### 9.1 Rapport d'audit complet

**Contenu :**

```
AUDIT | Audit & Refactorisation Complète
├─ Phase 0: État initial, stack, violations
├─ Phase 1: Architecture hexagonale corrigée
├─ Phase 2: DDD renforcé (VOs, Aggregates)
├─ Phase 3: SOLID appliqué (SRP, OCP, ISP)
├─ Phase 4: Dates standardisées
├─ Phase 5: Endpoints REST refactorisés
├─ Phase 6: Sécurité renforcée
├─ Phase 7: Code mort éliminé
├─ Phase 8: Qualité générale atteinte
└─ Recommandations futures
```

### 9.2 Code refactorisé

**Branches Git :**

```bash
git checkout -b refactor/phase-0-audit
git checkout -b refactor/phase-1-hex
git checkout -b refactor/phase-2-ddd
...
git checkout -b refactor/phase-9-final
```

### 9.3 Tests tous passants

```bash
mvn clean test -DskipITs
npm run test
```

### 9.4 Checklist finale

- [ ] Aucune régression
- [ ] Tous tests passants
- [ ] Coverage maintenu/amélioré
- [ ] Lint clean
- [ ] Documentation mise à jour
- [ ] PR/MR prête pour merge

### Artefacts à produire (Phase 9)

1. ✅ Rapport d'audit final (MD)
2. ✅ Diffs bien commentées
3. ✅ Tous tests passants
4. ✅ Code prêt pour production

---

## TIMELINE RÉSUMÉE

| Phase | Titre      | Durée  | Total  |
|-------|------------|--------|--------|
| 0     | Audit      | ✅ DONE | ✅ 2h   |
| 1     | Hexagonale | 2-3h   | 2-3h   |
| 2     | DDD        | 3-4h   | 5-7h   |
| 3     | SOLID      | 2-3h   | 7-10h  |
| 4     | Dates      | 1-2h   | 8-12h  |
| 5     | REST       | 3-4h   | 11-16h |
| 6     | Sécurité   | 2-3h   | 13-19h |
| 7     | Code mort  | 2h     | 15-21h |
| 8     | QA         | 2h     | 17-23h |
| 9     | Livrable   | 1h     | 18-24h |

**Total estimé :** 18-24 heures de travail

---

## DÉPENDANCES ENTRE PHASES

```
Phase 0 (audit)
    ↓
Phase 1 (architecture) — doit être fait en premier
    ↓
Phase 2 (DDD)
    ├→ Phase 3 (SOLID)
    ├→ Phase 4 (dates)
    └→ Phase 5 (REST)
        ↓
    Phase 6 (sécurité) — doit être fait avant prod
    ↓
    Phase 7 (code mort)
    ↓
    Phase 8 (QA)
    ↓
    Phase 9 (livrable)
```

**⚠️ IMPORTANT :** Phase 1 bloc tout le reste. Ne pas paralléliser.

---

**Fin du plan d'exécution**  
*Prêt à débuter Phase 1 à la demande utilisateur.*

