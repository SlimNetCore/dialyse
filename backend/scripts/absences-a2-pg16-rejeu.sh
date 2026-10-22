#!/bin/bash
# Rejeu de la slice A2 (cycle de vie des absences de patients) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..V43 (psql, dont l'index unique partiel du rattrapage), puis contrat PostgreSQL des absences enregistrees
# (verrous de ligne, unicite, echelle des montants) et contrat de concurrence des ecritures (attente du verrou, 409).
# A lancer en arriere-plan avec un log : backend/scripts/absences-a2-pg16-rejeu.sh 55449 > rejeu-absences-a2.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55449}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U absences_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U absences_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0
cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des absences (contexte absences-patients) ==="
./mvnw -o -pl contexts/absences-patients -am test \
  -Dtest='com.hemodialyse.backend.contexts.absencespatients.contrat.AbsencesPostgresContractIT,com.hemodialyse.backend.contexts.absencespatients.contrat.EcrituresConcurrentesPostgresContractIT,com.hemodialyse.backend.contexts.absencespatients.contrat.LecturesDuSuiviPostgresContractIT' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dabsences.pg16.url="$URL" -Dabsences.pg16.user=absences_test -Dabsences.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
