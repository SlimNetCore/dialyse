#!/bin/bash
# Rejeu de la slice P7 de la Planification (liberation des places) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql), requetes SELECT-only de verifications-production.md (section Slice P7) executees sur le schema reel,
# puis contrat PostgreSQL de la liberation (memes tests que H2 : places tenues, ecriture limitee aux colonnes de place, mouvement sans
# doublon, transaction par patient, echec d'un patient dans le lot, verrou du centre tenu ailleurs, COURSE liberation contre fiche).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p7-pg16-rejeu.sh 55467 > rejeu-planification-p7.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55467}"
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

echo "=== P7 : requetes de verifications-production.md (section Slice P7), executables sur le schema reel ==="
req() { # $1 = libelle, $2 = SQL
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }
}
PLACE="(p.salle_id IS NOT NULL OR p.position_id IS NOT NULL OR p.generateur_id IS NOT NULL)"
ECHUE="((p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat < current_date) OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat <= current_date) OR (p.etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND p.date_evenement_etat IS NULL))"
req "1 (centres inactifs avec places echues)" "SELECT p.center_id, c.actif, count(*) FROM patients p LEFT JOIN centers c ON c.id = p.center_id WHERE $PLACE AND (c.id IS NULL OR c.actif = FALSE) AND $ECHUE GROUP BY p.center_id, c.actif ORDER BY 3 DESC"
req "2 (places echues des centres actifs)" "SELECT p.center_id, p.etat_patient, count(*) FROM patients p JOIN centers c ON c.id = p.center_id AND COALESCE(c.actif, TRUE) = TRUE WHERE $PLACE AND $ECHUE GROUP BY p.center_id, p.etat_patient ORDER BY 3 DESC"
req "3 (etats jamais liberes)" "SELECT center_id, etat_patient, count(*) FROM patients WHERE (salle_id IS NOT NULL OR position_id IS NOT NULL OR generateur_id IS NOT NULL) AND date_evenement_etat < current_date AND (etat_patient IS NULL OR etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER')) GROUP BY center_id, etat_patient ORDER BY 3 DESC"
req "4 (sortis en sommeil avec place)" "SELECT center_id, count(*) FROM patients WHERE COALESCE(en_sommeil, FALSE) AND (salle_id IS NOT NULL OR position_id IS NOT NULL) AND etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') GROUP BY center_id ORDER BY 2 DESC"
req "5 (mouvements PLACE_LIBEREE identiques)" "SELECT center_id, patient_id, count(*) FROM mouvement_patient WHERE type = 'PLACE_LIBEREE' GROUP BY center_id, patient_id, etat_nouveau, salle_id, position_id, generateur_id, jours_dialyse HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "6 (PLACE_LIBEREE par jour d'effet)" "SELECT date_effet, count(*) FROM mouvement_patient WHERE type = 'PLACE_LIBEREE' AND date_effet >= current_date - 14 GROUP BY date_effet ORDER BY 1 DESC"
req "7 (alertes d'echec de la liberation)" "SELECT center_id, count(*), max(cree_le) FROM notification_evenement WHERE type = 'LIBERATION_PLACES_EN_ECHEC' GROUP BY center_id"
req "8 (centres actifs sans planning_parametres)" "SELECT c.id, c.code FROM centers c WHERE COALESCE(c.actif, TRUE) = TRUE AND NOT EXISTS (SELECT 1 FROM planning_parametres pp WHERE pp.center_id = c.id)"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL de la liberation des places (dont la course liberation contre fiche) ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.LiberationJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
