#!/bin/bash
# Rejeu de la slice P3 de la Planification (roulement, absences, remplacements, Mon planning) sur un PostgreSQL 16 REEL et JETABLE :
# cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) dont V47 (colonne infirmier_absence.declaree_par), constats sur le
# schema Flyway (colonne nullable, contrainte UNIQUE des remplacements), controles SELECT-only des donnees heritees tels que documentes
# dans verifications-production.md (affectations qui se chevauchent, absences qui se chevauchent : doivent etre vides sur une base
# neuve), puis contrat PostgreSQL : depots du roulement, des absences et des remplacements, lecture publiee, fuseau du centre, et les
# COURSES (6 affectations simultanees chevauchantes d'un infirmier : une seule passe ; 6 remplacements simultanes du meme remplacant
# sur un creneau : un seul passe) sous le verrou de la fiche (FOR UPDATE) et lock_timeout.
# A lancer en arriere-plan avec un log : backend/scripts/planification-p3-pg16-rejeu.sh 55463 > rejeu-planification-p3.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55463}"
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

echo "=== Constats sur le schema Flyway (V47 ajoute la colonne de l'auteur des absences) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'infirmier_absence' AND column_name = 'declaree_par' AND is_nullable = 'YES' AND data_type = 'uuid'" | tr -d '\r' | grep -qx 1 \
  && echo "infirmier_absence.declaree_par (uuid, nullable) presente" || { echo "ATTENDU : colonne declaree_par uuid nullable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_indexes WHERE tablename = 'infirmier_remplacement' AND indexdef ILIKE 'CREATE UNIQUE INDEX%(center_id, date_jour, creneau_id, infirmier_id)%'" | tr -d '\r' | grep -qx 1 \
  && echo "unicite des remplacements (center_id, date_jour, creneau_id, infirmier_id) presente" || { echo "ATTENDU : 1 index unique des remplacements"; RC=1; }
echo "-- P3 : controles SELECT-only des donnees heritees (verifications-production.md, doivent etre vides sur une base neuve)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM (SELECT a.infirmier_id, a.creneau_id, j FROM infirmier_affectation a, unnest(string_to_array(a.jours, ',')) j GROUP BY 1, 2, 3 HAVING count(*) > 1) d" | tr -d '\r' | grep -qx 0 \
  && echo "aucune affectation qui se chevauche" || { echo "ATTENDU : 0 chevauchement d'affectations"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM infirmier_absence a JOIN infirmier_absence b ON a.infirmier_id = b.infirmier_id AND a.id < b.id AND a.date_debut <= b.date_fin AND b.date_debut <= a.date_fin" | tr -d '\r' | grep -qx 0 \
  && echo "aucune absence qui se chevauche" || { echo "ATTENDU : 0 chevauchement d'absences"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL du roulement, des absences et des remplacements (depots, courses, fuseau, lecture publiee) ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.PlanningDesInfirmiersJdbcPostgresContractIT,$P.InfirmiersJdbcPostgresContractIT,$P.ParametrageJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
