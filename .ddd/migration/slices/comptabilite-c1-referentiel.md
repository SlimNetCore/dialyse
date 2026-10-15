---
status: approved
date: 2026-10-15
slice: comptabilite-c1-referentiel
order: 1 (C1 à C7, voir `.ddd/migration/roadmap.md`)
sources: [.ddd/tactical/comptabilite.md, .ddd/safety-net/comptabilite-c1-referentiel.md, .ddd/safety-net/known-bugs.md (KB-CPT-01 à 13), ADR 0024 (+ amendement C1), ADR 0025]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URLs, mêmes DTO JSON ; aucune façade, aucun routage, aucune
> projection, aucun drapeau, aucune migration de données. Retour arrière = revenir au commit précédent.

# C1 — Référentiel comptable (plan comptable, journaux, compte client de chaque payeur)

**Comportement observable** : l'administrateur tient le plan comptable de son centre (plan SCF de départ de 20 comptes,
RG-CPT-018), ses journaux (cinq journaux par défaut, RG-CPT-009) et le compte client de chaque payeur (RG-CPT-019), sous
`/api/v1/comptabilite` : `GET|PUT|DELETE /comptes[/{numero}]`, `GET|PUT|DELETE /journaux[/{code}]`, `GET /payeurs`,
`PUT /payeurs/{id}/compte`.

## État final (2026-10-15) : `built`

`./mvnw -o test` complet : module `contexts/comptabilite` **173 tests verts** ; `platform` 1274 tests, 4 ignorés, **1
échec sans rapport**
(`AbsencePatientIntegrationTest.the_week_view_lists_the_non_cancelled_absences_of_the_week_of_the_centre` : fixture
datée `LocalDate.now().minusDays(5)`, tombe un samedi ; non touché). **Parité : 80 tests de caractérisation sur 80 verts
contre le nouveau code** (76 d'origine + 3 d'alignement des modèles de persistance + 1 scénario pour KB-CPT-13a), dont
13 goldens `*_nouveau_code` (10 remplaçant des goldens caducs, 3 nouveaux pour KB-CPT-13a). GMAO inchangé et vert.
`check-arch.ts` : 0 violation. Frontend non modifié.

## Réalisation (`backend/contexts/comptabilite`, module `contexts-comptabilite`)

| Couche               | Contenu                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| domaine              | `plan/` : `PlanComptable` (plan SCF de départ, RG-CPT-018), `PlanDuCentre`, `CompteComptable`, `NumeroDeCompte` (1 à 20 lettres/chiffres, majuscules), règle « utilisé : désactivable seulement si libre, jamais supprimable » (`CompteUtilise`, `CompteAvecEcritures`) ; `journal/` : `Journal`, `CodeJournal`, journaux par défaut (RG-CPT-009), `JournalUtilise`, `JournalAvecEcritures` ; `payeur/` : `CompteClientDuPayeur` ; port `UsagesDuParametrage` |
| application          | `plan/` : `ListerLesComptes`, `EnregistrerUnCompte`, `SupprimerUnCompte`, `ConsulterLePlan` ; `journal/` : `ListerLesJournaux`, `EnregistrerUnJournal`, `SupprimerUnJournal`, `ConsulterLesJournaux` ; `payeur/` : `ListerLesPayeurs`, `AffecterUnCompteAuPayeur`, `ConsulterLeCompteDuPayeur` ; ports de lecture et `UniteDeTravail`                                                                                                                         |
| langage publié       | `PlanComptableEnLecture`, `JournauxDuCentre`, `ComptesClientsDesPayeurs` (lus par le legacy encore en place)                                                                                                                                                                                                                                                                                                                                                  |
| infrastructure       | `persistence/` : adaptateurs JPA des trois tables, `PayeursDuCentreJdbc` (lit `centre_payeur`), `UsagesDuParametrageJdbc` (mapping, payeurs, modèles, écritures, **articles**) ; `transaction/UniteDeTravailSpring` ; `acl/legacyidentite/SessionLegacy`                                                                                                                                                                                                      |
| interfaces           | `PlanComptableControleur`, `JournauxControleur`, `ComptesPayeursControleur` (mêmes `@PreAuthorize`, garde de centre, 403 si `centerId` ≠ session), `ReglesMetierVioleesAdvice`                                                                                                                                                                                                                                                                                |
| plateforme (câblage) | ACL minimale `infrastructure/acl/comptabilite/` : `PlanComptableDuContexte`, `JournauxDuContexte`, `ComptesClientsDesPayeursDuContexte` implémentent les anciens ports lus par `ComptabiliteService` et `PiecesComptablesService`                                                                                                                                                                                                                             |
| tests                | domaine (50), application avec doubles en mémoire (44), contrats communs aux doubles et aux adaptateurs JPA/JDBC sur H2 (40), ArchUnit ; contrats PostgreSQL 16 : `Compte                                                                                                                                                                                                                                                                                     |Journal|Payeur|UsagesPostgresContractIT` ; filet HTTP (`characterization/comptabilite/referentiel/`) |

## Écarts de comportement décidés (utilisateur, 2026-10-14)

