# 📑 INDEX — Rapports & Documentation du Projet

**Mis à jour :** 2026-06-12  
**Phases complétées :** 0-8  
**Prochaine phase :** 9 (CI/CD)

---

## 📚 Rapports de Phase

### ✅ Phases complétées

| Phase            | Date       | Rapport                                                 | Durée   | Status          |
|------------------|------------|---------------------------------------------------------|---------|-----------------|
| **0**            | 2026-03-14 | `AUDIT_PHASE_0.md`                                      | 1 day   | ✅ Complete      |
| **0** (synthèse) | 2026-03-14 | `PHASE_0_SYNTHESE.md` + `PHASE_0_INDEX.md`              | -       | ✅ Complete      |
| **1**            | 2026-04-15 | `PHASE_1_MIGRATION_COMPLETE.md`                         | 2 days  | ✅ Complete      |
| **1** (rapport)  | 2026-04-20 | `PHASE_1_RAPPORT.md`                                    | -       | ✅ Complete      |
| **2**            | 2026-04-25 | `PHASE_2_RAPPORT.md`                                    | 2 days  | ✅ Complete      |
| **3**            | 2026-05-02 | `PHASE_3_RAPPORT.md`                                    | 2 days  | ✅ Complete      |
| **4**            | 2026-05-02 | `PHASE_4_RAPPORT.md`                                    | 1 day   | ✅ Complete      |
| **5**            | 2026-05-03 | `PHASE_5_COMPLETION_REPORT.md` + `PHASE_5_RAPPORT.md`   | 1 day   | ✅ Complete      |
| **5** (fichiers) | 2026-05-03 | `PHASE_5_FILES_MANIFEST.md` + `PHASE_5_FINAL_REPORT.md` | -       | ✅ Complete      |
| **5** (résumé)   | 2026-05-03 | `PHASE_5_EXECUTION_SUMMARY.md`                          | -       | ✅ Complete      |
| **6**            | 2026-05-03 | `PHASE_6_RAPPORT.md`                                    | 1 day   | ✅ Complete      |
| **7**            | 2026-05-03 | `PHASE_7_RAPPORT.md`                                    | 2 days  | ✅ Complete      |
| **8**            | 2026-06-12 | **`PHASE_8_RAPPORT.md`**                                | 0.5 day | 🟢 **ACTUELLE** |

### 📋 À démarrer

| Phase | Titre               | Ressources                                      |
|-------|---------------------|-------------------------------------------------|
| **9** | CI/CD & Déploiement | `PHASE_9_PLAN_DETAILLE.md` ← Plan détaillé prêt |

---

## 🔍 Documentation de référence

### Architecture & Design

| Document           | Contenu                      | Localisation                            |
|--------------------|------------------------------|-----------------------------------------|
| Context Map DDD    | Bounded contexts, aggregates | `docs/architecture/context-map.md`      |
| ADR-001            | Multi-centre (single DB)     | `docs/architecture/adrs/ADR-001-*.md`   |
| ADR-002            | Architecture hexagonale      | `docs/architecture/adrs/ADR-002-*.md`   |
| Glossaire métier   | Terminologie + définitions   | `docs/domain/glossary.md`               |
| Definition of Done | Critères d'acceptation       | `docs/governance/definition-of-done.md` |

### Backlog & Planification

| Document      | Contenu                        |
|---------------|--------------------------------|
| MVP Backlog   | Epics, user stories, priorités | `docs/backlog/mvp-backlog.md` |
| Plan global   | 6 sprints (8-12 semaines)      | `plan/plan.md` |
| Execution log | Historique + actions           | `plan/execution-log.md` |

### DevTools & Infrastructure

| Document         | Contenu                |
|------------------|------------------------|
| DevTools summary | Intégration outils dev | `DEVTOOLS_IMPLEMENTATION_SUMMARY.md` |
| DevTools guide   | Guide d'utilisation    | `DEVTOOLS_INTEGRATION_GUIDE.md` |

---

## 📖 Guides pour développeurs

### Cette session (Phase 8)

