#!/bin/bash
# Rejeu des slices S5 (receptions et PMP), S6 (sorties manuelles) et S7 (inventaire) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit
# en sortie. Chaine Flyway V1..Vn (psql), puis contrats PostgreSQL du contexte stock : verrou de ligne SELECT ... FOR UPDATE avec delai
# de requete (409 STOCK_ECRITURE_CONCURRENTE apres 5 s), verrouillages croises sans interblocage, sorties simultanees d'un meme lot sans
# survente ; l'inventaire (S7 : cycle ouverture-comptage-cloture, deux ouvertures simultanees, ouverture qui attend une ecriture en
# cours) ; enfin la requete FEFO des lots disponibles (NULLS LAST) et la sequence BS lues par SQL.
# A lancer en arriere-plan avec un log : backend/scripts/stock-pg16-rejeu.sh 55443 > rejeu.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55443}"
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

echo "=== Constats SQL sur le schema Flyway PUR ==="
C='99996800-0000-0000-0000-00000000000b'
echo "-- S6 : lots disponibles en FEFO, date de peremption absente en dernier (NULLS LAST), lots epuises exclus"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q \
  -c "INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, unite) VALUES ('99996800-0000-0000-0001-000000000001', true, '$C', 'FEFO', false, 'Fefo', 0, 0, 'piece')" \
  -c "INSERT INTO lots (id, article_id, center_id, created_at, numero_lot, date_peremption, quantite_initiale, quantite_restante) VALUES
      ('99996800-0000-0000-0002-000000000001', '99996800-0000-0000-0001-000000000001', '$C', now(), 'SANS-DATE', NULL, 5, 5),
      ('99996800-0000-0000-0002-000000000002', '99996800-0000-0000-0001-000000000001', '$C', now(), 'TARD', current_date + 90, 5, 5),
      ('99996800-0000-0000-0002-000000000003', '99996800-0000-0000-0001-000000000001', '$C', now(), 'TOT', current_date + 10, 5, 5),
      ('99996800-0000-0000-0002-000000000004', '99996800-0000-0000-0001-000000000001', '$C', now(), 'VIDE', current_date + 1, 5, 0)" || RC=1
ORDRE="$("${PSQL[@]}" -d hemo_test -tAc "SELECT string_agg(numero_lot, ',' ORDER BY date_peremption ASC NULLS LAST, created_at ASC) FROM lots WHERE center_id = '$C' AND article_id = '99996800-0000-0000-0001-000000000001' AND quantite_restante > 0" | tr -d '\r')"
[ "$ORDRE" = "TOT,TARD,SANS-DATE" ] && echo "ordre FEFO correct ($ORDRE)" || { echo "ATTENDU : TOT,TARD,SANS-DATE, obtenu : $ORDRE"; RC=1; }
"${PSQL[@]}" -d hemo_test -q -c "DELETE FROM lots WHERE center_id = '$C'" -c "DELETE FROM articles WHERE center_id = '$C'" || RC=1

echo "-- S6 : la sequence SEQ_BS s'incremente atomiquement (UPDATE puis INSERT de app_settings)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q \
  -c "INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES ('$C', 'SEQ_BS', 'BS-', 1)" \
  -c "UPDATE app_settings SET dernier_compteur = dernier_compteur + 1 WHERE center_id = '$C' AND cle = 'SEQ_BS'" || RC=1
"${PSQL[@]}" -d hemo_test -tAc "SELECT prefixe || lpad(dernier_compteur::text, 5, '0') FROM app_settings WHERE center_id = '$C' AND cle = 'SEQ_BS'" | tr -d '\r' | grep -qx 'BS-00002' \
  && echo "BS-00002 lu" || { echo "ATTENDU : BS-00002"; RC=1; }
"${PSQL[@]}" -d hemo_test -q -c "DELETE FROM app_settings WHERE center_id = '$C'" || RC=1

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL du contexte stock (verrou de ligne et delai, verrouillages croises, sorties simultanees, inventaire) ==="
./mvnw -o -pl contexts/stock -am test -Dtest='StockPostgresContractIT,InventairePostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dstock.pg16.url="$URL" -Dstock.pg16.user=stock_test -Dstock.pg16.password="$PW" || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC

