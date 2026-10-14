---
status: proposed
date: 2026-10-10
slice: gmao-g2-intervenants
sources: [.ddd/migration/slices/gmao-g2-intervenants.md, gmao-g1-plans-maintenance.md (méthode), ADR 0016/0019/0021, .ddd/research/mutation-testing-pit.md]
---
# Filet de caractérisation — G2 intervenants

## Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle, JPA réel sur **H2**), `/api/v1/gmao/intervenants`, plus lecture SQL de `gmao_intervenants` pour les effets de bord. Aucun mock du code caractérisé. Réutilise l'infrastructure G1 : `GoldenMaster` (rendu `public`, surcharge `verify(Path, …)`), même scrubber (alias UUID, masquage des détails techniques `INTERNAL_ERROR`/`DATA_ACCESS_ERROR`), mêmes conventions. Corps lus en `BigDecimal` exact : l'échelle JSON fait partie du contrat.
- Test : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/intervenants/IntervenantHttpCharacterizationIntegrationTest.java` (39 tests).
- Goldens : `backend/platform/src/test/resources/characterization/gmao/intervenants/*.approved.json` (82). Approuver : `-DargLine=-Dcharacterization.approve=true` (revue humaine du diff obligatoire).
- Pas de Testcontainers (Docker arrêté) : schéma généré par Hibernate, pas par Flyway ; rejeu PG16 possible plus tard (`backend/scripts/gmao-pg16-rejeu.sh`, non requis ici ; les dépassements `NUMERIC`/longueurs et le tri `nom` sont à reconfirmer).

## Scénarios (39 tests)
| Domaine | Scénarios |
|---|---|
| Autorisation | non-ADMIN -> 403 sur POST, GET liste, PUT, desactiver (aucune donnée modifiée) ; anonyme -> 403 vide ; `GET /{id}` : 500 (admin et non-admin), anonyme 403 (KB-22) |
| POST enregistrer | nominal INTERNE 201 + ligne en base ; EXTERNE sans coordonnées ni tarif ; JSON minimal ; centre/actif/id du corps ignorés (centre = principal) ; nom non rogné, doublons acceptés ; validations nom vide/blanc/absent, nature absente/blanche/null (400 `VALIDATION_ERROR`) ; nature inconnue ou minuscules (400 `BAD_REQUEST`) ; corps absent, JSON illisible, media-type (500) |
| Tarif / arrondis | négatif -> 400 ; 0, `-0` acceptés ; null accepté ; échelle JSON conservée à la réponse (45.50, 45.5, 45, 12.345, 1E+1) vs base `NUMERIC(10,2)` (12.345 -> 12.35, 12.344 -> 12.34) ; 99999999.99 accepté, 100000000 -> 500 `DATA_ACCESS_ERROR` ; chaîne numérique coercée, texte -> 500 |
| Longueurs | nom 255 accepté / 256, téléphone 51, email 256 -> 500 `DATA_ACCESS_ERROR` |
| GET liste | périmètre centre, tri `nom`, désactivés inclus, centre B, centre vide ; pagination page/size, page hors limites, `size=0`/`page=-1` 400, `size=abc` 500, `size=100000` accepté |
| PUT modifier | nominal (nom, nature, coordonnées, tarif ; id/centre/actif inchangés) ; remplacement total (omis effacés) ; désactivé reste désactivé ; tarif vide/0/négatif ; nom vide, nature absente/inconnue ; inconnu/id invalide/autre centre (400) ; corps absent (500) |
| POST desactiver | nominal (autres champs intacts, jamais supprimé) ; idempotent (2 appels identiques, déjà désactivé) ; inconnu/invalide/autre centre |
| Routes absentes | `/reactiver`, `/activer`, DELETE, PATCH -> 500 ; aucune réactivation ni suppression |
| Parcours | enregistrer -> réviser le tarif (55.555) -> lister -> désactiver -> modifier encore |
| Lectures indirectes (clôture d'intervention) | créer/démarrer/terminer une intervention via HTTP : INTERNE -> `MAIN_OEUVRE` 90 min x 35,00 = 52,5000 ; EXTERNE -> `INTERVENANT` 150 min x 120,50 ; arrondi heures 20 min (0,33) et 55 min (0,92) ; sans tarif / tarif 0 / sans intervenant / autre centre -> aucune ligne ; désactivé valorisé ; tarif révisé avant clôture pris en compte |

## Non-déterminisme
`UUID.randomUUID()` -> alias `<UUID_GENERE_n>` (fixtures `99995200-…`) ; aucune horloge dans les réponses (la clôture n'est comparée que sur `lignesCout`, dates d'intervention fixes) ; liste triée côté serveur par `nom` (déterministe) ; messages Spring/Jackson/SGBD masqués ; nom de classe d'enum réduit. Aucun seam.

## Mesures
- Suite : `IntervenantHttpCharacterizationIntegrationTest` 39/39, **3 exécutions vertes consécutives en ordre aléatoire** (`-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`), 0 flaky. Tests G1 non rejoués ici (seule modif : visibilité de `GoldenMaster`, rétrocompatible).
- PIT 1.30.0 (+ junit5-plugin 1.2.3, ligne de commande comme G1, classpath `target/classes` + dépendances, script du scratchpad dérivé de G1) sur `Intervenant` (la cible du plan) : **16 mutants, 16 tués (100 %)**, couverture de lignes 11/13 (85 %, voir limites pour les 2 lignes non couvertes), force des tests 100 %. Cible ≥ 70 % atteinte. Rapport : `backend/platform/target/pit-reports-g2/`. Adapter, entité, DTO, contrôleur non mutés (mutation limitée au domaine, comme G1).
- Couverture de branches par outil : non mesurée (pas de JaCoCo configuré).

## Limites : ce qui N'EST PAS verrouillé
- Gardes `centreId == null` et `type == null` de `Intervenant.creer`/`modifier` (inatteignables par HTTP : centre du principal, `@NotBlank` sur la nature) : ce sont probablement les 2 lignes non couvertes par PIT (attribution non vérifiée ligne à ligne).
- Contraintes PostgreSQL réelles (NUMERIC(10,2), longueurs, types) et Flyway : H2 uniquement ; comportement exact des dépassements, collation du tri `nom`, concurrence (deux PUT simultanés : dernier gagnant, pas de verrou optimiste).
- Le texte des messages 500 (Spring/Jackson/SGBD), l'ordre des propriétés JSON hors golden, en-têtes, performances (p95 ADR 0021).
- Consommateurs non rejoués : rapport « bon d'intervention » (`BonInterventionReportService`, nom de l'intervenant : lu mais PDF non caractérisé), sélecteurs de l'UI (`gmao-api.service.ts`), autres routes qui lisent `gmao_intervenants` (ex. liste/détail d'intervention affichant le nom de l'intervenant : non vérifié ici).
- Clôture : seule la valorisation automatique est verrouillée (pas la rectification, l'ajout manuel de ligne ni l'interaction avec une ligne manuelle existante, G4).
- Interactions des fiches équipement/statistiques : G6.

## Bugs connus
`.ddd/safety-net/known-bugs.md`, section G2 : KB-GMAO-22 à KB-GMAO-31 (GET /{id} inexistant en 500, 400 au lieu de 404, pas de réactivation, désactivation idempotente, échelle du tarif, longueurs en 500, pagination non bornée, enum exposée, PUT total, tarif lu à la clôture).

## Lancer
Depuis `backend/` : `./mvnw -o -pl platform -am test -Dtest=IntervenantHttpCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` (ordre aléatoire : ajouter `-DargLine='-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`).
