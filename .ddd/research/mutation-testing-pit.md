---
topic: Mutation testing - PIT (pitest) avec Maven et JUnit 5
date: 2026-10-09
source: web (Maven Central metadata, GitHub pitest/pitest-junit5-plugin) - non vérifié par context7
libraryId: org.pitest:pitest-maven 1.30.0 ; org.pitest:pitest-junit5-plugin 1.2.3
---
## Réponses clés
- Dernières versions Maven Central : pitest-maven 1.30.0 ; pitest-junit5-plugin 1.2.3.
- JUnit 5 : déclarer `pitest-junit5-plugin` en `<dependency>` du plugin pitest-maven ; le plugin et PIT doivent évoluer ensemble (versions incompatibles entre elles depuis PIT 1.15.2).
- Si erreur "Minion exited abnormally"/NoClassDefFoundError : ajouter `junit-platform-launcher` explicitement.
- Lancement : `mvn org.pitest:pitest-maven:mutationCoverage` (par module, cibler `targetClasses` = package domaine pour limiter le temps) ; `-DwithHistory` / `scmMutationCoverage` pour l'incrémental.
- Usage pipeline : mesurer la qualité du filet de caractérisation avant d'y fier la migration (score de mutation sur code legacy couvert).

## Extraits
```xml
<plugin><groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId><version>1.30.0</version>
 <dependencies><dependency><groupId>org.pitest</groupId>
  <artifactId>pitest-junit5-plugin</artifactId><version>1.2.3</version></dependency></dependencies>
</plugin>
```

## Écarts / pièges
- Compat Java 21 non confirmée explicitement par les sources ; vérifier par un run pilote. Tests Spring Boot (contexte) rendent PIT très lent : viser les tests unitaires du domaine.
- Mockito (agent inline) peut nécessiter `argLine` ; à tester.

## Questions ouvertes
- Seuil de mutation acceptable par slice (à fixer dans le plan).
