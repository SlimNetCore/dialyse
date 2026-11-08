---
slice: seance-se4-derogation-regularisation
status: built
date: 2026-11-08
order: 4
context: seance
sources: [.ddd/tactical/seance.md, ADR 0024, 0062, 0074, 0091, .ddd/safety-net/seance-se4-derogation-regularisation.md]
---

# SE4 — Dérogation de régularisation : le rappel quotidien (pas d'agrégat « dérogation »)

> Historique : `planned` au 2026-11-02. Caractérisée le 2026-11-08 (net 10710fd0 : 10 scénarios, 10 goldens, KB-SEA-35 à 40) : **il n'existe aucun agrégat `DerogationRegularisation`**, la dérogation est le couple `seances.regularisation_deverrouillee_at/by`, déjà servi par le module depuis SE2 (`DeverrouillerUneSeance`). `built` depuis le 2026-11-08 (lots A, B, C). Reprise sur place (ADR 0024) : mêmes tables, aucune migration, aucune route, pas de façade ni de flag.

Le reste de SE4 est le **rappel quotidien** à l'administrateur des séances `CREE` oubliées des sept jours passés du centre (RG-SEA-046, 055).

## Cas d'usage (module `backend/contexts/seance`)
`RappelerLesSeancesARegulariser` : `executer()` (tous les centres, chacun à son jour du centre) et `executerPourLeCentre(centre, jour)` (jour explicite, tests et filet). Déclencheur : `RappelDesRegularisationsPlanifie`, `0 0 7 * * *`, fuseau `app.seance.fuseau-par-defaut` (repli Stock, Absences, `Africa/Algiers`).

## Fait (lots A, B et C, 2026-11-08)
| Couche | Contenu | Tests |
|---|---|---|
| Domaine | `FenetreDeRegularisation` (J-7 à J-1), `SeancesARegulariser`, `RappelDeRegularisation` (unique par centre et jour du centre, jamais vide) | `RappelDeRegularisationTest` 11 |
| Application | `RappelerLesSeancesARegulariser`, ports `CentresARappeler`, `RappelsDeRegularisation`, `VerrousDesRappels` ; lecture par le port existant `HistoriqueDesSeances` | `RappelerLesSeancesARegulariserTest` 12 (doubles en mémoire) |
| Sortant | ACL `RappelsParLeLegacy` sur le langage publié `RappelsFournis` (fourni par la plateforme : `RappelsFournisLegacy`, id UUID v3 centre + jour), ACL `CentresARappelerJdbc`, `VerrousDesRappelsJdbc` (`pg_advisory_xact_lock`, préfixe `rappel-regularisation|`) | contrat des rappels 8 (mémoire et ACL) ; `RappelsFournisLegacyTest` 5 ; `CentresARappelerJdbcTest` 2 ; `VerrousDesRappelsJdbcTest` 1 (H2) ; `VerrousDesRappelsPostgresContractIT` 3 (PG16 jetable par `seance-se4-pg16-rejeu.sh`) |
| Entrant | `RappelDesRegularisationsPlanifie` (un seul `@Scheduled`) | `RappelDesRegularisationsPlanifieTest` 3 |
| Routage | `SeanceRegularisationScheduler`, `SeanceRegularisationService` et leurs 2 tests supprimés ; seams inscrits puis refermés (`seams.md`, lots A et B), `seamAllowed` vide ; surcharge `notifySeancesARegulariser(…, idImpose)` ajoutée | |
| Parité | **10/10** : 9 identiques au legacy, 1 écart décidé (voir ci-dessous) ; legacy archivé en R100 | filet SE4 10 |
| Verrous | `seul_le_fournisseur_du_langage_publie_emet_le_rappel_de_regularisation` et `seul_contexts_seance_declenche_le_rappel_de_regularisation` (`FrontieresDesContextesTest`) ; entrée `CentresARappelerJdbc` de `TableDeLOrganisationTest` (lecteur de `centers`) | |
| Docs | RG-SEA-055, annexe 16, glossaire (proposed : séance à régulariser, fenêtre de régularisation, rappel de régularisation), `verifications-production.md` §56 (SELECT seulement), ADR 0091 | `ReglesDeGestionDocumentationTest` |

## Écart de parité (1, décidé, ADR 0091)
`rappel/cloisonnement_et_repetition`, étape 04 : deux appels du même jour pour le même centre donnaient deux diffusions et deux lignes de journal ; le module n'en émet qu'une (KB-SEA-35 corrigé, le rappel du jour reste figé à son premier passage). Étapes 01 à 03 inchangées. Approuvé en `cloisonnement_et_repetition_nouveau_code.approved.json` ; legacy archivé (R100, `git show 10710fd0:<chemin> | cmp`) sous `se4/_ecarts-legacy/rappel/cloisonnement_et_repetition_avant_se4.legacy.json`. **À confirmer métiers.** Le cycle complet (rappel, verrou 422, déverrouillage, rappel décroissant, validations) est identique au legacy.

