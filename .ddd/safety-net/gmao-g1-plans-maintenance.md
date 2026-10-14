---
status: proposed
date: 2026-10-09
slice: gmao-g1-plans-maintenance
sources: [.ddd/migration/slices/gmao-g1-plans-maintenance.md, ADR 0016/0019/0021, .ddd/research/approval-golden-master-java.md, .ddd/research/mutation-testing-pit.md]
---
# Filet de caractérisation — G1 plans de maintenance

## Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, chaîne Spring Security réelle, JPA réel sur **H2 mode PostgreSQL**), 9 endpoints `/api/v1/gmao/plans-maintenance`, plus lecture SQL de `gmao_plans_maintenance` pour les effets de bord. Aucun mock du code caractérisé.

Écarts assumés (décidés ici, à valider) :
- Emplacement : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/plans/` + goldens `backend/platform/src/test/resources/characterization/gmao/plans/*.approved.json` (accord A8 du plan ; Maven impose `src/test`, la règle `tests/characterization/<slice>/` n'est pas applicable). Le nom du plan `…/characterization/gmao/plans/` est respecté.
- H2 (convention des 40+ tests d'intégration existants) au lieu de Testcontainers PG16 : le daemon Docker est arrêté, `testcontainers` n'est pas au pom. Conséquence : le schéma est généré par Hibernate (`ddl-auto: update`), pas par Flyway V5/V6 ; les FK de V5 (équipement, centre, app_user) ne sont **pas** exercées. Le test de contrat PG16 reste à écrire pour la parité (`PlanMaintenanceParityIT`).
- Golden master maison (`GoldenMaster.java`, JSON relisible) au lieu d'ApprovalTests 31.0.0 (non dans `.m2`, compatibilité Jackson 3 non vérifiée) : aucune dépendance ajoutée. Approuver : `-Dcharacterization.approve=true` (revue humaine du diff obligatoire). `*.received.json` ignoré par git.

## Non-déterminisme
| Source | Traitement |
|---|---|
| Horloge (`OffsetDateTime.now`) | pas de seam ; fenêtre `[t0,t1]` de la requête -> jetons `<NOW>`, `<NOW+1W>`, `<NOW+15D>`, `<NOW+1M>`, `<NOW+3M>`, `<NOW+6M>`, `<NOW+1Y>` (la fenêtre est monotone, donc robuste à `plusMonths`) |
| `UUID.randomUUID()` | alias `<UUID_GENERE_n>` par ordre d'apparition ; UUID de fixtures fixes (`99995000-…`) |
| Ordre des listes | tri sur `designation` avant golden (ordre non garanti : KB-GMAO-20) |
| Messages techniques Spring/Jackson | détail masqué pour les 500 ; nom de classe d'enum réduit |

## Scénarios : 35 tests, 49 fichiers golden (dont +1 test, +2 goldens ajoutés au build : décalage horaire normalisé en UTC)
| Domaine | Scénarios |
|---|---|
| Autorisation | non-ADMIN -> 403 sur les 9 routes (aucune donnée modifiée) ; anonyme -> 403 vide |
| POST établir | nominal 201 + ligne en base ; centre/créateur pris du principal ; 6 fréquences ; désignation 255/256 ; validations (désignation blanche, équipement/fréquence/date absents) ; fréquence inconnue ou minuscule ; JSON illisible/absent/date illisible (500) ; équipement inconnu + date passée |
| GET /{id} | nominal ; inconnu 404 ; id invalide 400 ; autre centre 200 ; supprimé logiquement 200 |
| GET liste / actifs / en-retard / équipement | périmètre centre, statuts, suppression logique, liste vide, id invalide, vue inter-centres |
| POST executer | prochaine date pour les 6 fréquences ; plan prévu en 2099 ; 2 exécutions ; INACTIF/ARCHIVE 422 ; inconnu/id invalide 400 ; autre centre ; supprimé (résurrection) |
| POST desactiver / reactiver | transitions ACTIF<->INACTIF, 422 sur états interdits (ARCHIVE incluse), date passée acceptée, corps absent/sans date, inconnu |
| Parcours | établir -> exécuter -> désactiver -> (exécuter refusé) -> réactiver -> exécuter |

## Mesures
- Suite : 3 exécutions vertes consécutives, ordre aléatoire (`MethodOrderer$Random`), 0 flaky.
- PIT 1.30.0 (+ junit5-plugin 1.2.3, lancé en ligne de commande car le plugin Maven ne mute que les classes du module `platform` ; pom non modifié, commande dans le handoff) sur `PlanMaintenance` et `FrequenceMaintenance` : **30 mutants, 28 tués (93 %)**, couverture de lignes 94/96 (98 %), force des tests 100 %. Les 2 mutants non couverts sont `FrequenceMaintenance.getLabel/getJours` (jamais appelés par le chemin HTTP : `jours` n'est pas utilisé pour le calcul, KB-GMAO-12). Cible du plan ≥ 70 % atteinte.
- Couverture de branches non mesurée par outil (pas de JaCoCo configuré) ; ligne PIT ci-dessus.

## Limites : ce qui N'EST PAS verrouillé
- Clôture de fin de mois (exécution MENSUELLE un 31 -> `plusMonths(1)`) et borne exacte `<=` de `/en-retard` : horloge non substituable. Seam optionnel décrit dans `.ddd/safety-net/seams.md` (non appliqué).
- Contraintes et FK PostgreSQL / types `timestamptz` / Flyway V5-V6 ; comportement d'un équipement inexistant en prod (500 par FK) ; concurrence (deux exécutions simultanées, pas de verrou optimiste).
- `findActiveByEquipementId`, `countByCentreId`, `countActiveByCentreId`, `delete` (adaptateur) : non joignables via ces 9 routes (consommateurs Fiche équipement / Statistiques GMAO, slice G6) ; `GmaoStatsRestController` et `EquipementFicheRestController` non caractérisés ici.
- Messages d'erreur 500 Spring/Jackson (volontairement masqués), ordre des listes, en-têtes, performances/latence (critère p95 ADR 0021).
- Question ouverte 3 de la fiche (`archiver`) : aucun endpoint, rien à verrouiller. Question 4 répondue : KB-GMAO-17.
- Tableau de bord direction : pas de lecture de la table des plans confirmée par la fiche, non rejouée ici.

## Bugs connus
Voir `.ddd/safety-net/known-bugs.md` (KB-GMAO-06, 11-20).

## Lancer
Depuis `backend/` : `./mvnw -pl platform -am test -Dtest=PlanMaintenanceHttpCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` (ajouter `-o` hors ligne).

## Suite unique (après refactoring sur place, ADR 0024)
La même suite tourne désormais contre le nouveau code (module `contexts/gmao`), sans sous-classe de parité. Depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest='PlanMaintenanceHttpCharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (35/35). Écart décidé : KB-GMAO-14 (golden `*_nouveau_code`).
