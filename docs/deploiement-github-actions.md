# Guide — Déploiement automatique sur la VM Oracle (GitHub Actions)

Workflow concerné : `.github/workflows/deploy-vm.yml`
Déclencheur : push/merge sur `chore/angular-22-upgrade` (ou lancement manuel).

## 1. Liste exhaustive des variables GitHub

### Secrets (Settings → Secrets and variables → Actions → onglet **Secrets**)

| Nom          | Obligatoire          | Valeur                                                      | Utilisé pour         |
|--------------|----------------------|-------------------------------------------------------------|----------------------|
| `VM_HOST`    | **Oui**              | IP publique de la VM (console Oracle → Compute → Instances) | Connexion SSH        |
| `VM_SSH_KEY` | **Oui**              | Contenu **complet** de la clé privée SSH (voir §3)          | Authentification SSH |
| `VM_USER`    | Non (défaut : `opc`) | Nom d'utilisateur SSH                                       | Connexion SSH        |
| `VM_PORT`    | Non (défaut : `22`)  | Port SSH                                                    | Connexion SSH        |

### Variables (même page → onglet **Variables**)

| Nom          | Obligatoire                       | Valeur                             | Utilisé pour                     |
|--------------|-----------------------------------|------------------------------------|----------------------------------|
| `VM_APP_DIR` | Non (défaut : `/opt/hemodialyse`) | Dossier de l'application sur la VM | Destination du code et du `.env` |

### Fournis automatiquement par GitHub (rien à créer)

- `GITHUB_TOKEN` : utilisé par l'ancien `ci.yml` (push d'images GHCR, uniquement sur `main`).

### Environnement

- **`production`** (Settings → Environments) : créé automatiquement au premier run. Optionnel : y ajouter des *Required
  reviewers* pour valider chaque déploiement.

### Uniquement pour l'ancien workflow `ci.yml` (facultatif, sans lien avec le déploiement VM)

| Nom              | Type     | Rôle                                                |
|------------------|----------|-----------------------------------------------------|
| `SONAR_HOST_URL` | Variable | URL SonarQube ; si absente, le job Sonar est ignoré |
| `SONAR_TOKEN`    | Secret   | Jeton SonarQube                                     |

> Aucun secret applicatif (mot de passe DB, `JWT_SECRET`) n'est à créer dans GitHub :
> ils sont générés automatiquement dans le `.env` de la VM au premier déploiement.

## 2. Comment créer un secret / une variable

1. Ouvrir le dépôt sur GitHub → **Settings**.
2. Menu de gauche : **Secrets and variables** → **Actions**.
3. Onglet **Secrets** → **New repository secret** → renseigner *Name* et *Secret* → **Add secret**.
4. Onglet **Variables** → **New repository variable** → renseigner *Name* et *Value* → **Add variable**.

En ligne de commande (GitHub CLI `gh`) :

```bash
gh secret set VM_HOST --body "203.0.113.10"
gh secret set VM_SSH_KEY < ~/.ssh/deploy_key
gh secret set VM_USER --body "opc"            # optionnel
gh secret set VM_PORT --body "22"             # optionnel
gh variable set VM_APP_DIR --body "/opt/hemodialyse"   # optionnel
```

Astuce : les noms sont sensibles à la casse et sans espace.

## 3. Créer et installer la clé SSH de déploiement

Sur ton poste (PowerShell ou bash) :

```bash
ssh-keygen -t ed25519 -f deploy_key -C "github-actions" -N ""
```

- `deploy_key` (privée) → contenu complet dans le secret `VM_SSH_KEY`, lignes `-----BEGIN…` et `-----END…` comprises.
- `deploy_key.pub` (publique) → à ajouter sur la VM, dans `~/.ssh/authorized_keys` de l'utilisateur `opc` :

```bash
# sur la VM (connecté en opc)
echo "CONTENU_DE_deploy_key.pub" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Alternative : réutiliser la clé privée téléchargée à la création de la VM Oracle (aucun ajout sur la VM nécessaire). Une
clé dédiée reste préférable.

Ne jamais commiter la clé privée.

## 4. Préparation de la VM (une seule fois)

```bash
git clone https://github.com/SlimNetCore/dialyse.git && cd dialyse
git checkout chore/angular-22-upgrade
bash infra/oracle-vm-setup.sh     # swap, Docker, firewalld, dossier /opt/hemodialyse
exit                              # se reconnecter (groupe docker)
```

Dans la console Oracle : **Networking → VCN → Security List** → ajouter une règle *Ingress* TCP **80** (et **443**)
depuis `0.0.0.0/0`.

Le `.env` n'est pas à créer à la main : le pipeline exécute `cp env.prod.example .env` au premier déploiement et génère
`POSTGRES_PASSWORD` et `JWT_SECRET`. Pour ajuster d'autres valeurs (`SETUP_TOKEN`, clés de licence…),
éditer `/opt/hemodialyse/.env` puis relancer le workflow.

**Domaine et HTTPS** : définir dans *Settings → Secrets and variables → Actions → Variables* du dépôt `ACME_EMAIL`
(obligatoire, e-mail Let's Encrypt) et, si besoin, `APP_DOMAIN` (défaut `plateforme-hemodialyse.online`). Le pipeline
écrit `DOMAIN`, `ACME_EMAIL`, `AUTH_COOKIE_SECURE=true` et `CORS_ORIGINS` dans le `.env` à chaque déploiement. Les ports
80, 443 (TCP) et 443 (UDP) doivent être ouverts ; configuration DNS chez amen.fr : voir `infra/DEPLOIEMENT-DOMAINE.md`.

## 5. Déroulement du pipeline

1. **backend** : `./mvnw -B verify`.
2. **frontend** : correction du lockfile (`ng-table`), tests, build production.
3. **deploy** (push / lancement manuel seulement) :
    1. connexion SSH ;
    2. envoi du code (le `.env` et les volumes Docker sont conservés) ;
    3. création du `.env` s'il est absent ;
    4. `docker compose -f docker-compose.prod.yml up -d --build` ;
    5. attente de `/actuator/health` (4 min max) ;
    6. nettoyage des anciennes images.

Une pull request vers la branche exécute uniquement les jobs 1 et 2.

## 6. Premier lancement

1. Créer `VM_HOST` et `VM_SSH_KEY` (§1–§3).
2. Commiter et pousser les fichiers (Dockerfile, `docker-compose.prod.yml`, `env.prod.example`, workflow).
3. GitHub → **Actions** → *CI/CD — Déploiement VM* → **Run workflow** (ou merge sur la branche).
4. Ouvrir `http://<IP-de-la-VM>`.

## 7. Dépannage

| Symptôme                           | Cause probable                                                                                           |
|------------------------------------|----------------------------------------------------------------------------------------------------------|
| `Permission denied (publickey)`    | `VM_SSH_KEY` incomplète/incorrecte ou clé publique absente de `authorized_keys`                          |
| `ssh-keyscan` / timeout            | `VM_HOST` erroné, port 22 fermé (Security List Oracle ou firewalld)                                      |
| `docker: permission denied`        | `opc` pas encore dans le groupe `docker` : se reconnecter après le script de setup                       |
| `.env créé…` mais mauvais réglages | Éditer `/opt/hemodialyse/.env` sur la VM puis relancer le workflow                                       |
| Site inaccessible depuis Internet  | Règle Ingress TCP 80 manquante dans la Security List Oracle                                              |
| Backend ne répond pas              | Voir l'étape « Logs en cas d'échec » du job, ou `docker compose -f docker-compose.prod.yml logs backend` |

