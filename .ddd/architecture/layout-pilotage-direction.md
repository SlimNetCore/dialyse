---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/pilotage-direction.md, .ddd/architecture/layout.md, ADR 0009, ADR 0013, ADR 0066]
---

# Layout — contexte `pilotage-direction` (`backend/contexts/pilotage-direction`)

## 1. Module Maven

- Module `contexts-pilotage-direction` (artefact `contexts-pilotage-direction`), parent `backend-parent`.
- Enregistré dans `backend/pom.xml` ; dépendances `contexts-shared-kernel` (CentreId, SocieteId, Mois) et, par langage
  publié uniquement, les contextes amont (Facturation pour le verrou de clôture, …) lors des slices.
- Squelette uniquement : aucune logique métier ; les dépendances propres aux slices s'ajoutent avec elles.

## 2. Arborescence

```
backend/contexts/pilotage-direction/
  pom.xml
  src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/
    domain/            InstantaneMensuel, AlerteDirection, TableauDeBord, ExportSynthese, VO (InstantaneId, AlerteId, ExportId, TypeAlerte), events, ports, services purs
    application/       use cases (CapturerInstantaneMensuel, GenererAlerteDirection, MarquerAlerteLue, ExporterSynthese, ConsultationTableauDeBord), ports sortants, read models
    infrastructure/    persistence/ (entites *Jpa, mappers, adapters Repository, read models SQL) ; evenements/ (publication Modulith) ; acl/<contexte amont>/ (lecture langages publies) ; transaction/ horloge/ identifiants/
    interfaces/        http/ (controleurs REST, DTO) ; consumers/ (listeners) ; jobs/ (capture mensuelle, generation alertes)
    publishedlanguage/ v1/ (evenements d'integration pilotagedirection.v1 : records Java, sans dependance)
    configuration/     composition root du contexte : @Configuration, @Bean des use cases
  src/test/java/com/hemodialyse/backend/contexts/pilotagedirection/
    architecture/      ArchitectureHexagonaleTest
```

## 3. Règles d'architecture

- `domain/` : aucune dépendance Spring/JPA/Jackson/Lombok/SLF4J/Timefold ; identifiants et horloges injectés par ports.
- `application/` : sans framework de persistance/web/json/solveur.
- `publishedlanguage/` : types JDK uniquement, aucune dépendance au contexte ni à Spring.
- Inter-contextes : uniquement via `publishedlanguage` ou shared-kernel (conformiste en lecture, R4).
- Multi-centre : `centerId` obligatoire sur toute donnée, API, cache, WebSocket.
- Cache : `ref.instants.mois` (PT3M, clé centerId + mois + annee), `ref.alertes.centre` (PT3M, clé centerId).

## 4. Contextes consommateurs / fournisseurs

- Fournisseur OHS pour Reporting (synthèse PDF/HTML via ModeleDocumentPrinter) et l'UI direction.
- Consomme (lecture uniquement, via ACL/langages publiés) : Facturation (verrou mois clôture), Seance, Absences
  patients, Stock, GMAO, Planification, Patient & couverture, Dossier médical, Organisation. Ne produit aucune
  écriture cross-context (R4).

## 5. Reporting

- Tout export de synthèse est un modèle de document (catalogue, provisionnement par centre, impression via
  `ModeleDocumentPrinter`), cf. §11.1 AGENTS.md.