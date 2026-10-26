---
slice: planification-p1-parametrage
status: built
date: 2026-10-26
order: 1
context: planification
sources: [.ddd/tactical/planification.md (P10, P11, §8), .ddd/safety-net/planification-p1-parametrage.md, RG-PLN-001 à 006, 093, 097, 098, ADR 0024, 0043, 0049, 0053, 0055, known-bugs.md KB-PLN-01 à 11]
---

# P1 — Paramétrage de la planification (paramètres, réglages, préférences des patients, profils des infirmiers)

Refonte sur place (ADR 0024) : pas de façade, de routage, de projection ni de métrique ; mêmes tables, mêmes URLs. **Aucun drapeau** : le nouveau code sert les 9 routes directement et l'ancien est supprimé ; le retour arrière est celui du déploiement (aucune migration).

## Fait
| Couche | Contenu (module `backend/contexts/planification`) |
|---|---|
| domaine | `ParametresDuPlanning`, `RatioDeSecurite`, `PatientsParPosteEtSerie`, `ReglagesDePlanification`, `PreferenceDePlanification`, `ProfilDePlanification`, `JourDeLaSemaine`, `Competence`, identifiants typés, `Pagination`, erreurs nommées ; ports `ParametresDuPlanningRepository`, `ReglagesRepository`, `PreferencesRepository`, `ProfilsRepository`, `VerrouDuCentre` |
| application | 8 cas d'usage (`Consulter/ModifierLesParametres`, `Consulter/ModifierLesReglages`, `ListerLesPreferencesDesPatients`, `DefinirLaPreferenceDuPatient`, `ListerLesProfilsDesInfirmiers`, `DefinirLeProfilDeLInfirmier`) ; ports `UniteDeTravail`, `SessionCourante`, `SallesDuCentre`, `CreneauxDuCentre`, `PatientsDuCentre`, `InfirmiersDuCentre`, `LectureDesPreferences` |
| infrastructure | dépôts JDBC, `VerrouDuCentreJdbc`, `LectureDesPreferencesJdbc` (read model), ACL `legacyorganisation/legacypatient/legacyinfirmier/legacyidentite`, `UniteDeTravailSpring` (lock_timeout 5 s) |
| interfaces | `ParametresControleur`, `PreferencesControleur`, advices (400/422/409/415), `publie/ParametrageLuParLeLegacy` |
| langage publié | `ParametrageDeLaPlanification` (lecture : paramètres, réglages, préférences, profils, centres en replanification nocturne) |
| legacy | supprimés : 2 contrôleurs, `PlanningParametresService`, `PreferencesPlanningService`, 4 adaptateurs JDBC/JPA, entité et repository JPA ; ports `bc-seance` réduits à la lecture ; 4 ACL `platform/.../acl/planification/` ; `schema.sql` crée `planning_parametres` pour H2 |

## Preuves
- Domaine/application/contrats : 85 tests du module (domaine 25, cas d'usage 16, contrat double 8, contrat JDBC H2 22, publié 3, ArchUnit 11 sans `failOnEmptyShould`) ; `archunit.properties` tolérant retiré.
- PostgreSQL 16 jetable : `backend/scripts/planification-p1-pg16-rejeu.sh <port != 5432>` — contrat JDBC 23 tests dont verrou, clé créée entre-temps et `lock_timeout` (409) ; vert.
- Parité : 50 scénarios de caractérisation exécutés contre le nouveau code : 50 verts ; 45 goldens sur 225 changent par décision (voir écarts), ancien comportement dans `src/test/resources/characterization/planification/p1-parametrage/_ecarts-legacy`.
- Audit : `AuditDuParametrageDeLaPlanificationIntegrationTest` (filtre `AuditRequestFilter` monté, `PLANNING_MODIFICATION`, statut 200 et 400, lecture non tracée).
- Mutation : `backend/scripts/pit-p1-module.sh` — 88 mutants, 94 % tués, force des tests 97 % (survivants : retours ignorés sans effet, identifiants).
- `check-arch.ts` : 0 violation. Frontend : `preferences-planning.store.spec.ts` 7/7 (codes `PREFERENCE_PATIENT_SORTI`, `PROFIL_INFIRMIER_INACTIF`, `PLANIFICATION_ECRITURE_CONCURRENTE`, i18n fr/en/ar/kab). Build complet séquentiel `./mvnw -o test` : BUILD SUCCESS, 2170 tests, 0 échec (4 ignorés), dont le test garde des règles de gestion.

## Écarts de parité décidés (26/10/2026)
KB-PLN-01/02 (400 au lieu de 500), 07 (422 patient sorti / infirmier inactif), 08 (pagination), 09 (compétences triées), 10 (409 + verrou) ; KB-PLN-03 droits inchangés, règle RG-PLN-005 corrigée ; 05 gardé ; **04 et 06 reproduits « à décider »**. Détail : `known-bugs.md`.

## Reste à faire / points ouverts
- KB-PLN-04 (décimaux tronqués) et KB-PLN-06 (salle inconnue = salle étrangère, colonne `salles_isolement` remise à NULL) : à décider.
- Les 4 ACL `platform/.../acl/planification` et les modèles `PlanningParametres`, `PreferencePatient`, `ProfilInfirmier`, `ReglagesOptimisation` de `bc-seance` disparaissent avec leurs consommateurs (P3/P4 présence, P7/P8 optimisation) ; `DirectionCapaciteQueryService` lit encore `planning_parametres` en SQL (port `CapaciteDesCentres`, slice ultérieure).
- Glossaire : 8 termes `proposed` à valider. ADR 0055 `proposed`.
- `salle.isolement` a deux écrivains sans verrou commun : le référentiel d'administration (énumération `isolement`) et `SallesDuCentre` (dernier écrit gagne, comme le legacy) ; levé par le futur contexte Organisation (ADR 0053).
- **Décision à prendre en tête de la fiche de P5 (et P2 si elle écrit une place)** : `PlacesDuCentre` doit créer la ligne `planning_parametres` par défaut avant `FOR UPDATE` (ADR 0055) ; `VerrouDuCentreJdbc` ne crée rien.
- Page « paramètres du planning » du frontend : messages dédiés au 409 et à la salle d'isolement étrangère (`parametresErrorKey`, fr/en/ar/kab, testé).

## Plan de bascule et retour arrière
Pas de bascule progressive (ADR 0024) : déploiement unique. Contrôles avant mise en production : `verifications-production.md` § 32 (SELECT seulement). Surveillance : statuts 400/409/422 sur `/api/v1/planning/parametres` et `/preferences/**` dans le journal d'audit. Retour arrière : redéploiement de la version précédente (aucune migration, mêmes tables).

## Seam
Édition minimale du legacy autorisée par l'utilisateur (ports de lecture, ACL, `schema.sql`) : inscrite et fermée dans `.ddd/safety-net/seams.md` ; `seamAllowed` vide.
