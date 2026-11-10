#!/bin/bash
# Rejeu de la slice RF2 (catalogue de forfaits) de patient-couverture sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit
# en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues ou ecrites par les adapters (forfait,
# prise_en_charge, facture_lignes, seances) existent dans le schema reel, execution des requetes de lecture sur le schema reel, puis le
# contrat PostgreSQL du catalogue de forfaits : le meme jeu de tests que le double en memoire et que H2 (ForfaitsPostgresContractIT :
# ecritures, page triee, recherche avec jokers litteraux, prix numeric(10,2), compte des references des prises en charge SANS regarder
# le centre (KB-RF2-02), langage publie, prix nul).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-rf2-pg16-rejeu.sh 55792 (sortie dans le log du lanceur)
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55792}"
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
verifier_colonnes forfait id center_id code libelle prix
verifier_colonnes prise_en_charge center_id forfait_demande_id forfait_effectif_id
verifier_colonnes facture_lignes forfait_id
verifier_colonnes seances forfait_override_id

echo "=== Type du prix : numeric(10,2) ==="
[ "$(scalaire "SELECT numeric_precision || ',' || numeric_scale FROM information_schema.columns WHERE table_schema='public' AND table_name='forfait' AND column_name='prix'")" = "10,2" ] \
  || { echo "ATTENDU : forfait.prix numeric(10,2)"; RC=1; }

echo "=== RF2 : requetes de lecture executables sur le schema reel, SELECT seulement ==="
req() { SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" 2>&1)" && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; echo "$SORTIE"; RC=1; }; }
req "liste des forfaits d'un centre" "SELECT id, code, libelle, prix FROM forfait WHERE center_id = gen_random_uuid() ORDER BY code"
req "forfait d'un centre par identifiant" "SELECT id, code, libelle, prix FROM forfait WHERE center_id = gen_random_uuid() AND id = gen_random_uuid()"
req "page d'administration avec recherche litterale" "SELECT t.id, t.code, t.libelle, t.prix FROM forfait t WHERE t.center_id = gen_random_uuid() AND (LOWER(COALESCE(t.code, '')) LIKE '%\\%%' ESCAPE '\\' OR LOWER(COALESCE(t.libelle, '')) LIKE '%\\_%' ESCAPE '\\') ORDER BY t.code, t.id LIMIT 20 OFFSET 0"
req "references d'un forfait par les prises en charge (demande puis effectif, sans centre : KB-RF2-02)" "SELECT (SELECT count(*) FROM prise_en_charge WHERE forfait_demande_id = gen_random_uuid()) + (SELECT count(*) FROM prise_en_charge WHERE forfait_effectif_id = gen_random_uuid())"
req "references d'un forfait par les lignes de facture" "SELECT count(*) FROM facture_lignes WHERE forfait_id = gen_random_uuid()"
req "references d'un forfait imposé a une seance" "SELECT count(*) FROM seances WHERE forfait_override_id = gen_random_uuid()"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL du catalogue de forfaits ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.ForfaitsPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
