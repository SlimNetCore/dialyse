---
slice: dossier-medical-dm6-biologie
status: built
date: 2026-11-07
order: 6
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065, ADR 0085, .ddd/safety-net/dossier-medical-dm6-biologie.md, known-bugs.md KB-MED-72 à 84]
---

> **Refactoring SUR PLACE** (ADR 0024, 0072, 0085) : pas de façade, de routage ni de flag. Mêmes tables (`observations_biologiques`, `resultats_analyses` ; les tables du module gelé ne sont ni lues ni écrites ; `lignes_demande_examen` n'a pas de `center_id` et n'en reçoit pas), mêmes URL (`observations`, `demandes-examen/{id}/observations`, `analyses`, `analyses/search`), mêmes rôles. Bascule = déploiement du commit ; rollback = retour au commit précédent. Deux modèles coexistent sans projection (observations LOINC, bilan à colonnes fixes).

# DM6 — Biologie : observations biologiques et analyses

## Fait
| Couche | Contenu |
|---|---|
| Domaine | `ObservationBiologique` (racine : LOINC, valeur numérique avec unité XOR texte, date jamais future, correction qui passe à `CORRIGE`, dérivée immuable, bornes), `Analyse` (racine : date par défaut le jour, remplacement complet qui réinitialise la création, bornes), objets-valeur `ValeurMesuree`, `MesuresDuBilan`, `CodeClinique`, listes fermées `StatutDObservation`, `SourceDObservation`, ports `ObservationsBiologiques`, `Analyses`, `PageDeFiches`, refus `ObservationIntrouvable`, `AnalyseIntrouvable`, `EcritureConcurrenteSurUneObservation`, `EcritureConcurrenteSurUneAnalyse` |
| Application | `EnregistrerUneObservationBiologique`, `CorrigerUneObservationBiologique`, `SupprimerUneObservationBiologique`, `ConsulterLesObservationsBiologiquesDuPatient` (liste et par demande) ; `EnregistrerUneAnalyse`, `ModifierUneAnalyse`, `SupprimerUneAnalyse`, `ConsulterLesAnalysesDuPatient` (liste et recherche) ; port `DemandesDuPatient`, `BornesDePage` ; réutilise `PatientsDuCentre`, `SessionCourante`, `UniteDeTravail` (lock_timeout 5 s), `HorlogeDuCentre`, `GenerateurDIdentifiants` |
| Infrastructure | `ObservationsBiologiquesJdbc`, `AnalysesJdbc`, `DemandesDuPatientJdbc`, `VerrousJdbc` (55P03 vers 409) |
| Interface | `ObservationsBiologiquesControleur` (5 routes), `AnalysesControleur` (5 routes), DTO, `ErreursDuDossierMedicalAdvice` étendu (409, 404 analyse, 400 observation introuvable, 403 avant 400) |
| Composition | `DossierMedicalConfiguration` (horloge UTC dédiée `horlogeDeLaBiologie`) |

## Parité
Filet `characterization/dossiermedical/dm6` : **37/37 scénarios verts sur le nouveau code**, 37 goldens : 14 identiques au legacy, **23 écarts décidés** en goldens `*_nouveau_code` (observations 12, analyses 11), legacy archivé octet pour octet depuis 19b797f2 dans `dm6/{observations,analyses}/_ecarts-legacy/`. Aucun golden hors DM6 modifié ; les goldens des lecteurs (suivi de l'anémie, KDIGO, statistiques, CSV, FHIR), des formulaires et des profils de droits sont inchangés.

| Écart | Décision |
|---|---|
| KB-MED-72 (500 vers 400 ; 403 avant 400 ; bornes de colonne en 400) | corrigé |
| KB-MED-73 (aucune garde de patient : 404 à l'écriture) | corrigé |
| KB-MED-74 (PUT d'analyse = upsert et réaffectation : 404) | corrigé |
| KB-MED-75 (DELETE sans effet audité) | corrigé en audit seulement, reste 204 |
| KB-MED-77 et KB-MED-44 (lecture par demande sans contrôle du patient) | corrigé (liste vide si la demande n'est pas du patient et du centre) |
| Verrous (409), ordre déterministe, lignes d'observation illisibles ignorées + WARN | corrigés |
| KB-MED-76, 78, 79, 80, 81, 83, 84 | reproduits, consignés (ADR 0085, `known-bugs.md` « Décisions DM6 ») |
| KB-MED-82 (lecteurs) | protégés, inchangés |

## Reste à faire
- Confirmation métiers et DPO des décisions reproduites (liste dans l'ADR 0085, dont : contrôle de la demande à l'écriture, plausibilité du bilan, projection du bilan vers les observations, audit des lectures, 404 d'une observation introuvable).
- **Retrait des résidus Java lus par FHIR (DM8) et KDIGO (DM7)** : agrégat legacy `ObservationBiologique`, `ResultatAnalyse`, les deux `*RepositoryPort`, les deux adapters, entités et dépôts JPA, spécifications ; **liste nominative dans l'ADR 0085, revue au plus tard le 2026-12-15**.
- Nettoyage des observations illisibles en base : décision métiers et DBA avant DM7 (`verifications-production.md` §50 requête 6).

## Preuves
Domaine (17), application avec fakes (17), contrat par port sur double / H2 (10 + 15 pour les observations, 8 + 11 pour les analyses, +1 `DemandesDuPatient`), PostgreSQL 16 (2 classes, verrou de ligne à deux connexions), advice (6 + 6), ArchUnit du module ; script `backend/scripts/dossier-medical-dm6-pg16-rejeu.sh` (PostgreSQL 16 jetable, port 55476, jamais 5432 : chaîne Flyway, colonnes, absence de contrainte d'unicité, requêtes du §50, contrats). Les cas d'usage n'écrivent qu'une fiche : pas de test de transaction multi-écritures. `check-arch.ts` 0 violation, `FrontieresDesContextesTest`, `./mvnw -o test -fae` vert.

## Plan de bascule (refactoring sur place, pas de flag)
Recette de `verifications-production.md` §50 avant déploiement ; surveiller les 400 / 403 / 404 / 409 et les WARN « illisible en base » ; rollback = commit précédent. Aucune bascule progressive, aucune activation de production par l'assistant. État du flag : aucun.
