---
topic: Approval / golden-master testing en Java (ApprovalTests.Java)
date: 2026-10-09
source: web (GitHub approvals/ApprovalTests.Java, Maven Central) - non vérifié par context7
libraryId: com.approvaltests:approvaltests 31.0.0
---
## Réponses clés
- Dernière version Maven Central : 31.0.0 (30.1.1, 30.1.0 avant).
- `JsonApprovals.verifyAsJson(obj)` écrit/compare `*.approved.json` à côté du test ; `Approvals.verify(String)` pour texte brut. Fichiers `*.received.*` générés en cas d'écart (à ignorer dans git ; committer les `approved`).
- Pour caractériser un legacy : appeler le comportement avec des entrées figées, sérialiser (JSON) la sortie, approuver ; rejouer sur le nouveau code = test de parité.
- Alternatives : snapshot via AssertJ + fichiers JSON maison, ou JSONAssert (plus de code).

## Extraits
```java
@Test void facture_mensuelle() { JsonApprovals.verifyAsJson(service.calculer(cas)); }
```

## Écarts / pièges
- Normaliser les données instables (dates, UUID, ordres de collections) via scrubbers avant verify.
- Le projet est Spring Boot 4/Jackson 3 : ApprovalTests utilise sa propre sérialisation (Jackson 2 en interne probable, non vérifié) ; éviter le mélange sur le classpath.
- Setup JUnit 5 exact (dépendance + config reporter CI) non confirmé : consulter GettingStarted.md du dépôt.

## Questions ouvertes
- Compat Java 21 / reporters en CI headless à valider par un test pilote.
