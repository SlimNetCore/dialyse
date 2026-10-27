---
status: characterized (slice construite le 2026-10-27 : voir `.ddd/migration/slices/planification-p4-presence-cron.md` ; goldens modifiés par décision)
date: 2026-10-27
sources: .ddd/tactical/planification.md (P4), .ddd/migration/roadmap.md (§1 sexies), ADR 0049 à 0057, docs/reference/regles-de-gestion/10-infirmiers.md, known-bugs.md KB-PLN-42 à 51
---

# Filet de caractérisation — planification-p4-presence-cron

Refactoring sur place (ADR 0024) : pas de Strangler. Legacy figé ; **aucun seam, aucune modification de production**
(`seamAllowed` reste vide). Le contrôle de 07:15 est déclenché par appel direct de `PresenceInfirmierScheduler.controlerPresence()`.

## Périmètre

| Point d'entrée | Rôles observés | Lit / écrit |
|---|---|---|
| `GET /api/v1/infirmiers/presence/semaine` | ADMIN, SECRETAIRE, MEDECIN seul, MEDECIN+INFIRMIER | lecture : patients placés, roulement, absences, remplacements, ratio, fermetures |
| `GET …/presence/alertes` (horizon 1-60, défaut 14) | ADMIN, SECRETAIRE, MEDECIN+INFIRMIER | lecture |
| `GET …/presence/remplacants` | idem | lecture (dix candidats au plus) |
| `GET …/presence/charge` (mois, page, taille) | idem | lecture |
| `GET …/presence/imprimer` | idem | lecture ; crée le modèle de document du centre au premier appel |
| `PresenceInfirmierScheduler.controlerPresence()` (cron `0 15 7 * * *`) | — | `notification_evenement`, diffusions `/topic/center/{id}/events` |

