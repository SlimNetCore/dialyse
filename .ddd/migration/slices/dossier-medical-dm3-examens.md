---
slice: dossier-medical-dm3-examens
status: built
date: 2026-11-07
order: 3
context: dossier-medical
sources: [.ddd/safety-net/dossier-medical-dm3-examens.md, .ddd/architecture/adr/0082-dossier-medical-dm3-reprise-sur-place-des-demandes-d-examen.md, ADR 0024, 0072, 0080, 0081, known-bugs.md KB-MED-34 à 44]
---

# DM3 — Demandes d'examen (refactoring SUR PLACE, ADR 0024)

Pas de façade, de routage, de flag ni de plan de bascule par paliers : mêmes tables (`demandes_examen`, `lignes_demande_examen` SANS `center_id`), mêmes URL, mêmes rôles. Déploiement = remplacement du code ; **rollback = revenir au commit précédent** (aucune migration, aucune donnée modifiée).

## Fait
| Couche | Contenu (module `backend/contexts/dossier-medical`) |
|---|---|
| Domaine | `DemandeExamen` (racine), `LigneDeDemandeDExamen`, `CodeDExamen`, `CategorieDExamen`, `StatutDeDemandeDExamen`, port `DemandesDExamen`, `PageDeDemandesDExamen`, `DemandeExamenIntrouvable`, `EcritureConcurrenteSurUneDemandeDExamen` |
| Application | `DemanderUnExamen`, `EnregistrerLePrelevement`, `RendreLeResultatDisponible`, `ValiderUneDemandeDExamen`, `AnnulerUneDemandeDExamen`, `ConsulterLesDemandesDExamenDuPatient` |
| Infrastructure | `DemandesDExamenJdbc` (tables réelles), réutilisation de `PatientsDuCentreDeLaFiche`, `UniteDeTravailSpring` (lock_timeout 5 s) |
| Interface | `DemandesDExamenControleur` + DTO (`DemandeDExamenDemande`, `TransitionDeDemandeDExamen`, `DemandeDExamenReponse`, `PageDeDemandesDExamenReponse`) ; `ErreursDuDossierMedicalAdvice` étendu |
| Composition | `DossierMedicalConfiguration` (section DM3, bean `horlogeDesDemandesDExamen`) |

Tests du module : domaine (`DemandeExamenTest`, 10 : toutes les transitions depuis chaque statut), application avec fakes (`DemandesDExamenTest`, 10), transaction (`DemandeEtLignesTransactionTest`, 2, H2), contrat du port (double 4, H2 7, PostgreSQL 16 7 : `DemandesDExamenPostgresContractIT`, script `backend/scripts/dossier-medical-dm3-pg16-rejeu.sh`, vert).

## Parité avec le filet de caractérisation (33 scénarios)
33/33 verts sur le nouveau code. 21 scénarios ont tous leurs goldens inchangés (parité stricte). 12 scénarios portent 13 goldens avec un écart DÉCIDÉ (nommés `*_nouveau_code`, legacy archivé octet pour octet depuis 84075712 dans `src/test/resources/characterization/dossiermedical/dm3/_ecarts-legacy/**/*.legacy.json`) :

| Golden | Écart | KB |
|---|---|---|
| creation `dates`, `categories`, `erreurs` (lignes, tailles, illisibles), `reponses` (facultatifs) | 500 devenu 400 (date, catégorie, lignes, `urgent`, corps, identifiants, textes trop longs) | 35 |
| creation `reponses` et `demandes` (patient inconnu) | 200 devenu 404 `PATIENT_INTROUVABLE`, aucune demande orpheline | 38 |
| droits `refus` | 500 devenu 403 (profil sans droit, avant lecture du corps) ou 400 (ADMIN) | 35 |
| liste `bornes`, `listes_vides` | 500 devenu 400 (pagination non numérique, `centerId`, identifiant illisible) | 35 |
| transitions `erreurs` | 500 devenu 400 (corps, identifiants) ; routes inexistantes : 500 reproduit (gestionnaire global) | 35 |
| transitions `donnees_incoherentes` | la liste d'un patient avec une donnée incohérente rend 200 au lieu de 400 | 39 |

## Reproduit (à confirmer)
ADMIN sans écriture (40) ; LOINC / conclusion / prescripteur non contrôlés, pas d'idempotence (42) ; aucun événement, « résultat disponible » manuel (37) ; audit `PATIENTS_*`, lectures non tracées (43) ; « introuvable » = 400 (39) ; jour UTC de la JVM (34).

## Legacy retiré
Voir `seams.md` (section DM3) et ADR 0082 §8 ; `seamAllowed` refermé, entrées mortes de `covered.json` retirées.

## Reste à faire
- Aucune dans le périmètre. Hors périmètre : `GET demandes-examen/{id}/observations` (DM6, KB-MED-44), routes inexistantes en 500 (gestionnaire global), décisions métier de l'ADR 0082 « À confirmer ».
- `/ddd-verify` pour la slice.
