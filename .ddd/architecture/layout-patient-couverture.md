---
status: proposed
date: 2026-10-29
sources: [.ddd/tactical/patient-couverture.md, .ddd/architecture/layout.md, ADR 0004, ADR 0064]
---

# Layout — contexte `patient-couverture` (`backend/contexts/patient-couverture`)

## 1. Module Maven

- Module `contexts-patient-couverture` (artefact `contexts-patient-couverture`), parent `backend-parent`.
- Enregistre dans `backend/pom.xml` ; dépendance `contexts-shared-kernel` et `contexts-organisation`.
- Squelette uniquement : aucune logique métier ; les dépendances propres aux slices s'ajoutent avec elles.

## 2. Arborescence

```
backend/contexts/patient-couverture/
  pom.xml
  src/main/java/com/hemodialyse/backend/contexts/patientcouverture/
    domain/            Patient, Assure, PEC, Attestation, VO (PatientId, AssureId, PECId, AttestationId, EtatPatient, StatutPEC, ...), services purs
    application/       use cases (AdmettrePatient, TransfererPatient, DeclarerSortiePatient, DeclarerGreffe, AffecterAssurePrincipal, ValiderPEC, CloturerPEC, EnregistrerAttestation), ports sortants, read models
    infrastructure/    persistence/ (entites *Jpa, mappers, adapters Repository, read models SQL) ; evenements/ (publication Modulith) ; acl/legacyorganisation/ (lecture centres) ; transaction/ horloge/ identifiants/
    interfaces/        http/ (controleurs REST, DTO) ; consumers/ (listeners)
    publishedlanguage/ v1/ (evenements d'integration patientcouverture.v1 : records Java, sans dependance)
    configuration/     composition root du contexte : @Configuration, @Bean des use cases
  src/test/java/com/hemodialyse/backend/contexts/patientcouverture/
    architecture/      ArchitectureHexagonaleTest
```

## 3. Règles d'architecture

- `domain/` : aucune dépendance Spring/JPA/Jackson/Lombok/SLF4J/Timefold ; identifiants et horloges injectés par ports.
- `application/` : sans framework de persistance/web/json/solveur.
- `publishedlanguage/` : types JDK uniquement, aucune dépendance au contexte ni à Spring.
- Inter-contextes : uniquement via `publishedlanguage` ou shared-kernel.
- Multi-centre : `centerId` obligatoire sur toute donnée, API, cache, WebSocket.
- Cache : `ref.patient.detail`, `ref.pec.active`, `ref.liste.patients` (clés incluant `centerId`).

## 4. Contextes consommateurs / fournisseurs

- Fournisseur OHS pour Facturation, Comptabilite, Seance, Planification, Absences patients, Dossier medical, Pilotage.
- Consomme Organisation (lecture centres cibles) via ACL.
