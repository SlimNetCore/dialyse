---
status: proposed
date: 2026-10-23
deciders: utilisateur (décisions du 2026-10-23) ; réalise le modèle tactique `.ddd/tactical/absences-patients.md` § 10 (slice A4)
---

# ADR 0041 — Absences de patients : lectures publiées du pilotage (A4), écarts corrigés et fin du legacy (A5 soldé)

## Contexte

Après A1 à A3, seules quatre requêtes SQL lisaient encore `absence_patient` hors du contexte : le tableau de bord des séances d'un
centre (nombre du mois, détail de la branche absence) dans `SeanceRestController`, et la vue Direction (cumul par centre, statut et
motif ; mensuel) dans `DirectionAbsencesQueryService`. Le filet A4 (7 tests, 9 goldens) complète A1.

## Décisions

1. **Langage publié `AbsencesDuPilotage`** (`contexts/absences-patients/publishedlanguage`) : `aujourdhui()`, `nombreHorsAnnulees`,
   `horsAnnulees`, `cumulParCentre`, `mensuel`. Adaptateur `AbsencesDuPilotageJdbc` dans le module, SQL repris à l'identique. Contrat
   `AbsencesDuPilotageContrat` passé par le double en mémoire, H2 et PostgreSQL 16. Aucune isolation de société ni de centre dans le
   port : l'appelant fournit les centres autorisés (comme `SyntheseFinanciereDesCentres`).
2. **Seam accordé** (utilisateur, 2026-10-23, `seams.md`, `seamAllowed`) : un paramètre de constructeur `AbsencesDuPilotage` dans les deux
   classes legacy. Les décisions ci-dessous imposent en outre quatre retouches ponctuelles dans les mêmes fichiers, et un fichier
   nouveau (`DatesIllisiblesDeLaDirectionAdvice`) : **à valider**.
3. **KB-ABS-34 corrigé** : « aujourd'hui » du pilotage = jour du fuseau des centres (`app.absences.fuseau-par-defaut`, Africa/Algiers).
4. **KB-ABS-35 corrigé** : aucune absence postérieure à aujourd'hui n'est comptée, `periodEnd >= periodStart` ; **même règle pour la Direction** (décision du 23/10, suite à la revue) : cumul et mensuel s'arrêtent à aujourd'hui quel que soit `to`. Les absences à date future héritées d'avant A2, s'il en existe, ne sont plus comptées : requête de recette SELECT-only en `verifications-production.md` § 26.
5. **KB-ABS-33 corrigé** : 400 `BAD_REQUEST` pour `from`/`to` illisibles de `GET /api/v1/direction/absences` seulement (conseil `infrastructure/web/DatesIllisiblesDeLaDirectionAdvice`, route reconnue par son chemin ; toute autre route délègue à `ApiExceptionHandler`, 500 inchangé et testé).
6. **KB-ABS-36 corrigé** : 400 pour un format d'export inconnu (`csv`, `pdf`, `xlsx` reconnus sans égard à la casse).
7. **A5 soldé** : après A3 et A4 le legacy ne contient plus aucun code d'absence de patient. `TableDesAbsencesDePatientsTest` interdit
   toute mention de `absence_patient` dans les sources de `platform`, `shared-kernel` et des anciens `bc-*` ; `FrontieresDesContextesTest`
   interdit les couches internes du contexte. Restent hors périmètre : les migrations Flyway et `schema.sql` (la table appartient au
   contexte), les absences d'infirmiers (autre contexte).

## Conséquences

- Écarts décidés : goldens `*_nouveau_code` (4), comportement legacy conservé dans `.../consultation/_ecarts-legacy/lectures-publiees-a4/*.legacy.json` (non rejoué).
- Le port exposera des identifiants de patients dans `horsAnnulees` (détail d'un centre, nécessaire au tableau de bord) : le contrôle de
  centre reste dans `SeanceRestController` (`requireCenter`, KB-ABS-05).
- Mutualisation du fuseau : `AbsencesDuPilotageJdbc` reçoit la même propriété que `FuseauParDefaut` ; une colonne de fuseau par centre
  (non décidée) rendra `aujourdhui()` à argument de centre pour le tableau de bord (la Direction, multi-centres, gardera le fuseau par défaut).
- Reste A6 (clôture mensuelle) : remplace `TousLesMoisSontOuverts`.
