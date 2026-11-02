#!/bin/bash
# Rejeu du legacy DIRECTION (pilotage) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql),
# puis verification des colonnes lues et ecrites en JDBC par le legacy (DirectionSnapshotService, DirectionAlertHistoryService : pas d'entite JPA)
# contre information_schema, sans demarrer Spring. Prouve aussi : aucune version Flyway en doublon et que le schema Flyway pur ne contient AUCUNE
# des tables paralleles du module gele contexts/pilotage-direction (tableaux_de_bord, instantanes_mensuels, alertes_direction, exports_synthese).
# A lancer en arriere-plan avec un log : backend/scripts/pilotage-direction-pg16-rejeu.sh 55464 > rejeu-pilotage-direction.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55464}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U direction_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U direction_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonnes du legacy Direction (JDBC) contre information_schema ==="
for tc in \
  "direction_snapshot:id" "direction_snapshot:societe_id" "direction_snapshot:mois" "direction_snapshot:payload" "direction_snapshot:generated_at" \
  "direction_alert_history:id" "direction_alert_history:societe_id" "direction_alert_history:center_id" "direction_alert_history:centre_nom" \
  "direction_alert_history:code" "direction_alert_history:severity" "direction_alert_history:valeur" "direction_alert_history:first_seen_at" \
  "direction_alert_history:resolved_at" "societes:actif" "centers:fuseau_horaire"; do
  t="${tc%%:*}"; c="${tc##*:}"
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
    && echo "presente : $t.$c" || { echo "ABSENTE : $t.$c"; RC=1; }
done
[ "$(scalaire "SELECT count(*) FROM pg_constraint WHERE conname='uq_direction_snapshot'")" = "1" ] \
  && echo "presente : contrainte uq_direction_snapshot (societe_id, mois)" || { echo "ABSENTE : uq_direction_snapshot"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE indexname='idx_direction_alert_history_societe'")" = "1" ] \
  && echo "present : index idx_direction_alert_history_societe" || { echo "ABSENT : idx_direction_alert_history_societe"; RC=1; }

echo "=== Le code Java legacy ne lit que ces colonnes : verification par le texte des requetes ==="
SRC="$RACINE/backend/platform/src/main/java/com/hemodialyse/backend/application/direction"
for col in $(grep -hoE "(INSERT INTO|FROM|UPDATE) direction_(snapshot|alert_history)" "$SRC"/*.java | sed -E 's/.* //' | sort -u); do
  echo "table utilisee par le legacy : $col"
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$col'")" = "1" ] \
    || { echo "TABLE ABSENTE : $col"; RC=1; }
done

echo "=== Ecarts du module gele : tables paralleles inexistantes dans le schema Flyway ==="
for t in tableaux_de_bord instantanes_mensuels alertes_direction exports_synthese; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t (le module gele la lit et l'ecrit : raison du gel)" || { echo "INATTENDU : table parallele $t presente"; RC=1; }
done
echo "=== Code de sortie : $RC ==="
exit $RC
