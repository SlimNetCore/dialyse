---
status: proposed
date: 2026-10-11
slice: gmao-g3-cycle-vie-equipement
sources: [.ddd/migration/slices/gmao-g3-cycle-vie-equipement.md (note ADR 0024 : Strangler abandonné, refonte sur place), ADR 0019/0024, gmao-g1-plans-maintenance.md, gmao-g2-intervenants.md, .ddd/research/mutation-testing-pit.md]
---
# Filet de caractérisation — G3 cycle de vie de l'équipement (refonte sur place)

## Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle, JPA réel sur **H2**), `/api/v1/gmao/equipements` (8 routes), plus lectures hors contrôleur : tables `gmao_equipements`, `gmao_equipement_statut_historique`, `notification_evenement` (alerte durable), `CacheManager` (`ref.generateurs`), ports `SalleVuePort`, `ReferentialRepositoryPort`, `EquipementStatutHistoriqueRepositoryPort` + `IndisponibiliteCalculator` ; interventions créées/démarrées/terminées par HTTP. Aucun mock du code caractérisé, aucun seam, aucun fichier de production modifié. Réutilise `GoldenMaster` (G1) et la convention G2 (scrubber UUID -> alias, détails techniques masqués) ; instants (`dateCreation`, `dateModification`, horodatage inséré dans `observations`) -> `<HORLOGE>` ; `dateInstallation` (fournie par le client) reste visible.
- Test : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/equipements/EquipementHttpCharacterizationIntegrationTest.java` (54 tests).
- Goldens : `backend/platform/src/test/resources/characterization/gmao/equipements/*.approved.json` (126). Approuver : `-DargLine=-Dcharacterization.approve=true` (revue humaine du diff obligatoire).
- Pas de Testcontainers : schéma Hibernate (pas Flyway). Voir limites.

## Scénarios (54 tests)
| Domaine | Scénarios |
|---|---|
| Autorisation | non-ADMIN -> 403 sur les 8 routes (aucune donnée touchée) ; anonyme 403 vide ; `reformer` : ADMIN sans `GMAO_REFORME` -> 403, **`GMAO_REFORME` seul -> 200** (KB-32), technicien -> 403 |
| POST enregistrer | nominal (réponse, ligne en base, `EN_SERVICE`, `creePar`, journal `null>EN_SERVICE`, aucune alerte, cache évincé) ; JSON minimal ; id/statut/centre/creePar du corps ignorés ; date avec décalage (restituée en UTC) ; prix (0, `-0`, échelle reçue vs base, 3 décimales arrondies, négatif 400, dépassement 500) ; validations code/désignation/type/date (400), bornes 50/255 ; type inconnu/minuscules (400 enum), date illisible ou sans décalage (500) ; longueurs non validées (fabricant 101, localisation 256, numéroSérie 101 -> 500) ; corps absent/illisible/media-type (500) |
| Unicité du code (R7, KB-09) | doublon exact 400 ; variantes casse/espaces acceptées (`g-01`, ` G-01`, `G-01 `, `G -01`) ; code non rogné ; même code dans un autre centre accepté ; code d'un supprimé logique -> 500 (contrainte base) |
| Capacité des salles | générateur de trop 422 `SALLE_CAPACITE_DEPASSEE` (POST et PUT, en s'excluant soi-même) ; autre type, salle sans capacité, salle inconnue, salle d'un autre centre : aucune limite ; réformé/désactivé/supprimé libèrent la place, hors service la garde |
| GET /{id} et liste | nominal, réformé, inconnu/autre centre 404 sans corps, id invalide 400, supprimé logique 200 ; liste : centre, tri `dateCreation` décroissant, supprimés exclus, filtre `statut` (exact, inconnu, minuscules, vide), pagination (page hors limites, `size=0`, `page=-1`, `size=abc`, `size=100000`) |
| PUT modifier | nominal (code/type/statut/date du corps ignorés, aucun journal/alerte, cache évincé) ; remplacement total (omis effacés, dont `salleId`) ; accepté sur hors service/réformé ; validations ; inconnu/autre centre 400 ; corps absent 500 ; prix 0 et échelle |
| Hors service / réactiver / réformer / observation | nominal (statut, observations, `modifiePar`, journal, alerte avec patients, cache) ; non-générateur sans alerte ; second appel 422 sans journal ni alerte ; réformé 422 ; raison/motif/observation vides 400 ; inconnu/autre centre 400 ; id invalide 400 ; corps absent 500 ; réactivation seulement depuis `HORS_SERVICE` ; réforme depuis `HORS_SERVICE` sans alerte ; observations cumulées (`\n`), acceptées sur réformé, motif du journal = cumul |
| Table-driven (7 statuts × commandes) | matrices goldens : hors-service (générateur et station d'eau), réactiver, réformer (générateur, station d'eau), observation, modification, démarrage d'intervention vers `EN_MAINTENANCE` / `EN_SERVICE` : http, code, statut final, journal, alertes pour chaque statut de départ |
| Alerte Planification | uniquement `GENERATEUR_DIALYSE` et précédent `EN_SERVICE` ; rôles `ADMIN,SECRETAIRE` ; patients placés (max 5 cités, total donné) ; centre B isolé ; pas d'alerte au retour en service, à la création, au PUT, à l'observation |
| Cache `ref.generateurs` | évincé par les 5 écritures (POST, PUT, hors-service, réactiver, réformer) et par le démarrage d'intervention ; non évincé par GET/liste/observation/refus ; le référentiel relit la table (générateur réformé conservé, salle) |
| Clôture d'intervention (côté équipement) | démarrage -> `EN_MAINTENANCE` (journal « Intervention démarrée », alerte, cache) ; terminer -> `EN_SERVICE` (journal « Intervention terminée », pas d'alerte) ; clôture `EN_ATTENTE_PIECE` ; états `REFORME`/`DESACTIF` refusés 400 ; démarrage sur réformé 422 ; réactiver après clôture 422 |
| Isolation de centre | 6 commandes d'un autre centre 400, GET 404, liste filtrée, rien modifié ni alerté |
| Parcours et indisponibilité | enregistrer -> hors service -> réactiver -> observer -> modifier -> réformer, états terminaux 422, journal complet ; `IndisponibiliteCalculator` sur le journal produit par HTTP : durée = intervalles `HORS_SERVICE`, la réforme clôt sans compter |

## Non-déterminisme
`UUID.randomUUID()` -> alias ; horloges -> `<HORLOGE>` (assertions relationnelles pour l'indisponibilité : durée attendue calculée sur les `changed_at` relus) ; liste triée par `dateCreation` sur des données semées à dates fixes ; messages Spring/Jackson/SGBD masqués ; centres/ids dédiés `99996300-…`. Aucun seam.

## Mesures
- Suite : 54/54 ; **3 exécutions vertes consécutives en ordre aléatoire** (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`), 0 flaky ; aucun `*.received.json` résiduel.
- PIT 1.30.0 (+ junit5-plugin 1.2.3, ligne de commande comme G1/G2, script `backend/scripts/pit-g3-equipement.sh`) sur `Equipement.java` : **55 mutants, 41 tués (75 %)**, couverture de lignes 116/122 (95 %), 0 sans couverture. Cible ≥ 70 % atteinte. Rapport : `backend/platform/target/pit-reports-g3/`. Les 14 survivants sont tous des gardes de nullité/blancs du domaine masquées par Bean Validation en amont ou atteignables seulement par une autre voie : `creer` l.55-60 (code/désignation/type/date/centre/prix), `modifier` l.144 et 147 (désignation, prix), `reformer` l.222 (motif), `ajouterObservation` l.235, `changerStatutIntervention` l.200 (état null) et l.204 (REFORME/DESACTIF : refusé avant, par `Intervention.terminer`, KB-40). Ils ne deviennent mutables que par un test unitaire du domaine, à écrire dans la refonte.
- Couverture de branches par outil : non mesurée (pas de JaCoCo configuré).

## Limites : ce qui N'EST PAS verrouillé
- **PostgreSQL réel** : contraintes d'unicité (V5 `code UNIQUE` global vs (centre, code) sous H2, KB-33), types/longueurs, collation du tri, FK. Rejeu PG16 possible plus tard (`backend/scripts/gmao-pg16-rejeu.sh`, non adapté ici). État réel des colonnes en prod (`salle_id`, `prix_acquisition`, `deleted_at` : absents des migrations relevées, créés par `ddl-auto` ?).
- **Échec de l'alerte** (« une alerte qui échoue ne fait pas échouer le changement de statut ») et non-atomicité équipement/journal/alerte : aucune injection de panne sans seam (voir `seams.md`).
- **Lecteurs SQL de `gmao_equipements`** non rejoués : `PlanningDonneesJdbcAdapter`, `OptimisationDonneesJdbcAdapter`, `MouvementPatientJdbcAdapter`, `DirectionCapaciteQueryService`, `DirectionGmaoQueryService`, `SeanceRestController:456`, `PatientRestController:335`, `ReferentialAdminTables` (usage `salle_id`) ; seuls `ReferentialRepositoryAdapter` (générateurs) et `SalleVueJdbcAdapter` le sont. Ils lisent statut, `salle_id`, `deleted_at` : à rejouer par le filet des slices concernées ou un test de lecteurs dédié avant de changer le schéma.
- **`EquipementFicheRestController`** (fiche, indisponibilité en HTTP) : G6. `IndisponibiliteCalculator` n'est vérifié qu'à travers le journal que produit G3.
- **`InterventionEquipementStatutService`, `InterventionRestController`, `GenerateurIndisponibleService`** : non modifiés et non couverts au sens `covered.json` (la clôture n'est observée que côté équipement ; rectification, lignes de coût, snapshot : G4/G2). Un nouvel écouteur d'événement remplaçant l'appel direct devra rejouer les goldens d'alerte/cache/journal de ce filet.
- Concurrence (deux écritures simultanées : dernier gagnant, pas de verrou optimiste), performances, en-têtes, ordre des propriétés JSON hors golden, texte des messages 500, horodatages absolus.
- Fichiers non protégés par `covered.json` volontairement : `StatutEquipement`, `TypeEquipement` (partagés avec G4/G6), `EquipementFicheRestController`, services ci-dessus. Les méthodes du port `EquipementRepositoryPort` utilisées par d'autres slices (`findByCentreIdAndType…`, `existsById`, `delete`, `countBy…`) ne sont exercées qu'indirectement.

## Bugs connus
`.ddd/safety-net/known-bugs.md`, section G3 : KB-GMAO-32 à KB-GMAO-41 (réforme avec la seule habilitation, unicité exacte et contrainte base, supprimé logique « ressuscité », statuts HTTP/validation, PUT total, règle d'alerte, cache/atomicité, lecteurs, intervention qui contourne le cycle de vie, journal = cumul des observations).

## Lancer
Depuis `backend/` : `./mvnw -o -pl platform -am test -Dtest=EquipementHttpCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` (ordre aléatoire : ajouter `-DargLine='-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`). Mutation : `./mvnw -o -q -pl platform -am test-compile`, `./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=<fichier>` puis `CPFILE=<fichier> bash backend/scripts/pit-g3-equipement.sh`.
