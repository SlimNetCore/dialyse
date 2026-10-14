---
topic: Tests d'architecture - ArchUnit et Spring Modulith verify()
date: 2026-10-09
source: web (docs.spring.io/spring-modulith verification ; Maven Central) - non vérifié par context7
libraryId: com.tngtech.archunit:archunit-junit5 1.5.1 ; spring-modulith 2.1.1 (doc)
---
## Réponses clés
- ArchUnit : dernière version Maven Central 1.5.1 (1.4.0-1.4.2, 1.5.0 avant). Artefact `com.tngtech.archunit:archunit-junit5`, scope test. Compat Java 21 : non confirmée explicitement par la source (1.x récent, très probable).
- Modulith `ApplicationModules.of(App.class).verify()` (test JUnit ordinaire, s'appuie sur ArchUnit) vérifie : pas de cycles entre modules ; accès aux autres modules seulement via leur package API (pas les sous-packages) ; `allowedDependencies` si déclaré.
- `@NamedInterface` (package-info) expose un sous-package comme API ; `@ApplicationModule(allowedDependencies = "inventory")`.
- Si jMolecules ArchUnit est présent, règles DDD ajoutées auto ; hexagonal : `JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT)` via `VerificationOptions`.
- `detectViolations().filter(..).throwIfPresent()` pour tolérer des violations connues (legacy).

## Extraits
```java
@Test void modules() { ApplicationModules.of(Application.class).verify(); }
```
```java
// ArchUnit : domaine sans dépendance infra
noClasses().that().resideInAPackage("..domain..")
  .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","org.springframework..");
```

## Écarts / pièges
- Modulith suppose des modules = sous-packages d'un package racine ; notre structure Maven multi-modules (bc-*) : un `ApplicationModules.of` par application assemblée, ou ArchUnit sur classpath global. À trancher.
- Règle domaine "sans Spring" : attention aux annotations JPA si le domaine est partagé avec l'entité.

## Questions ouvertes
- Modulith utilisable avec bc-* en modules Maven séparés (classpath scanning) ? Prototype nécessaire.
