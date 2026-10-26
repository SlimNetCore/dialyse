---
status: characterized
date: 2026-10-26
sources: .ddd/tactical/planification.md (P14 à P16, B4, B5, B6, B9), docs/reference/regles-de-gestion/10-infirmiers.md (RG-INF-020 à 022, 030 à 032, 052), roadmap §1 sexies (P3), ADR 0049 à 0056, known-bugs.md KB-PLN-25 à 39
---

# Filet de caractérisation — planification-p3-roulement-absences-remplacements

Refactoring sur place (ADR 0024) : pas de Strangler. Legacy figé ; **aucun seam, aucune modification de production**
(`seamAllowed` reste vide).

## Périmètre (12 routes écrites + effets sur les lectures de P4)

| Route                                                      | Rôles observés                                               | Tables                                                                                                            |
|------------------------------------------------------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `POST /api/v1/infirmiers/{id}/affectations`, `PUT          | DELETE …/{id}/affectations/{affectationId}`                  | ADMIN                                                                                                             | `infirmier_affectation` |
| `GET                                                       | POST /api/v1/infirmiers/absences`, `DELETE …/absences/{id}`  | lecture ADMIN, SECRETAIRE, MEDECIN+INFIRMIER ; écriture ADMIN, SECRETAIRE                                         | `infirmier_absence`, `notification_evenement` (+ diffusion) |
| `GET /infirmiers/moi/planning`, `GET                       | POST /infirmiers/moi/absences`, `DELETE …/moi/absences/{id}` | ADMIN, SECRETAIRE, INFIRMIER seul, MEDECIN+INFIRMIER avec fiche liée (MEDECIN seul : 403 `ROLE_SCOPE`, KB-PLN-31) | `infirmier`, `infirmier_absence`, lectures de présence et de planning de la semaine |
| `POST /infirmiers/presence/remplacements`, `DELETE …/{id}` | ADMIN, SECRETAIRE                                            | `infirmier_remplacement`                                                                                          |

