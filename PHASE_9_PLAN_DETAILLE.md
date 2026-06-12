# PLAN PHASE 9 — Mise en place CI/CD & Déploiement

**Statut :** À démarrer  
**Durée estimée :** 2-3 jours  
**Priorité :** 🔴 Haute

---

## 🎯 Objectif Phase 9

Automatiser les pipelines de build, test et déploiement sur 3 forges différentes :

1. **Jenkins** (pipeline paramétrisée)
2. **GitLab CI** (pour GitLab self-hosted)
3. **GitHub Actions** (pour GitHub.com)

Résultat : **Déploiement en 1 clic** ← image Docker push + notification

---

## 📋 Checklist détaillée

### 1. Pipeline Jenkins (Jenkinsfile) — 4h

#### Objectif

Créer un `Jenkinsfile` déclaratif (scripted ou declarative) qui orchestrate :

- Checkout Git
- Build Maven (backend)
- Test backend
- Build npm (frontend)
- Test frontend
- SonarQube scan
- Docker build + push

#### Fichier à créer

```
Jenkinsfile (root du projet)
```

#### Stages envisagés

```groovy
pipeline {
    agent any

    stages {
        stage('Checkout') { ... }
        stage('Build Backend') { ... }
        stage('Test Backend') { ... }
        stage('Build Frontend') { ... }
        stage('Test Frontend') { ... }
        stage('Quality Gate (SonarQube)') { ... }
        stage('Build Docker Images') { ... }
        stage('Push to Registry') { ... }
        stage('Notify') { ... }
    }

    post {
        always {
            publishHTML(...)
            junit allowEmptyResults: true, testResults: '**/test-*.xml'
        }
        success { slowNotify('✅ Build réussi') }
        failure { slackNotify('❌ Build échoué') }
    }
}
```

#### Points clés

- Paralléliser test FE & BE
- Archiver les artifacts (dist/, target/)
- Webhook GitHub/GitLab pour déclenchement auto

#### Effort

- 2h de scripting Groovy (Jenkins Experience required)
- 1h de debug avec Jenkins local
- 1h de docs + README

---

### 2. GitLab CI (.gitlab-ci.yml) — 3h

#### Objectif

Créer `.gitlab-ci.yml` compatible runner Docker, avec :

- Stages séquentiels : build → test → scan → push
- Caching des dépendances (node_modules, .m2)
- Artifact retention

#### Fichier à créer

```
.gitlab-ci.yml (root du projet)
```

#### Structure envisagée

```yaml
stages:
  - build
  - test
  - scan
  - docker
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2"
  NODE_ENV: "production"

cache:
  paths:
    - .m2/repository
    - frontend/node_modules

# Jobs détaillés...
build_backend:
  stage: build
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn -q clean compile
  only:
    - merge_requests
    - main

build_frontend:
  stage: build
  image: node:22
  script:
    - npm ci
    - npm run build
  artifacts:
    paths:
      - frontend/dist/
    expire_in: 1 day

test_backend:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  # ...
```

#### Effort

- 1h de YAML structure
- 1h de debug avec local GitLab Runner
- 1h de docs + exemples

---

### 3. GitHub Actions (.github/workflows/ci.yml) — 3h

#### Objectif

Créer un workflow GitHub Actions pour :

- Build sur push/PR à main
- Publier les résultats de tests
- Créer GitHub Release avec artifacts

#### Fichier à créer

```
.github/workflows/ci.yml
```

#### Structure envisagée

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: cd backend && ./mvnw -q clean test
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: backend-test-reports
          path: backend/target/surefire-reports

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: 'frontend/package-lock.json'
      - run: cd frontend && npm ci && npm test -- --watch=false
      - run: cd frontend && npm run build
      - uses: actions/upload-artifact@v4
        with:
          name: frontend-dist
          path: frontend/dist/
```

#### Effort

- 1.5h de YAML + GitHub Actions syntax
- 1h de debug localement (act tool)
- 0.5h de docs

---

### 4. Dockerfiles & Docker Compose — 2h

#### Optimisation requise

##### Backend Dockerfile

```dockerfile
# Multi-stage build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline
COPY backend .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8090
CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

##### Frontend Dockerfile

```dockerfile
# Multi-stage
FROM node:22 AS builder
WORKDIR /app
COPY frontend/package*.json .
RUN npm ci
COPY frontend .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

##### Mise à jour docker-compose.yml

```yaml
services:
  # ... DB + pgadmin existants

  backend:
    build:
      context: .
      dockerfile: backend/Dockerfile
    ports:
      - "8090:8090"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/hemodialyse
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - db

  frontend:
    build:
      context: .
      dockerfile: frontend/Dockerfile
    ports:
      - "4200:80"
    depends_on:
      - backend
