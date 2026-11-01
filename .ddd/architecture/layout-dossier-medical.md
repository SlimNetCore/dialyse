---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/dossier-medical.md, .ddd/architecture/layout.md, ADR 0007, ADR 0065]
---

# Layout — contexte `dossier-medical` (`backend/contexts/dossier-medical`)

## 1. Module Maven

- Module `contexts-dossier-medical` (artefact `contexts-dossier-medical`), parent `backend-parent`.
- Enregistré dans `backend/pom.xml` ; dépendance `contexts-shared-kernel` (et, à la slice, `contexts-patient-couverture`
  et `contexts-organisation`).
- Squelette uniquement : aucune logique métier ; les dépendances propres aux slices s'ajoutent avec elles.

## 2. Arborescence

```
backend/contexts/dossier-medical/
  pom.xml
  src/main/java/com/hemodialyse/backend/contexts/dossiermedical/
    domain/            Constante, Prescription, DemandeExamen, Serologie, Antecedent, Allergie, ObservationBiologique, BilanPreGreffe, EtapeBilanPreGreffe, AdministrationTraitement, VO, events, ports, specifications, services purs
    application/       use cases (EnregistrerConstante, Prescrire, SignerPrescription, DemanderExamen, SaisirResultat, SaisirSerologie, GererAntecedent, GererAllergie, SaisirObservation, ConduireBilanPreGreffe, …), ports sortants, read models
    infrastructure/    persistence/ (entités *Jpa, mappers, adapters Repository, read models SQL) ; evenements/ (publication Modulith) ; acl/ (legacyorganisation, legacypatient, legacyseance) ; transaction/ horloge/ identifiants/
    interfaces/        http/ (contrôleurs REST, DTO) ; consumers/ (listeners)
    publishedlanguage/ v1/ (événements d'intégration dossiermedical.v1 : records Java, sans dépendance)
    configuration/     composition root du contexte : @Configuration, @Bean des use cases
  src/test/java/com/hemodialyse/backend/contexts/dossiermedical/
    architecture/      ArchitectureHexagonaleTest
```

## 3. Règles d'architecture

- `domain/` : aucune dépendance Spring/JPA/Jackson/Lombok/SLF4J/Timefold ; identifiants et horloges injectés par ports.
- `application/` : sans framework de persistance/web/json/solveur.
- `publishedlanguage/` : types JDK uniquement, aucune dépendance au contexte ni à Spring.
- Inter-contextes : uniquement via `publishedlanguage` ou shared-kernel.
- Multi-centre : `centerId` obligatoire sur toute donnée, API, cache, WebSocket.
- Données de santé : cloisonnement d'accès par centre ; aucune fuite inter-centres.
- Cache : `ref.dossier.constant`, `ref.dossier.prescriptions.actives` (clés incluant `centerId`).

## 4. Contextes consommateurs / fournisseurs

- Fournisseur OHS pour Seance, Facturation, Planification, Pilotage (ports publiés `RisquesInfectieux`,
  `AbordsVasculaires`).
- Consomme Patient & couverture (identité patient) et Organisation (centres) via ACL.