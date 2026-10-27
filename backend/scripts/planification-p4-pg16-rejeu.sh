#!/bin/bash
# Rejeu de la slice P4 de la Planification (presence, alertes, charge, impression, controle de 07:15) sur un PostgreSQL 16 REEL et
# JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), controles SELECT-only des donnees heritees tels que
# documentes dans verifications-production.md (ratios hors 1-20, centres actifs sans infirmier actif, doublons de notifications de
# presence), puis contrat PostgreSQL du personnel de la presence et des centres actifs (memes tests que le double en memoire et H2).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p4-pg16-rejeu.sh 55464 > rejeu-planification-p4.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55464}"
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

echo "=== P4 : controles SELECT-only des donnees heritees (verifications-production.md, doivent etre vides sur une base neuve) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM planning_parametres WHERE patients_par_infirmier IS NOT NULL AND (patients_par_infirmier < 1 OR patients_par_infirmier > 20)" | tr -d '\r' | grep -qx 0 \
  && echo "aucun ratio de securite hors 1-20" || { echo "ATTENDU : 0 ratio hors 1-20"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM centers c WHERE c.actif AND NOT EXISTS (SELECT 1 FROM infirmier i WHERE i.center_id = c.id AND i.actif)" | tr -d '\r' | grep -qE '^[0-9]+$' \
  && echo "centres actifs sans infirmier actif : requete executable (le nombre est a lire en production)" || { echo "ATTENDU : requete executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM (SELECT center_id, type, cree_le::date FROM notification_evenement WHERE type IN ('INFIRMIER_SOUS_EFFECTIF','INFIRMIER_SUREFFECTIF') GROUP BY 1, 2, 3 HAVING count(*) > 1) d" | tr -d '\r' | grep -qx 0 \
  && echo "aucun doublon de notification de presence (meme centre, meme type, meme jour)" || { echo "ATTENDU : 0 doublon"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL du personnel de la presence et des centres actifs ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.PersonnelDeLaPresenceJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
