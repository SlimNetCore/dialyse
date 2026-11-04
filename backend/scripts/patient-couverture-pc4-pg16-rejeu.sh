#!/bin/bash
# Rejeu de la slice PC4 (attestations de droits) de patient-couverture sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit
# en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues ou ecrites par les adapters JDBC
# (attestation_droit en lecture et ecriture, patients en lecture pour la recherche) existent dans le schema reel, requetes SELECT-only
# de verifications-production.md (attestations) executees sur le schema reel, puis le contrat PostgreSQL du module (memes tests que H2 et
# que les doubles en memoire) : depot des attestations, recherche paginee (jokers % _ \ echappes, ESCAPE '\' sur PostgreSQL), plus les
# courses reelles deja couvertes (R1, capture inter-centres, fiche) ; verrou du centre pris en tete de toute ecriture d'attestation.
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-pc4-pg16-rejeu.sh 55464 > rejeu-pc4.log 2>&1 &
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

echo "=== Colonnes lues ou ecrites par les adapters JDBC du module (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes attestation_droit id patient_id center_id date_debut date_fin created_at
verifier_colonnes patients id center_id code_patient nom prenom numero_assurance
verifier_colonnes planning_parametres center_id jours_ouverts updated_at

echo "=== PC4 : requetes SELECT-only de verifications-production.md, executables sur le schema reel ==="
req() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }; }
req "PC4-0a (fuseau de la session PostgreSQL)" "SHOW timezone"
req "PC4-0b (fuseaux des centres : NULL, vides, valeurs reconnues par PostgreSQL)" "SELECT id, name, fuseau_horaire, (fuseau_horaire IS NULL OR TRIM(fuseau_horaire) = '') AS sans_fuseau FROM centers ORDER BY name"
req "PC4-0c (fuseaux des centres reconnus)" "SELECT name FROM pg_timezone_names WHERE name IN (SELECT fuseau_horaire FROM centers) ORDER BY 1"
req "PC4-1 (attestations orphelines : patient absent ou d'un autre centre)" "SELECT a.id, a.center_id FROM attestation_droit a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL LIMIT 50"
req "PC4-2 (periodes inversees)" "SELECT center_id, count(*) FROM attestation_droit WHERE date_fin < date_debut GROUP BY 1"
req "PC4-3 (doublons exacts de periode pour un patient)" "SELECT patient_id, date_debut, date_fin, count(*) FROM attestation_droit GROUP BY 1, 2, 3 HAVING count(*) > 1 ORDER BY 4 DESC LIMIT 50"
req "PC4-4 (chevauchements pour un patient)" "SELECT a.patient_id, a.id, b.id FROM attestation_droit a JOIN attestation_droit b ON a.patient_id = b.patient_id AND a.id < b.id AND a.date_debut <= b.date_fin AND b.date_debut <= a.date_fin LIMIT 50"
req "PC4-5 (patients non vacanciers a PEC validee sans attestation couvrant aujourd'hui)" "SELECT c.center_id, count(*) FROM prise_en_charge c JOIN patients p ON p.id = c.patient_id WHERE c.statut = 'VALIDEE' AND NOT EXISTS (SELECT 1 FROM attestation_droit a WHERE a.patient_id = c.patient_id AND a.center_id = c.center_id AND current_date BETWEEN a.date_debut AND a.date_fin) GROUP BY 1"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL du module (patients, mouvements, assures, PEC, attestations) et courses reelles ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.PatientsJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
