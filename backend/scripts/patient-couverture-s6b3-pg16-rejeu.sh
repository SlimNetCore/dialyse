#!/bin/bash
# Rejeu de S6b-3 (ADR 0134, KB-S6a-2 : etat d'origine d'un assure rapproche, colonne migration_id_map.etat_origine) sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql, V57 comprise), verification de la colonne (additive,
# nullable), puis les requetes ecrites par IdMappingJdbcAdapter (INSERT avec et sans etat d'origine, SELECT du lot) dans une transaction
# ANNULEE (rien n'est conserve).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-s6b3-pg16-rejeu.sh 55851
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55851}"
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
echo "Chaine Flyway appliquee (derniere : $(cd "$MIG" && ls V*.sql | sort -V | tail -1))"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonne etat_origine : presente, nullable, texte ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='migration_id_map' AND column_name='etat_origine' AND is_nullable='YES' AND data_type='character varying'")" = "1" ] \
  || { echo "ATTENDU : migration_id_map.etat_origine nullable varchar"; RC=1; }
echo "colonne verifiee"

echo "=== Requetes de IdMappingJdbcAdapter sur le schema reel (transaction annulee) ==="
SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtA <<'SQL' 2>&1
BEGIN;
INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, etat_origine)
  VALUES (gen_random_uuid(), '99997800-0000-0000-0000-00000000000a', '99997800-0000-0000-0000-00000000b001', 'ASSURES', 'A1', 'ZR1', 'UPDATED', now(), 'v=1&nom=EXISTANT');
INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, etat_origine)
  VALUES (gen_random_uuid(), '99997800-0000-0000-0000-00000000000a', '99997800-0000-0000-0000-00000000b001', 'ASSURES', 'A2', 'ZR2', 'CREATED', now(), NULL);
SELECT entity, legacy_id, target_id, operation, etat_origine FROM migration_id_map
  WHERE center_id = '99997800-0000-0000-0000-00000000000a' AND batch_id = '99997800-0000-0000-0000-00000000b001' ORDER BY created_at;
ROLLBACK;
SQL
)" || { echo "ECHEC des requetes"; echo "$SORTIE"; RC=1; }
echo "$SORTIE"
echo "$SORTIE" | grep -q "ASSURES|A1|ZR1|UPDATED|v=1&nom=EXISTANT" || { echo "ATTENDU : etat d'origine relu"; RC=1; }
echo "=== Code de sortie : $RC ==="
exit $RC
