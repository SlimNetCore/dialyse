#!/bin/bash
# Rejeu de la slice P8 de la Planification (optimisation du planning) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql), requetes SELECT-only de verifications-production.md (section Slice P8) executees sur le schema reel,
# puis contrat PostgreSQL de la persistance de l'optimisation (memes tests que H2 et le double en memoire : executions, deplacements
# temporaires, calendrier fige) et « un seul calcul par centre » tenu EN BASE (lancements simultanes de plusieurs instances simulees,
# lock_timeout, 409).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p8-pg16-rejeu.sh 55468 > rejeu-planification-p8.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55468}"
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

echo "=== P8 : requetes de verifications-production.md (section Slice P8), executables sur le schema reel ==="
req() { # $1 = libelle, $2 = SQL
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }
}
req "1 (executions par centre, statut et perimetre)" "SELECT center_id, statut, perimetre, count(*), max(cree_le) FROM planification_optimisation GROUP BY center_id, statut, perimetre ORDER BY 1, 2, 3"
req "2 (calculs restes EN_COURS depuis plus d'une heure)" "SELECT id, center_id, cree_le, lance_par FROM planification_optimisation WHERE statut = 'EN_COURS' AND cree_le < now() - interval '1 hour' ORDER BY cree_le"
req "3 (centres avec plusieurs calculs EN_COURS : doit etre vide)" "SELECT center_id, count(*) FROM planification_optimisation WHERE statut = 'EN_COURS' GROUP BY center_id HAVING count(*) > 1"
req "4 (plus de 20 executions par centre : doit etre vide)" "SELECT center_id, count(*) FROM planification_optimisation GROUP BY center_id HAVING count(*) > 20"
req "5 (propositions appliquees)" "SELECT center_id, perimetre, count(*), max(applique_le) FROM planification_optimisation WHERE applique_le IS NOT NULL GROUP BY center_id, perimetre ORDER BY 1, 2"
req "6 (empreintes absentes ou de longueur inattendue)" "SELECT id, center_id, length(empreinte) FROM planification_optimisation WHERE empreinte IS NULL OR length(empreinte) <> 64"
req "7 (calendriers orphelins)" "SELECT c.center_id, c.run_id, count(*) FROM planification_calendrier_case c WHERE NOT EXISTS (SELECT 1 FROM planification_optimisation o WHERE o.id = c.run_id AND o.center_id = c.center_id) GROUP BY c.center_id, c.run_id"
req "8 (deplacements temporaires dont la cible est incoherente)" "SELECT d.center_id, d.patient_id, d.date_seance FROM deplacement_temporaire d WHERE NOT EXISTS (SELECT 1 FROM gmao_equipements e WHERE e.id = d.generateur_id AND e.statut = 'EN_SERVICE' AND e.deleted_at IS NULL) OR NOT EXISTS (SELECT 1 FROM salle s WHERE s.id = d.salle_id AND s.center_id = d.center_id) OR NOT EXISTS (SELECT 1 FROM position_creneau p WHERE p.id = d.creneau_id AND p.center_id = d.center_id) ORDER BY d.date_seance"
req "9 (deplacements temporaires passes)" "SELECT center_id, count(*), min(date_seance), max(date_seance) FROM deplacement_temporaire WHERE date_seance < current_date GROUP BY center_id"
req "10 (remplacements sans remplace, crees par une couverture)" "SELECT center_id, count(*) FROM infirmier_remplacement WHERE remplace_infirmier_id IS NULL GROUP BY center_id"
req "11 (alertes de l'optimisation)" "SELECT type, count(*), max(cree_le) FROM notification_evenement WHERE type IN ('OPTIMISATION_PROPOSITION','SEANCES_DEPLACEES','REPLANIFICATION_NOCTURNE','INFIRMIER_ROULEMENT_MODIFIE','INFIRMIER_REMPLACEMENT_AFFECTE') GROUP BY type"
req "12 (centres actifs avec replanification de nuit)" "SELECT r.center_id FROM planification_reglages r JOIN centers c ON c.id = r.center_id AND COALESCE(c.actif, TRUE) = TRUE WHERE r.replanification_auto = TRUE ORDER BY 1"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL de la persistance de l'optimisation + un seul calcul par centre tenu en base ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.OptimisationJdbcPostgresContractIT,$P.LancementConcurrentPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
