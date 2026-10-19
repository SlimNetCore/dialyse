#!/bin/bash
# Rejeu de la slice F1 (referentiel de facturation) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql), constat KB-FAC-07 sur le schema Flyway pur (l'ancien MERGE ... KEY de
# FacturationSettingsJdbcAdapter, syntaxe H2, doit ECHOUER), puis contrats PostgreSQL : parametres de facturation (contexte
# facturation, UPDATE puis INSERT) et regles de TVA (contexte comptabilite : ajout, lecture par identifiant, remplacement,
# desactivation).
# A lancer en arriere-plan avec un log : backend/scripts/facturation-pg16-rejeu.sh 55442 > rejeu.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55442}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U facturation_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U facturation_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constat sur le schema Flyway PUR ==="
echo "-- KB-FAC-07 : l'ancien MERGE INTO ... KEY (center_id) (syntaxe H2) doit ECHOUER sur PostgreSQL"
"${PSQL[@]}" -d hemo_test -c "MERGE INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) KEY (center_id) VALUES ('99996700-0000-0000-0000-00000000000a', 'FAC-{YEAR}-{SEQ}', true, CURRENT_TIMESTAMP, 'system')" \
  && { echo "ATTENDU : echec"; RC=1; } || echo "echec constate (attendu)"
echo "-- l'ecriture actuelle (UPDATE puis INSERT) passe"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -c "UPDATE facturation_settings SET code_format = 'FAC-{SEQ}' WHERE center_id = '99996700-0000-0000-0000-00000000000c'" \
  -c "INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) VALUES ('99996700-0000-0000-0000-00000000000c', 'FAC-{SEQ}', true, CURRENT_TIMESTAMP, 'rejeu')" \
  -c "DELETE FROM facturation_settings WHERE center_id = '99996700-0000-0000-0000-00000000000c'" || RC=1

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL des parametres (contexte facturation) ==="
./mvnw -o -pl contexts/facturation test -Dtest='ParametresPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrat PostgreSQL des regles de TVA (contexte comptabilite) ==="
./mvnw -o -pl contexts/comptabilite test -Dtest='RegleTvaPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcomptabilite.pg16.url="$URL" -Dcomptabilite.pg16.user=facturation_test -Dcomptabilite.pg16.password="$PW" || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
