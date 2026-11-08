---
slice: seance-se4-derogation-regularisation
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se4-derogation-regularisation.md, ADR 0024, 0074, 0088, 0089, .ddd/safety-net/seance-se2-validation-signature.md, known-bugs.md KB-SEA-35 à 40]
---

# Filet SE4 : dérogation de régularisation (refactoring sur place)

Remplace le fichier du 2026-11-01, qui annonçait « characterized » sans aucun test de caractérisation SE4 (il ne citait que des tests classiques et décrivait un legacy qui n'existe plus). Constat de lecture du code au 2026-11-08 : il n'existe **aucun agrégat `DerogationRegularisation`** ; la « dérogation » est le couple `seances.regularisation_deverrouillee_at/by`. Le déverrouillage (`POST /api/v1/seances/{id}/deverrouiller-regularisation`) est déjà servi par `contexts/seance` (SE2, `DeverrouillerUneSeance`) et verrouillé par les 8 goldens SE2 `deverrouillage/` (effet, idempotence, bornes de jour, statuts, 15 profils + anonyme, cloisonnement, audit, écritures simultanées, effets limités) : **non rejoués ici**. Ce qui reste hors du module et propre à SE4 : le rappel quotidien (`SeanceRegularisationScheduler`, `SeanceRegularisationService`, `NotificationService.notifySeancesARegulariser`), et la table inutilisée `derogations_regularisation` (V50).

## Point d'observation
- Déverrouillage, validation, signature, lectures : HTTP (MockMvc, contexte Spring complet, sécurité réelle, H2 `test`) et SQL de `seances`.
- Rappel (aucune route HTTP) : `SeanceRegularisationScheduler.rappelerCentre(centre, jourDeLaDemande)` du vrai bean, de visibilité paquet, **appelé par réflexion avec un jour fixe nommé** (aucun seam, aucun code de production modifié, `seamAllowed` vide) ; observé : diffusions du courtier, journal `notification_evenement`, empreinte SQL de `seances`.
- Aucun mock du code caractérisé.

## Scénarios (10 tests, 10 goldens `src/test/resources/characterization/seance/se4/<dossier>/<scénario>/*.approved.json`)
| Dossier | Classe | Tests | Contenu |
|---|---|---|---|
| rappel | `LeRappelDesSeancesARegulariser…` | 6 | nominal (3 oubliées sur 12 séances de statuts, jours et centres variés, déverrouillée exclue, lecture seule) ; fenêtre de 7 jours (-9 à +30) ; statuts comptés (8 valeurs, dont inconnu et minuscules, et un centre mêlant `CREE` et statut illisible) ; plus ancienne par date ; cloisonnement par centre, centre inconnu, deux appels = deux rappels ; lecture seule et jour de la demande |
| cycle | `LeCycleDeRegularisation…` | 4 | cycle complet rappel → verrou 422 → déverrouillage (diffusion aux infirmiers) → rappel décroît → validation infirmier → validation administrateur sans déverrouillage → rappel à zéro ; patient disparu au déverrouillage (404, aucune écriture) ; lectures (détail sans l'indicateur, liste filtrée `deverrouillee`, administrateur et infirmier) ; déverrouillée puis validée puis signée, redéverrouillage refusé 422 |

## Déterminisme
Séances à dates FIXES nommées `<D+n>` (janvier 2026, toujours passées au jour du centre, donc verrouillées) ; jour de la demande du rappel fixe ; horodatages masqués ; ids stables par alias ; données fictives ; aucun `LocalDate.now`, `Instant.now` ni fuseau système dans les tests SE4 (le harnais hérité attend seulement si le jour du centre bascule dans les 2 minutes, comme SE1 à SE3). Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`), jamais `-Dcharacterization.approve=true`.

Exécutions : suites `characterization.seance.**` (SE1 à SE4, 102 tests) 3 fois consécutives (2 en ordre par défaut, 1 en `-Dsurefire.runOrder=random`) : 102 verts à chaque fois, 0 flaky.

## covered.json
Ajoutés : `SeanceRegularisationService`, `SeanceRegularisationScheduler` (exercés par les scénarios `rappel/` et `cycle/`). Déjà présents et exercés : `SeanceRestController`, `NotificationService`, `SeanceUseCase`, `SeanceRepositoryAdapter`. Le module `contexts/seance` n'est pas dans `covered.json` (hors `legacyPaths`).

## Limites (non verrouillé)
- Le travail planifié public `rappelerRegularisations()` (cron 07:00, jour UTC, tous les centres de `centers`) n'est pas rejoué : il lit l'heure (KB-SEA-36). Seule la méthode par centre l'est ; l'isolement d'erreur d'un centre est dans `SeanceRegularisationSchedulerTest` (mocks).
- `derogations_regularisation` : la table n'existe pas dans le schéma H2 (Flyway V50 seulement) ; la valeur -1 est verrouillée, pas son contenu sous PostgreSQL (`backend/scripts/seance-pg16-rejeu.sh`).
- Droits par profil, audit, cloisonnement, concurrence du déverrouillage : verrouillés par SE2 (non dupliqués) ; le rappel n'a ni route ni droit ; la visibilité par rôle de la diffusion (`targetRoles: ADMIN`) est filtrée côté client, non testée ici.
- Couverture de branches non mesurée, aucune mutation exécutée (aucun outil configuré, tests Spring lents) ; la couverture annoncée est celle des scénarios.
- Lecture de la liste par un profil autre qu'administrateur et infirmier ; liste du centre B : seul le code 200 est verrouillé.
- Schéma PostgreSQL non rejoué ; JWT réel et licences non testés.

## Bugs consignés
KB-SEA-35 à KB-SEA-40 (`.ddd/safety-net/known-bugs.md`), aucun corrigé.

## Questions ouvertes
Créer l'agrégat `DerogationRegularisation` (motif, autorité, date) ou retirer la table V50 (KB-SEA-40) ; borner ou non le verrou et le rappel (KB-SEA-37, 13) ; clé déterministe du rappel (KB-SEA-35) ; jour du centre pour le rappel (KB-SEA-36).