Lectures de P4 observées seulement comme **effets** : `presence/semaine` (projection des cases non vides),
`presence/remplacants`, `presence/charge` (différences, jamais de valeur absolue : elle dépend du calendrier du mois).
`alertes`, `imprimer`, `PresenceInfirmierScheduler` : non rejoués.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL normalisé des trois
tables, `notification_evenement`, canal du courtier de messages (diffusions temps réel), `audit_log` (filtre générique
monté à la demande). Aucun mock. Harnais :
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/planification/RoulementCharacterizationSupport.java`
(au-dessus de `InfirmiersCharacterizationSupport` de P2 et `PlanificationCharacterizationSupport` de P1).

Tests (6 classes, 66 scénarios) :
`…/characterization/planification/roulement/{Roulement,Absences,MonPlanning,Remplacements}…`, `Autorisations…`,
`AuditEtDiffusions…` (`…CharacterizationIntegrationTest`) ; goldens :
`backend/platform/src/test/resources/characterization/planification/p3-roulement/{roulement,absences,mon-planning,remplacements,autorisations,audit}/`
(136 fichiers, le plus long 173 lignes).
Commande :
`./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.roulement.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`).

## Scénarios (par comportement)

- **Autorisations** : matrice 12 routes × 11 profils + anonyme (statut, code, « a-t-on écrit ? ») ; formes de refus ;
  corps lu avant l'autorisation (KB-PLN-30) ; garde de centre (`?centerId=` identique, autre centre 403, illisible 500)
  sur 5 familles de routes.
- **Roulement** : création (jours triés, doublons fusionnés, avertissement de sur-effectif sur les jours en surnombre ou
  sans patient) ; jamais deux affectations d'un infirmier sur le même créneau le même jour, même dans deux salles, un
  seul jour commun suffit ; autres créneau / jours / infirmier acceptés ; ordre des contrôles (création et
  modification) ; infirmier désactivé ; 14 entrées mal formées ; modification (propres jours, chevauchement refusé, base
  inchangée) ; suppression (inconnue, d'un autre infirmier, d'un autre centre) ; effets sur la présence (cases, conflit
  hérité `DOUBLE_AFFECTATION`) ; rôles refusés ; **concurrence** de deux ajouts chevauchants (KB-PLN-25).
- **Absences (administration)** : liste vide, ordre `date_debut DESC, id`, isolement par centre, pagination ;
  déclaration par ADMIN (médecin prévenu) et SECRETAIRE (administrateur aussi) ; bornes 1 jour, 366 jours, 367 refusé,
  année bissextile ; 15 entrées mal formées et ordre des contrôles ; motif (trim, blanc, 255/256) ; passé et futur
  lointains ; chevauchements acceptés (KB-PLN-26) ; suppression ; effets sur la présence, les remplaçants proposés et la
  charge.
- **Mon planning** : planning complet (roulement, remplacement, absence, patients, collègues, isolement, compte) ;
  n'importe quel jour de la semaine → même planning ; semaine courante par défaut ; fiche inactive, compte sans fiche,
  fiche dans un autre centre, compte disparu ; paramètres illisibles ; mes absences (seulement les miennes,
  pagination) ; déclaration (à venir, aujourd'hui, en cours, fin aujourd'hui, fin hier refusée, passée) ; bornes
  366/367 ; ordre des contrôles ; notification + diffusion ; retrait (demain accepté, aujourd'hui / en cours / passée
  refusés, d'un collègue, inconnue, absence saisie par l'administration) ; profils avec fiche liée.
- **Remplacements** : affectation nominale et effets (présence, remplaçants, charge) ; remplacé nommé ; éligibilité
  (absent, prévu même salle ou autre salle, déjà remplaçant, désactivé, inconnu, autre centre, non habilité en
  isolement, double vacation, autre jour) ; jour férié et dates lointaines ; ordre des contrôles ; 10 entrées mal
  formées ; annulation ; remplaçant devenu absent ; **concurrence** du même remplaçant (KB-PLN-29).
- **Audit et diffusions** : 7 écritures tracées avec leur route et leur statut, refus tracés, lectures non tracées ;
  seules les déclarations d'absence notifient et diffusent (KB-PLN-33, 34).

## Dates et fuseau (aucune date réelle)

Deux familles d'alias : `<J>`, `<J-4>`, `<J+3>` (relatifs à aujourd'hui, pour les règles de « Mon planning ») et
`<S1.LUN>`… (semaines dimanche à samedi numérotées depuis la semaine courante ; S1 et suivantes sont dans le futur quel
que soit le jour d'exécution : aucune dépendance au jour de la semaine). Une collision d'alias ou une date réelle
restante dans un golden fait échouer le scénario. Le legacy lit l'heure UTC, la cible lira le fuseau du centre
(`Africa/Algiers`) : le harnais ignore (hypothèse JUnit) ses scénarios entre 23:00 et 24:00 UTC, où les deux jours
diffèrent, et attend à moins de deux minutes de l'un des deux minuits. Les rares dates fixes (2020, 2040, 2028
bissextile) sont nommées explicitement.

## Seams

Aucun. Le non-déterminisme est maîtrisé par normalisation : UUID générés (`<UUID_GENERE_n>`, jeux d'ordre SQL
explicite), alias nommés des fixtures, horodatages, dates nommées. Les deux scénarios de concurrence n'affirment que des
invariants (statuts possibles, nombre de lignes) : l'issue exacte est affichée en console (`[KB-PLN-25]`,
`[KB-PLN-29]`).

## Bugs et constats

KB-PLN-25 à 39 (`known-bugs.md`), reproduits tels quels. Décisions à demander : voir la réponse à l'appelant.

## Couverture et mutation

- Stabilité : 3 exécutions de toute la suite Planification (P1 + P2 + P3 + tests existants du paquet, 0 échec), dont 2
  avec `-Dsurefire.runOrder=random` : 0 flaky. Les deux tests de concurrence ont des issues variables mais des
  invariants stables.
- PIT 1.30.0 (`backend/scripts/pit-p3-roulement.sh`, ~11 min, Spring complet) sur les 4 services, les 4 contrôleurs
  (routes de lecture de présence exclues), les 3 adaptateurs JDBC et les 3 agrégats : **145 mutants, 123 tués (85 %), 2
  sans couverture, force des tests 86 %**, couverture de lignes des classes mutées 93 % (321/345). Première mesure : 122
  tués ; le scénario ajouté pour `InfirmierRestController:120` a tué le 123e.
- Survivants analysés : gardes de constructeur des trois agrégats (`AbsenceInfirmier` 25-27 et 32,
  `AffectationInfirmier` 23-27, `RemplacementInfirmier` 20-24) **inatteignables par HTTP** (validés en amont par
  `@NotNull` / `@NotEmpty` / contrôles de service) ; `AffectationInfirmierJdbcAdapter` 41, 69, 74 (jours vides jamais
  écrits, liste d'identifiants jamais vide, un seul identifiant) et `AbsenceInfirmierJdbcAdapter` 61, 72 (`COUNT` jamais
  nul) équivalents ; `InfirmierService:32` (fiche trouvée par `user_id` donc compte jamais nul à cet endroit)
  équivalent ; sans couverture : fusion de doublons de `mesCases` (jamais de clé en double) et surcharge `declarer(…)` à
  6 arguments d'`AbsenceInfirmierService` (code mort côté HTTP : le contrôleur appelle la surcharge à 7 arguments).
  `InfirmierRestController:120` (compte absent) était le seul survivant atteignable : scénario `un_compte_disparu_…`
  ajouté.

## Limites (ce qui n'est PAS verrouillé)

- Lectures de présence (P4) : `semaine`, `remplacants`, `charge` observées comme effets seulement ; `alertes`,
  `imprimer`, `PresenceInfirmierScheduler` (07:15), `RemplacantService`/`PresenceInfirmierService` en détail (barème de
  classement, FERME, motifs) : slice P4. `PresenceInfirmierRestController` et `PresenceDonneesJdbcAdapter` sont inscrits
  ou non selon leur usage : le contrôleur est dans `covered.json` (routes de remplacements seulement caractérisées) ;
  l'adaptateur de lecture ne l'est pas.
- Écritures du roulement et des remplacements par l'optimisation (`OptimisationApplicationService`, P8) et
  `PlanningAffectationService` : non rejouées.
- Le chemin de production (PostgreSQL) : verrous et contraintes réels, `lock_timeout` ; seules les issues H2 sont
  observées (les courses H2 ne prouvent pas celles de PostgreSQL).
- Course : issue non déterministe, seuls les invariants sont affirmés ; la notification après enregistrement (hors
  transaction) n'est pas testée en échec.
- Clôture d'un centre, jours fermés du planning : un seul jour férié (`center_holiday`) ; pas de `center_closure_day`.
- Aucun test frontend.
