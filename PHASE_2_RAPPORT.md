# ✅ RAPPORT PHASE 2 — DDD (Value Objects)

**Date :** 2026-05-03  
**Statut :** ✅ Complétée  
**Objectif :** Introduire des Value Objects métier pour fiabiliser les données de contact (email/téléphone) sans casser
les contrats REST/JPA.

---

## Corrections appliquées

### 1) Value Objects ajoutés

- `backend/src/main/java/com/hemodialyse/backend/domain/patient/vo/Email.java`
    - Validation format email
    - Normalisation (`trim` + lowercase)
    - Immutabilité via `record`
- `backend/src/main/java/com/hemodialyse/backend/domain/patient/vo/PhoneNumber.java`
    - Validation téléphone (8 à 15 chiffres)
    - Normalisation (`spaces`, `-`, parenthèses, préfixe `00` → `+`)
    - Immutabilité via `record`

### 2) Intégration DDD dans le domaine

- `backend/src/main/java/com/hemodialyse/backend/domain/patient/model/Patient.java`
    - Setters `setEmail`, `setTelPersonnel`, `setTelMobile`, `setTelBureau` passent par les VO
    - Les getters restent en `String` pour préserver la compatibilité actuelle
- `backend/src/main/java/com/hemodialyse/backend/domain/assure/model/Assure.java`
    - Setters téléphones passent par `PhoneNumber`

### 3) Correction de cohérence post-Phase 1

- `backend/src/main/java/com/hemodialyse/backend/application/query/PatientListQueryService.java`
    - Import corrigé vers le nouveau DTO :
    - `com.hemodialyse.backend.infrastructure.web.dto.request.PatientSearchRequest`

### 4) Fiabilisation des tests de contexte

- `backend/src/main/java/com/hemodialyse/backend/infrastructure/config/SeedPasswordInitializer.java`
    - Tolérance ajoutée si `app_user` n’existe pas dans le schéma de test
    - Évite un échec du chargement de contexte (`BackendApplicationTests`)

---

## Tests ajoutés (DDD)

- `backend/src/test/java/com/hemodialyse/backend/domain/patient/vo/EmailTest.java`
- `backend/src/test/java/com/hemodialyse/backend/domain/patient/vo/PhoneNumberTest.java`
- `backend/src/test/java/com/hemodialyse/backend/domain/patient/model/PatientContactValidationTest.java`

Ces tests couvrent :

- Normalisation
- Cas invalides
- Application des VOs via les setters de l’agrégat `Patient`

---

## Vérifications exécutées

### Compilation

- Commande exécutée : `mvnw.cmd clean compile`
- Résultat : ✅ `BUILD SUCCESS`

### Tests ciblés DDD

- Commande exécutée : `mvnw.cmd "-Dtest=EmailTest,PhoneNumberTest,PatientContactValidationTest" test`
- Résultat : ✅ 8 tests, 0 failure

### Suite backend complète

- Commande exécutée : `mvnw.cmd test`
- Résultat : ✅ 11 tests, 0 failure

---

## Décisions d’implémentation

- **Approche incrémentale** : VO introduits côté domaine sans modifier les DTO REST ni entités JPA.
- **Compatibilité conservée** : getters/domain API en `String` maintenus pour limiter l’impact transversal.
- **Validation centralisée** : la règle métier de format est portée par les VO, pas dans les controllers.

---

## Statut des phases

- Phase 0 : ✅
- Phase 1 : ✅
- Phase 2 : ✅

**Prêt pour Phase 3 (SOLID).**

