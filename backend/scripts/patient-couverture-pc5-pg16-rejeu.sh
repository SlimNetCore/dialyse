#!/bin/bash
# Rejeu de la slice PC5 (lectures publiees patientcouverture.v1) de patient-couverture sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues par les adapters du langage publie
# (patients, prise_en_charge, attestation_droit, forfait) existent dans le schema reel, puis le contrat PostgreSQL du module : le meme
# jeu de tests que le double en memoire et que H2 pour FichesDesPatients, CouvertureDesPatients et CompteursDeCouverture
# (LecturesPublieesPostgresContractIT), plus le contrat des depots (PatientsJdbcPostgresContractIT, courses reelles comprises).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-pc5-pg16-rejeu.sh 55464 > rejeu-pc5.log 2>&1 &
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
"$PGBIN/initdb" -D "$TMP/data" -U patient_couverture_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U patient_couverture_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonnes lues par les adapters du langage publie (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes patients id center_id prenom nom etat_patient date_evenement_etat date_admission created_at
verifier_colonnes prise_en_charge id patient_id center_id statut date_debut_demande date_fin_demande forfait_demande_id date_debut_effectif date_fin_effectif forfait_effectif_id created_at
verifier_colonnes attestation_droit id patient_id center_id date_debut date_fin created_at
verifier_colonnes forfait id center_id libelle prix

echo "=== PC5 : requetes de parite de verifications-production.md section 44, SELECT seulement, executables sur le schema reel ==="
req() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }; }
req "44-1 (compteurs du tableau de bord par centre)" "SELECT c.id, (SELECT count(1) FROM prise_en_charge WHERE center_id = c.id AND statut = 'CREE') AS pec_cree, (SELECT count(1) FROM prise_en_charge WHERE center_id = c.id AND statut = 'VALIDEE' AND date_fin_demande <= current_date + 30) AS pec_a_echeance, (SELECT count(1) FROM attestation_droit WHERE center_id = c.id AND date_fin <= current_date + 30) AS attestation_a_echeance FROM centers c ORDER BY c.name"
req "44-3 (eligibilite a la facturation au jour, trois etages)" "SELECT c.center_id, count(DISTINCT c.patient_id) FILTER (WHERE COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL AND ((c.date_debut_effectif IS NOT NULL AND current_date >= c.date_debut_effectif AND (current_date <= c.date_fin_effectif OR c.date_fin_effectif IS NULL)) OR (c.date_debut_demande IS NOT NULL AND current_date >= c.date_debut_demande AND (current_date <= c.date_fin_demande OR c.date_fin_demande IS NULL)))) AS etage_1, count(DISTINCT c.patient_id) FILTER (WHERE c.statut = 'VALIDEE' AND EXISTS (SELECT 1 FROM attestation_droit a WHERE a.center_id = c.center_id AND a.patient_id = c.patient_id AND current_date >= a.date_debut AND (current_date <= a.date_fin OR a.date_fin IS NULL))) AS etage_3 FROM prise_en_charge c GROUP BY 1 ORDER BY 1"
req "44-4 (forfaits de PEC absents ou d'un autre centre)" "SELECT c.center_id, count(*) FROM prise_en_charge c LEFT JOIN forfait f ON f.id = COALESCE(c.forfait_effectif_id, c.forfait_demande_id) AND f.center_id = c.center_id WHERE COALESCE(c.forfait_effectif_id, c.forfait_demande_id) IS NOT NULL AND f.id IS NULL GROUP BY 1"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL du langage publie et des depots du module ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.LecturesPublieesPostgresContractIT,$P.PatientsJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
