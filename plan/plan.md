# Plan de travail executable - Application de gestion des centres d hemodialyse

Date: 2026-03-14
Statut: v1

## 1) Objectif
Mettre en place une application web de gestion des centres d hemodialyse avec:
- Front: Angular 21 + Angular Material + Signal Store
- Back: Spring Boot 4
- Data: PostgreSQL
- Securite: JWT
- API: Swagger/OpenAPI
- Deploiement: Docker + docker-compose
- CI/CD: Jenkins, GitLab CI, GitHub Actions
- Architecture: DDD + hexagonale
- Contrainte cle: multi-centre dans une seule base de donnees

## 2) Perimetre fonctionnel prioritaire
- Gestion patient (identite, administratif, assurance, pieces jointes)
- Gestion assure (multi-assure + historique)
- Ecosysteme assurance (Caisse -> Agence -> Centre payeur)
- Attestation ouverture de droit
- Prise en charge (workflow: CREE -> VALIDEE -> CLOTUREE)
- Regles vacancier / non-vacancier
- Planification (medecin, creneau, salle, generateur, transporteur)
- Regles etat patient

## 3) Decision architecture multi-centre (obligatoire)
Modele retenu: single database multi-centre.

Regles structurantes:
- Toute donnee metier sensible porte `center_id` (UUID, NOT NULL)
- Toute requete metier est scopee par `center_id`
- Toute unicite metier est scopee par centre (`UNIQUE(center_id, business_key)`)
- Un utilisateur ne peut agir que sur ses centres assignes
- Les acces cross-centre sont refuses et traces (audit)

## 4) DDD + architecture hexagonale
### 4.1 Bounded contexts
- `PatientIdentity`
- `InsuranceCoverage`
- `CareAuthorization`
- `SessionPlanning`
- `CenterResources`
- `AccessControl`

### 4.2 Aggregates principaux
- `PatientAggregate`
- `AffiliationAssuranceAggregate`
- `AttestationAggregate`
- `PriseEnChargeAggregate`
- `PlanningSeanceAggregate`
- `RessourceTechniqueAggregate`

### 4.3 Ports / adaptateurs
- Ports entrants: use cases applicatifs
- Ports sortants: repository, horloge, stockage fichiers, messagerie
- Adaptateurs: REST, JPA, JWT, OpenAPI, Postgres
- Regle: aucune logique metier dans les adaptateurs

### 4.4 Tenant scope
Chaque use case recoit un objet `TenantScope(centerId, userId, roles)`.
Le domaine applique les invariants metier + centre.

## 5) Regles metier critiques (a coder en premier)
- Seance interdite si prise en charge au statut `CREE` ou `CLOTUREE`
- Seance autorisee uniquement si prise en charge `VALIDEE`
- Non-vacancier: attestation ouverture de droit obligatoire et valide
- Vacancier: regles de dispense selon type de caisse (a figer en atelier)
- Unicites patient scopees par centre (numero assurance, numero securite)
- Historique assure non destructif (date debut/date fin)

## 6) Modele de donnees PostgreSQL (MVP)
Tables cibles:
- `centers`
- `users`, `roles`, `user_center_assignment`
- `patients`
- `patient_identifiers`
- `assures`
- `affiliation_history`
- `attestation_droit`
- `prise_en_charge`
- `seances`
- `caisses`, `agences`, `centres_payeurs`
- `medecins`, `creneaux`, `salles`, `generateurs`, `transporteurs`
- `audit_events`

Contraintes:
- `center_id UUID NOT NULL` sur tables metier
- FK vers `centers(id)`
- Uniques metier scopees par centre
- Index minimum: `(center_id)`, `(center_id, status)`, `(center_id, created_at)`

Option securite avancee:
- RLS PostgreSQL avec policy sur `app.current_center`

## 7) API et securite
- API contract-first OpenAPI (`/api/v1`)
- Erreurs RFC7807
- JWT stateless avec claims: user id, roles, centers autorises
- Roles initiaux:
  - `ADMIN`
  - `MEDECIN`
  - `AGENT_ADMISSION`
  - `AGENT_ASSURANCE`
  - `LECTURE`
- Verification center access sur chaque endpoint et use case

## 8) Plan d execution (8 a 12 semaines)
## Sprint 0 (Semaine 1) - Cadrage
### Checklist
- [ ] Valider glossaire metier
- [ ] Valider context map DDD
- [ ] Valider ADR multi-centre
- [ ] Prioriser backlog MVP
- [ ] Definir Definition of Done

### Livrables
- `docs/domain/glossary.md`
- `docs/architecture/context-map.md`
- `docs/architecture/adrs/*.md`
- Backlog priorise

### Criteres d acceptation
- [ ] Regles critiques valides par metier
- [ ] Scope MVP fige

## Sprint 1 (Semaines 2-3) - Socle technique
### Checklist
- [ ] Initialiser `frontend/` Angular 21 + Material + Signal Store
- [ ] Initialiser `backend/` Spring Boot 4 modulith DDD
- [ ] Config PostgreSQL + migration tool
- [ ] Config JWT minimal
- [ ] Config OpenAPI/Swagger
- [ ] Dockerfiles front/back
- [ ] `docker-compose.yml` local

### Livrables
- Squelettes front/back
- Build local dockerise
- OpenAPI initiale

### Criteres d acceptation
- [ ] Authentification JWT fonctionnelle
- [ ] API health + doc swagger exposees

## Sprint 2 (Semaines 4-5) - Data multi-centre
### Checklist
- [ ] Creer `centers` et `user_center_assignment`
- [ ] Ajouter `center_id` aux tables MVP
- [ ] Ajouter contraintes + indexes
- [ ] Ecrire scripts backfill
- [ ] Mettre en place anti-requete sans center scope

