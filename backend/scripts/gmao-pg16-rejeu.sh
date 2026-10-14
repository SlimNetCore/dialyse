#!/bin/bash
# Rejeu de G1 (plans), G2 (intervenants), G3 (equipements), G4 (contrat des interventions) et G5 (contrat des pieces jointes) sur un PostgreSQL 16 REEL et
# JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), constat du schema Flyway pur (unicite du code equipement, colonne
# tachesaeffectuer), contrats PG du module gmao, puis suites de caracterisation HTTP (ddl-auto=update comme la production
# historique : la colonne tachesaeffectuer, KB-21, n'existe que par Hibernate).
# G4 temps 2 : la migration V36 (table gmao_taches_intervention, ADDITIVE) est jouee avec les autres ; son schema Flyway pur est
# constate ci-dessous et le contrat des interventions (taches comprises) est rejoue.
# G6 : le contrat PG des lectures de pilotage (PilotagePostgresContractIT : sommes de couts, comptages, historique, generateurs installes) est rejoue ici ;
# la suite HTTP de G6 reste sur H2.
# G5 : le contrat PG des pieces jointes (colonne contenu = vrai bytea, liste sans octets) et celui des interventions hotes, avec la preuve que
# SELECT ... FOR UPDATE fait attendre une seconde transaction (quota de 50 sous concurrence, KB-GMAO-58), sont rejoues ici ; la suite HTTP de G5
# insere ses interventions en SQL sans les cles etrangeres : elle reste sur H2.
# G4 : seul le contrat PG du repository des interventions est rejoue ici (InterventionPostgresContractIT) ; la suite HTTP de G4
# insere ses fixtures en SQL sans les cles etrangeres vers centers et app_user : elle reste sur H2.
# Dure environ 8 minutes : a lancer en arriere-plan avec un log (backend/scripts/gmao-pg16-rejeu.sh > rejeu.log 2>&1 &).
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Usage : backend/scripts/gmao-pg16-rejeu.sh [port]
# Code de sortie : 0 si tout est vert, 1 si une etape a echoue (toutes les etapes sont jouees).
set -uo pipefail
PORT="${1:-55432}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U gmao_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U gmao_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constat sur le schema Flyway PUR (avant tout demarrage d'Hibernate) ==="
echo "-- contraintes d'unicite de gmao_equipements (KB-GMAO-33 : V5 declare code UNIQUE global, JPA declare (centre_id, code))"
"${PSQL[@]}" -d hemo_test -c "select conname, pg_get_constraintdef(oid) from pg_constraint where conrelid = 'gmao_equipements'::regclass and contype = 'u'"
echo "-- colonne du texte des taches de gmao_plans_maintenance (KB-GMAO-21 : tachesaeffectuer ou taches_a_effectuer)"
"${PSQL[@]}" -d hemo_test -c "select column_name from information_schema.columns where table_name = 'gmao_plans_maintenance' and column_name in ('tachesaeffectuer', 'taches_a_effectuer')"

echo "-- table des taches d'intervention (V36, KB-GMAO-55) : colonnes et cle etrangere vers gmao_interventions"
"${PSQL[@]}" -d hemo_test -c "select column_name, data_type, is_nullable from information_schema.columns where table_name = 'gmao_taches_intervention' order by ordinal_position"
"${PSQL[@]}" -d hemo_test -c "select conname, pg_get_constraintdef(oid) from pg_constraint where conrelid = 'gmao_taches_intervention'::regclass and contype = 'f'"

echo "-- table des pieces jointes (V7, G5) : colonnes (contenu = bytea) et absence de cle etrangere"
"${PSQL[@]}" -d hemo_test -c "select column_name, data_type, is_nullable from information_schema.columns where table_name = 'gmao_documents_intervention' order by ordinal_position"
"${PSQL[@]}" -d hemo_test -c "select count(*) as cles_etrangeres from pg_constraint where conrelid = 'gmao_documents_intervention'::regclass and contype = 'f'"

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL du module gmao (G1, G2, G3, G4, G5) ==="
./mvnw -o -pl contexts/gmao test -Dtest='PlanMaintenancePostgresContractIT,IntervenantPostgresContractIT,EquipementPostgresContractIT,InterventionPostgresContractIT,PieceJointePostgresContractIT,InterventionsHotesPostgresContractIT,PilotagePostgresContractIT' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dgmao.pg16.url="$URL" -Dgmao.pg16.user=gmao_test -Dgmao.pg16.password="$PW" || RC=1
echo "=== Suites de caracterisation HTTP (G1, G2, G3) ==="
./mvnw -o -pl platform -am test -Dtest='PlanMaintenanceHttpCharacterizationIntegrationTest,IntervenantHttpCharacterizationIntegrationTest,EquipementHttpCharacterizationIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dgmao.pg16=true -DargLine="-Dgmao.pg16=true" \
  -Dspring.datasource.url="$URL" -Dspring.datasource.username=gmao_test -Dspring.datasource.password="$PW" \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver -Dspring.jpa.hibernate.ddl-auto=update -Dspring.sql.init.mode=never || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
