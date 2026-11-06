---
status: characterized (filet vert sur le legacy, commité en 853fd67b ; PC3 repris sur place ensuite, voir la fiche)
date: 2026-11-03
sources: .ddd/migration/slices/patient-couverture-pc3-pec.md, .ddd/tactical/patient-couverture.md, ADR 0024/0064/0073/0076, docs/reference/regles-de-gestion/04-patients-assures-pec.md (RG-PEC-*), known-bugs.md KB-PAT-20 à 28, 35, 36
---

# Filet de caractérisation : patient-couverture-pc3-pec

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Référence : `PecRestController` (`/api/v1/pec`, routes de PEC), `PecDomainService`, `PriseEnCharge`, `PecRepositoryAdapter` (cache), `PecReadQueryService`/`PecSpecifications` (recherche), la demande de PEC de la fiche (`CouvertureDeLaFicheJdbc`, reprise PC1), et leurs lecteurs. Le module `contexts/patient-couverture` (Pec*, gelé, exclu de la compilation) n'est pas exercé.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL de `prise_en_charge` (jointure sur `patients` et `forfait`, codes de forfait), notifications durables, diffusions (canal du courtier), audit ; les trois lecteurs sont appelés tels que câblés dans le contexte Spring : `ForfaitsDesPatients` (Absences, RG-ABS-030), `SeanceBillingEligibilityPort` (Séance, création de séance, Facturation). Aucun mock. Harnais : `CouvertureCharacterizationSupport` (voir PC2). Goldens : `backend/platform/src/test/resources/characterization/patientcouverture/pc3/<dossier>/<scénario>/` (79 fichiers).

## Déjà verrouillé avant (non rejoué)

| Filet | Verrouille |
|---|---|
| `characterization/patientcouverture/pec` (5 scénarios, 10 goldens) | création, liste, validation, session-allowed, clôture, suppression, cas nominaux de l'ADMIN du centre A |
| PC1 `consultation` | filtre « facturable » de la recherche de patients (PEC validée) |
| Séance, Absences A2, Facturation F | leurs effets de bout en bout (séance facturable, valorisation, facture) ; non rejoués |

## Scénarios ajoutés (30, 6 classes)

| Classe | Nb | Verrouille |
|---|---|---|
| `pc3.CreationDeLaPriseEnCharge` | 5 | nominale (CREE, période et forfait demandés) ; attestation du non-vacancier (10 cas, bornes incluses, inversée, autre centre) ; 10 refus de forme ; périodes et forfait sans contrôle (7 cas) ; chevauchement, doublon, inclusion acceptés |
| `pc3.CycleDeLaPriseEnCharge` | 7 | cycle nominal avec diffusions, notifications, audit ; transitions interdites (4 statuts x valider/clôturer, statut inconnu 400) ; validation sans contrôle (5 cas) ; refus de validation/clôture (10 cas) ; `session-allowed` (statuts, attestation, périodes, 5 refus) ; suppression (statuts, patientId, 5 refus) ; deux validations simultanées |
| `pc3.LecturesDesPrisesEnCharge` | 6 | listes du centre et du patient, 5 cas limites ; cache des listes (écriture directe périmée) ; recherche paginée (forme, tri, 4 pages, total) ; bornes (10 cas) ; 32 filtres (code, nom, assurance, statut, search, dates exactes/intervalles/formats/illisibles) ; isolement et dates effectives ignorées |
| `pc3.AutorisationsEtCentresDeLaPriseEnCharge` | 3 | matrices 11 profils + `PHARMACIEN`, `DIRECTEUR_ADMINISTRATIF`, rôle personnalisé de 8 routes (lectures, création, validation réservée à ADMIN, clôture, suppression) + anonyme ; absence de garde de centre (ADMIN du centre B, ADMIN sans centre) |
| `pc3.DemandeDePecDeLaFiche` | 5 | demande de la fiche : création, demandes incomplètes ignorées, PEC validée jamais ramenée à « créée », révision, ajout, `pecId` déjà pris (500, fiche annulée) |
| `pc3.LecteursDeLaPriseEnCharge` | 4 | forfait applicable (17 cas) ; chevauchement de PEC (5 cas) ; cause de non-facturabilité (11 cas) ; cycle de la PEC piloté par l'API : ce que lisent Absences, Séance (facturabilité) et la route `session-allowed` |

## Non-déterminisme

Dates par alias relatifs nommés (`<J>`, `<J-5>`, `<J+25>`, `<J-300>`...), jamais de date réelle ni de jour de mois ou de semaine (le harnais échoue si une date non nommée reste dans un golden). Horodatages de création des PEC de fixture fixes (ordre de départage déterministe). `PecDomainService` lit `LocalDate.now()` (fuseau de la JVM, KB-PAT-20) : les scénarios qui touchent la borne « aujourd'hui » ne sont exécutés que si le jour de la JVM égale le jour du centre (`Assumptions`, fenêtre d'une heure par jour si la JVM n'est pas à Africa/Algiers). Course (deux validations) : seules les issues possibles sont verrouillées. Messages techniques Spring, SGBD et cache normalisés. Cache de listes vidé avant chaque test.

## Stabilité

