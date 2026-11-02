---
status: accepted
date: 2026-11-02
decision-makers: [utilisateur (décision du 2026-11-02, décision déléguée à l'assistant pour l'acceptation formelle), architecte]
context-scope: dossier-medical
related: ADR 0024 (refactoring sur place), 0007, 0065, 0067 ; `.ddd/verify/verify-dossier-medical.md` ; `backend/contexts/dossier-medical/GEL.md`
---

# 0072 - Dossier médical : gel du module parallèle, revert de `center_id`, reprise sur place

## Contexte
La vérification indépendante (`verify-dossier-medical.md`, verdict fail, 7 blockers) a établi que `backend/contexts/dossier-medical` n'a jamais été branché (`platform/pom.xml` ne le référence pas) : 100 % du trafic médical passe par le legacy (`bc-medical`, 16 contrôleurs). Le module crée un modèle parallèle (tables `serologies`, `antecedents`, `allergies`, `bilans_pregreffe`, `etapes_bilan_pregreffe`, routes `/api/v1/dossier-medical/*`), contraire à l'ADR 0024, sans filet, sans contrôle d'accès ni audit. Le commit `21f6ff7a` a en outre ajouté au legacy une colonne JPA `lignes_demande_examen.center_id` absente de Flyway, et `2978e639` a élargi `covered.json` de 42 fichiers sans caractérisation.

## Décision
1. **Geler le module** : retiré du réacteur (`backend/pom.xml`) et du `Dockerfile` ; dossier, ADR, fiches, tactique (`proposed`) et glossaire conservés (`GEL.md`).
2. **Restaurer la production** : revert exact de `LigneDemandeExamenJpaEntity` et `DemandeExamenRepositoryAdapter` à `21f6ff7a^`. Aucune migration : aucun code déployé n'a besoin de `center_id` sur les lignes (la demande parente le porte). Preuve : `backend/scripts/dossier-medical-pg16-rejeu.sh` (PostgreSQL 16 jetable, 14 entités médicales conformes à Flyway V1..V52).
3. **Retirer 42 entrées de `covered.json`** (ordonnances, demandes d'examen) : le hook `guard-legacy` protège de nouveau ces fichiers.
4. **Retirer de la H2 de dev** les tables `demandes_examen` et `lignes_demande_examen` ajoutées à `schema.sql` par `2978e639` (collisions de noms avec les tables réelles).
5. **Désactiver** `TableDuDossierMedicalTest` (motif dans `@Disabled`).
6. **Statuts réels** : DM1 `characterized` ; DM2 à DM8 `planned`.

## Reprise
Filet de caractérisation du legacy médical d'abord, puis slices sur place DM1 à DM8 avec les mêmes tables, les mêmes URL et les mêmes rôles (détail dans `GEL.md`).

## Conséquences
- Plus de risque de démarrage `validate` en échec ni de split brain médical.
- Le travail du module gelé (domaine, tests) reste réutilisable comme matière de réflexion, pas comme code à brancher.
- Les routes `/api/v1/dossier-medical/*` ne sont plus servies ; le service frontend `dossier-medical-context-api.service.ts` n'a aucun consommateur.
