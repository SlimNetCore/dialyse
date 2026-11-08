---
slice: seance-se5-forfait-surcharge
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se5-forfait-surcharge.md, ADR 0024, 0062, 0074, 0088, known-bugs.md KB-SEA-41 à 48]
---

# Filet SE5 : forfait surchargé (refactoring sur place)

Remplace le fichier du 2026-11-01, qui annonçait « characterized » sans aucun test de caractérisation SE5 : il ne citait que des tests classiques et décrivait des règles fausses (« ADMIN ou médecin », « premier auteur conservé », « forfait d'un autre centre refusé » non prouvés). Constat de lecture du code au 2026-11-08 : la surcharge n'est PAS migrée. Écriture : `PUT /api/v1/seances/{id}/forfait` dans `SeanceRestController.updateForfait` (legacy, `SeanceApplicationService` -> `SeanceDomainService.updateForfait` -> `Seance.modifierForfait`, catalogue `SeanceForfaitCatalogAdapter`). Lecture du forfait courant (surcharge, sinon PEC) : déjà servie par `contexts/seance` via le port publié `ForfaitsFournis` (adaptateur `ForfaitsFournisLegacy`, mêmes requêtes que le legacy).

Écrivain concurrent hors slice : Facturation `SeancesDeFacturationJdbc.poserLeForfait` (changement de forfait de simulation), couvert par ses propres goldens ; son écart avec la Séance est consigné (KB-SEA-47), non rejoué.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`) + SQL des colonnes `forfait_override_*` de `seances` + journal d'audit. Aucun mock du code caractérisé, **aucun seam** (`seamAllowed` vide, `seams.md` inchangé).

## Scénarios (16 tests, 16 goldens `src/test/resources/characterization/seance/se5/<dossier>/<scénario>/*.approved.json`)
| Dossier | Tests | Contenu |
|---|---|---|
| surcharge | 10 | nominal (la surcharge prime sur la PEC, instantané code/nom/prix/auteur/instant, détail à 7 clés, liste, le référentiel change : l'instantané reste, aucune diffusion, statut inchangé) ; remplacement (F1 puis F2 puis F2 : tout écrasé, instant renouvelé, aucun historique) ; prix (absent, 0.00, 12.50, 100, 99999999.99) ; auteur (libre, nettoyé, absent/vide/espaces 400, 100 car. ok, 101 car. : 500) ; matrice des 6 statuts (dont `EN_COURS` illisible) ; séance facturée (422, colonnes inchangées) ; paramètres (16 cas : séance/forfait inconnus, forfait d'un autre centre, champs absents ou mal formés, corps illisible, centre du corps contre session) ; droits (15 profils + anonyme) ; cloisonnement A/B ; audit |
| forfait_courant | 6 | priorité surcharge > effectif > demandé ; PEC couvrant le jour contre repli sur la plus récente ; repli effectif d'abord ; aucun forfait (null) ; statut de la PEC ignoré (6 statuts) ; forfait d'un autre centre ignoré |

Déjà verrouillé ailleurs et **non rejoué** : création/consultation/liste (SE1, forme du détail et de la liste), droits de lecture, goldens Facturation `simulation/exclusion-forfait` (49) et `PolitiqueDeForfaitApplique` (RG-FAC-021), cahier de dialyse.

## Déterminisme
Séances à dates FIXES nommées `<D+n>` (aucun `LocalDate.now`, `Instant.now`, fuseau système ni cron lu dans les tests SE5 ; le jour du centre n'intervient pas). L'instant de la surcharge est masqué (`<HORODATAGE>`, « renseigne »), sa variation est observée par comparaison de deux lectures brutes (garde de 2 ms entre requêtes du harnais racine). Ids stables par alias, données fictives. Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`), jamais `-Dcharacterization.approve=true`.

Exécutions : SE5 seule 3 fois (1 passe de génération rouge, puis 2 vertes dont 1 en `-Dsurefire.runOrder=random`) puis `characterization.seance.**` (SE1 à SE5, 118 tests) en ordre aléatoire : verts ; suite complète `./mvnw -o test -fae` voir la réponse à l'appelant. 0 flaky observé.

## Résultats clés (risques d'argent et droits, KB-SEA-41 à 48)
Prix absent/0 accepté ; auteur libre (n'importe quel administrateur au nom d'un autre) ; statut non contrôlé hors `FACTUREE` (séance `ABSENT` surchargée) ; surcharge sans historique ni trace du montant dans l'audit ; forfait courant qui ignore le statut de la PEC ; Facturation n'écrit pas `forfait_override_code` ; entrées mal formées en 500. Droits : seul `ADMIN` (le commentaire de l'ancien fichier parlait de médecin : faux).

## Limites (NON verrouillé)
- Pas de couverture de branches ni de mutation mesurées (pas d'outil lancé sur la zone ; PIT absent du dépôt à ce jour) : la couverture est celle des scénarios ci-dessus.
- Le chemin Facturation `poserLeForfait` et l'interaction des deux écrivains (code non écrit, statuts différents) ne sont pas rejoués (KB-SEA-47, lecture du code).
- Aucun test avec TVA, arrondi à trois décimales ni prix négatif : le prix vient de `forfait.prix` (DECIMAL(10,2)), le référentiel n'est pas validé ici.
- Deux surcharges simultanées sur la même séance (concurrence) non testées ; H2 et non PostgreSQL (le dépassement de 100 caractères y donne la même erreur technique, non rejoué).
- Les anciens tests classiques (`SeanceDetailsIntegrationTest`, `SeanceDomainServiceTest`) ne sont pas des filets de parité.
- `SeanceRestController.updateForfait` retombe sur `LocalDate.now()` si la date de séance est nulle : chemin inatteignable (colonne non nulle), non testé.

## covered.json
Ajoutés : `SeanceForfaitCatalogPort`, `SeanceForfaitCatalogAdapter`, `UpdateSeanceForfaitRequest` (exercés par les scénarios `surcharge/`). Déjà présents et exercés : `SeanceRestController`, `SeanceApplicationService`, `SeanceDomainService`, `Seance`, `SeanceRepositoryAdapter`, `SeanceJpaEntity`, `SeanceFactureeNonModifiableException`, `ApiExceptionHandler`, `infrastructure/acl/seance/*.java`.
