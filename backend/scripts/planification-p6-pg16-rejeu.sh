#!/bin/bash
# Rejeu de la slice P6 de la Planification (planning de la semaine, presence des patients, base de la capacite theorique) sur un
# PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), requetes SELECT-only de
# verifications-production.md (section Slice P6) executees sur le schema reel, puis contrat PostgreSQL des lectures de la semaine
# (memes tests que H2 : sejours des patients, seances realisees, deplacements temporaires, series du centre).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p6-pg16-rejeu.sh 55466 > rejeu-planification-p6.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55466}"
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

echo "=== P6 : requetes de verifications-production.md (section Slice P6), executables sur le schema reel ==="
req() { # $1 = libelle, $2 = SQL
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }
}
req "1 (patients en sommeil avec case)" "SELECT center_id, count(*) AS en_sommeil_avec_case FROM patients WHERE COALESCE(en_sommeil, FALSE) = TRUE AND salle_id IS NOT NULL AND position_id IS NOT NULL AND (jour_dimanche OR jour_lundi OR jour_mardi OR jour_mercredi OR jour_jeudi OR jour_vendredi OR jour_samedi) GROUP BY center_id ORDER BY 2 DESC"
req "2 (centres de file active 1 a 4)" "SELECT c.id, c.code, count(p.id) AS file_active FROM centers c LEFT JOIN patients p ON p.center_id = c.id AND COALESCE(p.en_sommeil, FALSE) = FALSE AND (p.date_admission IS NULL OR p.date_admission <= current_date) AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') OR (p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= date_trunc('year', current_date))) GROUP BY c.id, c.code HAVING count(p.id) BETWEEN 1 AND 4 ORDER BY 3"
req "3 (generateurs plus comptes)" "SELECT centre_id, statut, count(*) FROM gmao_equipements WHERE type = 'GENERATEUR_DIALYSE' AND deleted_at IS NULL AND statut NOT IN ('EN_SERVICE', 'REFORME', 'DESACTIF') GROUP BY centre_id, statut ORDER BY 1, 2"
req "4 (jours d'ouverture en minuscules)" "SELECT center_id, jours_ouverts FROM planning_parametres WHERE jours_ouverts IS NOT NULL AND jours_ouverts <> upper(jours_ouverts)"
req "5 (patients par poste hors 1..10)" "SELECT center_id, patients_par_poste_serie FROM planning_parametres WHERE patients_par_poste_serie IS NOT NULL AND (patients_par_poste_serie < 1 OR patients_par_poste_serie > 10)"
req "6 (feries et fermetures a venir)" "SELECT center_id, day_date, label FROM center_holiday WHERE day_date BETWEEN current_date AND current_date + 60 UNION ALL SELECT center_id, day_date, reason FROM center_closure_day WHERE day_date BETWEEN current_date AND current_date + 60 ORDER BY 2"

req "7 (ecarts entre les regles de presence)" "SELECT center_id, count(*) FILTER (WHERE COALESCE(en_sommeil, FALSE) AND salle_id IS NOT NULL AND position_id IS NOT NULL) AS sommeil_avec_case, count(*) FILTER (WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND date_evenement_etat IS NOT NULL AND date_evenement_etat < current_date AND salle_id IS NOT NULL AND position_id IS NOT NULL) AS sortis_avec_case, count(*) FILTER (WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND date_evenement_etat IS NULL) AS sortis_sans_date FROM patients GROUP BY center_id HAVING count(*) FILTER (WHERE COALESCE(en_sommeil, FALSE) OR etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI')) > 0 ORDER BY 1"

echo "=== Base neuve : aucun jour d'ouverture en minuscules, aucun patient en sommeil avec case ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM planning_parametres WHERE jours_ouverts <> upper(jours_ouverts)" | tr -d '\r' | grep -qx 0 \
  && echo "aucun jour d'ouverture en minuscules" || { echo "ATTENDU : 0"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL des lectures de la semaine ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.LecturesDeLaSemaineJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1

echo "=== Contrat PostgreSQL de la detection des absences (presence unique, ordre des identifiants) ==="
./mvnw -o -pl contexts/absences-patients -am test \
  -Dtest="com.hemodialyse.backend.contexts.absencespatients.contrat.DetectionPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dabsences.pg16.url="$URL" -Dabsences.pg16.user=planification_test -Dabsences.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
