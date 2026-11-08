#!/bin/bash
# Rejeu de la slice SE5 (forfait surcharge d'une seance) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire
# detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes forfait_override_* de seances et colonnes de forfait lues par le module
# (schema reel, types et bornes) ; aucune table ni colonne ajoutee ; requetes de controle de verifications-production.md section 57
# (SELECT seule) executables sur le schema reel ; puis le contrat PostgreSQL des seances (dont le verrou de ligne de deux impositions
# simultanees) et le contrat PostgreSQL du referentiel des forfaits fourni par la plateforme.
# A lancer en arriere-plan avec un log : backend/scripts/seance-se5-pg16-rejeu.sh 55505 > rejeu-seance-se5.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55505}"
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

echo "=== Colonnes du forfait surcharge (schema reel : types et bornes de l'instantane) ==="
colonne seances forfait_override_id uuid ""
colonne seances forfait_override_code "character varying" 50
colonne seances forfait_override_nom "character varying" 255
colonne seances forfait_override_prix numeric ""
colonne seances forfait_override_updated_at "timestamp with time zone" ""
colonne seances forfait_override_updated_by "character varying" 100
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='seances'")" = "28" ] \
  && echo "ok : seances a exactement 28 colonnes (aucune ajoutee par SE5)" || { echo "INATTENDU : nombre de colonnes de seances"; RC=1; }

echo "=== Colonnes du referentiel lues par le fournisseur de la plateforme (forfait) ==="
colonne forfait id uuid ""
colonne forfait center_id uuid ""
colonne forfait code "character varying" 50
colonne forfait libelle "character varying" 255
colonne forfait prix numeric ""
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='forfait' AND column_name='prix' AND is_nullable='YES'")" = "1" ] \
  && echo "ok : forfait.prix est NULLABLE (KB-SEA-41 : un forfait sans prix peut etre impose)" || { echo "INATTENDU : forfait.prix n'est plus nullable"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='forfait'")" = "5" ] \
  && echo "ok : forfait a cinq colonnes" || { echo "INATTENDU : colonnes de forfait"; RC=1; }

echo "=== Aucune table d'historique des surcharges (KB-SEA-45 : reproduit, aucune table ajoutee) ==="
for t in seance_forfait_historique forfaits_imposes historique_forfaits_seance seance_forfaits; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== SE5 : requetes de controle (SELECT seule, section 57), executables sur le schema reel ==="
for q in \
  "SELECT s.center_id, count(*) AS surchargees_sans_prix_ou_a_zero FROM seances s WHERE s.forfait_override_id IS NOT NULL AND (s.forfait_override_prix IS NULL OR s.forfait_override_prix = 0) GROUP BY s.center_id" \
  "SELECT s.center_id, s.statut, count(*) AS surchargees FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.statut IN ('CREE','ABSENT') GROUP BY s.center_id, s.statut" \
  "SELECT s.center_id, s.id, s.statut, s.facture_id, s.forfait_override_updated_at FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.facture_id IS NOT NULL AND s.forfait_override_updated_at IS NOT NULL ORDER BY s.forfait_override_updated_at DESC LIMIT 50" \
  "SELECT s.center_id, count(*) AS id_sans_code FROM seances s WHERE s.forfait_override_id IS NOT NULL AND (s.forfait_override_code IS NULL OR s.forfait_override_code = '') GROUP BY s.center_id" \
  "SELECT s.center_id, s.id, s.forfait_override_code, f.code AS code_du_referentiel FROM seances s INNER JOIN forfait f ON f.id = s.forfait_override_id AND f.center_id = s.center_id WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS DISTINCT FROM f.code" \
  "SELECT s.center_id, s.forfait_override_updated_by, count(*) AS surcharges FROM seances s WHERE s.forfait_override_id IS NOT NULL GROUP BY s.center_id, s.forfait_override_updated_by ORDER BY 3 DESC LIMIT 50" \
  "SELECT s.center_id, s.id FROM seances s LEFT JOIN forfait f ON f.id = s.forfait_override_id AND f.center_id = s.center_id WHERE s.forfait_override_id IS NOT NULL AND f.id IS NULL"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL des seances (forfait impose, ecriture des seules colonnes forfait_override_*, verrou de ligne de deux impositions) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.SeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Contrat PostgreSQL du referentiel des forfaits fourni par la plateforme ==="
./mvnw -o -pl platform -am test \
  -Dtest="com.hemodialyse.backend.infrastructure.acl.seance.LecturesDeLaSeancePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
