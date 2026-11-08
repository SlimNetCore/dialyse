#!/bin/bash
# Rejeu de la sous-slice SE7a (journal du jour, calendrier du centre, volet medical, seances recentes) du contexte Seance sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes reelles de center_holiday,
# center_closure_day et volet_medical (types, bornes, unicite) ; requete SQL de l'ACL des patients du jour executable sur le schema reel ;
# requetes de controle de verifications-production.md section 59 (SELECT seule) executables ; puis les contrats PostgreSQL du calendrier, des
# volets medicaux et des seances (dont la lecture des dernieres seances).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7a-pg16-rejeu.sh 55507 > rejeu-seance-se7a.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55507}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${SEANCE_PG16_PASSWORD:-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)}"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U seance_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
export SEANCE_PG16_PASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U seance_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }
colonne() { # table colonne type longueur(ou vide)
  local n
  if [ -n "$4" ]; then
    n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3' AND character_maximum_length=$4")"
  else
    n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3'")"
  fi
  [ "$n" = "1" ] && echo "ok : $1.$2 $3 $4" || { echo "INATTENDU : $1.$2 ($3 $4)"; RC=1; }
}
unicite() { # table colonnes (ex. "center_id, day_date") : une contrainte d'unicite sur exactement ces colonnes
  local n
  n="$(scalaire "SELECT count(*) FROM pg_constraint c WHERE c.contype IN ('u','p') AND c.conrelid = 'public.$1'::regclass AND (SELECT string_agg(a.attname, ', ' ORDER BY a.attname) FROM pg_attribute a WHERE a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)) = '$2'")"
  [ "$n" -ge 1 ] && echo "ok : unicite $1 ($2)" || { echo "INATTENDU : pas d'unicite $1 ($2)"; RC=1; }
}

echo "=== Calendrier du centre : colonnes reelles, bornes du libelle (255), unicite (centre, jour), aucune colonne ajoutee ==="
colonne center_holiday id uuid ""
colonne center_holiday center_id uuid ""
colonne center_holiday day_date date ""
colonne center_holiday label "character varying" 255
colonne center_closure_day id uuid ""
colonne center_closure_day center_id uuid ""
colonne center_closure_day day_date date ""
colonne center_closure_day reason "character varying" 255
unicite center_holiday "center_id, day_date"
unicite center_closure_day "center_id, day_date"
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='center_holiday'")" = "4" ] \
  && echo "ok : center_holiday a exactement 4 colonnes" || { echo "INATTENDU : colonnes de center_holiday"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='center_closure_day'")" = "4" ] \
  && echo "ok : center_closure_day a exactement 4 colonnes" || { echo "INATTENDU : colonnes de center_closure_day"; RC=1; }

echo "=== Volet medical : colonnes reelles, unicite par seance, aucune colonne ajoutee ==="
colonne volet_medical id uuid ""
colonne volet_medical center_id uuid ""
colonne volet_medical seance_id uuid ""
# Les six observations sont de type text SANS borne (aucun plafond applicatif, KB-SEA-59 point 5 de l'ADR 0094) : data_type text, longueur nulle.
colonne volet_medical prescription text ""
colonne volet_medical tolerance_seance text ""
colonne volet_medical examen_clinique text ""
colonne volet_medical resultats_biologiques text ""
colonne volet_medical ajustements_therapeutiques text ""
colonne volet_medical conclusion_medicale text ""
colonne volet_medical created_at "timestamp with time zone" ""
colonne volet_medical updated_at "timestamp with time zone" ""
unicite volet_medical "seance_id"
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='volet_medical'")" = "11" ] \
  && echo "ok : volet_medical a exactement 11 colonnes" || { echo "INATTENDU : colonnes de volet_medical"; RC=1; }