### Livrables
- Migrations SQL versionnees
- Guide migration + rollback

### Criteres d acceptation
- [ ] 100% tables MVP avec `center_id NOT NULL`
- [ ] Tests integration SQL passants

## Sprint 3 (Semaines 6-7) - Coeur metier patient/PEC
### Checklist
- [ ] Use cases patient (create/update/read)
- [ ] Assure multiples + historique
- [ ] Attestation ouverture droit
- [ ] Workflow prise en charge
- [ ] Regle blocage seance
- [ ] Validation vacancier/non-vacancier

### Livrables
- Module metier `patient-care`
- Endpoints API v1 patient/assurance/PEC

### Criteres d acceptation
- [ ] Interdiction seance si PEC non `VALIDEE`
- [ ] Non-vacancier sans attestation: rejet

## Sprint 4 (Semaines 8-9) - Front fonctionnel
### Checklist
- [ ] Ecrans patient + formulaires obligatoires
- [ ] Onglet prise en charge active apres creation patient
- [ ] Ecrans assurance (caisse/agence/centre payeur)
- [ ] Champs grises: code centre, code agence
- [ ] Gestion `currentCenterId` dans Signal Store
- [ ] Filtrage centre sur toutes listes/detail

### Livrables
- Front MVP operationnel
- Parcours principal bout en bout

### Criteres d acceptation
- [ ] Utilisateur centre A ne voit pas centre B
- [ ] Parcours patient -> PEC -> seance controle

## Sprint 5 (Semaines 10-11) - Ressources et qualite
### Checklist
- [ ] Gestion medecins, creneaux, salles, generateurs, transporteurs
- [ ] Tests unitaires domaine
- [ ] Tests integration API/repository
- [ ] Tests e2e (multi-centre)
- [ ] Logs structures + metrics + traces

### Livrables
- Modules planning/ressources
- Dashboard observabilite minimum

### Criteres d acceptation
- [ ] Couverture tests modules critiques >= seuil fixe
- [ ] Aucun test de fuite cross-centre

## Sprint 6 (Semaine 12) - Industrialisation et release pilote
### Checklist
- [ ] `Jenkinsfile`
- [ ] `.gitlab-ci.yml`
- [ ] `.github/workflows/ci.yml`
- [ ] Pipelines: lint/test/scan/build/publish/deploy
- [ ] Runbook exploitation + rollback
- [ ] Pilote sur 1 a 2 centres

### Livrables
- CI/CD multi-forges verte
- Package release + notes de version

### Criteres d acceptation
- [ ] Deploiement pilote valide
- [ ] KPI securite/perf dans les seuils

## 9) Backlog epics et user stories (MVP)
### Epic E1 - Gestion patient
- US: creer patient avec validations obligatoires
- US: controler unicites par centre
- US: gerer pieces jointes

### Epic E2 - Assurance
- US: administrer caisse/agence/centre payeur
- US: associer assures multiples
- US: historiser les changements d assure

### Epic E3 - Prise en charge
- US: creer PEC
- US: valider PEC
- US: cloturer PEC
- US: bloquer seance selon statut PEC

### Epic E4 - Planification
- US: affecter medecin/creneau
- US: affecter salle/generateur
- US: affecter transporteur aller/retour

### Epic E5 - Multi-centre et securite
- US: restreindre acces par centre
- US: journaliser refus cross-centre
- US: superviser anomalies d autorisation

## 10) Definition of Done (DoD)
Une story est terminee seulement si:
- [ ] Regles metier codees dans domaine
- [ ] Tests unitaires/integration/e2e verts
- [ ] Contrat OpenAPI a jour
- [ ] Migration data incluse si impact schema
- [ ] Logs/audit ajoutes sur actions sensibles
- [ ] Revue securite passee
- [ ] Documentation mise a jour

## 11) Risques et mitigations
- Ambiguite metier -> ateliers hebdo + exemples de cas limites
- Derive du schema assurance -> prototype rapide + revues data
- Fuite cross-centre -> tests auto + audit + RLS optionnelle
- Retard CI/CD multi-forges -> pipeline canonique unique puis adaptation
- Dette qualite -> quality gates bloquants

## 12) Gouvernance et pilotage
Rituels:
- Daily team
- Weekly product review
- Weekly architecture/security review
- Sprint review + retro

Indicateurs:
- Lead time story
- Taux de tests passants
- Nb refus cross-centre
- Defauts critiques post-release

## 13) Prerequis de demarrage
- Inventaire des donnees existantes
- Matrice roles-utilisateurs-centres
- Environnements dev/staging proches prod
- Referents identifies: PO, Tech lead, Data owner, Sec owner
- Decision RLS: MVP ou post-MVP

## 14) Demarrage rapide (execution immediate)
### Semaine 1 - actions immediates
- [ ] Ouvrir atelier regles metier critiques
- [ ] Valider modeles DDD et multi-centre
- [ ] Initialiser repositories front/back/infra
- [ ] Ecrire ADR principales
- [ ] Lancer Sprint 1 avec backlog ferme

### Commandes type (a adapter au contexte projet)
```bash
# Front
npm install
npm run test

# Back
./mvnw test

# Local stack
docker compose up -d
```

## 15) Jalons Go/No-Go
- Gate 1 (fin Sprint 2): data multi-centre stable
- Gate 2 (fin Sprint 4): parcours MVP bout en bout
- Gate 3 (fin Sprint 6): pilote exploitable et securise

---
Ce document est la reference d execution. Toute decision impactant architecture, securite, schema ou workflow doit passer par ADR.

