#!/bin/bash
# Rejeu de la slice RF3a (chaine des payeurs : caisses d'assurance, agences, centres payeurs) de patient-couverture sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues ou
# ecrites par les adapters (caisse_assurance, agence, centre_payeur, patients.centre_payeur_id) existent dans le schema reel, execution
# des requetes de lecture sur le schema reel, puis le contrat PostgreSQL de la chaine des payeurs : le meme jeu de tests que le double
# en memoire et que H2 (PayeursPostgresContractIT : ecritures par niveau, signal d'invalidation, page avec parent du meme centre,
# recherche avec jokers litteraux, compte des references).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-rf3-pg16-rejeu.sh 55794 (sortie dans le log du lanceur)
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55794}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
[ -n "$(printf '%s' "$PW" | tee "$TMP/pw")" ] || exit 3 # le mot de passe va dans le fichier, jamais a l'ecran
trap '"$PGBIN/pg_ctl" -D "$TMP/data" -s stop -m fast || true; rm -rf "$TMP"' EXIT
SORTIE="$("$PGBIN/initdb" -D "$TMP/data" -U patient_couverture_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C 2>&1)" || { echo "$SORTIE"; exit 3; }
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 -s start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U patient_couverture_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do
  SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" 2>&1)" || { echo "ECHEC de $f"; echo "$SORTIE"; exit 3; }
done
echo "Chaine Flyway appliquee"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonnes lues ou ecrites par les adapters (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes caisse_assurance id center_id code nom type_caisse
verifier_colonnes agence id center_id code nom caisse_id
verifier_colonnes centre_payeur id center_id code nom adresse agence_id
verifier_colonnes patients centre_payeur_id

echo "=== RF3a : requetes de lecture executables sur le schema reel, SELECT seulement ==="
req() { SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" 2>&1)" && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; echo "$SORTIE"; RC=1; }; }
req "caisses d'un centre" "SELECT id, center_id, code, nom, type_caisse FROM caisse_assurance WHERE center_id = gen_random_uuid() ORDER BY code, id"
req "agences d'un centre" "SELECT id, center_id, code, nom, caisse_id FROM agence WHERE center_id = gen_random_uuid() ORDER BY code, id"
req "centres payeurs d'un centre" "SELECT id, center_id, code, nom, adresse, agence_id FROM centre_payeur WHERE center_id = gen_random_uuid() ORDER BY code, id"
req "page des agences avec recherche litterale et caisse du meme centre" "SELECT t.id, t.code, t.nom, t.caisse_id AS parent_id, CONCAT(r.code, ' · ', r.nom) AS parent_libelle FROM agence t LEFT JOIN caisse_assurance r ON r.id = t.caisse_id AND r.center_id = t.center_id WHERE t.center_id = gen_random_uuid() AND (LOWER(COALESCE(t.code, '')) LIKE '%\\%%' ESCAPE '\\' OR LOWER(COALESCE(r.nom, '')) LIKE '%\\_%' ESCAPE '\\') ORDER BY t.code, t.id LIMIT 20 OFFSET 0"
req "page des centres payeurs avec agence du meme centre" "SELECT t.id, t.code, t.nom, t.adresse, t.agence_id AS parent_id, CONCAT(r.code, ' · ', r.nom) AS parent_libelle FROM centre_payeur t LEFT JOIN agence r ON r.id = t.agence_id AND r.center_id = t.center_id WHERE t.center_id = gen_random_uuid() ORDER BY t.code, t.id LIMIT 20 OFFSET 0"
req "references d'une caisse (agences)" "SELECT COUNT(*) FROM agence WHERE caisse_id = gen_random_uuid()"
req "references d'une agence (centres payeurs)" "SELECT COUNT(*) FROM centre_payeur WHERE agence_id = gen_random_uuid()"
req "references d'un centre payeur (patients)" "SELECT COUNT(*) FROM patients WHERE centre_payeur_id = gen_random_uuid()"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL de la chaine des payeurs ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.PayeursPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