Voir PC2 : 3 exécutions des 120 tests (alphabétique, deux aléatoires) : 0 échec, 0 ignoré, 0 flaky.

## Couverture et mutation

Pas de JaCoCo ni de PIT sur la zone : aucune mesure de branches ni de mutants. Couverture raisonnée : `PecDomainService` (création avec et sans attestation, patient inconnu, validation, clôture, suppression, `canCreateSession` : toutes les issues), `PriseEnCharge` (3 transitions et refus), `PecRepositoryAdapter` (cache et évictions), `PecSpecifications` (chaque filtre et chaque forme de date), les trois SQL des lecteurs (chaque branche de la cause de non-facturabilité et du choix du forfait). 13 fichiers inscrits dans `covered.json`.

## Limites (NON verrouillé)

- **Routes d'attestation** de `PecRestController` (`/attestations`, `/attestations/{id}`, `/attestations-center/search`, suppression) et la moitié attestation de `PecReadQueryService` : non exercées (PC4). Tout changement de ces deux fichiers pour PC3 doit laisser ces routes intactes ou attendre le filet PC4.
- Lecteurs non exercés de bout en bout : `SeancesFacturablesJdbc` (Facturation), `DashboardRestController` (compteurs de PEC et d'échéances, RG-PEC-010 : PC5), `SeanceRestController` (tableau de bord, journal), valorisation HTTP des absences (seul le port `ForfaitsDesPatients` est appelé) : filets Absences/Séance/Facturation.
- `PecValidationJdbc` (`contexts/seance`) : hors réacteur, non câblé, non exercé (KB-PAT-25) ; `FacturationDuPatient` (port publié de Facturation, factures) ne lit pas `prise_en_charge` : hors périmètre.
- Diffusion de la création et de la suppression de PEC : non capturées (seules `PEC_VALIDATED` et `PEC_CLOSED` le sont).
- PostgreSQL (H2 seulement) : interclassement, `LIKE`, verrous, vraie concurrence (une paire d'appels) ; volumétrie ; cache : expiration (TTL) non éprouvée.
- Export/impression de PEC (`reports/prise_en_charge.jrxml`), migration historique (`HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`), référentiel de forfaits (`ReferentialAdminTables`) : non rejoués.
- Fuseau : bascule de minuit non rejouée.

## Lecteurs et écrivains hors module de `prise_en_charge`

Lecteurs : `ForfaitsDesPatientsJdbc` (Absences), `SeanceBillingEligibilityJdbcAdapter` (Séance), `SeancesFacturablesJdbc` (Facturation), `PecValidationJdbc` (Séance, mort), `DashboardRestController`, `SeanceRestController` (l. 351, 383), `PatientListQueryService` (filtre facturable, PC1), `ReferentialAdminTables` (usage des forfaits), `MigrationRollbackJdbcAdapter`, `HistoricalRecordJdbcAdapter`. Écrivains : `CouvertureDeLaFicheJdbc` (PC1, demande de la fiche) ; les migrations (`HistoricalRecordJdbcAdapter` : insertion, `MigrationRollbackJdbcAdapter` : suppression) ; aucun autre contexte n'écrit la table.

## Comportements suspects

`.ddd/safety-net/known-bugs.md` : KB-PAT-20 à 28, 35 et 36.

## Questions de décision pour l'utilisateur (PC3)

1. Garde de centre : appliquer à la PEC la décision du 2026-11-02 (403 si `centerId` ≠ centre de session, ADMIN sans centre refusé) comme pour la fiche et l'assuré (KB-PAT-22) ? Droits : la validation reste réservée à ADMIN ; clôture, création, suppression à qui ?
2. Chevauchement : refuser ou clore automatiquement la PEC précédente ? Plusieurs PEC validées simultanées ? Contrôle de période (fin ≥ début, dans l'avenir) et de forfait (existant, du centre) à la création et à la validation (KB-PAT-24) ?
3. « PEC valide à une date » : unifier `session-allowed` (statut seul) et l'éligibilité de la séance (période + statut + attestation, y compris vacancier) ; qui fait foi (KB-PAT-25) ? Que faire de `PecValidationJdbc` ?
4. Forfait : le forfait effectif prime-t-il quand seule la période demandée couvre ? Une PEC non validée ou clôturée doit-elle encore porter un forfait pour la valorisation des absences (KB-PAT-26) ? Et sans PEC couvrante, retomber sur la plus récente ?
5. Suppression : autorisée pour une PEC validée ou clôturée ? Vérifier le `patientId` de l'URL ?
6. Erreurs : 404 « PEC introuvable » à la place du message du cache (KB-PAT-21), 400 à la place des 500 (KB-PAT-23).
7. Fuseau : jour du centre pour la validité de l'attestation (KB-PAT-20).
8. Vocabulaire : `CREE`/`VALIDEE`/`CLOTUREE`, `demande`/`effectif` ; `patientNom` réel dans les diffusions (KB-PAT-27) ; action d'audit `PEC_VALIDATION`/`PEC_CLOTURE` (KB-PAT-28).
9. Demande de la fiche (RG-PEC-007) : réviser la PEC existante plutôt qu'en ajouter une ; refuser une demande incomplète au lieu de l'ignorer ; `pecId` déjà pris (KB-PAT-35).
