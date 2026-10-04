# Brancher plateforme-hemodialyse.online sur la VM (84.235.227.26)

Architecture : navigateur → **Caddy** (HTTPS 443, certificat Let's Encrypt automatique) → Nginx (frontend) → backend →
PostgreSQL.
Seuls les ports **80 et 443** sont ouverts sur Internet.

## 1. Côté amen.fr — zone DNS

Espace client Amen → *Mes noms de domaine* → `plateforme-hemodialyse.online` → **Gérer la zone DNS** (ou « Éditer la
zone DNS »).
Vérifier que le domaine utilise les **serveurs DNS d'Amen** (sinon la zone ne s'applique pas).

| Type | Nom / Hôte       | Valeur / Cible  | TTL                                 |
|------|------------------|-----------------|-------------------------------------|
| A    | `@` (domaine nu) | `84.235.227.26` | 3600 (300 pendant la mise en place) |
| A    | `www`            | `84.235.227.26` | 3600                                |

À faire :

- **Supprimer** tout enregistrement `A`/`AAAA`/`CNAME` existant sur `@` et `www` (page de parking Amen, redirection par
  défaut), sinon Let's Encrypt peut valider la mauvaise adresse.
- Ne **pas** ajouter d'enregistrement `AAAA` (IPv6) si la VM n'a pas d'IPv6 configurée.
- Désactiver toute **redirection / redirection web Amen** ou service de parking sur ce domaine.
- Facultatif : un enregistrement `CAA` `0 issue "letsencrypt.org"` pour restreindre l'émission de certificats.
- La propagation prend de quelques minutes à quelques heures. Vérifier depuis un poste :
  ```bash
  nslookup plateforme-hemodialyse.online
  nslookup www.plateforme-hemodialyse.online
  ```
  Les deux doivent répondre `84.235.227.26` avant de démarrer Caddy.

## 2. Côté VM — pare-feu

Ouvrir en entrée **TCP 80, TCP 443 et UDP 443** (HTTP/3) :

- chez l'hébergeur (groupe de sécurité / liste de sécurité / pare-feu cloud) ;
- sur la VM, si un pare-feu local est actif :
  ```bash
  sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp && sudo ufw allow OpenSSH && sudo ufw enable
  ```
  (VM Oracle/Ubuntu avec iptables : ajouter les mêmes règles avant la règle `REJECT` puis `netfilter-persistent save`.)
- Le port 5432 (PostgreSQL) est lié à `127.0.0.1` par `docker-compose.prod.yml` : **ne jamais l'ouvrir** dans le
  pare-feu (voir section « Base de données »).

## 3. Côté projet — déploiement

Sur la VM (Docker et Docker Compose installés, voir `infra/oracle-vm-setup.sh`) :

```bash
git clone <dépôt> hemodialyse && cd hemodialyse
cp env.prod.example .env
nano .env
```

Dans `.env`, renseigner au minimum :

```dotenv
POSTGRES_PASSWORD=<mot de passe fort>
JWT_SECRET=<openssl rand -base64 48>
SETUP_TOKEN=<jeton aléatoire, exigé pour créer le compte propriétaire>
DOMAIN=plateforme-hemodialyse.online
ACME_EMAIL=<votre e-mail>
AUTH_COOKIE_SECURE=true
CORS_ORIGINS=https://plateforme-hemodialyse.online
```

Puis :

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f caddy   # attendre « certificate obtained successfully »
```

Ouvrir https://plateforme-hemodialyse.online : à la première visite, créer le compte propriétaire (écran d'installation,
jeton `SETUP_TOKEN`).

## 4. Vérifications

```bash
curl -I http://plateforme-hemodialyse.online          # 308 → https
curl -I https://plateforme-hemodialyse.online          # 200
curl https://plateforme-hemodialyse.online/actuator/health
curl -I https://www.plateforme-hemodialyse.online      # 301 → domaine principal
```

## 5. Base de données : consultation, sauvegarde, restauration

PostgreSQL écoute sur `127.0.0.1:5432` de la VM (inaccessible depuis Internet). Trois façons de l'administrer :

**a) Depuis votre poste, avec DBeaver / pgAdmin / IntelliJ — via tunnel SSH (recommandé).**
Dans la connexion, onglet « SSH » : hôte `84.235.227.26`, port 22, votre utilisateur et votre clé. Onglet principal :
hôte
`localhost`, port `5432`, base `hemodialyse`, utilisateur `hemo_user`, mot de passe `POSTGRES_PASSWORD` du `.env` de la
VM.
Ou en ligne de commande, tunnel puis client local :

```bash
ssh -N -L 5433:127.0.0.1:5432 <utilisateur>@84.235.227.26      # laisser ouvert ; connecter le client sur localhost:5433
```

**b) Directement sur la VM.**

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U hemo_user -d hemodialyse
```

**c) Sauvegarde.** `infra/backup-db.sh` produit un dump compressé daté (rotation 14 jours, contrôle d'intégrité).

```bash
bash infra/backup-db.sh
# quotidien à 02:30 : crontab -e  →  30 2 * * * cd /opt/hemodialyse && bash infra/backup-db.sh >> backups/backup.log 2>&1
scp <utilisateur>@84.235.227.26:/opt/hemodialyse/backups/hemodialyse_*.sql.gz .   # copie hors de la VM (indispensable)
```

Une sauvegarde qui reste sur la même VM ne protège pas d'une perte de la VM : copiez-la régulièrement ailleurs (poste,
stockage objet, autre serveur). Attention : le pipeline de déploiement efface le contenu de `/opt/hemodialyse` à chaque
déploiement (hors `.env`), donc
`./backups` serait supprimé. Placez les sauvegardes ailleurs :
`BACKUP_DIR=/var/backups/hemodialyse bash infra/backup-db.sh`
(et dans la ligne `crontab`).

**Restauration** (sur la VM, application arrêtée) :

```bash
docker compose -f docker-compose.prod.yml stop backend
gunzip -c hemodialyse_AAAAMMJJ_HHMMSS.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U hemo_user -d hemodialyse
docker compose -f docker-compose.prod.yml start backend
```

## 6. Dépannage

- **Certificat non émis** : DNS pas encore propagé, port 80 fermé (le défi HTTP-01 passe par le 80), ou ancien
  enregistrement `AAAA`. Voir `docker compose logs caddy`. Let's Encrypt limite les essais échoués : corriger avant de
  redémarrer en boucle.
- **Connexion impossible / cookies refusés** : `AUTH_COOKIE_SECURE=true` impose HTTPS ; ne pas tester via `http://`.
- **WebSocket (notifications)** : passe par `/ws` sur le même domaine, rien à configurer de plus.
- **Mise à jour** : `git pull && docker compose -f docker-compose.prod.yml up -d --build` (le certificat est conservé
  dans le volume `caddy_data`).