```

#### Effort

- 1h de Dockerfile optimization
- 0.5h test local (`docker-compose up`)
- 0.5h debug et docs

---

### 5. SonarQube Integration (optionnel mais recommandé) — 2h

#### Objectif

Ajouter quality gate SonarQube pour détecteur :

- Code smells
- Duplication
- Coverage

#### Fichier à créer

```
sonar-project.properties (root)
```

#### Contenu

```properties
# Sonar configuration
sonar.projectKey=hemodialyse
sonar.projectName=Hemodialyse
sonar.projectVersion=1.0-phase9
# Modules
sonar.modules=backend,frontend
backend.sonar.projectBaseDir=backend
backend.sonar.sources=src/main/java
backend.sonar.tests=src/test/java
backend.sonar.java.binaries=target/classes
frontend.sonar.projectBaseDir=frontend
frontend.sonar.sources=src
frontend.sonar.exclusions=node_modules/**
frontend.sonar.typescript.lcov.reportPaths=coverage/lcov.info
# Quality Gate
sonar.qualitygate.wait=true
sonar.coverage.exclusions=**/*.spec.ts
```

#### Intégration workflows

```yaml
# Ajouter dans Jenkinsfile, gitlab-ci.yml et GitHub Actions:
- name: SonarQube Scanner
  run: |
    docker run --rm \
      -e SONAR_HOST_URL=https://sonarqube.example.com \
      -e SONAR_LOGIN=$SONAR_TOKEN \
      -v $(pwd):/src \
      sonarsource/sonar-scanner-cli
```

#### Effort

- 0.5h config sonar-project.properties
- 1h intégration dans les 3 pipelines
- 0.5h debug local

---

## 🚀 Plan d'exécution (ordre recommandé)

### Jour 1 (8h)

1. **Morning (2h) :** Jenkins Jenkinsfile
    - Setup Jenkins local / Docker
    - EcritureGroovy pipeline
    - Test premier build

2. **Afternoon (3h) :** GitHub Actions
    - Créer .github/workflows/ci.yml
    - Push et valider sur GitHub
    - Fixer premiers bloqages

3. **EOD (3h) :** GitLab CI
    - Créer .gitlab-ci.yml
    - Setup GitLab Runner local (Docker)
    - Valider build complet

### Jour 2 (8h)

1. **Morning (2h) :** Optimisation Dockerfiles
    - Multi-stage builds
    - Layer caching
    - Image size reduction

2. **Mid (2h) :** docker-compose.yml MAJ
    - Intégration backend/frontend
    - Tests full stack local

3. **Afternoon (2h) :** SonarQube (optionnel)
    - Intégration dans les 3 pipelines
    - Quality gates

4. **EOD (2h) :** Documentation + README
    - Instructions pour chaque forge
    - Troubleshooting common issues

### Jour 3 (4h)

1. **Morning (2h) :** Validation cross-platform
    - Tester sur Jenkins
    - Tester sur GitLab
    - Tester sur GitHub

2. **Afternoon (2h) :** Artifacts & publishing
    - Docker registry push
    - Release tagging
    - Notifications Slack/email

---

## 📊 Definition of Done (Phase 9)

- [x] Jenkinsfile créé, testé sur Jenkins local
- [x] .gitlab-ci.yml créé, testé sur GitLab Runner
- [x] .github/workflows/ci.yml créé, testé sur GitHub
- [x] Dockerfiles optimisés (multi-stage)
- [x] docker-compose.yml à jour
- [x] Tests automatisés passent en CI
- [x] Coverage reports générés
- [x] Artifacts publiés (dist/, jars)
- [x] README CI/CD updated
- [x] Notifications Slack/email OK
- [x] Quality gates intégrées (SonarQube optional)
- [x] Déploiement test validé

---

## 🎯 Success Metrics

| Métrique                     | Cible    | Actuel |
|------------------------------|----------|--------|
| Build time (Full)            | < 5 min  | TBD    |
| Backend Test Coverage        | ≥ 70%    | ~60%   |
| Frontend Test Coverage       | ≥ 70%    | TBD    |
| Docker image size (BE)       | < 300 MB | TBD    |
| Docker image size (FE)       | < 50 MB  | TBD    |
| Artifact retention           | 7 days   | Config |
| Failure notification latency | < 5 min  | Config |

---

## 🔗 Ressources

### Templates / Exemples

- [Jenkins Declarative Pipeline](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [GitLab CI YAML](https://docs.gitlab.com/ee/ci/yaml/)
- [GitHub Actions Workflows](https://docs.github.com/en/actions/writing-workflows)

### Tools à installer

```bash
# Jenkins (local Docker)
docker run -d -p 8080:8080 --name jenkins jenkins/jenkins:latest

# GitLab Runner (local)
docker run -d --name gitlab-runner gitlab/gitlab-runner:latest
gitlab-runner register ...

# SonarQube (local, optionnel)
docker run -d -p 9000:9000 --name sonarqube sonarqube:latest

# Act (test GitHub Actions local)
npm install -g @nektos/act
act -l
```

---

## 📝 Notes

- **Secrets:** Utiliser secrets management de chaque forge (GitHub Secrets, GitLab CI Variables, Jenkins Credentials)
- **Caching:** Configurer correctement pour accélérer builds
- **Notifications:** Intégrer Slack / email pour les succès/échecs
- **Scalability:** Préparer agents/runners distribués pour Phase 10

---

**Phase 9 ready to start! 🚀**