| Fichier                            | Utilité                      | Public     |
|------------------------------------|------------------------------|------------|
| **`SESSION_SYNTHESE_20260612.md`** | Recap exact de cette session | Tous       |
| **`TECH_SUMMARY_PHASE_8.md`**      | Guide technique complet      | Devs       |
| **`PHASE_9_PLAN_DETAILLE.md`**     | Checklist Phase 9 détaillée  | CI/CD Lead |

### Backend

| Fichier             | Contenu                  |
|---------------------|--------------------------|
| `backend/README.md` | Build, run, test backend |
| `backend/HELP.md`   | FAQ Spring Boot          |
| `backend/pom.xml`   | Dépendances, plugins     |

### Frontend

| Fichier                 | Contenu                   |
|-------------------------|---------------------------|
| `frontend/README.md`    | Build, run, test frontend |
| `frontend/angular.json` | Config Angular CLI        |
| `frontend/package.json` | Dépendances npm           |

### Infrastructure

| Fichier              | Contenu                     |
|----------------------|-----------------------------|
| `docker-compose.yml` | Stack local (DB + services) |
| `infra/README.md`    | Docker, deployment notes    |

---

## 🎯 Quick Links par rôle

### Pour un **Product Owner**

1. 📖 Lire : `plan/plan.md` (35 min)
2. 📖 Lire : `docs/domain/glossary.md` (10 min)
3. ✅ Check : `plan/execution-log.md` (dernières actions)

### Pour un **Backend Developer** (rejoindre)

1. 📖 Lire : `TECH_SUMMARY_PHASE_8.md` (15 min)
2. ▶️ Run : `mvnw clean test` (3 min)
3. 📖 Lire : `docs/architecture/context-map.md` (10 min)
4. 🔍 Check : `backend/src/main/java/com/hemodialyse/backend/domain/` (structure)

### Pour un **Frontend Developer** (rejoindre)

1. 📖 Lire : `TECH_SUMMARY_PHASE_8.md` (15 min)
2. ▶️ Run : `npm test -- --watch=false` (5 min)
3. 📖 Lire : `docs/architecture/context-map.md` (10 min)
4. 🔍 Check : `frontend/src/app/features/` (routes)

### Pour un **DevOps/CI-CD Lead** (Phase 9)

1. 📖 Lire : `PHASE_9_PLAN_DETAILLE.md` (30 min)
2. 📖 Lire : `TECH_SUMMARY_PHASE_8.md` section Stack (10 min)
3. 🔍 Check : `docker-compose.yml` (setup local)
4. ▶️ Plan : Jenkinsfile, .gitlab-ci.yml, .github/workflows/

### Pour un **QA / Test Lead**

1. 📖 Lire : `TECH_SUMMARY_PHASE_8.md` section Tests (10 min)
2. ▶️ Run : Tests FE & BE localement
3. 📖 Lire : `PHASE_9_PLAN_DETAILLE.md` section "E2E tests" (pour Phase 9)

### Pour un **Security Reviewer**

1. 📖 Lire : `docs/architecture/adrs/ADR-001-*.md` (multi-centre)
2. 📖 Lire : `docs/architecture/adrs/` (autres ADRs)
3. 🔍 Check : `backend/src/main/java/.../SecurityConfig.java`
4. 🔍 Check : JWT implementation

---

## 📊 Statistiques Projet

```
Codebase:
  Backend:   75 fichiers Java, 7 modules métier
  Frontend:  53+ fichiers TypeScript (standalone components)
  Languages: 4 (ar, en, fr, kab)
  DB:        12 migrations Flyway, schema multi-centre

Tests:
  Backend:   11 tests JUnit5 ✅ 100%
  Frontend:  4 tests Vitest ✅ 100%
  Coverage:  ~60% (BE) / TBD (FE)

Build:
  Backend:   Maven 3.9
  Frontend:  Angular 21.2, npm 10.9.2
  Containers: Docker + Docker Compose

Docs:
  Total files: 8 phases + 3 guides = 11 rapports
  Architecture docs: 5 (context-map + ADRs)
  Backlog: 1 file
```

---

## 🔗 Liens importants

### Forges de code

