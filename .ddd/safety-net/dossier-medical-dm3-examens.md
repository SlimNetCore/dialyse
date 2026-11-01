---
slice: dossier-medical-dm3-examens
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/dossier-medical-dm3-examens.md, .ddd/tactical/dossier-medical.md, ADR 0065]
---

# Filet de caractérisation — DM3 Demandes d'examen et résultats

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL des tables `demandes_examen` / `lignes_demande_examen`. Aucun mock du code caractérisé.
Les lectures/écritures passent par `DemandeExamenRestController`, `DemandeExamenDomainService` et
`DemandeExamenUseCase`,
avec isolation multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/bc-medical/src/test/java/.../domain/medical/examen/service/DemandeExamenDomainServiceTest` (logique domaine,
  statuts, transitions) ;
- `backend/bc-medical/src/test/java/.../domain/medical/examen/specification/TransitionStatutDemandeSpecificationTest`
  (machine à états) ;
- intégration REST `DemandeExamenRestController` (à caractériser si absente — multi-centre, pagination).

## Scénarios verrouillés (multi-centre)

- Création d'une demande d'examen (`POST`) rattachée à un `center_id` et à un patient valide ; statut initial `DEMANDE`.
- Transitions : `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE`, avec `ANNULE` terminal ; transitions invalides
  rejetées (`TransitionStatutDemandeSpecification`).
- Validation (`valider`) avec conclusion ; annulation (`annuler`).
- Isolation inter-centres : une demande du centre A n'apparaît pas pour le centre B ; `centerId` systématiquement
  propagé dans `DemandeExamenUseCase`/`DemandeExamenRepositoryPort`.
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.
- Cache (le cas échéant) invalidé par `center_id` ; pas de fuite inter-centres.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par les tests domaine et REST existants.
Aucun mock du code caractérisé.

## Points d'attention

- Statuts : `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE`, avec `ANNULE` terminal.
- Catégories : `BIOLOGIE`, `IMAGERIE`, `FONCTIONNEL`, `ANAPATH`.
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-DM-0xx (voir `docs/reference/regles-de-gestion/`).
- Couverture `centerId` obligatoire sur toute lecture/écriture (règle multi-centre §2).