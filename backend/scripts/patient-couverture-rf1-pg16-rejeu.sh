#!/bin/bash
# Rejeu de la slice RF1 (transporteurs et categories de transport) de patient-couverture sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues ou ecrites par les adapters
# (transporteur, categorie_transport, patients) existent dans le schema reel, puis le contrat PostgreSQL du carnet de transporteurs : le
# meme jeu de tests que le double en memoire et que H2 (TransporteursPostgresContractIT : ecritures, page triee, recherche avec jokers
# litteraux, compte des references aller et retour de tous les centres, langage publie, categories).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-rf1-pg16-rejeu.sh 55465 > rejeu-rf1.log 2>&1 &
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

echo "=== Colonnes lues ou ecrites par les adapters (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes transporteur id center_id nom telephone
verifier_colonnes categorie_transport id center_id libelle
verifier_colonnes patients transporteur_aller_id transporteur_retour_id categorie_transport_id

echo "=== RF1 : requetes de lecture executables sur le schema reel, SELECT seulement ==="
req() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }; }
req "liste des transporteurs d'un centre" "SELECT id, nom FROM transporteur WHERE center_id = gen_random_uuid() ORDER BY nom"
req "page d'administration avec recherche litterale" "SELECT t.id, t.nom, t.telephone FROM transporteur t WHERE t.center_id = gen_random_uuid() AND (LOWER(COALESCE(t.nom, '')) LIKE '%\\%%' ESCAPE '\\' OR LOWER(COALESCE(t.telephone, '')) LIKE '%\\_%' ESCAPE '\\') ORDER BY t.nom, t.id LIMIT 20 OFFSET 0"
req "references d'un transporteur (aller et retour, tous centres)" "SELECT (SELECT count(*) FROM patients WHERE transporteur_aller_id = gen_random_uuid()) + (SELECT count(*) FROM patients WHERE transporteur_retour_id = gen_random_uuid())"
req "categories de transport d'un centre" "SELECT id, libelle FROM categorie_transport WHERE center_id = gen_random_uuid() ORDER BY libelle"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL du carnet de transporteurs ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.TransporteursPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