- [ ] GitHub: `https://github.com/...hemodialyse` (à remplir)
- [ ] GitLab: `https://gitlab.com/...hemodialyse` (à remplir)

### Services

- 📍 **Local API** : `http://localhost:8090`
- 📍 **Local Frontend** : `http://localhost:4200`
- 📍 **Swagger API** : `http://localhost:8090/swagger-ui.html`
- 📍 **Database** : `localhost:5432/hemodialyse`

### CI/CD (à mettre en place Phase 9)

- [ ] Jenkins: `https://jenkins.example.com/job/hemodialyse/`
- [ ] GitLab CI: Project CI/CD Pipelines
- [ ] GitHub Actions: `.github/workflows/` status badges

---

## 🚀 Prochaines phases (Roadmap)

```
Phase 9  [2-3 jours]     ← À démarrer
  → Jenkinsfile
  → .gitlab-ci.yml
  → .github/workflows/ci.yml
  → Docker registry push
  ✨ Objectif: Deployment en 1 clic

Phase 10 [1-2 semaines]
  → Tests e2e complets
  → Monitoring & Observabilité
  → Performance tuning
  ✨ Objectif: Production-ready

Phase 11 [2-4 semaines]
  → Pilot deployment (1-2 centres)
  → SLA monitoring
  → Rollback procedures
  ✨ Objectif: Go-live pilot
```

---

## 📝 Notes importantes

### Secrets & Sécurité

- ⚠️ `JWT_SECRET` : Ne **jamais** commiter (utiliser env vars)
- ⚠️ `application.yml` : Pas de credentials en dur
- ⚠️ Voir `TECH_SUMMARY_PHASE_8.md` section Security

### Multi-centre (CRITIQUE)

- ✅ Toute requête doit avoir `center_id`
- ✅ Vérifier `@PreAuthorize` sur chaque endpoint
- ✅ Uniques scopées par centre
- 📖 Voir : `docs/architecture/adrs/ADR-001-single-db-multicentre.md`

### Dépendances importantes

- Spring Boot 4.0.0 (Java 21)
- Angular 21.2
- PostgreSQL 15+
- Flyway pour migrations
- JWT / Spring Security

---

## 🆘 Troubleshooting rapide

| Problème            | Solution                                              |
|---------------------|-------------------------------------------------------|
| Tests FE échouent   | Check: `frontend/src/app/app.spec.ts` ✅ corrigé       |
| Build FE lent       | Clear cache: `npm ci --no-cache`                      |
| Tests BE timeout    | Augmenter: `mvnw -DargLine="-Xmx2g" test`             |
| DB connexion fail   | Check: `docker-compose ps`, `docker logs`             |
| Swagger not loading | Vérifier Spring Security `/swagger-ui/**` non-blocked |

---

## 📞 Support

### Problème identifié ?

1. 📝 Créer issue dans forge (GitHub/GitLab)
2. 🔗 Référencer le rapport de phase pertinent
3. ✅ Vérifier checklist dans Definition of Done (`docs/governance/`)
4. 🚀 Assigner à developer nearest

### Documentation approfondie ?

- 📚 Lire les ADRs : `docs/architecture/adrs/`
- 📚 Lire le glossaire : `docs/domain/glossary.md`
- 📚 Check plan détaillé : `plan/plan.md`

---

## 📅 Calendar

| Date           | Phase | Milestone                        |
|----------------|-------|----------------------------------|
| 2026-03-14     | 0-1   | Audit & bootstrap                |
| 2026-04-15     | 1-2   | Architecture, DDD                |
| 2026-04-25     | 2-3   | Tests, refactor                  |
| 2026-05-02     | 3-5   | Features, optim                  |
| 2026-05-03     | 5-7   | Final, cleanup                   |
| **2026-06-12** | **8** | **Stabilisation** ← YOU ARE HERE |
| 2026-06-13+    | 9+    | CI/CD, Deploy                    |

---

**Fichier généré :** 2026-06-12  
**Maintenance :** Keep updated after each phase completion  
**Format :** Markdown (GitHub/GitLab compatible)

---

**Happy coding! 🚀**

