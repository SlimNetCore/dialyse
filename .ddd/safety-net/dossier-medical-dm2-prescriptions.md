---
slice: dossier-medical-dm2-prescriptions
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/dossier-medical-dm2-prescriptions.md, .ddd/tactical/dossier-medical.md, ADR 0065]
---

# Filet de caractérisation — DM2 Prescriptions (ordonnances)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL des tables `ordonnances` / `lignes_ordonnance`. Aucun mock du code caractérisé.
Les lectures/écritures passent par `OrdonnanceRestController`, `OrdonnanceDomainService` et `OrdonnanceUseCase`,
avec isolation multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/bc-medical/src/test/java/.../domain/medical/ordonnance/service/OrdonnanceDomainServiceTest` (logique domaine,
  statuts, transitions) ;
-
`backend/bc-medical/src/test/java/.../domain/medical/ordonnance/specification/TransitionStatutOrdonnanceSpecificationTest`
(machine à états) ;
- intégration REST `OrdonnanceRestController` (à caractériser si absente — multi-centre, pagination).

## Scénarios verrouillés (multi-centre)

- Création d'une ordonnance (`POST`) rattachée à un `center_id` et à un patient valide ; statut initial `BROUILLON`.
- Signature (`SIGNEE`) : attribution d'un numéro séquentiel par centre ; ordonnance verrouillée (plus de modification
  des lignes).
- Marquage `IMPRIMEE` puis `ANNULEE` ; transitions invalides rejetées (`OrdonnanceDejaSignee`,
  `PrescriptionSurOrdonnanceVerrouillee`).
- Prescription liée à une ordonnance signée (R3) : refus si ordonnance non signée.
- Isolation inter-centres : une ordonnance du centre A n'apparaît pas pour le centre B ; `centerId` systématiquement
  propagé dans `OrdonnanceUseCase`/`OrdonnanceRepositoryPort`.
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.
- Cache (le cas échéant) invalidé par `center_id` ; pas de fuite inter-centres.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par les tests domaine et REST existants.
Aucun mock du code caractérisé.

## Points d'attention

- Statuts : `BROUILLON → SIGNEE → IMPRIMEE`, avec `ANNULEE` terminal ; une ordonnance signée est verrouillée (annuler
  puis recréer).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-DM-0xx (voir `docs/reference/regles-de-gestion/`).
- Couverture `centerId` obligatoire sur toute lecture/écriture (règle multi-centre §2).