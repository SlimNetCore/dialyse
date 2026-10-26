#!/bin/bash
# Rejeu de la slice S8 (controle des peremptions) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie.
# Chaine Flyway V1..Vn (psql), puis contrat PostgreSQL du contexte stock : vrais adaptateurs (centres actifs, lectures des lots, articles,
# fuseau du centre, unite de travail) du ControlerLesPeremptions : centres actifs seulement, fenetre J+30 incluse, lots epuises et sans
# date exclus, articles inactifs exclus, rappel hebdomadaire des perimes ; enfin le constat SQL de la requete des lots proches de la
# peremption (type DATE, borne incluse) sur le schema Flyway PUR.
# A lancer en arriere-plan avec un log : backend/scripts/stock-s8-pg16-rejeu.sh 55444 > rejeu-s8.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55444}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U stock_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U stock_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constat SQL sur le schema Flyway PUR ==="
C='99996800-0000-0000-0000-0000000000e1'
echo "-- S8 : lots en stock qui perimment au plus tard a J+30 (borne incluse), epuises et sans date exclus, plus proches d'abord"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q \
  -c "INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, unite) VALUES ('99996800-0000-0000-0001-0000000000e1', true, '$C', 'PER', false, 'Per', 0, 0, 'piece')" \
  -c "INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, date_peremption, quantite_initiale, quantite_restante) VALUES
      ('99996800-0000-0000-0002-0000000000e1', '99996800-0000-0000-0001-0000000000e1', '$C', now(), 'J30', current_date + 30, 5, 5),
      ('99996800-0000-0000-0002-0000000000e2', '99996800-0000-0000-0001-0000000000e1', '$C', now(), 'J31', current_date + 31, 5, 5),
      ('99996800-0000-0000-0002-0000000000e3', '99996800-0000-0000-0001-0000000000e1', '$C', now(), 'PERIME', current_date - 1, 5, 5),
      ('99996800-0000-0000-0002-0000000000e4', '99996800-0000-0000-0001-0000000000e1', '$C', now(), 'VIDE', current_date + 1, 5, 0),
      ('99996800-0000-0000-0002-0000000000e5', '99996800-0000-0000-0001-0000000000e1', '$C', now(), 'SANS-DATE', NULL, 5, 5)" || RC=1
ORDRE="$("${PSQL[@]}" -d hemo_test -tAc "SELECT string_agg(numero_lot, ',' ORDER BY date_peremption ASC, numero_lot ASC) FROM lots WHERE center_id = '$C' AND quantite_restante > 0 AND date_peremption IS NOT NULL AND date_peremption <= current_date + 30" | tr -d '\r')"
[ "$ORDRE" = "PERIME,J30" ] && echo "lots proches corrects ($ORDRE)" || { echo "ATTENDU : PERIME,J30, obtenu : $ORDRE"; RC=1; }
"${PSQL[@]}" -d hemo_test -q -c "DELETE FROM lots WHERE center_id = '$C'" -c "DELETE FROM articles WHERE center_id = '$C'" || RC=1

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du contrôle des peremptions (centres actifs, fenetre, articles inactifs, rappel hebdomadaire) ==="
./mvnw -o -pl contexts/stock -am test -Dtest='PeremptionsPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dstock.pg16.url="$URL" -Dstock.pg16.user=stock_test -Dstock.pg16.password="$PW" || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
