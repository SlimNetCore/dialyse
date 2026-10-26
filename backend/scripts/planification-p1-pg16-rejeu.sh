#!/bin/bash
# Rejeu de la slice P1 de la Planification (parametrage : parametres du planning, reglages, preferences des patients, profils des
# infirmiers) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..V44 (psql), constats sur
# le schema Flyway pur (AUCUNE migration en P1 : les quatre tables existent deja), puis contrat PostgreSQL : depots du parametrage,
# aucune ligne creee par une lecture ni par le verrou, colonne historique remise a NULL, read model des ecrans, ports vers
# Organisation / Patient / infirmiers, serialisation des ecritures (verrou de ligne SELECT ... FOR UPDATE du centre, cle creee
# entre-temps => 409, lock_timeout => 409).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p1-pg16-rejeu.sh 55461 > rejeu-planification-p1.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55461}"
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

echo "=== Constats sur le schema Flyway PUR (P1 n'ajoute aucune migration) ==="
echo "-- P1 : les quatre tables du parametrage existent et la cle primaire de chacune est celle que l'adaptateur suppose"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('planning_parametres', 'planification_reglages', 'planning_preference_patient', 'infirmier_profil_planning')" | tr -d '\r' | grep -qx 4 \
  && echo "tables presentes (4/4)" || { echo "ATTENDU : 4 tables"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid WHERE c.contype = 'p' AND t.relname IN ('planning_parametres', 'planification_reglages', 'planning_preference_patient', 'infirmier_profil_planning')" | tr -d '\r' | grep -qx 4 \
  && echo "cles primaires presentes (4/4)" || { echo "ATTENDU : 4 cles primaires"; RC=1; }
echo "-- P1 : colonne salle.isolement presente (marque d'isolement ecrite par le port Organisation)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'salle' AND column_name = 'isolement'" | tr -d '\r' | grep -qx 1 \
  && echo "salle.isolement presente" || { echo "ATTENDU : salle.isolement"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL du parametrage (depots, read model, verrou du centre, lock_timeout) ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.ParametrageJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
