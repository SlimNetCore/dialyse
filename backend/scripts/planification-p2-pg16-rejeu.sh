#!/bin/bash
# Rejeu de la slice P2 de la Planification (referentiel des infirmiers et comptes) sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), constats sur le schema Flyway pur (AUCUNE migration en P2 : contrainte
# UNIQUE(center_id, matricule) et index ux_infirmier_user existants), controle SELECT-only des doublons de casse du matricule (tel que
# documente dans verifications-production.md), puis contrat PostgreSQL : depots des fiches et des comptes, contraintes reelles,
# verrou du centre (UPDATE centers SET id = id => FOR NO KEY UPDATE, n'empeche pas d'inserer une fiche), lock_timeout => 409.
# A lancer en arriere-plan avec un log : backend/scripts/planification-p2-pg16-rejeu.sh 55462 > rejeu-planification-p2.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55462}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U planification_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U planification_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constats sur le schema Flyway PUR (P2 n'ajoute aucune migration) ==="
echo "-- P2 : contrainte UNIQUE (center_id, matricule) sensible a la casse, index unique (center_id, user_id)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_indexes WHERE tablename = 'infirmier' AND indexdef ILIKE 'CREATE UNIQUE INDEX%(center_id, matricule)%'" | tr -d '\r' | grep -qx 1 \
  && echo "unicite (center_id, matricule) presente" || { echo "ATTENDU : 1 index unique (center_id, matricule)"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_indexes WHERE tablename = 'infirmier' AND indexname = 'ux_infirmier_user'" | tr -d '\r' | grep -qx 1 \
  && echo "index ux_infirmier_user present" || { echo "ATTENDU : ux_infirmier_user"; RC=1; }
echo "-- P2 : controle SELECT-only des doublons de casse du matricule (verifications-production.md, doit etre vide sur une base neuve)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM (SELECT center_id, upper(btrim(matricule)) FROM infirmier GROUP BY 1, 2 HAVING count(*) > 1) d" | tr -d '\r' | grep -qx 0 \
  && echo "aucun doublon de casse" || { echo "ATTENDU : 0 doublon"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL du referentiel des infirmiers (depots, comptes, contraintes, verrou du centre, lock_timeout) ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.InfirmiersJdbcPostgresContractIT,$P.ParametrageJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
