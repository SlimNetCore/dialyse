#!/bin/bash
# Rejeu de la sous-slice SE7b (tableau de bord mensuel des seances) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes reelles lues par les adapters (types) ; les requetes SQL des trois
# adapters de lecture (seances realisees, patients du centre, presents du mois, identites) executees sur le schema reel (SELECT seule) ;
# requetes de controle de verifications-production.md section 60 (SELECT seule) executables. Aucun contrat Maven : ces adapters ne
# font que lire (le calendrier du centre garde son contrat PostgreSQL de SE7a).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7b-pg16-rejeu.sh 55517 > rejeu-seance-se7b.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente). Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55517}"
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
colonne() { # table colonne type
  local n
  n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3'")"
  [ "$n" = "1" ] && echo "ok : $1.$2 $3" || { echo "INATTENDU : $1.$2 ($3)"; RC=1; }
}

echo "=== Colonnes reelles lues par les adapters du tableau de bord (types) ==="
for c in id center_id patient_id; do colonne seances $c uuid; done
colonne seances date_seance date
colonne seances statut "character varying"
colonne patients id uuid
colonne patients center_id uuid
colonne patients date_naissance date
colonne patients date_admission date
colonne patients date_evenement_etat date
colonne patients en_sommeil boolean
for j in lundi mardi mercredi jeudi vendredi samedi dimanche; do colonne patients "jour_$j" boolean; done
for c in sexe etat_patient nom prenom; do colonne patients $c "character varying"; done

echo "=== Requetes des adapters, executees sur le schema reel (SELECT seule) ==="
CA="'00000000-0000-0000-0000-00000000000a'"
for q in \
  "SELECT COUNT(1) FROM seances WHERE center_id = $CA AND date_seance BETWEEN DATE '2026-01-01' AND DATE '2026-01-31' AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')" \
  "SELECT patient_id, date_seance, statut FROM seances WHERE center_id = $CA AND date_seance BETWEEN DATE '2026-01-01' AND DATE '2026-01-31' AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')" \
  "SELECT id, sexe, date_naissance, date_admission, en_sommeil, etat_patient, date_evenement_etat, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, jour_dimanche FROM patients WHERE center_id = $CA" \
  "SELECT DISTINCT p.id, p.sexe, p.date_naissance FROM seances s INNER JOIN patients p ON p.id = s.patient_id WHERE s.center_id = $CA AND s.date_seance BETWEEN DATE '2026-01-01' AND DATE '2026-01-31' AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')" \
  "SELECT id, nom, prenom FROM patients WHERE center_id = $CA" \
  "SELECT day_date FROM center_holiday WHERE center_id = $CA AND day_date BETWEEN DATE '2026-01-01' AND DATE '2026-01-31'" \
  "SELECT day_date FROM center_closure_day WHERE center_id = $CA AND day_date BETWEEN DATE '2026-01-01' AND DATE '2026-01-31'"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

echo "=== SE7b : requetes de controle (SELECT seule, section 60), executables sur le schema reel ==="
for q in \
  "SELECT s.center_id, count(*) AS seances_de_patients_d_un_autre_centre FROM seances s INNER JOIN patients p ON p.id = s.patient_id WHERE p.center_id <> s.center_id GROUP BY s.center_id" \
  "SELECT a.center_id, a.roles, count(*) AS lectures_du_detail_nominatif FROM audit_log a WHERE a.route_template = '/api/v1/seances/dashboard/details' AND a.status_code BETWEEN 200 AND 299 GROUP BY a.center_id, a.roles ORDER BY 3 DESC LIMIT 50" \
  "SELECT s.center_id, s.date_seance, s.patient_id, count(*) AS seances_le_meme_jour FROM seances s WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY s.center_id, s.date_seance, s.patient_id HAVING count(*) > 1 ORDER BY 4 DESC LIMIT 100" \
  "SELECT p.center_id, count(*) AS patients_sans_sexe_connu FROM patients p WHERE upper(trim(p.sexe)) NOT IN ('M', 'H', 'F') GROUP BY p.center_id" \
  "SELECT p.center_id, p.etat_patient, count(*) AS sortis_sans_date FROM patients p WHERE p.etat_patient IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI') AND p.date_evenement_etat IS NULL GROUP BY p.center_id, p.etat_patient"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done
exit $RC
