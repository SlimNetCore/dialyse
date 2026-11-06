# GEL du module dossier-medical (2026-11-02) — LEVÉE PARTIELLE le 2026-11-06 : DM1 repris, DM2 à DM8 à venir

> **Levée partielle (ADR 0080)** : DM1 (lecture des constantes) est repris SUR PLACE et le module est de nouveau au réacteur (`backend/pom.xml`, `platform/pom.xml`, `Dockerfile`). Le contenu gelé décrit ci-dessous (modèle parallèle DM2 à DM8 : sérologies, antécédents, allergies, demandes d'examen, observations, bilans pré-greffe, leurs adapters JDBC, contrôleurs et tests) a été RETIRÉ du module : il est conservé dans l'historique git au commit **542c7571** (`git show 542c7571:backend/contexts/dossier-medical/...`). DM2 à DM8 seront repris slice par slice, sur place, après leur filet de caractérisation ; rien de l'ancien contenu n'est acquis. Le tactique `.ddd/tactical/dossier-medical.md` reste périmé pour DM1 (lecture pure, sans agrégat `Constante`) : à réviser par slice. Le script `dossier-medical-pg16-rejeu.sh` est remplacé par `dossier-medical-dm1-pg16-rejeu.sh`.

Le reste de ce fichier est l'état du gel au 2026-11-02, à titre historique.

Statut : **gelé, non branché, hors réacteur Maven** (`backend/pom.xml` ne le liste plus, `backend/Dockerfile` ne copie plus son `pom.xml`, `platform/pom.xml` ne l'a jamais référencé). Le dossier, ses sources, ses tests, ses ADR (0007, 0065, 0067), ses fiches (`.ddd/migration/slices/dossier-medical-*.md`), le tactique (`.ddd/tactical/dossier-medical.md`, `proposed`) et le glossaire sont conservés intacts pour la reprise. Décision de l'utilisateur du 2026-11-02. ADR : 0072.

## Pourquoi
- **Modèle parallèle sans parité** : le module réécrit sérologies, antécédents, allergies, observations biologiques, demandes d'examen et bilans pré-greffe avec son propre modèle, sans reprendre le comportement observable du legacy (`bc-medical`, 16 contrôleurs `platform/.../web/rest`). Aucune caractérisation DM2 à DM8, aucun golden `_nouveau_code`.
- **URL et tables nouvelles, contraires à l'ADR 0024** (refactoring sur place : mêmes tables, mêmes URL) : routes `/api/v1/dossier-medical/*`, tables `serologies`, `antecedents`, `allergies`, `bilans_pregreffe`, `etapes_bilan_pregreffe`, et trois collisions de noms avec des tables réelles (`demandes_examen`, `lignes_demande_examen`, `observations_biologiques`) sans garantie de colonnes.
- **Pas de filet** : seules les constantes (DM1) sont caractérisées sur le legacy (4 goldens).
- Défauts bloquants relevés par `.ddd/verify/verify-dossier-medical.md` : pas de `@PreAuthorize` ni de garde de centre issue de la session (B6), routes absentes du Journal d'audit (B7), pas de transaction ni d'outbox, horloge du domaine (M4).

## Ce qui a été fait au gel
- Revert du code legacy de production de `21f6ff7a` (colonne `center_id` sur `LigneDemandeExamenJpaEntity` / `DemandeExamenRepositoryAdapter`) : la colonne n'existe pas dans Flyway V1..V52, aucun code déployé n'en a besoin. Preuve : `backend/scripts/dossier-medical-pg16-rejeu.sh`.
- 42 entrées sans caractérisation retirées de `.ddd/safety-net/covered.json` (ordonnances, demandes d'examen) : `guard-legacy` protège de nouveau ces fichiers.
- Retrait de `platform/src/main/resources/db/schema.sql` (H2 de dev) des tables `demandes_examen` et `lignes_demande_examen` ajoutées par `2978e639` (créées par Hibernate comme avant).
- `TableDuDossierMedicalTest` désactivé (`@Disabled` motivé). `FrontieresDesContextesTest` : la règle « le legacy n'utilise le dossier médical que par son langage publié » est conservée (vide, `allowEmptyShould`).
- Le service frontend `dossier-medical-context-api.service.ts` (aucun consommateur, routes non servies) est laissé en place mais non utilisable ; à supprimer ou à réaligner à la reprise.

## Plan de reprise
1. **Filet de caractérisation du legacy médical d'abord** (`/ddd-safety-net` par slice) : constantes (DM1, déjà 4 goldens), ordonnances et prescriptions (DM2), demandes d'examen (DM3), sérologie (DM4, avec le flux P5 de Planification), antécédents et allergies (DM5), observations biologiques (DM6), bilans pré-greffe (DM7), puis FHIR / KDIGO / export greffe.
2. **Slices sur place DM1 à DM8** : mêmes tables (`serologies_patient`, `antecedents_medicaux`, `allergies_patient`, `bilans_pre_greffe`, `etapes_bilan_pre_greffe`, `ordonnances`, `prescriptions_medicales`, `demandes_examen`, `lignes_demande_examen`, `observations_biologiques`), mêmes URL `/api/v1/patients/{id}/...`, mêmes rôles, garde de centre, entrées du Journal d'audit, transactions, horloge injectée (fuseau du centre), pas de nouveau schéma.
3. Réécrire la garde de tables sur les tables réelles (platform inclus, liste blanche explicite pour l'ACL de Planification) et rebrancher le module dans `backend/pom.xml`, `platform/pom.xml`, `Dockerfile`.
4. Réviser le tactique (DM1 : lecture du volet de séance, propriétaire ; DM2 : Ordonnance et PrescriptionMedicale) avant tout code.
