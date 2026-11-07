---
slice: dossier-medical-dm5-antecedents-allergies
status: built
date: 2026-11-07
order: 5
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065, ADR 0084, .ddd/safety-net/dossier-medical-dm5-antecedents-allergies.md, known-bugs.md KB-MED-58 à 71]
---

> **Refactoring SUR PLACE** (ADR 0024, 0072, 0084) : pas de façade, de routage ni de flag. Mêmes tables (`antecedents_medicaux`, `allergies_patient`, `abords_vasculaires` ; les tables parallèles gelées `antecedents` / `allergies` du module ne sont ni lues ni écrites), mêmes URL, mêmes rôles. Bascule = déploiement du commit ; rollback = retour au commit précédent. Périmètre élargi aux **abords vasculaires** par délégation (à confirmer avec les métiers).

# DM5 — Antécédents, allergies et abords vasculaires

## Fait
| Couche | Contenu |
|---|---|
| Domaine | `Antecedent` (racine : diagnostic ou libellé, période, résolution, correction destructrice reproduite, bornes), `Allergie` (racine : criticité haute exige des manifestations, date jamais future, correction étroite), `AbordVasculaire` (racine : fin pas avant création, remplacement complet, type libre), objet-valeur `CodeClinique`, listes fermées `TypeDAntecedent`, `StatutClinique`, `SystemeDeCodage`, `CategorieDAllergie`, `CriticiteDAllergie`, `TypeDeReaction`, `StatutDeVerification`, ports `Antecedents`, `Allergies`, `AbordsVasculaires`, `PageDeFiches`, refus `AntecedentIntrouvable`, `AllergieIntrouvable`, `AbordVasculaireIntrouvable`, `EcritureConcurrenteSur…` (3) |
| Application | `EnregistrerUnAntecedent`, `CorrigerUnAntecedent`, `ResoudreUnAntecedent`, `SupprimerUnAntecedent`, `ConsulterLesAntecedentsDuPatient` ; `DeclarerUneAllergie`, `CorrigerUneAllergie`, `SupprimerUneAllergie`, `ConsulterLesAllergiesDuPatient` ; `EnregistrerUnAbordVasculaire`, `ModifierUnAbordVasculaire`, `SupprimerUnAbordVasculaire`, `ConsulterLesAbordsVasculairesDuPatient` ; réutilise `PatientsDuCentre`, `SessionCourante`, `UniteDeTravail` (lock_timeout 5 s), `HorlogeDuCentre`, `GenerateurDIdentifiants` |
| Infrastructure | `AntecedentsJdbc`, `AllergiesJdbc`, `AbordsVasculairesJdbc`, `VerrousJdbc` (55P03 vers 409, verrou consultatif du diagnostic) |
| Interface | `AntecedentsControleur` (5 routes), `AllergiesControleur` (5 routes), `AbordsVasculairesControleur` (4 routes), DTO, `ErreursDuDossierMedicalAdvice` étendu (403 avant 400, 404, 409, 400 introuvable) |
| Composition | `DossierMedicalConfiguration` (horloge UTC dédiée `horlogeDesFichesCliniques`) |

## Parité
Filet `characterization/dossiermedical/dm5` : **51/51 scénarios verts sur le nouveau code**, 51 goldens : 19 identiques au legacy, **32 écarts décidés** en goldens `*_nouveau_code` (antécédents 12, allergies 10, abords 10), legacy archivé octet pour octet depuis 531ae309 dans `dm5/{antecedents,allergies,abords}/_ecarts-legacy/`. Une assertion du test d'abords (centre B vide après le PUT) devient « 1 ligne » (KB-MED-62). Aucun golden hors DM5 modifié ; le filet `LesAbordsVasculairesEtLaPlanification…` (compétence CATHETER) et les exports FHIR sont inchangés.

| Écart | Décision |
|---|---|
| KB-MED-58 (500 vers 400 ; 403 avant 400) | corrigé |
| KB-MED-59 (nom d'énumération interne) | corrigé |
| KB-MED-61 (aucune garde de patient : 404 à l'écriture) | corrigé |
| KB-MED-62 (PUT d'abord = upsert et réaffectation : 404) | corrigé |
| Bornes de colonne (500 vers 400), verrous (409), DELETE sans effet sans audit | corrigés |
| Ordre déterministe (abords sans date en dernier sur tous les SGBD) | corrigé, invisible aux goldens H2 |
| KB-MED-60, 63, 64, 65, 66, 67, 68, 69, 70, 71 | reproduits ou laissés, consignés (ADR 0084, `known-bugs.md` « Décisions DM5 ») |
| Lignes illisibles en base | ignorées par les lectures, WARN avec identifiant seul, `verifications-production.md` §49 requête 6 |

## Reste à faire
- Confirmation métiers et DPO des décisions reproduites (liste dans l'ADR 0084, dont : bandeau d'allergies pour l'infirmier, allergie réfutée dans le bandeau, type d'abord libre, audit des lectures, événements non publiés).
- Retrait des résidus Java lus par FHIR (DM8), `GreffePdfService` (DM7), import et rollback : agrégats legacy, ports `*RepositoryPort`, énumérations, adapters, entités JPA ; **revue au plus tard le 2026-12-15**.
- Codes FHIR d'allergie inventés (KB-MED-70) : DM8. Contrainte d'unicité du diagnostic actif en base : non décidée, après relevé des doublons (§49 requête 2).

## Preuves
Domaine (27), application avec fakes (28), contrat par port sur double / H2 / PostgreSQL 16 (9 + 13 + 15, 8 + 12 + 13, 6 + 8 + 9), advice (7 + 7 + 6), ArchUnit du module ; script `backend/scripts/dossier-medical-dm5-pg16-rejeu.sh` (PostgreSQL 16 jetable, port 55475, jamais 5432 : chaîne Flyway, colonnes, absence de contrainte d'unicité, requêtes du §49, contrats avec verrou de ligne et verrou de diagnostic à deux connexions). Les cas d'usage n'écrivent qu'une fiche : pas de test de transaction multi-écritures. `check-arch.ts` 0 violation, `FrontieresDesContextesTest`, `./mvnw -o test -fae` vert.

## Plan de bascule (refactoring sur place, pas de flag)
Recette de `verifications-production.md` §49 avant déploiement ; surveiller les 400 / 403 / 404 / 409 et les WARN « illisible en base » ; rollback = commit précédent. Aucune bascule progressive, aucune activation de production par l'assistant.
