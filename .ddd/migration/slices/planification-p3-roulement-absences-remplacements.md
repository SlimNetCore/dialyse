---
slice: planification-p3-roulement-absences-remplacements
status: built
date: 2026-10-26
order: 3
context: planification
sources: [.ddd/tactical/planification.md (P14 à P16, B4, B5, B6, B9), .ddd/safety-net/planification-p3-roulement-absences-remplacements.md, RG-INF-020 à 024, 030 à 033, 052, ADR 0024, 0049, 0053, 0054, 0056, 0057, known-bugs.md KB-PLN-25 à 41]
---

# P3 — Roulement, absences, remplacements, « Mon planning » (12 routes)

Refonte sur place (ADR 0024) : pas de façade, de routage, de projection ni de métrique ; mêmes tables, mêmes URLs.
**Aucun drapeau** : le nouveau code sert les 12 routes
directement et l'ancien est supprimé. **Une migration additive** (V47, colonne nullable
`infirmier_absence.declaree_par`) ; le retour arrière est le redéploiement.

## Fait

| Couche                       | Contenu (module `backend/contexts/planification`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| domaine                      | agrégats `RoulementDeLInfirmier` (entités `Affectation`, immuable : `affecter`, `modifier`, `retirer`, `prevuSurLeCreneau`, `prevuSurLaCase`), `AbsenceInfirmier` (VO `PeriodeDAbsence`, `TypeAbsence`, motif ≤ 255, auteur conservé ; `declarer`, `declarerParLInfirmier`, `exigerRetirablePar`), `Remplacement` (preuve `RemplacantEligible`, `personnesDistinctes`, `exigerUneDateDeTravail`, `exigerUnRemplaceConcerne`) ; politique `AbsencesSansChevauchement` ; ports `RoulementsRepository`, `AbsencesRepository`, `RemplacementsRepository` ; `InfirmiersRepository.parId` ; `Infirmier.exigerActifPourLePlanning`                      |
| application                  | 12 cas d'usage (`AffecterAuRoulement`, `ModifierLAffectation`, `RetirerLAffectation`, `ListerLesAbsences`, `DeclarerUneAbsenceDInfirmier`, `RetirerUneAbsence`, `ConsulterMonPlanning`, `ListerMesAbsences`, `DeclarerMonAbsence`, `RetirerMonAbsence`, `AffecterUnRemplacant`, `AnnulerLeRemplacement`) ; read models `AbsenceLue`, `RemplacementLu` ; ports `HorlogeDesCentres`, `NouveauxIdentifiantsDuPlanning`, `SessionCourante.utilisateur()/administrateur()`                                                                                                                                                                            |
| langage publié               | **lecture** `RoulementEtAbsencesDesInfirmiers` (présence, charge, alertes, optimisation) ; **ports consommés** (fournis par le legacy) `CalendrierDuCentre`, `SurEffectifDuRoulement`, `SemaineDeLInfirmier`, `NotificationsDesAbsencesDInfirmiers`                                                                                                                                                                                                                                                                                                                                                                                              |
| infrastructure               | `RoulementsJdbc` (dépôt + lecture groupée des fiches), `AbsencesJdbc`, `RemplacementsJdbc`, `RoulementEtAbsencesLusParLeLegacyJdbc`, ACL `legacycentres/HorlogeDesCentresLegacy`, `NouveauxIdentifiantsDuPlanningAleatoires` ; supprimée : ACL `legacyroulement` (lecture seule de P2)                                                                                                                                                                                                                                                                                                                                                           |
| interfaces                   | `RoulementControleur` (3 routes), `AbsencesDesInfirmiersControleur` (3), `MonPlanningControleur` (4), `RemplacementsControleur` (2) ; advices P1/P2 étendus (400/415/422/409)                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| plateforme (ACL temporaires) | `acl/planification/{CalendrierDuCentreLegacy, SurEffectifDuRoulementLegacy, SemaineDeLInfirmierLegacy, NotificationsDesAbsencesDInfirmiersLegacy}` ; `PresenceDonneesJdbcAdapter` lit le roulement/absences/remplacements par le langage publié                                                                                                                                                                                                                                                                                                                                                                                                  |
| legacy retiré                | contrôleurs `InfirmierRestController`, `AbsenceInfirmierRestController`, `MonPlanningInfirmierRestController` ; services `AffectationInfirmierService`, `AbsenceInfirmierService`, `MonPlanningInfirmierService`, `RemplacementInfirmierService`, **`InfirmierService`** ; ports/adaptateurs **`ComptesInfirmierPort`/`ComptesInfirmierJdbcAdapter`**, `InfirmierRepositoryPort`/`InfirmierJdbcAdapter`, `AbsenceInfirmierRepositoryPort`/`AbsenceInfirmierJdbcAdapter`, record `Infirmier` ; routes de remplacements de `PresenceInfirmierRestController` ; `InfirmierRestControllersTest` (remplacé par `PresenceInfirmierRestControllerTest`) |
| frontend                     | i18n fr/en/ar/kab : 5 codes d'erreur (`INFIRMIER_INACTIF`, `ABSENCE_CHEVAUCHEMENT`, `REMPLACEMENT_DATE_PASSEE`, `REMPLACEMENT_JOUR_FERME`, `REMPLACEMENT_REMPLACE_NON_CONCERNE`) et la notification `INFIRMIER_ABSENCE_RETIREE` (titre + message), rafraîchissement du planning sur cet évènement, `presenceErrorKey` étendu                                                                                                                                                                                                                                                                                                                     |

## Preuves

- Module : tests de domaine (roulement 9, absence 9, remplacement 7), cas d'usage avec doubles (21), contrat
  roulement/absences/remplacements : double 9, JDBC H2 14 (dont lecture publiée, fuseau, **2 courses**), PostgreSQL 16
  14 ; ArchUnit 11 ; `check-arch.ts` : 0 violation.
- PostgreSQL 16 jetable : `backend/scripts/planification-p3-pg16-rejeu.sh <port != 5432>` (vert : constats Flyway V47,
  contrôles SELECT-only vides, contrat PG de P1, P2 et P3 : 58 tests) — course de 6 affectations chevauchantes : 1
  ligne ; course de 6 remplacements du même remplaçant : 1 ligne.
- Parité : les **66 scénarios** de caractérisation sont exécutés contre le nouveau code (66/66 verts) ; **35 goldens
  changent par décision** (28 modifiés, 7 renommés/retirés), ancien comportement conservé dans
  `src/test/resources/characterization/planification/p3-roulement/_ecarts-legacy` ; 7 nouveaux goldens (`*_refuses`,
  remplacé prévu/non concerné, infirmier désactivé refusé). Scénarios adaptés aux décisions : chevauchements d'absences,
  infirmier désactivé, remplacé nommé, retrait d'une absence saisie par l'administration (`absenceParLAdministration`),
  diffusion du retrait.
- Mutation : `backend/scripts/pit-p3-module.sh` (domaine + application du module, P1 + P2 + P3) : 419 mutants, 96 % tués
  (403), force des tests 97 %, couverture de lignes 96 % ; survivants : retours `null` d'accesseurs et de lambdas
  d'erreurs, lambdas de verrou.
- Build complet `./mvnw -o test` : voir la réponse à l'appelant. Frontend : `infirmier-stores.spec.ts`,
  `notification-presentation.util.spec.ts`, `planning.util.spec.ts` (47 tests verts).

## Écarts de parité décidés (2026-10-26, ADR 0057)

| KB         | Décision                                                                                                   |
|------------|------------------------------------------------------------------------------------------------------------|
| 25, 29     | verrou de la fiche + 409 (roulement, remplaçant) ; plus de 500                                             |
| 26         | `ABSENCE_CHEVAUCHEMENT` 422 (admin et « Mon planning »)                                                    |
| 27, 28     | soi-même en premier (400) ; remplacé absent ou prévu sur la case ; jour fermé et passé 422                 |
| 30, 36, 37 | 400 `BAD_REQUEST`, 415, 403 avant le corps ; pagination négative 400, plafond 100 ; motif ≤ 255            |
| 31         | droits inchangés ; RG-INF-031/032 corrigées (matrice verrouillée par les goldens d'autorisation)           |
| 32         | « aujourd'hui » au fuseau du centre                                                                        |
| 33, 34     | audit métier avec l'identifiant créé ; retrait d'une absence notifié et diffusé                            |
| 35         | infirmier désactivé refusé (roulement, absence)                                                            |
| 38         | ordre aligné (entrées → existence → état) ; roulement : infirmier → état → salle → créneau → chevauchement |
| 39         | auteur conservé (V47) ; l'infirmier ne retire que ses absences                                             |

## Reste à faire / points ouverts

- **À valider** : interprétation de KB-PLN-39 (l'infirmier ne peut plus retirer ce que l'administration a saisi ;
  l'administration retire toute absence) ; absences sans auteur (antérieures à V47) restent retirables par l'infirmier ;
  confirmation écrite de l'extension des libellés d'audit à P3 (`seams.md`) ; numérotation KB-36/38 de la demande.
- **Dette** : P8 (`OptimisationApplicationService`) écrit encore roulement et remplacements par les ports legacy sans le
  verrou de la fiche (KB-PLN-40) ; `AffectationInfirmierRepositoryPort`, `RemplacementInfirmierRepositoryPort` et leurs
  adaptateurs subsistent pour lui.
- Les 4 ACL de `platform/.../acl/planification/` et la lecture SQL de `infirmier` par `PresenceDonneesJdbcAdapter`
  disparaissent avec P4/P5.
- L'ACL `legacyinfirmier/InfirmiersDuCentreJdbc` (P1) devrait lire `InfirmiersRepository`.
- Événements de domaine (`AffectationAjoutee`, `AbsenceInfirmierDeclaree`, `RemplacantAffecte`…) : aucun consommateur,
  non introduits.
- Glossaire : 11 termes `proposed` à valider. ADR 0057 `proposed`. Aucun test Playwright/ng serve (lancés par
  l'utilisateur).

## Plan de bascule et retour arrière

Pas de bascule progressive (ADR 0024) : déploiement unique. **Avant** : contrôles SELECT-only de
`verifications-production.md` § 34 (affectations et absences qui se chevauchent déjà, fuseaux des centres).
**Surveillance** : actions d'audit `AFFECTATION_*`, `ABSENCE_*`, `REMPLACANT_AFFECTE`, `REMPLACEMENT_ANNULE` ; statuts
400/409/422 sur `/api/v1/infirmiers/**` ; notifications `INFIRMIER_ABSENCE_RETIREE`. **Retour arrière** : redéploiement
de la version précédente (V47 ignorée par l'ancien code ; l'ancien code lit les mêmes tables).

## Seams

Édition minimale du legacy autorisée par l'utilisateur, inscrite dans `.ddd/safety-net/seams.md` et `covered.json` ;
**fermée** à la fin de la slice ; `seamAllowed` vide.
