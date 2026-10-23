#!/bin/bash
# Rejeu de la slice A6 (cloture mensuelle des absences de patients) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..V44 (psql), constats sur le schema Flyway pur (V44 additive : deux tables VIDES, aucun mois clos au depart,
# idempotente), puis contrats PostgreSQL : mois d'absences (unicite, CHECK, instants, journal), concurrence cloture / ecriture (verrou de
# ligne SELECT ... FOR UPDATE, creation simultanee de la ligne du mois ON CONFLICT DO NOTHING, lock_timeout => 409) et non-regression des
# contrats de A2/A3 (absences, detection, ecritures concurrentes, lectures).
# A lancer en arriere-plan avec un log : backend/scripts/absences-a6-pg16-rejeu.sh 55456 > rejeu-absences-a6.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55456}"
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

echo "=== Constats sur le schema Flyway PUR ==="
echo "-- A6 (migration V44) : les deux tables de la cloture mensuelle des absences existent et sont VIDES (aucun mois clos au depart)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('mois_absences', 'mois_absences_journal')" | tr -d '\r' | grep -qx 2 \
  && echo "tables presentes (2/2)" || { echo "ATTENDU : 2 tables"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT (SELECT count(*) FROM mois_absences) + (SELECT count(*) FROM mois_absences_journal)" | tr -d '\r' | grep -qx 0 \
  && echo "aucune ligne au depart" || { echo "ATTENDU : 0 ligne"; RC=1; }
echo "-- A6 : V44 est idempotente (rejeu sans erreur)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/V44__absences_patients_cloture_mensuelle.sql" >/dev/null && echo "rejeu V44 ok" || RC=1
echo "-- A6 : la cle primaire (centre, annee, mois) et les CHECK existent"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_constraint WHERE conname IN ('ck_mois_absences_mois', 'ck_mois_absences_statut', 'ck_mois_absences_journal_type')" | tr -d '\r' | grep -qx 3 \
  && echo "contraintes presentes (3/3)" || { echo "ATTENDU : 3 contraintes"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.absencespatients.contrat
echo "=== Contrats PostgreSQL de la cloture mensuelle (mois d'absences, concurrence cloture / ecriture, lock_timeout) ==="
./mvnw -o -pl contexts/absences-patients -am test \
  -Dtest="$P.MoisAbsencesPostgresContractIT,$P.MoisAbsencesConcurrentPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dabsences.pg16.url="$URL" -Dabsences.pg16.user=absences_test -Dabsences.pg16.password="$PW" || RC=1
echo "=== Non-regression : contrats PostgreSQL des absences (A1 a A3) ==="
./mvnw -o -pl contexts/absences-patients -am test \
  -Dtest="$P.AbsencesPostgresContractIT,$P.DetectionPostgresContractIT,$P.EcrituresConcurrentesPostgresContractIT,$P.LecturesDuSuiviPostgresContractIT,$P.AbsencesDuPilotagePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dabsences.pg16.url="$URL" -Dabsences.pg16.user=absences_test -Dabsences.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
