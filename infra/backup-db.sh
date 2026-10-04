#!/usr/bin/env bash
# Sauvegarde PostgreSQL (dump compressé, rotation). À lancer sur la VM depuis la racine du projet :
#   bash infra/backup-db.sh                # sauvegarde dans ./backups, conserve 14 jours
#   RETENTION_DAYS=30 BACKUP_DIR=/data/backups bash infra/backup-db.sh
# Planification quotidienne (crontab -e) :
#   30 2 * * * cd /opt/hemodialyse && bash infra/backup-db.sh >> backups/backup.log 2>&1
# Restauration : voir infra/DEPLOIEMENT-DOMAINE.md.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
COMPOSE="docker compose -f docker-compose.prod.yml"

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/hemodialyse_$(date +%Y%m%d_%H%M%S).sql.gz"

# pg_dump s'exécute dans le conteneur ; les variables POSTGRES_* y sont déjà définies.
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists' | gzip > "$FILE"

# Un dump vide ou tronqué ne doit jamais être conservé comme sauvegarde valide.
if ! gzip -t "$FILE" || [ "$(stat -c %s "$FILE")" -lt 1024 ]; then
  rm -f "$FILE"
  echo "Sauvegarde invalide, supprimée" >&2
  exit 1
fi

chmod 600 "$FILE"
find "$BACKUP_DIR" -name 'hemodialyse_*.sql.gz' -mtime +"$RETENTION_DAYS" -delete
echo "Sauvegarde OK : $FILE ($(du -h "$FILE" | cut -f1))"