## Décisions (ADR 0091, toutes « à confirmer métiers / DPO »)
KB-SEA-35 corrigé (un rappel par centre et par jour, verrou consultatif) · KB-SEA-36 corrigé (jour du centre) · **KB-SEA-37 reproduit, RISQUE EN TÊTE** (fenêtre de 7 jours, verrou sans borne : séance oubliée depuis 8 jours ou plus jamais rappelée ; arbitrage au plus tard le 2026-12-15, requête §56-1 chaque semaine) · KB-SEA-38, 39, droits, libellés reproduits sans alerte · KB-SEA-40 : aucun agrégat, table V50 conservée (aucune opération destructive).

## Bascule : aucun flag, condition de déploiement
Le legacy ne tourne plus (planificateur supprimé) : **le planificateur du module est actif dès le déploiement, sans indicateur d'activation** et sans double déclenchement. **Condition de déploiement** : vérifier qu'**une seule instance** porte le planificateur, ou que le verrou consultatif par centre et par jour et l'identifiant déterministe du journal sont opérationnels sur le PostgreSQL de production (`VerrousDesRappelsPostgresContractIT`, `seance-se4-pg16-rejeu.sh`). Surveillance : un rappel par centre et par jour, §56-5 à 0 ligne après le déploiement, journal `[SEANCES]`. Rollback : revenir au commit précédent (le rappel redevient celui du service legacy, un identifiant aléatoire par appel).

## Bilan des résidus (revue au plus tard le 2026-12-15 ; date butoir de retrait propre : 2027-01-31)
| Résidu | Où | Sort |
|---|---|---|
| Table V50 `derogations_regularisation` (vide, jamais écrite, KB-SEA-40) | `platform/.../db/migration/V50__*.sql` | revue 2026-12-15 : créer l'agrégat (motif, autorité, date : demande métier) ou migration de retrait validée PAR ÉCRIT (jamais de `DROP` sans validation) ; contenu surveillé par §56-2 ; butoir 2027-01-31 |
| `NotificationService.notifySeancesARegulariser` à trois arguments (identifiant aléatoire) | `platform/.../application/notification` | plus aucun appelant de production ; retrait avec son test `NotificationServiceSaisieInfirmierTest` (cas `SEANCES_A_REGULARISER`) ; 2027-01-31 |
| `RappelsFournisLegacy`, ACL `RappelsParLeLegacy`, type `SEANCES_A_REGULARISER` de `NotificationService` | `platform/.../acl/seance`, `contexts/seance/.../acl/legacyseance` | disparaissent avec la reprise des notifications (hors slices Séance) ; 2027-01-31 |
| `CentresARappelerJdbc` (SQL direct sur `centers`) | `contexts/seance/.../acl/legacyorganisation` | `organisation.v1` (jalon O4) ; entrée de `TableDeLOrganisationTest` à retirer avec ; 2027-01-31 |
| Fenêtre de 7 jours sans borne du verrou (KB-SEA-37) | `FenetreDeRegularisation`, `Seance` (verrou) | décision métier écrite (rappel sans limite, verrou borné, ou acceptation) ; 2026-12-15 |
| KB-SEA-38 (séance `CREE` sans patient, rappelée à vie), KB-SEA-39 (détail sans l'indicateur) | `Seance`, `SeancesControleur` | décisions métier (suppression ou statut ; champ ajouté) ; revue 2026-12-15 |
| Rejeu bout en bout du travail planifié (cron, fuseau `app.seance.fuseau-par-defaut`) et de deux instances simultanées : seuls le verrou (PG16), le déclencheur (cron 07:00, un seul `@Scheduled`) et la déduplication du fournisseur sont testés séparément ; KB-SEA-36 couvert par les tests d'application | `RappelDesRegularisationsPlanifie` | à écrire (PG16 jetable, deux contextes) au plus tard le 2027-01-31 ; d'ici là recette manuelle §56 (e) |
| Mentions des classes supprimées dans les Javadoc du filet SE4 (`Se4CharacterizationSupport`, `LeRappel…`) | tests de caractérisation | historiques, conservées pour la lecture des goldens ; nettoyage avec le retrait du filet |

## Reste à faire
Rien pour SE4. Suite : `/ddd-verify seance-se4-derogation-regularisation`, puis SE5.
