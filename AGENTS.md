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
    - **La clé de cache doit toujours inclure le `centerId`** (voir règle multi-centre, section 2) pour éviter toute
      fuite de données entre centres.
    - Toute opération d'écriture (create/update/delete) qui invalide une donnée cachée doit déclencher l'éviction
      correspondante (`@CacheEvict`) de façon cohérente.
- Un agent IA qui ajoute une méthode de lecture fréquente sur une donnée peu volatile doit évaluer si elle doit être
  cachée, et proposer la configuration de cache correspondante.

---

## 7. Tests — couverture **OBLIGATOIRE** (unitaires + intégration)

Tout code généré doit être accompagné de tests. Un code métier livré sans test doit être considéré comme incomplet.

- **Backend** :
    - Tests unitaires (JUnit 5 + Mockito) pour la logique de `domain/` et `application/` : services de domaine, use
      cases, en mockant les ports.
    - Tests d'intégration (`@SpringBootTest`, H2 en mémoire) pour les adaptateurs (`infrastructure/persistence`,
      `web/rest`) : repository adapters, controllers REST, sécurité, scheduling.
    - Toute règle de scoping multi-centre (section 2) doit être vérifiée par au moins un test (ex : vérifier qu'une
      requête sans/avec mauvais `centerId` ne retourne pas de données d'un autre centre).
- **Frontend** :
    - Tests unitaires (Vitest) pour les stores NgRx Signals, services, pipes, et logique pure des composants.
    - Tests d'intégration / end-to-end (Playwright) pour les parcours utilisateurs critiques (connexion, création
      patient, séance, mouvement de stock…).
- **Règle générale** : toute nouvelle fonctionnalité (méthode de service, endpoint REST, composant, store) générée par
  un agent IA doit être livrée avec ses tests correspondants dans la même réponse/commit, pas comme une suite séparée à
  faire "plus tard".

---

## 8. Base de données

- **Dev / Démo** : H2 en mémoire (`MODE=PostgreSQL`) — démarre automatiquement, sans Docker.
- **Production** : PostgreSQL 16.
- Le code (requêtes, migrations, types de colonnes) doit rester **compatible avec les deux** : éviter les fonctions
  spécifiques à un seul moteur.
- `db/schema.sql` — DDL pour les tables sans `@Entity` JPA (tables référentielles).
- `db/seed.sql` — Données de démo, toujours exécuté en dev (`spring.sql.init.mode: always`).
- `ddl-auto: update` gère automatiquement les tables mappées par JPA.
- Toutes les clés primaires sont des `UUID` ; tous les timestamps sont en UTC.

---

## 9. Developer Workflows

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

## 10. Reporting

- Templates JasperReports dans `backend/src/main/resources/reports/*.jrxml` (compilés en `.jasper`).
- Export PDF/HTML via `openhtmltopdf`.
- Endpoints de reporting dans `infrastructure/reporting/` et `web/rest/DocumentRestController.java`.

---

## 11. Configuration clé (variables d'environnement)

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

## 12. Récapitulatif des règles OBLIGATOIRES pour tout agent IA

1. **Architecture hexagonale + DDD** côté backend — sans exception.
2. **Angular 22** (zoneless, NgRx Signals, Material) pour tout code frontend — sans exception.
3. **Isolation multi-centre systématique** (`centerId`) sur toute donnée, API, cache, WebSocket.
4. Le `domain/` ne dépend jamais de Spring/JPA.
5. **Principes SOLID** respectés dans toute classe/service/composant généré.
6. **Mise en cache obligatoire** des données fréquemment lues et peu volatiles (référentiels, détail/liste/comptage
   patient…), avec clé de cache incluant systématiquement le `centerId`.
7. **Couverture de tests obligatoire** : tests unitaires (logique domaine/application, stores, services) **et** tests
   d'intégration (adaptateurs, endpoints REST, parcours e2e) pour toute fonctionnalité générée.
8. i18n obligatoire (`ngx-translate`) pour toute chaîne affichée côté UI.
9. Toute nouvelle fonctionnalité de cache doit enregistrer son nom dans `CacheConfig.java`.
10. Compatibilité H2 (dev) / PostgreSQL (prod) pour tout SQL généré.

**Un agent (Claude, Copilot, ou autre) qui génère du code ne respectant pas ces règles doit être considéré en erreur et
le code doit être corrigé avant d'être accepté.**
