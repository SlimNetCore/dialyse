#!/bin/bash
# Rejeu des contrats de la Comptabilite (C1 referentiel, C2 mapping et regles de TVA) sur un PostgreSQL 16 REEL et JETABLE :
# cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), constat du schema Flyway pur des tables de C1 et C2, puis
# contrats PG du module comptabilite (plan, journaux, payeurs, usages, mapping, regles de TVA) avec ddl-auto=update comme la
# production historique. La suite HTTP reste sur H2 (fixtures SQL sans cles etrangeres).
# C2 (KB-CPT-21) : l'ancien INSERT de POST /regles-tva (RANDOM_UUID, fonction H2) est rejoue pour constat (il doit ECHOUER) ;
# RegleTvaPostgresContractIT prouve que l'INSERT actuel (identifiant genere en Java) REUSSIT.
# A lancer en arriere-plan avec un log : backend/scripts/comptabilite-pg16-rejeu.sh 55441 > rejeu.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55441}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U comptabilite_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U comptabilite_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constat sur le schema Flyway PUR (avant tout demarrage d'Hibernate) ==="
echo "-- contraintes des tables de C1 et C2 (unicite (centre, numero), (centre, code), (centre) du mapping, cles)"
"${PSQL[@]}" -d hemo_test -c "select conrelid::regclass as table_, conname, pg_get_constraintdef(oid) from pg_constraint where conrelid in ('comptes_comptables'::regclass, 'journaux_comptables'::regclass, 'comptes_payeurs'::regclass, 'mapping_comptable'::regclass, 'tva_types'::regclass) order by 1, 2"
echo "-- colonnes de comptes d'article lues par le controle d'usage (KB-CPT-13a)"
"${PSQL[@]}" -d hemo_test -c "select column_name, data_type from information_schema.columns where table_name = 'articles' and column_name in ('compte_stock', 'compte_charge')"
echo "-- KB-CPT-21 : l'ancien INSERT de POST /regles-tva (RANDOM_UUID) doit ECHOUER sur PostgreSQL ; random_uuid n'existe pas"
"${PSQL[@]}" -d hemo_test -c "INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif, created_at, created_by) VALUES (RANDOM_UUID(), '99996200-0000-0000-0000-00000000000a', 'HEMODIALYSE 19.00%', 19.00, 'HEMODIALYSE', false, DATE '2026-01-01', TRUE, CURRENT_TIMESTAMP, 'comptabilite')" && { echo "ATTENDU : echec"; RC=1; } || echo "echec constate (attendu)"
"${PSQL[@]}" -d hemo_test -c "select proname from pg_proc where proname in ('random_uuid', 'gen_random_uuid')"
echo "-- bornes de la colonne taux numeric(5,2) : 19.999 -> 20.00, 19.005 -> 19.01, 1000 refuse"
"${PSQL[@]}" -d hemo_test -c "select 19.999::numeric(5,2), 19.005::numeric(5,2)"
"${PSQL[@]}" -d hemo_test -c "select 1000::numeric::numeric(5,2)" && { echo "ATTENDU : echec"; RC=1; } || echo "echec constate (attendu)"

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL du module comptabilite ==="
./mvnw -o -pl contexts/comptabilite test -Dtest='ComptePostgresContractIT,JournalPostgresContractIT,PayeurPostgresContractIT,UsagesPostgresContractIT,MappingPostgresContractIT,RegleTvaPostgresContractIT' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dcomptabilite.pg16.url="$URL" -Dcomptabilite.pg16.user=comptabilite_test -Dcomptabilite.pg16.password="$PW" || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
