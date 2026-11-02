#!/bin/bash
# Rejeu du contexte Organisation sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..V51
# (psql), constats sur le schema Flyway pur (V51 additive et idempotente : salle.ouverte, tables machine et protocole VIDES), puis
# contrats JDBC d'Organisation sur ce schema (salles, creneaux, medecins, machines, protocoles, suppressions bornees au centre).
# A lancer en arriere-plan avec un log : backend/scripts/organisation-pg16-rejeu.sh 55457 > rejeu-organisation.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55457}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U organisation_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U organisation_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constats sur le schema Flyway PUR ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'salle' AND column_name = 'ouverte'" | tr -d '\r' | grep -qx 1 \
  && echo "salle.ouverte presente" || { echo "ATTENDU : salle.ouverte"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('machine', 'protocole')" | tr -d '\r' | grep -qx 2 \
  && echo "tables machine et protocole presentes (2/2)" || { echo "ATTENDU : 2 tables"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT (SELECT count(*) FROM machine) + (SELECT count(*) FROM protocole)" | tr -d '\r' | grep -qx 0 \
  && echo "machine et protocole vides au depart" || { echo "ATTENDU : 0 ligne"; RC=1; }
echo "-- V51 est idempotente (rejeu sans erreur)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/V51__organisation_salle_ouverte_machine_protocole.sql" >/dev/null && echo "rejeu V51 ok" || RC=1

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.organisation.contrat
echo "=== Contrats JDBC d'Organisation sur le schema Flyway reel ==="
./mvnw -o -pl contexts/organisation -am test \
  -Dtest="$P.OrganisationJdbcPostgresContractIT,$P.OrganisationJdbcContratTest" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dorganisation.pg16.url="$URL" -Dorganisation.pg16.user=organisation_test -Dorganisation.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