Détail : `.ddd/safety-net/known-bugs.md`, « Décisions C1 ». Goldens `*_nouveau_code`.

- **KB-CPT-01** : `actif` absent ou `null` = **400** « actif obligatoire » (avant : 500).
- **KB-CPT-04** : numéros de compte **normalisés en majuscules** (`abc9` devient `ABC9`) ; contrôle de production n° 14
  (`verifications-production.md`). Le mapping et les pièces du legacy gardent la comparaison exacte (un numéro en
  minuscules y reste refusé).
- **KB-CPT-13a** : un compte rattaché à un article (`compte_stock`, `compte_charge`) est « utilisé » : 422
  `COMPTE_UTILISE` à la désactivation et à la suppression.
- **Reproduits** : KB-CPT-02, 03, 05 (jokers), 06, 07, 08 (libellés « Compte N »), 09 à 12, 13b, 13c ; `SUPERADMIN`
  bloqué par `ROLE_SCOPE` ; 500 sur routes absentes et paramètres illisibles. **Reproduits, correction à la livraison
  suivante.**

## Mutation et PostgreSQL 16

- PIT : `backend/scripts/pit-c1-referentiel.sh` (domaine et cas d'usage, tests unitaires) : résultat dans « Mesures
  finales » ci-dessous.
- PostgreSQL 16 : `backend/scripts/comptabilite-pg16-rejeu.sh <port>` (cluster jetable, port non standard, 5432 refusé,
  détruit en sortie, à lancer en arrière-plan avec un log) : résultat dans « Mesures finales ». La suite HTTP reste sur
  H2 (fixtures SQL sans clés étrangères).

## Données et plan de bascule

Mêmes tables (`comptes_comptables`, `journaux_comptables`, `comptes_payeurs`, `centre_payeur` en lecture), aucune
migration : pas de plan de bascule (ADR 0024). À valider avant livraison : requêtes SELECT n° 14.

## Reste à retirer avec C2 à C7 (conservé car encore lu)

| Conservé                                                                                                                                                                                                   | Lu par                                                             | Retrait                                                 |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|---------------------------------------------------------|
| `bc-comptabilite` : ports réduits `PlanComptableUseCase` (`exigerActifs`), `JournalRepositoryPort` (`findByCenter`), `ComptePayeurRepositoryPort` (`find`) et leurs trois adaptateurs ACL de la plateforme | `ComptabiliteService` (C2, C5, C6), `PiecesComptablesService` (C4) | quand ces services lisent directement les ports publiés |
| `JournalCode`, `Journal` (VO legacy)                                                                                                                                                                       | écritures, mapping, modèles, opérations                            | C3 à C6                                                 |
| `ComptabiliteRestController` (mapping, règles de TVA, écritures, clôture, rebuild, stock)                                                                                                                  | C2, C3, C5, C6                                                     | C2 à C6                                                 |
| `ComptabiliteParametrageRestController` (modèles, pièces)                                                                                                                                                  | C4                                                                 | C4                                                      |
| `MappingComptableJpaAdapter`, `ModelePieceJdbcAdapter`, `EcritureComptableJpaAdapter`                                                                                                                      | C2, C4, C3                                                         | avec leur slice                                         |

Retirés par C1 : `PlanComptableService`, `JournauxService`, `ComptesPayeursService`, `CompteComptable`, ports
`CompteRepositoryPort`, `PayeursPort`, `JournauxUseCase`, `ComptesPayeursUseCase`, trois services d'application,
`PlanComptableJdbcAdapter`, `ComptePayeurJdbcAdapter`, `JournalComptableJpaAdapter/Entity/Repository`, six routes des
deux contrôleurs partagés.

## Frontend : ce qui change (aucune modification faite)

- `PUT /comptes/{n}` et `PUT /journaux/{c}` sans `actif` : 400 au lieu de 500 (l'écran envoie toujours `actif`).
- Les numéros de compte reviennent en majuscules (l'écran peut afficher `ABC9` après saisie de `abc9`).
- Désactiver ou supprimer un compte d'article : 422 `COMPTE_UTILISE` (message à afficher comme pour les autres usages).

## Mesures finales (2026-10-15)

- **PIT 1.30.0** (`pit-c1-referentiel.sh`, domaine et cas d'usage du module, 268 tests unitaires) : **151 mutants, 150
  tués (99 %)**, force des tests 99 %, aucun mutant sans couverture, couverture de lignes 307/307. Cible ≥ 70 %
  atteinte.
- **PostgreSQL 16 réel** (`backend/scripts/comptabilite-pg16-rejeu.sh 55441`, cluster jetable, port 55441, jamais 5432,
  détruit en sortie) : **code de sortie 0** ; `ComptePostgresContractIT` 10, `JournalPostgresContractIT` 6,
  `PayeurPostgresContractIT` 8, `UsagesPostgresContractIT` 8 (dont les comptes d'article), tous verts ; unicités
  `uq_compte_comptable_centre_numero` et `uq_journal_comptable_centre_code` constatées sur le schéma Flyway pur.
- Test garde des règles de gestion (RG-CPT-009, 018, 019) vert ; `check-arch.ts` : 0 violation.
