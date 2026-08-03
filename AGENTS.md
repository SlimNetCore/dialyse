# AGENTS.md — Guide for AI Coding Agents

> **⚠️ INSTRUCTION POUR TOUT AGENT IA (Claude, GitHub Copilot, ou autre)**
> Ce fichier doit être lu et respecté **avant toute génération, modification ou suggestion de code** dans ce dépôt.
> Les règles marquées **OBLIGATOIRE** ne sont pas négociables : tout code généré qui ne les respecte pas doit être
> considéré comme invalide et corrigé.

---

## 1. Contexte métier du projet

**Hemodialyse** est une plateforme de gestion multi-centres d'hémodialyse.

L'application couvre le suivi complet de patients dialysés à travers plusieurs centres de dialyse, et s'organise autour
des modules fonctionnels suivants :

| Module            | Description                                                                                    |
|-------------------|------------------------------------------------------------------------------------------------|
| **Administratif** | Gestion des patients, dossiers administratifs, admissions, centres, utilisateurs               |
| **Médical**       | Suivi médical des patients, séances de dialyse, prescriptions, constantes, historique clinique |
| **Stock**         | Gestion des stocks de consommables/médicaments, mouvements, bons de commande/livraison         |
| **Réglements**    | Facturation, règlements, prise en charge, suivi financier                                      |
| **Paramétrage**   | Configuration des référentiels (médecins, machines, protocoles, centres, etc.)                 |

Chaque module doit être développé en respectant l'architecture hexagonale et les frontières de domaine (voir section 3).

---

## 2. Multi-centre — règle transversale **OBLIGATOIRE**

L'application est **multi-centres d'hémodialyse**. Toute donnée métier appartient à un centre (`centerId` / `id_centre`,
UUID).

- **Aucune donnée ne doit être créée, lue, modifiée ou supprimée sans être rattachée à un `centerId`.**
- Cette règle s'applique à :
  - toutes les tables et entités JPA (colonne `center_id`) ;
  - tous les endpoints REST (filtrage systématique par centre courant) ;
  - les souscriptions WebSocket (`/topic/center/{centerId}/events`) ;
  - les clés de cache (le `centerId` doit toujours faire partie de la clé pour éviter les fuites de données
    inter-centres).
- Côté frontend, le centre actif provient de `AppShellStore.currentCenterId()`.
- Un agent IA qui génère du code d'accès aux données (repository, service, controller, store) doit **systématiquement**
  vérifier/propager le `centerId`, même si ce n'est pas explicitement demandé dans le prompt.

---

## 3. Architecture Backend — Hexagonale + DDD **OBLIGATOIRE**

- **Backend** : Spring Boot 4 / Java 21, architecture **hexagonale** et **Domain-Driven Design**, port 8090.
- Cette architecture est **obligatoire** pour tout nouveau code, tout refactoring, et toute suggestion — pas
  d'exception, même pour du code "rapide" ou "temporaire".

