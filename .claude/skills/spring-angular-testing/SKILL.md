---
name: spring-angular-testing
description: Fast, reliable test strategy for Spring Boot + Angular legacy refactoring — test level selection, Spring context-cache rules, Testcontainers singleton/reuse, characterization at the HTTP boundary, ArchUnit, PIT scoped/incremental, affected-only Maven/Angular commands. Use when writing characterization tests, adapters or reviewing test speed.
---

# Tests rapides — Spring Boot + Angular

> **Statut des faits : non vérifié par Context7** (clé invalide / web indisponible à la rédaction). Principes stables,
> mais **noms d'annotations, versions et options** à confirmer avec
> `/ddd-research spring-test,testcontainers,pitest,archunit,angular-testing` avant de les figer dans le code (ex.
> `@MockBean` est remplacé par `@MockitoBean` dans les versions récentes de Spring Framework).

## 1. Choisir le niveau le moins cher qui prouve le comportement

| Besoin                                   | Niveau                                                                       | Coût                                               |
|------------------------------------------|------------------------------------------------------------------------------|----------------------------------------------------|
| Règle métier / calcul                    | JUnit pur (domaine)                                                          | ms                                                 |
| Use case                                 | JUnit + fakes in-memory                                                      | ms                                                 |
| Contrat HTTP (statut, JSON, validation)  | `@WebMvcTest` / MockMvc                                                      | ~1-3 s, contexte web seul                          |
| Requêtes JPA, mapping                    | `@DataJpaTest` + conteneur partagé                                           | ~3-6 s                                             |
| Chaîne complète legacy (caractérisation) | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + REST-assured/WebTestClient | contexte complet : **un seul** pour toute la suite |
| Parcours critique UI                     | Playwright (≤ 3 par slice)                                                   | lent, rare                                         |

## 2. Cache de contexte Spring : la règle d'or

Spring réutilise un contexte de test si la **configuration est identique** (classes, profils, propriétés, mocks
déclarés, initialiseurs…). Chaque combinaison différente = un **nouveau démarrage complet**. Donc dans
`tests/characterization/**` :

- **Une seule classe de base** `@SpringBootTest` + `@ActiveProfiles("characterization")` dont héritent tous les tests ;
  aucune `@TestPropertySource`/`properties=` locale.
- **Interdits** : `@MockBean`/`@MockitoBean`/`@SpyBean` par classe, `@DirtiesContext`. Remplacer par : bean de test
  défini **une fois** dans la config partagée (ex. `Clock` fixe, faux client externe configurable par scénario), ou
  serveur HTTP simulé (WireMock/MockWebServer) démarré une fois.
- Isoler les données par **transaction rollbackée** (`@Transactional` côté test quand le test et le code partagent le
  thread) ou par `TRUNCATE`/script de nettoyage en `@BeforeEach` — jamais par redémarrage.

## 3. Base de données & conteneurs

- **Conteneur singleton** démarré une fois par JVM (static dans la classe de base ou extension JUnit globale), pas un
  conteneur par classe.
- Réutilisation entre exécutions locales : option `reuse` de Testcontainers (activation côté poste, pas en CI) — à
  vérifier.
- Alimentation : un schéma Flyway/Liquibase **identique à la prod** + fixtures minimales ; pour la caractérisation d'un
  legacy, un **dump anonymisé réduit** chargé une fois.
- Plusieurs agents/worktrees en parallèle : un **port et un schéma distincts** par worktree (variable d'environnement),
  sinon collision.

## 4. Caractérisation côté HTTP (Spring)

1. Point d'observation : l'endpoint du legacy (`query.ts entrypoints`).
2. Golden master = requête + réponse JSON **normalisée** (dates, ids générés, tri) comparée à un fichier
   `*.approved.json` ; JSONAssert en mode non strict sur l'ordre, ou ApprovalTests/Verify selon la stack (à vérifier).
3. Effets de bord : états de base relus par requête SQL normalisée, messages publiés capturés (bean de test), e-mails
   capturés.
4. Parité : **la même classe de scénarios** paramétrée par une URL de base : cible legacy puis cible nouveau code
   (identifiants `S1..Sn` du contrat).

## 5. ArchUnit (remplace `check-arch.ts` pour le Java)

Un test `ArchitectureTest` par module : règle `onionArchitecture()`/`layeredArchitecture()` (domaine ne dépend de rien,
application du domaine, adapters des deux),
`noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..")`,
et accès inter-contextes seulement via `..published..`. Il tourne dans le gate (pas à chaque édition). Garder
`check-arch.ts` pour le hook rapide.

## 6. Mutation (PIT) — hors chemin critique

Cibler **uniquement les classes de la slice** (`targetClasses`, `targetTests`), activer l'**historique incrémental**, ne
muter que le code modifié (mode basé sur SCM), lancer en **tâche de fond** pendant la revue ; les mutants survivants
deviennent des constats, pas un bloqueur. Jamais sur le projet entier.

## 7. Commandes ciblées (Maven)

`mvnd` (daemon) si disponible ; `-pl <module> -am` (module + dépendances) ; `-Dtest=Classe1,Classe2` ;
`-DfailIfNoTests=false` ; `-T 1C` seulement si les tests sont indépendants ; `-o` hors ligne. **Jamais**
`mvn clean install` global pour une slice. Toujours via `tools/ddd-scan/locked.ts maven -- …`.

## 8. Angular

- Runner du projet (Karma/Jest/Vitest) : **mode non interactif** (`--watch=false`, `--browsers=ChromeHeadless` pour
  Karma) et `--include <glob>` pour **ne lancer que les specs touchés**.
- Services : `provideHttpClientTesting`/`HttpTestingController` avec `verify()` ; composants : harness Angular
  Material/CDK ; pas de `fixture.detectChanges()` en boucle ni de `setTimeout` réels (`fakeAsync`).
- Type-check rapide : `npx tsc --noEmit -p tsconfig.app.json` ; lint ciblé :
  `npx ng lint --lint-file-patterns <fichiers>` (ou eslint direct).

## 9. Diagnostic de lenteur (à faire avant d'optimiser)

`gate-<s>.json` donne la durée par commande. Pour Maven : rapport Surefire (temps par classe) ; chercher les classes >
20 s et le nombre de « Started … in … seconds » dans les logs (= nombre de contextes Spring démarrés ; il doit être 1-3
pour la suite de caractérisation).
