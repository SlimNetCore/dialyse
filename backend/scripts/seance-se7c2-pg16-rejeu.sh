#!/bin/bash
# Rejeu de la sous-slice SE7c2 (ecritures et lectures de la table des seances par la Facturation) sur un PostgreSQL 16 REEL et
# JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), puis le contrat PostgreSQL de la course de deux
# emissions simultanees sur les memes seances (LiaisonConcurrentePostgresContractIT, ecrit dans la base jetable uniquement) et le
# contrat des seances de la Facturation (SeancesPostgresContractIT).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7c2-pg16-rejeu.sh 55545
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# FACTURATION_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven (le test la lit dans
# l'environnement). Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55545}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${FACTURATION_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U facturation_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
export FACTURATION_PG16_PASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U facturation_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
RC=0
echo "=== Contraintes reelles de seances.facture_id (aucune cle etrangere vers factures attendue) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name WHERE tc.table_name = 'seances' AND tc.constraint_type = 'FOREIGN KEY' AND k.column_name = 'facture_id'" | tr -d '\r'
echo "=== SE7c2 : requetes de controle (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT s.center_id, count(*) AS seances_facturees_sans_facture FROM seances s WHERE s.statut = 'FACTUREE' AND s.facture_id IS NULL GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_facture_sans_statut_facturee FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_liees_a_une_facture_inexistante FROM seances s WHERE s.facture_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM factures f WHERE f.id = s.facture_id) GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_liees_a_la_facture_d_un_autre_centre FROM seances s INNER JOIN factures f ON f.id = s.facture_id WHERE f.center_id <> s.center_id GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS surcharges_avec_id_sans_code FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS NULL GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS memoires_d_exclusion_blanches FROM seances s WHERE s.statut_avant_exclusion IS NOT NULL AND trim(s.statut_avant_exclusion) = '' GROUP BY s.center_id" \
  "SELECT s.center_id, s.statut, count(*) AS statuts_hors_casse_canonique FROM seances s WHERE s.statut <> upper(s.statut) GROUP BY s.center_id, s.statut"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
echo "=== Contrat PostgreSQL (module Seance, ADR 0097) : course de deux emissions et ports publies de la Seance pour la Facturation ==="
# Depuis SE7c2 lot B : les ecritures vivent dans contexts/seance (ports publies) ; la variable d'environnement est SEANCE_PG16_PASSWORD.
export SEANCE_PG16_PASSWORD="$PW"
./mvnw -o -pl contexts/seance -am test \
  -Dtest="LiaisonConcurrentePostgresContractIT,FacturationDesSeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="$URL" -Dseance.pg16.user=facturation_test || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