```
backend/src/main/java/com/hemodialyse/backend/
├── domain/         # Domaine pur : modèles métier, ports (interfaces), services de domaine
│   ├── patient/port/PatientUseCase.java          ← Port In (exemple)
│   └── patient/port/PatientRepositoryPort.java   ← Port Out (exemple)
├── application/    # Orchestration : auth, notification, query, system
└── infrastructure/ # Adaptateurs :
    ├── web/rest/               # Controllers REST (un par entité de domaine)
    ├── web/dto/                # DTOs request/response
    ├── persistence/adapter/    # RepositoryAdapter implémentant le Port Out
    ├── persistence/entity/     # Entités JPA @Entity
    ├── security/               # JwtAuthenticationFilter, SecurityConfig
    ├── websocket/              # Endpoint STOMP `/ws`, broker `/topic/**`
    ├── scheduling/             # Jobs planifiés (ex: ExpirationAlertScheduler)
    └── config/                 # CacheConfig (Caffeine), SeedPasswordInitializer
```

**Règles strictes de dépendance (DDD/hexagonal) :**

- Les classes de `domain/` **ne doivent jamais importer Spring/JPA**. Toutes les annotations Spring vivent dans
  `infrastructure/`.
- Le `domain/` ne dépend de rien d'autre ; `application/` dépend de `domain/` ; `infrastructure/` dépend de
  `application/` et `domain/` — jamais l'inverse.
- Toute nouvelle fonctionnalité métier doit d'abord être modélisée en termes de domaine (agrégats, ports) avant d'écrire
  l'infrastructure.
- **Nouvel agrégat de domaine** : créer `domain/<name>/{model,port,service}` +
  `infrastructure/persistence/{entity,repository,adapter}` + `web/{dto,rest}` stubs.

---

## 4. Architecture Frontend — Angular 22 **OBLIGATOIRE**

- **Tout code frontend généré doit être en Angular 22** (zoneless), NgRx Signals, Angular Material, port 4200.
- Aucune autre version d'Angular, aucun autre framework (React, Vue, etc.) ne doit être proposé ou généré pour ce
  projet, même à titre d'exemple.
- **Pas de `zone.js`** — l'application utilise `provideZonelessChangeDetection()`. Utiliser les signals, pas
  `markForCheck()`.
- Utiliser en priorité le control flow Angular 22 (`@if`, `@for`) pour tout nouveau code ; les écrans existants avec
  `*ngIf`/`*ngFor` sont en cours de migration et ne doivent pas servir de modèle pour du code neuf.
- Tous les formulaires de l'application doivent être en signal forms

```
frontend/src/app/
├── core/
│   ├── api/        # Services HTTP (BackendApiService est le client principal)
│   ├── auth/       # Guards et helpers d'authentification
│   ├── i18n/       # Bootstrap/providers de langue
│   ├── layout/     # Shell + layout applicatif de haut niveau
│   ├── state/      # Stores NgRx Signal globaux (AuthStore, AppShellStore, ReferentialsStore)
│   ├── theme/      # Thème Material + état d'apparence
│   └── ws/         # WebSocketService (STOMP sur SockJS)
├── features/       # Modules fonctionnels lazy-loaded
│   ├── patient/state/  # Stores niveau feature (PatientListStore, PatientWizardStore…)
│   ├── stock/          # Dashboard stock + mouvements + bons
│   ├── admin/          # Administration utilisateurs/rôles
│   ├── seances/        # Calendrier + dashboard séances
│   └── reporting/      # Modèles documents + impression
└── shared/         # Composants réutilisables (confirm-dialog, searchable-select…)
```

### Pattern de state management

Tous les stores utilisent `@ngrx/signals` `signalStore()` + `withDevtools()` (depuis
`@angular-architects/ngrx-toolkit`). Une factory existe pour les données référentielles :
```ts
// core/state/referential-store.factory.ts
createReferentialStore({storeName: 'MedecinsStore', load: (api, id) => api.getMedecins(id)})
```

### Flux d'authentification
- JWT stocké en cookies HttpOnly (`HEMO_AUTH` / `HEMO_REFRESH`) — jamais en localStorage.
- `authInterceptor` ajoute `withCredentials: true` sur tous les appels `/api/v1/`.
- Les réponses 401 déclenchent un refresh silencieux via `/api/v1/auth/refresh` ; en cas d'échec → redirection `/login`.
- L'App initializer appelle `AuthStore.initFromServer()` avant le rendu des routes protégées.

### Autres règles frontend

- **i18n** : toutes les chaînes UI doivent utiliser `ngx-translate` (pipe `| translate`), langue par défaut `fr` (
  fichiers sous `frontend/public/i18n/*.json`).
- **Nouveau module fonctionnel** : ajouter un fichier `*.routes.ts` et le lazy-load dans `app.routes.ts`.


---

# 4.1 Responsive Design — OBLIGATOIRE ET NON NÉGOCIABLE

## Principe général

Toutes les interfaces utilisateur de l'application doivent être entièrement responsive.

Cette règle est obligatoire et ne souffre aucune exception.

Aucune fonctionnalité, aucun écran, aucun dialogue, aucun tableau, aucun formulaire et aucun composant ne peut être
livré si son comportement responsive n'a pas été conçu, implémenté et vérifié.

Un écran fonctionnel sur desktop mais inutilisable sur tablette ou mobile doit être considéré comme non conforme et
refusé.

---

## Terminaux supportés

L'application doit fonctionner correctement sur :

### Mobile

```text
22
320px à 767px
```

## 5. Principes SOLID **OBLIGATOIRE**

Tout code généré, backend comme frontend, doit respecter les principes **SOLID** :

- **S — Single Responsibility** : une classe/service/composant = une seule raison de changer. Ne pas mélanger logique
  métier, accès aux données et présentation dans la même classe.
- **O — Open/Closed** : privilégier l'extension (nouvelles implémentations de ports, nouvelles stratégies) plutôt que la
  modification de code existant qui fonctionne.
- **L — Liskov Substitution** : toute implémentation d'un port (`*Port`, `*UseCase`) ou d'une interface doit pouvoir
  remplacer une autre implémentation sans casser le comportement attendu par l'appelant.
- **I — Interface Segregation** : préférer plusieurs ports/interfaces fins et spécifiques (ex: `PatientRepositoryPort`,
  `PatientQueryPort`) plutôt qu'une interface fourre-tout.
- **D — Dependency Inversion** : le `domain/` définit des ports (interfaces) ; `infrastructure/` fournit les
  implémentations. Les couches de haut niveau ne dépendent jamais des détails d'implémentation (JPA, HTTP client, etc.),
  conformément à l'architecture hexagonale de la section 3.

Un agent IA qui propose une classe monolithique, un couplage direct à une implémentation concrète, ou une interface trop
large doit reconsidérer sa proposition avant de la générer.

---

## 6. Stratégie de cache **OBLIGATOIRE**

Certaines données sont lues très fréquemment et changent peu : elles doivent être mises en cache pour éviter des
allers-retours inutiles en base de données.

- **Données à mettre en cache par défaut** : référentiels (médecins, machines, protocoles, centres…), détail patient,
  liste patient, comptages patient — voir les TTL déjà définis dans la table de configuration (section 11) :
  `CACHE_TTL_REFERENTIALS`, `CACHE_TTL_PATIENT_DETAIL`, `CACHE_TTL_PATIENT_LIST`, `CACHE_TTL_PATIENT_COUNT`.
- **Données à ne pas mettre en cache (ou avec prudence)** : données très volatiles ou temps réel (statut de séance en
  cours, événements poussés en WebSocket), données financières sensibles nécessitant une fraîcheur garantie.
- **Implémentation** :
  - Le cache (Caffeine) est configuré dans `infrastructure/config/CacheConfig.java` — toute nouvelle fonctionnalité
    cacheable doit y enregistrer son nom et son TTL.
  - Les annotations `@Cacheable` / `@CacheEvict` / `@CachePut` se placent dans la couche `infrastructure/` (services
    applicatifs ou adaptateurs), jamais dans le `domain/` pur.
  - **La clé de cache doit toujours inclure le `centerId`** (voir règle multi-centre, section 2) pour éviter toute fuite
    de données entre centres.
  - Toute opération d'écriture (create/update/delete) qui invalide une donnée cachée doit déclencher l'éviction
    correspondante (`@CacheEvict`) de façon cohérente.
- Un agent IA qui ajoute une méthode de lecture fréquente sur une donnée peu volatile doit évaluer si elle doit être
  cachée, et proposer la configuration de cache correspondante.

---

## 7. Tests — couverture **OBLIGATOIRE** (unitaires + intégration, **back ET front**)

Tout code généré doit être accompagné de tests. Un code métier livré sans test doit être considéré comme incomplet.

**Les tests unitaires ET les tests d'intégration sont obligatoires à la fois côté backend et côté frontend.** Aucune des
deux couches ne peut se limiter à un seul type de test — ce n'est pas "unitaire au back / intégration au front" ou
l'inverse, les deux couches doivent avoir les deux types de tests :

|              | Tests unitaires                                                                                                    | Tests d'intégration                                                                                                                                       |
|--------------|--------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Backend**  | JUnit 5 + Mockito — logique de `domain/` et `application/` : services de domaine, use cases, en mockant les ports. | `@SpringBootTest` (H2 en mémoire) — adaptateurs (`infrastructure/persistence`, `web/rest`) : repository adapters, controllers REST, sécurité, scheduling. |
| **Frontend** | Vitest — stores NgRx Signals, services, pipes, logique pure des composants.                                        | Playwright — parcours utilisateurs critiques de bout en bout (connexion, création patient, séance, mouvement de stock…).                                  |

Précisions complémentaires :

- Toute règle de scoping multi-centre (section 2) doit être vérifiée par au moins un test **côté backend** (ex :
  vérifier qu'une requête sans/avec mauvais `centerId` ne retourne pas de données d'un autre centre) **et**, quand c'est
  pertinent, un test d'intégration **côté frontend** (ex : le store/composant n'affiche que les données du centre
  actif).
- Toute règle de cache (section 6) ajoutée ou modifiée côté backend doit être couverte par un test d'intégration
  vérifiant le comportement du cache (hit/miss, éviction, isolation par `centerId`).
- **Règle générale** : toute nouvelle fonctionnalité générée par un agent IA — méthode de service backend, endpoint
  REST, composant Angular, store NgRx Signals — doit être livrée avec **son test unitaire ET son test d'intégration
  correspondants**, dans la même réponse/commit, jamais comme une suite séparée à faire "plus tard".

---

## 8. Interdiction des éléments dépréciés (deprecated) — vérification systématique **OBLIGATOIRE**

Aucun code généré ou modifié ne doit utiliser une API, une méthode, une annotation, une bibliothèque ou une
configuration marquée comme **dépréciée (deprecated)** — que ce soit côté backend ou côté frontend.

**Backend (Java / Spring) :**

- Ne jamais utiliser une classe/méthode annotée `@Deprecated`, ni une API signalée "deprecated" dans la Javadoc
  officielle.
- Ne jamais utiliser une configuration Spring Security dépréciée (ex. `WebSecurityConfigurerAdapter`) — utiliser le
  style actuel à base de bean `SecurityFilterChain`.
- Utiliser systématiquement `jakarta.*` (et non `javax.*`) pour la persistance/servlet, conformément à Spring Boot 4.
- Avant d'ajouter une dépendance Maven, vérifier qu'elle est activement maintenue et non dépréciée ou archivée.

**Frontend (Angular) :**

- Ne jamais utiliser une API Angular marquée deprecated dans le changelog/documentation officielle (anciennes APIs de
  routing, anciens décorateurs, etc.).
- Utiliser le control flow actuel (`@if`/`@for`) et non `*ngIf`/`*ngFor` (déjà précisé en section 4).
- Utiliser les fonctions RxJS actuelles (`firstValueFrom`, `lastValueFrom`) et non les méthodes dépréciées comme
  `toPromise()`.
- Avant d'ajouter un package npm, vérifier son statut (absence de mention "deprecated", dépôt non archivé).

**Règle générale pour tout agent IA :**

- **À chaque prompt, avant de répondre**, l'agent doit systématiquement vérifier si le code concerné (fichier modifié,
  code environnant, dépendance utilisée) contient des éléments dépréciés — ce n'est pas une vérification ponctuelle mais
  une étape obligatoire de **chaque** tâche de génération ou de modification de code, même si la demande initiale ne
  mentionne pas ce sujet.
- Si du code déprécié est détecté, l'agent doit **toujours proposer une alternative récente et actuellement recommandée
  **, avec une brève explication du remplacement (nouvelle API/méthode à utiliser), sans attendre que l'utilisateur le
  demande explicitement.
- Lorsqu'un agent modifie un fichier existant contenant du code déprécié, il doit **proposer son remplacement** par
  l'alternative actuelle recommandée plutôt que de le reproduire ou de l'étendre.
- En cas de doute sur le statut "deprecated" d'une API ou d'une librairie (les connaissances d'un agent IA peuvent être
  obsolètes), l'agent doit vérifier la documentation officielle à jour avant de générer du code.
- Le code déprécié existant, repéré en dehors du périmètre de la tâche demandée, doit être signalé (commentaire ou
  remarque) même s'il n'est pas corrigé immédiatement.

---

## 9. Base de données

- **Dev / Démo** : H2 en mémoire (`MODE=PostgreSQL`) — démarre automatiquement, sans Docker.
- **Production** : PostgreSQL 16.
- Le code (requêtes, migrations, types de colonnes) doit rester **compatible avec les deux** : éviter les fonctions
  spécifiques à un seul moteur.
- `db/schema.sql` — DDL pour les tables sans `@Entity` JPA (tables référentielles).
- `db/seed.sql` — Données de démo, toujours exécuté en dev (`spring.sql.init.mode: always`).
- `ddl-auto: update` gère automatiquement les tables mappées par JPA.
- Toutes les clés primaires sont des `UUID` ; tous les timestamps sont en UTC.

---

## 10. Developer Workflows

### Run locally (sans Docker — H2 démarre automatiquement)
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Frontend (terminal séparé)
cd frontend && npm start
```

### Run avec PostgreSQL réel
```bash
docker-compose up -d          # démarre postgres:16 + redis:7
# Puis définir les variables d'env :
# DB_URL=jdbc:postgresql://localhost:5432/hemodialyse
# DB_USERNAME=hemo_user  DB_PASSWORD=hemo_pass  DB_DRIVER=org.postgresql.Driver
```

### Build & test
```bash
cd backend  && ./mvnw verify
cd frontend && npm run build
cd frontend && npm test        # vitest
cd frontend && npm run e2e     # Playwright
```

- E2E lit `E2E_BASE_URL` (défaut `http://127.0.0.1:4200`), `E2E_USERNAME`, `E2E_PASSWORD`, et `E2E_CENTER_ID` (
  optionnel).

### Key URLs (dev)
- Swagger UI: `http://localhost:8090/swagger-ui.html`
- H2 Console: `http://localhost:8090/h2-console`
- Actuator health: `http://localhost:8090/actuator/health`

---

## 11. Reporting
- Templates JasperReports dans `backend/src/main/resources/reports/*.jrxml` (compilés en `.jasper`).
- Export PDF/HTML via `openhtmltopdf`.
- Endpoints de reporting dans `infrastructure/reporting/` et `web/rest/DocumentRestController.java`.

---

## 12. Configuration clé (variables d'environnement)

| Var                        | Défaut                  | Usage                                    |
|----------------------------|-------------------------|------------------------------------------|
| `DB_URL`                   | H2 en mémoire           | URL JDBC de la base                      |
| `JWT_SECRET`               | `change-me-…`           | **À surcharger obligatoirement en prod** |
| `CORS_ORIGINS`             | localhost:4200 + vercel | Origines CORS autorisées                 |
| `CACHE_TTL_REFERENTIALS`   | `PT6H`                  | TTL des caches référentiels              |
| `CACHE_TTL_PATIENT_DETAIL` | `PT15M`                 | TTL des caches patient detail            |
| `CACHE_TTL_PATIENT_LIST`   | `PT3M`                  | TTL des caches listes patient            |
| `CACHE_TTL_PATIENT_COUNT`  | `PT3M`                  | TTL du cache de comptage patient         |
| `STOCK_DEMO_DATA`          | `true`                  | Seed des données de démo stock           |
| `REPORTS_DIR`              | classpath               | Override du répertoire de base jasper    |

---

## 13. Récapitulatif des règles OBLIGATOIRES pour tout agent IA

1. **Architecture hexagonale + DDD** côté backend — sans exception.
2. **Angular 22** (zoneless, NgRx Signals, Material) pour tout code frontend — sans exception.
3. **Isolation multi-centre systématique** (`centerId`) sur toute donnée, API, cache, WebSocket.
4. Le `domain/` ne dépend jamais de Spring/JPA.
5. **Principes SOLID** respectés dans toute classe/service/composant généré.
6. **Mise en cache obligatoire** des données fréquemment lues et peu volatiles (référentiels, détail/liste/comptage
   patient…), avec clé de cache incluant systématiquement le `centerId`.
7. **Couverture de tests obligatoire côté backend ET côté frontend** : tests unitaires (logique domaine/application côté
   back, stores/services côté front) **et** tests d'intégration (adaptateurs/endpoints REST côté back, parcours e2e côté
   front) pour toute fonctionnalité générée — aucune des deux couches ni aucun des deux types de test ne peut être omis.
8. **Aucun élément déprécié (deprecated)** — API, méthode, annotation, dépendance ou configuration — ne doit être
   utilisé, ni côté backend ni côté frontend. **À chaque prompt**, l'agent doit vérifier la présence de code déprécié et
   proposer systématiquement une alternative récente, sans attendre qu'on le lui demande.
9. i18n obligatoire (`ngx-translate`) pour toute chaîne affichée côté UI.
10. Toute nouvelle fonctionnalité de cache doit enregistrer son nom dans `CacheConfig.java`.
11. Compatibilité H2 (dev) / PostgreSQL (prod) pour tout SQL généré.
12. Tout fichier généré ou modifié par un agent IA doit être ajouté au versionnement Git (`git add`) dans la même tâche, sauf s'il est explicitement couvert par `.gitignore`.
13. Un agent IA **ne doit jamais créer de commit Git automatiquement** sans demande explicite de l'utilisateur ; par
    défaut il prépare/stage (`git add`) et laisse l'utilisateur effectuer le commit.

# 14. DDD Tactique et Découpage des Domaines — OBLIGATOIRE

## Objectif

Toute évolution ou refactoring doit viser une architecture métier explicite basée sur le Domain Driven Design (DDD).

Les décisions de conception doivent partir du métier et non de la structure technique existante.

## Découpage des domaines

Les domaines fonctionnels du projet doivent être considérés comme des Bounded Contexts distincts :

- Administratif
- Médical
- Stock
- Règlements
- Paramétrage

Chaque domaine peut être subdivisé en sous-domaines disposant d'une responsabilité métier unique.

Exemple :

Patient
├── Gestion administrative
├── Admissions
├── Affectation aux centres
└── Historique administratif

Il est interdit de mélanger plusieurs responsabilités métier dans un même module.

## Structure obligatoire d'un domaine

Chaque nouveau domaine ou sous-domaine doit respecter la structure suivante :

domain/
├── aggregate
├── entity
├── valueobject
├── event
├── repository
├── service
├── specification
└── port

## Agrégats

Chaque domaine doit identifier explicitement ses agrégats.

Un agrégat :

- protège les invariants métier ;
- contrôle les modifications d'état ;
- constitue la frontière transactionnelle.

Exemples :

- PatientAggregate
- SeanceDialyseAggregate
- BonCommandeAggregate
- FactureAggregate

Aucune modification métier significative ne doit contourner l'agrégat.

## Aggregate Root

Chaque agrégat doit posséder une Aggregate Root.

Exemples :

- Patient
- SeanceDialyse
- Facture
- BonCommande

Toutes les opérations métier doivent passer par la racine d'agrégat.

## Entités

Les entités doivent :

- posséder une identité métier ;
- avoir un cycle de vie ;
- encapsuler leur comportement.

Une entité ne doit jamais être un simple conteneur de données.

## Value Objects

Tout concept métier immuable doit être modélisé sous forme de Value Object.

Exemples :

- Email
- NumeroTelephone
- Adresse
- NumeroDossier
- Montant
- Devise
- Poids
- TensionArterielle

Les validations doivent vivre dans le Value Object.

Aucun primitive obsession n'est autorisé.

## Domain Events

Tout événement métier significatif doit être représenté par un Domain Event.

Exemples :

- PatientCreated
- PatientTransferred
- SeanceStarted
- SeanceCompleted
- StockThresholdReached
- InvoiceGenerated

Les événements doivent être exprimés dans le langage métier.

## Domain Services

Les Domain Services sont autorisés uniquement lorsque le comportement :

- n'appartient pas naturellement à une entité ;
- n'appartient pas à un Value Object.

Les Domain Services ne doivent pas devenir des services métier génériques.

## Specifications

Les règles métier complexes doivent être encapsulées dans des Specifications.

Exemples :

- PatientCanStartSessionSpecification
- PatientIsEligibleForDialysisSpecification
- StockMovementAllowedSpecification

## Repositories

Le domaine ne contient que des interfaces.

Exemples :

- PatientRepositoryPort
- SessionRepositoryPort
- InvoiceRepositoryPort

Les implémentations restent dans infrastructure/.

# 15. Utilisation Obligatoire des Patterns Métier

## Strategy Pattern

Toute variation de comportement métier doit être implémentée via des stratégies.

Interdiction de multiplier les if/else ou switch pour exprimer des comportements métier.

Exemples :

- PricingStrategy
- NotificationStrategy
- BillingStrategy
- PrescriptionStrategy

## Factory Pattern

Les objets métier complexes doivent être créés via des Factories lorsque nécessaire.

Exemples :

- PatientFactory
- SeanceFactory
- InvoiceFactory

## Specification Pattern

Toute règle métier complexe ou combinable doit utiliser le Specification Pattern.

# 16. Module Shared et Shared Kernel — OBLIGATOIRE

Les éléments mutualisés entre domaines doivent être placés dans un Shared Kernel.

shared/

Il peut contenir :

- Entity
- AggregateRoot
- ValueObject
- DomainEvent
- BusinessException
- Identifier
- Money
- Address
- Email
- Pagination

Le Shared Kernel ne doit contenir aucune logique métier spécifique à un domaine.

# 17. Nettoyage de Code Obligatoire

Avant chaque Pull Request, merge ou livraison :

## Backend

Supprimer systématiquement :

- endpoints inutilisés ;
- DTO inutilisés ;
- use cases inutilisés ;
- handlers inutilisés ;
- repositories inutilisés ;
- services inutilisés ;
- événements inutilisés ;
- configurations inutilisées.

## Frontend

Supprimer systématiquement :

- composants inutilisés ;
- services inutilisés ;
- stores inutilisés ;
- pipes inutilisés ;
- directives inutilisées ;
- routes inutilisées ;
- assets inutilisés ;
- styles inutilisés ;
- modèles inutilisés.

## Objectif

Le projet ne doit contenir aucun code mort.

Tout élément conservé doit avoir au moins un consommateur identifié.

# 18. Audit Front ↔ Back Obligatoire

Toute nouvelle fonctionnalité ou refactoring important doit documenter :

Composant Angular
→ Store
→ Service
→ Endpoint REST
→ Use Case
→ Domaine
→ Repository

Cette traçabilité est obligatoire afin de :

- détecter les endpoints morts ;
- détecter les fonctionnalités non utilisées ;
- éviter la duplication ;
- simplifier les refactorings.

# 19. Internationalisation (i18n) Renforcée — OBLIGATOIRE

En complément des règles ngx-translate existantes :

- aucune chaîne visible par l'utilisateur ne peut être hardcodée ;
- chaque nouvelle clé doit être présente dans toutes les langues du projet ;
- les langues obligatoires du projet sont **fr, ar, kab, en** ;
- toute nouvelle clé i18n doit être ajoutée et traduite dans **fr + ar + kab + en** dans la même tâche ;
- toute clé supprimée doit être retirée de l'ensemble des fichiers de traduction ;
- tout écran doit être intégralement traduisible.

## Audit i18n obligatoire

Avant livraison :

Identifier :

- Clés manquantes
- Clés orphelines
- Clés dupliquées
- Textes hardcodés
- Langues incomplètes

## Objectif

- 100 % des écrans traduits
- 100 % des clés synchronisées
- 0 texte hardcodé

# 20. Gouvernance Architecturale

Toute proposition de code par un agent IA doit vérifier les points suivants avant génération :

✅ Respect DDD

✅ Respect Architecture Hexagonale

✅ Respect SOLID

✅ Respect règle Multi-Centre (centerId)

✅ Respect stratégie de cache

✅ Respect Angular 22

✅ Respect NgRx Signals

✅ Respect i18n

✅ Respect couverture de tests

✅ Absence de code mort

✅ Absence de dépendances dépréciées

✅ Respect découpage métier

✅ Respect Bounded Contexts

✅ Respect Agrégats

✅ Respect Value Objects

✅ Respect Domain Events

Toute réponse d'un agent qui ne respecte pas cette checklist doit être considérée comme invalide et corrigée avant
intégration.

**Un agent (Claude, Copilot, ou autre) qui génère du code ne respectant pas ces règles doit être considéré en erreur et
le code doit être corrigé avant d'être accepté.**

# 21. Responsivité obligatoire pour tout développement frontend

**Tout composant ou développement côté frontend doit être responsive.** Cette exigence s'applique sans exception aux
pages, composants Angular, formulaires, onglets, tableaux, cartes, graphiques, dialogues, menus et états vides.

- Chaque interface doit être conçue, implémentée et vérifiée au minimum sur mobile (320px à 767px), tablette et desktop.
- Aucun contenu ne doit provoquer de débordement horizontal de la page : les tableaux et données denses utilisent un
  conteneur de défilement horizontal local lorsque nécessaire.
- Les champs, boutons et actions doivent rester lisibles, atteignables et utilisables au tactile sur petit écran.
- Les grilles doivent se replier de manière cohérente, les actions secondaires ne doivent pas masquer les actions
  principales, et les dialogues doivent rester contenus dans le viewport.
- Toute modification frontend doit inclure une vérification responsive automatisée ou manuelle avant livraison.