echo "=== ACL des patients du jour : la requete du legacy s'execute sur le schema reel (SELECT seule) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT s.id AS seance_id, s.patient_id AS patient_id, s.date_seance AS date_seance, s.statut AS statut, p.code_patient AS code_patient, p.nom AS nom, p.prenom AS prenom FROM seances s INNER JOIN patients p ON p.id = s.patient_id WHERE s.center_id = '00000000-0000-0000-0000-00000000000a' AND s.date_seance = DATE '2026-06-15' ORDER BY p.nom ASC, p.prenom ASC, s.id ASC" >/dev/null \
  && echo "requete executable" || { echo "ECHEC de la requete des patients du jour"; RC=1; }
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT id FROM seances WHERE center_id = '00000000-0000-0000-0000-00000000000a' AND patient_id = '00000000-0000-0000-0000-000000000001' AND date_seance < DATE '2026-06-15' ORDER BY date_seance DESC, created_at DESC NULLS LAST, id ASC LIMIT 10" >/dev/null \
  && echo "requete des dernieres seances executable" || { echo "ECHEC de la requete des dernieres seances"; RC=1; }

echo "=== SE7a : requetes de controle (SELECT seule, section 59), executables sur le schema reel ==="
for q in \
  "SELECT v.center_id, count(*) AS volets_sur_seance_signee FROM volet_medical v INNER JOIN seances s ON s.id = v.seance_id AND s.center_id = v.center_id WHERE s.statut = 'SIGNEE' AND v.updated_at > s.signed_medecin_at GROUP BY v.center_id" \
  "SELECT a.center_id, a.roles, a.username, count(*) AS ecritures, count(*) FILTER (WHERE a.roles NOT LIKE '%MEDECIN%') AS ecritures_sans_role_medecin FROM audit_log a WHERE a.action_code = 'SEANCES_MODIFICATION' AND a.http_method = 'PUT' AND a.route_template = '/api/v1/seances/{seanceId}/volet-medical' AND a.status_code BETWEEN 200 AND 299 GROUP BY a.center_id, a.roles, a.username ORDER BY 4 DESC LIMIT 50" \
  "SELECT v.center_id, count(*) AS volets, count(*) FILTER (WHERE v.prescription IS NULL AND v.tolerance_seance IS NULL AND v.examen_clinique IS NULL AND v.resultats_biologiques IS NULL AND v.ajustements_therapeutiques IS NULL AND v.conclusion_medicale IS NULL) AS volets_vides, max(GREATEST(COALESCE(length(v.prescription),0), COALESCE(length(v.tolerance_seance),0), COALESCE(length(v.examen_clinique),0), COALESCE(length(v.resultats_biologiques),0), COALESCE(length(v.ajustements_therapeutiques),0), COALESCE(length(v.conclusion_medicale),0))) AS longueur_maximale_d_une_observation FROM volet_medical v GROUP BY v.center_id" \
  "SELECT s.center_id, s.statut, count(*) AS seances_exposees_aux_recentes FROM seances s WHERE s.statut = 'CREE' AND s.date_seance < CURRENT_DATE GROUP BY s.center_id, s.statut" \
  "SELECT h.center_id, h.day_date, count(*) AS seances_un_jour_ferie FROM center_holiday h INNER JOIN seances s ON s.center_id = h.center_id AND s.date_seance = h.day_date GROUP BY h.center_id, h.day_date ORDER BY 3 DESC LIMIT 100" \
  "SELECT c.center_id, c.day_date, count(*) AS seances_un_jour_de_fermeture FROM center_closure_day c INNER JOIN seances s ON s.center_id = c.center_id AND s.date_seance = c.day_date GROUP BY c.center_id, c.day_date ORDER BY 3 DESC LIMIT 100" \
  "SELECT 'center_holiday' AS table_liee, count(*) AS libelles_de_plus_de_200_caracteres FROM center_holiday WHERE length(label) > 200 UNION ALL SELECT 'center_closure_day', count(*) FROM center_closure_day WHERE length(reason) > 200"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
for contrat in CalendrierDuCentrePostgresContractIT VoletsMedicauxPostgresContractIT SeancesPostgresContractIT; do
  echo "=== Contrat PostgreSQL : $contrat ==="
  ./mvnw -o -pl contexts/seance -am test \
    -Dtest="com.hemodialyse.backend.contexts.seance.contrat.$contrat" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
done
exit $RC