Lecteurs hors module : `DirectionCapaciteQueryService` lit `planning_parametres.patients_par_poste_serie` (capacité théorique, rien à voir
avec le ratio infirmiers/patients ni avec la charge) : **non rejoué ici** (filet Direction). `SurEffectifDuRoulementLegacy`,
`SemaineDeLInfirmierLegacy`, `ReplanificationAutomatiqueService`, `OptimisationDonneesJdbcAdapter` lisent `PresenceInfirmierQueryService`
/ `PresenceDonneesPort` : exercés seulement par les effets des écritures de P3 et P8 (filets P3, P8).

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`) ; texte du PDF (OpenPDF), du HTML (PDF embarqué
décodé) et cellules du classeur XLSX ; `notification_evenement` ; canal du courtier de messages. Aucun mock.
Harnais : `…/characterization/planification/PresenceCharacterizationSupport.java` (au-dessus de P3, P2, P1).

Tests (8 classes, 75 scénarios) : `…/characterization/planification/presence/{Autorisations,SemaineDePresence,AlertesDeSousEffectif,RemplacantsProposes,ChargeMensuelle,ImpressionDeLaPresence,ControleDePresenceDe0715,EffetsDeP3SurLaPresence}…CharacterizationIntegrationTest` ;
goldens `backend/platform/src/test/resources/characterization/planification/p4-presence/` (129 fichiers, le plus long 90 lignes).
Commande :
`./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.presence.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`).

## Scénarios

| Classe | Scénarios | Verrouille |
|---|---|---|
| Autorisations | 3 | matrice 5 routes x 11 profils + anonyme ; formes de refus ; garde de centre (autre centre, inconnu, illisible) |
| SemaineDePresence | 19 | forme, statuts (couvert, sous-effectif, sans patient, fermé), surplus, ratio défaut 4 / configuré / nul / invalide, isolement et habilitation, conflits, double affectation, absents, remplaçants (ignorés si absents ou désactivés), patients sortis (transfert, décès, greffe, guérison, séjour limité), jours fermés, période, dates illisibles |
| AlertesDeSousEffectif | 11 | horizon 1-60, défaut 14, hors bornes, tri, absence remplacée ou non, ratio, fermetures, isolement, patient sorti, isolation de centre, lecture sans écriture |
| RemplacantsProposes | 7 | exclusions, score et raisons, isolement, limite de dix, paramètres, cases inconnues, jour fermé |
| ChargeMensuelle | 9 | séances, remplacements, absences aux bornes, fermetures, tri, pagination, formats du mois, mois par défaut |
| ImpressionDeLaPresence | 9 | PDF (texte lu), HTML, XLSX (cellules lues), formats inconnus, nom de fichier, modèle désactivé, provisionnement |
| ControleDePresenceDe0715 | 13 | déclaration du cron, sous-effectif, sur-effectif et heures par vacation, horizon 14 jours, jours fermés, ratio, centres sans infirmier actif ou désactivés, isolation par échec de centre, doublons |
| EffetsDeP3SurLaPresence | 5 | absence, remplacement, roulement, désactivation de fiche, écritures refusées |

## Après la construction (2026-10-27)

Les goldens d'origine ont été écrasés à l'approbation ; 28 d'entre eux changent par décision. Leur état legacy a été **reconstitué** en rejouant le
filet dans un worktree jetable de HEAD (scénarios remis dans leurs prémisses d'origine) et rangé dans `p4-presence/_ecarts-legacy/**/*.legacy.json` ;
les goldens du nouveau code sont `*_nouveau_code.approved.json`. Ces fichiers sont des rejeux, non les sorties du jour de la caractérisation.

## Non-déterminisme

Dates par alias (`<S1.LUN>`, `<J+3>`, jamais mélangés dans un scénario) ; patients placés sur les sept jours pour que les alertes ne dépendent
pas du jour de la semaine (le nom du jour est écarté des goldens d'alertes) ; attente de minuit (UTC et Alger) et scénarios ignorés entre
23:00 et 24:00 UTC (hérité de P3). La charge est lue sur un mois de référence fixe, février 2026, qui compte exactement quatre occurrences de
chaque jour (garde de l'hypothèse dans le test) : seule exception à la règle « pas de date réelle », sans dépendance à l'horloge.
Ordre des clés des payloads de notification : celui du `HashMap` de `NotificationService` (stable pour des clés `String`).

## Stabilité

Trois exécutions de la suite complète : 75 verts (ordre par défaut, puis deux ordres aléatoires `-Dsurefire.runOrder=random`) ; 0 flaky.

## Couverture et mutation

Pas de JaCoCo dans la chaîne Maven hors ligne (aucune mesure de branches) ; PIT non lancé : le domaine concerné (`bc-seance`) a ses propres
tests unitaires et le filet HTTP est trop lent pour PIT ; pas de script `pit-p4-*.sh`. Couverture raisonnée par chemin : les neuf fichiers
ajoutés à `covered.json` sont exercés par au moins un scénario ; branches du domaine de présence (case fermée, absence, remplacement doublon,
isolement, double affectation, sortie de patient) toutes visées.

## Limites (NON verrouillé)

- Valeurs absolues de la charge du mois courant ; mois autres que février 2026.
- Fuseau : la fenêtre 23:00-24:00 UTC (jour du centre différent) n'est pas rejouée ; l'heure de déclenchement réelle du cron n'est pas exercée
  (déclaration vérifiée par réflexion seulement).
- Contenu graphique du PDF (mise en page) ; seul son texte est lu. Modèle personnalisé du centre (`activeCustom`) non rejoué.
- Concurrence (écriture de P3 pendant une lecture) ; PostgreSQL (H2 seulement) ; volumétrie.
- `DirectionCapaciteQueryService`, ACL de P3, optimisation (P8) : voir leurs filets.
- Sur-effectif : observé uniquement par le job (aucune route HTTP).

## Comportements douteux

`known-bugs.md` KB-PLN-42 à KB-PLN-51 (paramètres illisibles en 500, droits hétérogènes, fuseau UTC, centres contrôlés, doublons de notification,
ratio invalide, remplaçants sans validation, charge, double affectation, formats d'impression).
