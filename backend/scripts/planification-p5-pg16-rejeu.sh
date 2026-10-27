#!/bin/bash
# Rejeu de la slice P5 de la Planification (placement : places des patients, verrou du centre, risque infectieux, noms) sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), controles SELECT-only des donnees heritees tels que
# documentes dans verifications-production.md (patients dont le placement est incomplet, deux patients sur le meme generateur un meme
# jour, patients sains en salle d'isolement, doublons d'alertes ISOLEMENT_IMPOSSIBLE / GENERATEUR_INDISPONIBLE), puis contrat PostgreSQL des
# places (memes tests que le double en memoire et H2, dont lock_timeout et la course sur la meme place).
# A lancer en arriere-plan avec un log : backend/scripts/planification-p5-pg16-rejeu.sh 55465 > rejeu-planification-p5.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55465}"
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

echo "=== P5 : controles SELECT-only des donnees heritees (verifications-production.md, doivent etre vides sur une base neuve) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM patients WHERE (salle_id IS NOT NULL OR position_id IS NOT NULL OR generateur_id IS NOT NULL) AND NOT (salle_id IS NOT NULL AND position_id IS NOT NULL AND generateur_id IS NOT NULL AND (jour_dimanche OR jour_lundi OR jour_mardi OR jour_mercredi OR jour_jeudi OR jour_vendredi OR jour_samedi))" | tr -d '\r' | grep -qE '^[0-9]+$' \
  && echo "patients au placement incomplet : requete executable (le nombre est a lire en production)" || { echo "ATTENDU : requete executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM (SELECT center_id, generateur_id, 1 FROM patients WHERE generateur_id IS NOT NULL AND coalesce(etat_patient,'') NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') AND jour_lundi GROUP BY center_id, generateur_id HAVING count(*) > 1) d" | tr -d '\r' | grep -qx 0 \
  && echo "aucun generateur partage le lundi" || { echo "ATTENDU : 0 generateur partage"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM (SELECT center_id, type, cree_le::date, payload FROM notification_evenement WHERE type IN ('ISOLEMENT_IMPOSSIBLE','GENERATEUR_INDISPONIBLE','RETOUR_SALLE_ORDINAIRE_PROPOSE') GROUP BY 1, 2, 3, 4 HAVING count(*) > 1) d" | tr -d '\r' | grep -qx 0 \
  && echo "aucun doublon d'alerte de placement (meme centre, type, jour, contenu)" || { echo "ATTENDU : 0 doublon"; RC=1; }

echo "=== P5 : requetes de verifications-production.md (section Slice P5), executables sur le schema reel ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT c.id, c.code FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id = c.id)" >/dev/null \
  && echo "requete 1 (centres sans ligne planning_parametres) executable" || { echo "ATTENDU : requete 1 executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT p.center_id, p.id, p.nom, p.prenom, s.nom AS salle FROM patients p JOIN salle s ON s.id = p.salle_id WHERE s.isolement = 'OUI' AND NOT EXISTS (SELECT 1 FROM serologies_patient x WHERE x.center_id = p.center_id AND x.patient_id = p.id AND x.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND x.resultat = 'POSITIF' AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = x.center_id AND n.patient_id = x.patient_id AND n.marqueur = x.marqueur AND (n.date_prelevement > x.date_prelevement OR (n.date_prelevement = x.date_prelevement AND n.created_at > x.created_at))))" >/dev/null \
  && echo "requete 2 (patients sains en salle d'isolement) executable" || { echo "ATTENDU : requete 2 executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT s.center_id, s.patient_id, count(*) FROM serologies_patient s WHERE NOT EXISTS (SELECT 1 FROM patients p WHERE p.id = s.patient_id AND p.center_id = s.center_id) GROUP BY 1, 2" >/dev/null \
  && echo "requete 3 (serologies de patients hors centre) executable" || { echo "ATTENDU : requete 3 executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT id, code, fuseau_horaire FROM centers WHERE fuseau_horaire IS NULL OR btrim(fuseau_horaire) = ''" >/dev/null \
  && echo "requete 4 (centres sans fuseau) executable" || { echo "ATTENDU : requete 4 executable"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT center_id, payload::jsonb->>'generateur' AS generateur, cree_le::date AS jour, count(*) FROM notification_evenement WHERE type = 'GENERATEUR_INDISPONIBLE' GROUP BY 1, 2, 3 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50" >/dev/null \
  && echo "requete 5 (doublons d'alertes de generateur) executable" || { echo "ATTENDU : requete 5 executable"; RC=1; }

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.planification.contrat
echo "=== Contrat PostgreSQL des places (dont lock_timeout et course sur la meme place) ==="
./mvnw -o -pl contexts/planification -am test \
  -Dtest="$P.PlacesJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dplanification.pg16.url="$URL" -Dplanification.pg16.user=planification_test -Dplanification.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
